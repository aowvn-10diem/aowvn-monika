package vn.aow.monika.apkinstall

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException

/**
 * Hai ContentProvider nhận tin/dữ liệu từ bộ nạp trong game chỉ chấp nhận gói đã đăng ký đúng việc.
 * Trong test không có "người gọi" thật (callingPackage = null) nên kiểm các đường TỪ CHỐI; không được lộ dữ liệu hay đổi trạng thái.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class LoaderProvidersTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val pkg = "com.fixture.game"
    private val jobId = "job-loader-test"

    @After fun cleanup() {
        DataJobs.remove(jobId)
        RepackRegistry.remove(context, pkg)
        LoaderEvents.reset(pkg)
    }

    private fun job() = DataJob(jobId, pkg, listOf(DataFile("data/a.bin", Payload("a.bin", 3) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })))

    @Test fun repackRegistryTracksKnownPackagesOnly() {
        assertFalse(RepackRegistry.isKnown(context, null))
        assertFalse(RepackRegistry.isKnown(context, pkg))

        RepackRegistry.add(context, pkg)
        assertTrue(RepackRegistry.isKnown(context, pkg))
        assertFalse(RepackRegistry.isKnown(context, "com.other.app"))

        RepackRegistry.remove(context, pkg)
        assertFalse(RepackRegistry.isKnown(context, pkg))
    }

    @Test fun dataJobsRegisterLookupAndRemove() {
        val job = job()
        DataJobs.register(job)
        assertEquals(job, DataJobs.get(jobId))

        DataJobs.remove(jobId)
        assertNull(DataJobs.get(jobId))
    }

    @Test fun busProviderRejectsCallsFromUnregisteredCallers() {
        val provider = Robolectric.buildContentProvider(LoaderBusProvider::class.java).create().get()
        LoaderEvents.reset(pkg)
        val extras = Bundle().apply { putString("summary", "boom") }

        assertNull(provider.call("started", null, extras))
        assertNull(provider.call("crash", null, extras))

        val state = LoaderEvents.state(pkg)
        assertEquals(0L, state.startedAt)
        assertEquals(0L, state.crashAt)
        assertNull(state.crashSummary)
    }

    @Test fun providersExposeNoQueryableData() {
        val bus = Robolectric.buildContentProvider(LoaderBusProvider::class.java).create().get()
        val data = Robolectric.buildContentProvider(GameDataProvider::class.java).create().get()
        val uri = Uri.parse("content://${context.packageName}.gamedata/x/manifest")

        for (provider in listOf(bus, data)) {
            assertNull(provider.query(uri, null, null, null, null))
            assertNull(provider.getType(uri))
            assertNull(provider.insert(uri, ContentValues()))
            assertEquals(0, provider.delete(uri, null, null))
            assertEquals(0, provider.update(uri, ContentValues(), null, null))
        }
    }

    @Test fun dataProviderRejectsMalformedUnknownAndUnauthorizedRequests() {
        val provider = Robolectric.buildContentProvider(GameDataProvider::class.java).create().get()
        val base = "content://${context.packageName}.gamedata"

        for (bad in listOf("$base/only-one-segment", "$base/a/b/c")) {
            try { provider.openFile(Uri.parse(bad), "r"); fail("phải từ chối $bad") } catch (_: FileNotFoundException) {}
        }
        try { provider.openFile(Uri.parse("$base/khong-co-job/manifest"), "r"); fail("job không tồn tại") } catch (_: FileNotFoundException) {}

        // Có job nhưng người gọi không phải gói của job (callingPackage = null) => bị từ chối, kể cả khi gói đã đăng ký.
        DataJobs.register(job())
        RepackRegistry.add(context, pkg)
        for (path in listOf("manifest", "0")) {
            try { provider.openFile(Uri.parse("$base/$jobId/$path"), "r"); fail("phải từ chối người gọi lạ: $path") } catch (_: SecurityException) {}
        }
    }
}
