package vn.aow.monika.ui

import android.app.Application
import vn.aow.monika.AppGraph

/** App rút gọn cho test giao diện: chỉ khởi tạo AppGraph (không đặt lịch WorkManager, không J2ME). */
class TestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
