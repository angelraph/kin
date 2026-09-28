package app.kin.name

import app.kin.Config
import app.kin.solana.Pda
import app.kin.solana.PublicKey
import app.kin.solana.SolanaRpc
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Resolves a wallet's AllDomains "main domain" (e.g. a .skr domain Seeker hands out by default, or a .sol
 * one) so the app can show a name instead of an address. This is read-only and has nothing to do with
 * which cluster a circle runs on: domains only exist on mainnet, so this always reads mainnet over its own
 * connection, never the app's devnet one. A missing domain, a network error or a malformed account are all
 * the same outcome to the caller: no name, fall back to the address.
 *
 * Program, PDA seeds and account layout are taken directly from AllDomains' tld-parser source
 * (github.com/onsol-labs/tld-parser, src/svm/constants.ts and src/svm/state/main-domain.ts) and confirmed
 * against live mainnet accounts, including real .skr examples such as "nasa.skr".
 */
class NameResolver(rpcUrl: String = Config.NAME_RESOLUTION_RPC_URL) {
    private val rpc = SolanaRpc(rpcUrl)
    private val cache = mutableMapOf<String, String?>()

    companion object {
        private val TLD_HOUSE_PROGRAM_ID = PublicKey.fromBase58("TLDHkysf5pCnKsVA4gXpNvmy7psXLPEu4LAdDJthT9S")
        private val MAIN_DOMAIN_PREFIX = "main_domain".toByteArray()
        // The MainDomain account discriminator, [109,239,227,199,98,226,66,175] as unsigned bytes.
        private val DISCRIMINATOR = byteArrayOf(109, -17, -29, -57, 98, -30, 66, -81)

        fun mainDomainAddress(wallet: PublicKey): PublicKey = Pda.find(listOf(MAIN_DOMAIN_PREFIX, wallet.bytes), TLD_HOUSE_PROGRAM_ID).first

        /** Parses a MainDomain account's raw data into "domain" + "tld" (e.g. "nasa" + ".skr"), or null if malformed. */
        fun parse(data: ByteArray): String? {
            if (data.size < 8 || !data.copyOfRange(0, 8).contentEquals(DISCRIMINATOR)) return null
            var offset = 8 + 32 // skip discriminator and the nameAccount pubkey
            fun readString(): String? {
                if (offset + 4 > data.size) return null
                val len = ((data[offset].toInt() and 0xff) or
                    ((data[offset + 1].toInt() and 0xff) shl 8) or
                    ((data[offset + 2].toInt() and 0xff) shl 16) or
                    ((data[offset + 3].toInt() and 0xff) shl 24))
                offset += 4
                if (len < 0 || offset + len > data.size) return null
                val s = String(data, offset, len, Charsets.UTF_8)
                offset += len
                return s
            }
            val tld = readString() ?: return null
            val domain = readString() ?: return null
            return "$domain$tld"
        }
    }

    /** Resolves one wallet's name, or null if it has none (or the lookup could not complete). Cached in memory. */
    suspend fun resolve(wallet: PublicKey): String? {
        val key = wallet.toBase58()
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return null
        val name = runCatching {
            rpc.accountInfo(mainDomainAddress(wallet))?.let { parse(it.data) }
        }.getOrNull()
        cache[key] = name
        return name
    }

    /** Resolves several wallets in parallel. Missing or failed lookups are simply absent from the result. */
    suspend fun resolveMany(wallets: List<PublicKey>): Map<String, String> = coroutineScope {
        wallets.distinct()
            .map { it to async { resolve(it) } }
            .mapNotNull { (wallet, deferred) -> deferred.await()?.let { wallet.toBase58() to it } }
            .toMap()
    }
}
