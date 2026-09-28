package app.kin.name

import app.kin.solana.PublicKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class NameResolverTest {
    // A real MainDomain account on mainnet, captured by scanning the AllDomains TLD House program:
    // it holds the .skr domain "nasa". Bytes confirmed to decode and parse to "nasa.skr" independently
    // in Node before being embedded here.
    private val realSkrAccount = Base64.getDecoder().decode(
        "be/jx2LiQq8KBbBEh1m9Drnl+3waqIXmUVN9r4K0ibGAnZuTABV6wAQAAAAuc2tyBAAAAG5hc2EAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==",
    )

    @Test
    fun parseReadsARealMainnetSkrDomain() {
        assertEquals("nasa.skr", NameResolver.parse(realSkrAccount))
    }

    @Test
    fun parseRejectsDataThatIsTooShortToBeAMainDomainAccount() {
        assertNull(NameResolver.parse(ByteArray(10)))
    }

    @Test
    fun parseRejectsTheWrongDiscriminator() {
        val wrong = realSkrAccount.copyOf()
        wrong[0] = 0
        assertNull(NameResolver.parse(wrong))
    }

    @Test
    fun parseRejectsALengthThatWouldRunPastTheEndOfTheBuffer() {
        // Cuts off partway through the domain string itself, not the account's trailing zero padding.
        val truncated = realSkrAccount.copyOf(54)
        assertNull(NameResolver.parse(truncated))
    }

    @Test
    fun mainDomainAddressMatchesTheReferenceSdkForAKnownWallet() {
        // Computed independently with @onsol/tldparser's own findMainDomain() against the same wallet.
        val wallet = PublicKey.fromBase58("2EGGxj2qbNAJNgLCPKca8sxZYetyTjnoRspTPjzN2D67")
        assertEquals("3P1RoQJyfjTGqDVQWC9Kc6JfXSgqdMwB6s8HGvjyUUTC", NameResolver.mainDomainAddress(wallet).toBase58())
    }

    @Test
    fun mainDomainAddressIsDifferentForDifferentWallets() {
        val a = PublicKey(ByteArray(32) { 1 })
        val b = PublicKey(ByteArray(32) { 2 })
        assertEquals(false, NameResolver.mainDomainAddress(a) == NameResolver.mainDomainAddress(b))
    }
}
