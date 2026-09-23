package app.kin.wallet

import android.net.Uri
import app.kin.Config
import app.kin.solana.Base58
import app.kin.solana.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult

sealed interface WalletResult<out T> {
    data class Ok<T>(val value: T) : WalletResult<T>
    data class Error(val message: String) : WalletResult<Nothing>
    data object NoWallet : WalletResult<Nothing>
}

/**
 * Talks to the user's wallet app through Mobile Wallet Adapter.
 * On a Seeker the wallet is the Seed Vault Wallet, so every signature needs on-device authentication.
 * Kin never holds keys: it builds an unsigned transaction and the wallet signs and sends it.
 */
class WalletSession(private val sender: ActivityResultSender) {
    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse(Config.IDENTITY_URI),
            iconUri = Uri.parse("favicon.ico"),
            identityName = Config.IDENTITY_NAME,
        ),
    ).also { it.blockchain = Solana.Devnet }

    var account: PublicKey? = null
        private set

    suspend fun connect(): WalletResult<PublicKey> =
        when (val r = adapter.connect(sender)) {
            is TransactionResult.Success -> {
                val key = PublicKey(r.authResult.accounts.first().publicKey)
                account = key
                // Without this, every later transact() starts a brand-new authorization instead of
                // reusing this approved session, which some wallets and OEM Android builds reject.
                adapter.authToken = r.authResult.authToken
                WalletResult.Ok(key)
            }
            is TransactionResult.NoWalletFound -> WalletResult.NoWallet
            is TransactionResult.Failure -> WalletResult.Error(r.e.message ?: "Could not connect to wallet")
        }

    /** Asks the wallet to sign and submit an unsigned serialized transaction; returns the signature. */
    suspend fun signAndSend(unsignedTx: ByteArray): WalletResult<String> {
        val result = adapter.transact(sender) { authResult ->
            account = PublicKey(authResult.accounts.first().publicKey)
            adapter.authToken = authResult.authToken
            signAndSendTransactions(arrayOf(unsignedTx))
        }
        return when (result) {
            is TransactionResult.Success -> {
                val sig = result.payload.signatures.firstOrNull()
                if (sig == null) WalletResult.Error("Wallet returned no signature") else WalletResult.Ok(Base58.encode(sig))
            }
            is TransactionResult.NoWalletFound -> WalletResult.NoWallet
            is TransactionResult.Failure -> {
                // A stale or rejected auth token is the likely cause of an authorization failure.
                // Clearing it means the next attempt starts a fresh authorization instead of repeating it.
                if (result.e.message?.contains("authoriz", ignoreCase = true) == true) {
                    adapter.authToken = null
                }
                WalletResult.Error(result.e.message ?: "Signing failed")
            }
        }
    }

    fun disconnect() {
        account = null
        adapter.authToken = null
    }
}
