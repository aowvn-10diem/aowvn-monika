package vn.aow.monika.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent

/** Mở trang web bằng Chrome Custom Tab (đăng nhập Google được, bấm X là về app); máy không hỗ trợ → trình duyệt. */
object CustomTab {
    fun open(activity: Activity, url: Uri) {
        runCatching {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF202124.toInt()).build())
                .setShowTitle(true).build()
                .launchUrl(activity, url)
        }.onFailure { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, url)) } }
    }
}
