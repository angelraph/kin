package app.kin.solana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaucetProgramTest {
    @Test
    fun addressesMatchTheDeployedFaucet() {
        // Derived independently with @solana/web3.js when the faucet was deployed to devnet.
        assertEquals("25Un8ofZie7Xt8Q3Fz4wdLW5w86XEgQpQyW34cz35vsY", FaucetProgram.MINT_AUTHORITY.toBase58())
        assertEquals("3y8zB5PX4o9equB9pZGpgG1kPpet44EAnS4NDBGaEGRD", FaucetProgram.SOL_VAULT.toBase58())
    }

    @Test
    fun claimAndRefuelHaveTheRightShape() {
        val user = PublicKey(ByteArray(32) { 7 })
        val mint = PublicKey(ByteArray(32) { 8 })
        val refuel = FaucetProgram.refuel(user)
        val claim = FaucetProgram.claim(user, mint)
        assertEquals(3, refuel.accounts.size)
        assertEquals(8, claim.accounts.size)
        assertTrue(refuel.accounts[0].isSigner && claim.accounts[0].isSigner)
        assertEquals(8, refuel.data.size)
        assertEquals(8, claim.data.size)
        assertTrue(!refuel.data.contentEquals(claim.data))
    }

    @Test
    fun explainsFaucetErrorsInPlainWords() {
        assertNotNull(FaucetProgram.explain("custom program error: 0x1771"))
        assertNotNull(FaucetProgram.explain("Error Code: VaultEmpty"))
        assertNull(FaucetProgram.explain("insufficient funds"))
        assertNull(FaucetProgram.explain(null))
    }
}
