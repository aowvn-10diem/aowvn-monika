package vn.aow.monika.library

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Danh sách việc giải nén đang chạy hiện thành ô riêng trong Thư viện. */
@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class GameTasksTest {
    private val context get() = ApplicationProvider.getApplicationContext<Application>()

    @Before fun clear() { GameTasks.running.value = emptyMap() }

    private fun task(id: String, percent: Int? = null) = GameTask(id, "Game $id", null, "Đang giải nén", percent)

    @Test fun putReplacesTaskWithSameId() {
        GameTasks.put(task("a", 10))
        GameTasks.put(task("b"))
        GameTasks.put(task("a", 20))

        assertEquals(setOf("a", "b"), GameTasks.running.value.keys)
        assertEquals(20, GameTasks.running.value.getValue("a").percent)
    }

    @Test fun progressUpdatesOnlyKnownTasksAndKeepsOtherFields() {
        GameTasks.put(task("a"))

        GameTasks.progress("a", 55)
        GameTasks.progress("ghost", 99)

        val a = GameTasks.running.value.getValue("a")
        assertEquals(55, a.percent)
        assertEquals("Game a", a.title)
        assertEquals("Đang giải nén", a.phase)
        assertNull(GameTasks.running.value["ghost"])
    }

    @Test fun removeDropsTaskAndIgnoresUnknownIds() {
        GameTasks.put(task("a")); GameTasks.put(task("b"))

        GameTasks.remove("a")
        GameTasks.remove("khong-co")

        assertEquals(setOf("b"), GameTasks.running.value.keys)
    }

    @Test fun noRunningDownloadsGivesEmptyList() {
        assertTrue(GameTasks.downloads(context).isEmpty())
    }
}
