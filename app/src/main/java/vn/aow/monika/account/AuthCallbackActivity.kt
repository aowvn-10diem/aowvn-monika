package vn.aow.monika.account

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import vn.aow.monika.AppGraph
import vn.aow.monika.ui.MainActivity

/** Nhận `aowmonika://login#...` từ trang đăng nhập web, lưu phiên rồi quay về màn hình trước. */
class AuthCallbackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val error = AppGraph.account.complete(intent?.data?.fragment ?: intent?.data?.encodedFragment)
        val s = AppGraph.account.session.value
        Toast.makeText(this, error ?: "Đã đăng nhập: ${s?.name ?: s?.email}", Toast.LENGTH_LONG).show()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
