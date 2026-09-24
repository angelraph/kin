package app.kin.wallet

import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.ExecutionException

class WalletSessionTest {
    private fun remote(code: Int, message: String) = JsonRpc20Client.JsonRpc20RemoteException(code, message, null)

    @Test
    fun aWalletRefusingAuthorizationIsRecognised() {
        assertTrue(isAuthorizationFailure(remote(-1, "authorization request failed")))
    }

    @Test
    fun theWrappedErrorTheLibraryActuallyReturnsIsRecognised() {
        // The library wraps the remote error in an ExecutionException. This is the exact shape seen on a
        // real phone, where the message reads "...JsonRpc20RemoteException: -1/authorization request failed".
        val wrapped = ExecutionException(remote(-1, "authorization request failed"))
        assertTrue(isAuthorizationFailure(wrapped))
        assertTrue(isAuthorizationFailure(RuntimeException("outer", wrapped)))
    }

    @Test
    fun otherWalletErrorsAreNotTreatedAsAuthorizationFailures() {
        // -2 is ERROR_NOT_SIGNED: the user declined to sign. Retrying would nag them for no reason.
        assertFalse(isAuthorizationFailure(remote(-2, "declined")))
        assertFalse(isAuthorizationFailure(ExecutionException(remote(-2, "declined"))))
        assertFalse(isAuthorizationFailure(remote(-4, "too many payloads")))
        assertFalse(isAuthorizationFailure(IOException("network down")))
        assertFalse(isAuthorizationFailure(ExecutionException(IOException("network down"))))
        assertFalse(isAuthorizationFailure(IllegalStateException("boom")))
    }

    @Test
    fun aFailureIsNeverBlank() {
        val timeout = ExecutionException(java.util.concurrent.TimeoutException())
        val text = describeFailure("Timed out while waiting for result", timeout)
        assertTrue(text.startsWith("Timed out while waiting for result"))
        assertTrue("names the underlying error even when it has no message", text.contains("TimeoutException"))
        assertTrue(describeFailure("", IllegalStateException()).isNotBlank())
    }

    @Test
    fun aCauseLoopCannotHangTheCheck() {
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b) // a and b now cause each other
        assertFalse(isAuthorizationFailure(a))
    }
}
