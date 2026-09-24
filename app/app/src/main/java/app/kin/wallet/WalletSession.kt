package app.kin.wallet

import android.net.Uri
import android.util.Log
import app.kin.Config
import app.kin.solana.PublicKey
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient.AuthorizationResult

sealed interface WalletResult<out T> {
    data class Ok<T>(val value: T) : WalletResult<T>
    data class Error(val message: String) : WalletResult<Nothing>
    data object NoWallet : WalletResult<Nothing>
}

/** Mobile Wallet Adapter's ERROR_AUTHORIZATION_FAILED. Wallets send it when they refuse an auth token. */
private const val ERROR_AUTHORIZATION_FAILED = -1

/**
 * True when the wallet rejected the request because it did not accept our authorization.
 * The library hands back the remote error wrapped inside an ExecutionException, so the whole
 * cause chain is checked, not just the outermost exception.
 */
internal fun isAuthorizationFailure(e: Throwable): Boolean =
    generateSequence(e) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .any { it is JsonRpc20Client.JsonRpc20RemoteException && it.code == ERROR_AUTHORIZATION_FAILED }

private const val MAX_CAUSE_DEPTH = 8

private const val TAG = "KinWallet"

/**
 * A readable reason for a failed wallet request. The library's own description comes first
 * ("Timed out while waiting for result"), then the underlying errors, so a failure is never a blank.
 */
internal fun describeFailure(libraryMessage: String, e: Throwable): String {
    val causes = generateSequence(e) { it.cause }
        .take(MAX_CAUSE_DEPTH)
        .mapNotNull { c -> c.message?.takeIf { it.isNotBlank() }?.let { "${c.javaClass.simpleName}: $it" } ?: c.javaClass.simpleName }
        .distinct()
        .toList()
    return (listOf(libraryMessage.ifBlank { "Wallet request failed" }) + causes).joinToString(" | ")
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
                WalletResult.Ok(key)
            }
            is TransactionResult.NoWalletFound -> WalletResult.NoWallet
            is TransactionResult.Failure -> WalletResult.Error(r.e.message ?: "Could not connect to wallet")
        }

    /**
     * Runs [block] against the wallet. The adapter remembers the auth token from the first call and
     * uses it to *reauthorize* on every later call. Some wallets, Phantom included, refuse that with
     * ERROR_AUTHORIZATION_FAILED. The protocol says the app should then drop the token and authorize
     * from scratch, which the library does not do for us, so it is done here, once.
     */
    private suspend fun <T> transactWithFreshAuthFallback(
        block: suspend AdapterOperations.(authResult: AuthorizationResult) -> T,
    ): TransactionResult<T> {
        val first = adapter.transact(sender, null, block)
        if (first is TransactionResult.Failure && isAuthorizationFailure(first.e)) {
            adapter.authToken = null
            return adapter.transact(sender, null, block)
        }
        return first
    }

    /**
     * Asks the wallet to sign a transaction and returns the signed bytes. The wallet only signs; Kin
     * submits the transaction itself, so a wallet that cannot broadcast on devnet cannot stall it, and
     * network errors come back with their real message.
     *
     * [buildTx] runs after the wallet has authorized, so the blockhash is fresh when it is signed even if
     * the user took a while to approve the connection.
     */
    suspend fun sign(buildTx: suspend () -> ByteArray): WalletResult<ByteArray> {
        val result = transactWithFreshAuthFallback { authResult ->
            account = PublicKey(authResult.accounts.first().publicKey)
            signTransactions(arrayOf(buildTx())).signedPayloads
        }
        return when (result) {
            is TransactionResult.Success -> {
                val signed = result.payload.firstOrNull()
                if (signed == null || signed.isEmpty()) WalletResult.Error("Wallet returned no signed transaction") else WalletResult.Ok(signed)
            }
            is TransactionResult.NoWalletFound -> WalletResult.NoWallet
            is TransactionResult.Failure -> {
                if (isAuthorizationFailure(result.e)) {
                    adapter.authToken = null
                    WalletResult.Error("The wallet would not authorize Kin. Open your wallet, check that Testnet Mode is on, and try again.")
                } else {
                    Log.w(TAG, "sign failed: ${result.message}", result.e)
                    WalletResult.Error(describeFailure(result.message, result.e))
                }
            }
        }
    }

    fun disconnect() {
        account = null
        adapter.authToken = null
    }
}
