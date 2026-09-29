package vn.aow.monika

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Bắt crash: ghi nội dung lỗi ra file rồi mở [CrashActivity] (tiến trình riêng) để người dùng sao chép gửi cho AowVN.
 * Không gửi gì lên mạng. Chạy trước mọi thứ khác trong Application.
 */
object CrashReporter {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                // Lưu vào kho báo cáo (kèm lõi/game đang chạy nếu là lỗi ở tiến trình game) + file "lỗi gần nhất" cho màn báo lỗi.
                val report = vn.aow.monika.diag.Diagnostics.recordJavaCrash(app, thread.name, e)
                file(app).writeText(report.toText())
                app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }
            if (previous != null && thread.name != "main") previous.uncaughtException(thread, e)
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    fun file(context: Context) = File(context.filesDir, "last-crash.txt")
}

/** Màn báo lỗi đơn giản (View thuần, không Compose) — chạy trong tiến trình ":crash" nên không bị lỗi cũ kéo theo. */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = runCatching { CrashReporter.file(this).readText() }.getOrDefault("Không đọc được nội dung lỗi.")
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad * 2, pad, pad); setBackgroundColor(0xFF202124.toInt()) }
        root.addView(TextView(this).apply {
            this.text = "Aow Monika gặp lỗi 😢\nBấm \"Sao chép lỗi\" rồi gửi cho AowVN để sửa nhanh."
            setTextColor(0xFFFFFFFF.toInt()); textSize = 17f
        })
        val body = TextView(this).apply { this.text = text; setTextColor(0xFFC8C5CB.toInt()); textSize = 11f; setTextIsSelectable(true); setPadding(0, pad, 0, pad) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        row.addView(Button(this).apply {
            this.text = "Sao chép lỗi"
            setOnClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lỗi Aow Monika", text))
                Toast.makeText(this@CrashActivity, "Đã sao chép. Dán gửi cho AowVN nhé!", Toast.LENGTH_LONG).show()
            }
        })
        row.addView(Button(this).apply { this.text = "Đóng"; setOnClickListener { finishAndRemoveTask() } })
        root.addView(row)
        setContentView(root)
    }
}
