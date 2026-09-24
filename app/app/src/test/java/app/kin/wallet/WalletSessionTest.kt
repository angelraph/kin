package app.kin.wallet

import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WalletSessionTest {
    @Test
    fun aWalletRefusingAuthorizationIsRecognised() {
        // This is exactly what Phantom returns when it refuses to reauthorize a saved token.
        val e = JsonRpc20Client.JsonRpc20RemoteException(-1, "authorization request failed", null)
        assertTrue(isAuthorizationFailure(e))
    }

    @Test
    fun otherWalletErrorsAreNotTreatedAsAuthorizationFailures() {
        // -2 is ERROR_NOT_SIGNED: the user declined to sign. Retrying would nag them for no reason.
        assertFalse(isAuthorizationFailure(JsonRpc20Client.JsonRpc20RemoteException(-2, "declined", null)))
        assertFalse(isAuthorizationFailure(JsonRpc20Client.JsonRpc20RemoteException(-4, "too many payloads", null)))
        assertFalse(isAuthorizationFailure(IOException("network down")))
        assertFalse(isAuthorizationFailure(IllegalStateException("boom")))
    }
}
