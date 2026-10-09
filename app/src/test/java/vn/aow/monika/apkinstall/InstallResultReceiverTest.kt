package vn.aow.monika.apkinstall

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Kết quả PackageInstaller đi từ receiver tới bên đang chờ; hộp thoại xác nhận của Android được mở. Không cài gì thật. */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class InstallResultReceiverTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun result(session: Int, status: Int, message: String? = null): Intent =
        Intent(InstallResultReceiver.ACTION)
            .putExtra(InstallResultReceiver.EXTRA_SESSION, session)
            .putExtra(PackageInstaller.EXTRA_STATUS, status)
            .apply { if (message != null) putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message) }

    @Test fun successIsDeliveredToTheWaitingSession() = runBlocking {
        val waiting = InstallResults.expect(4101)

        InstallResultReceiver().onReceive(context, result(4101, PackageInstaller.STATUS_SUCCESS))

        assertEquals(InstallOutcome.Success, waiting.await())
    }

    @Test fun failureMessageIsTranslatedForThePlayer() = runBlocking {
        val waiting = InstallResults.expect(4102)

        InstallResultReceiver().onReceive(context, result(4102, PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_VERSION_DOWNGRADE"))

        val failure = waiting.await() as InstallOutcome.Failure
        assertEquals(InstallOutcome.Kind.DOWNGRADE, failure.kind)
        assertTrue(failure.raw.contains("VERSION_DOWNGRADE"))
    }

    @Test fun userCancelIsNotAFailure() = runBlocking {
        val waiting = InstallResults.expect(4103)

        InstallResultReceiver().onReceive(context, result(4103, PackageInstaller.STATUS_FAILURE_ABORTED))

        assertEquals(InstallOutcome.UserAborted, waiting.await())
    }

    @Test fun resultsAreRoutedBySessionId() = runBlocking {
        val first = InstallResults.expect(4104)
        val second = InstallResults.expect(4105)

        InstallResultReceiver().onReceive(context, result(4105, PackageInstaller.STATUS_SUCCESS))

        assertFalse(first.isCompleted)
        assertEquals(InstallOutcome.Success, second.await())
        InstallResults.forget(4104)
    }

    @Test fun forgottenOrUnknownSessionsAreIgnored() {
        val waiting = InstallResults.expect(4106)
        InstallResults.forget(4106)

        InstallResultReceiver().onReceive(context, result(4106, PackageInstaller.STATUS_SUCCESS))
        InstallResultReceiver().onReceive(context, result(999_999, PackageInstaller.STATUS_SUCCESS))

        assertFalse(waiting.isCompleted)
    }

    @Test fun pendingUserActionOpensConfirmDialogAndKeepsWaiting() {
        shadowOf(context).grantPermissions("android.permission.POST_NOTIFICATIONS")
        vn.aow.monika.notify.Notifier.createChannels(context)
        val waiting = InstallResults.expect(4107)
        val confirm = Intent("android.content.pm.action.CONFIRM_INSTALL").putExtra("fixture", 7)
        val pending = result(4107, PackageInstaller.STATUS_PENDING_USER_ACTION).putExtra(Intent.EXTRA_INTENT, confirm)

        InstallResultReceiver().onReceive(context, pending)

        val started = shadowOf(context).nextStartedActivity
        assertNotNull(started)
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(waiting.isCompleted)
        InstallResults.forget(4107)
    }

    @Test fun pendingUserActionWithoutIntentDoesNothing() {
        val waiting = InstallResults.expect(4108)

        InstallResultReceiver().onReceive(context, result(4108, PackageInstaller.STATUS_PENDING_USER_ACTION))

        assertNull(shadowOf(context).nextStartedActivity)
        assertFalse(waiting.isCompleted)
        InstallResults.forget(4108)
    }
}
