package vn.aow.monika.cheats

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import org.json.JSONTokener

/**
 * Cheat cho game chạy trong WebView (RPG Maker MV/MZ, TyranoScript) + hệ số tốc độ cho mọi game web/Flash.
 * Monika chèn assets/cheats/web-boot.js vào trang game rồi gọi các hàm `__M.*`; kết quả là chuỗi JSON.
 * [eval] chạy 1 đoạn JavaScript trong trang và trả kết quả (đã bỏ lớp bọc chuỗi của evaluateJavascript).
 */
class WebCheatController(context: Context, private val eval: (String, (String?) -> Unit) -> Unit) {
    class TVar(val scope: String, val key: String, val value: Double)

    private val boot: String by lazy { runCatching { context.assets.open("cheats/web-boot.js").bufferedReader().readText() }.getOrDefault("") }

    var open by mutableStateOf(false)
    var engine by mutableStateOf("none")
    var flags by mutableStateOf<Map<String, Double>>(emptyMap())
    var speed by mutableStateOf(1.0)
    var message by mutableStateOf<String?>(null)
    var tyrano by mutableStateOf<List<TVar>>(emptyList())
    var varInfo by mutableStateOf<String?>(null)

    /** Gọi mỗi khi trang tải xong: chèn bộ cheat và áp lại tốc độ người chơi đã chọn. */
    fun onPageFinished() {
        if (boot.isEmpty()) return
        eval(boot) { if (speed != 1.0) call("speed", speed.toString()) }
    }

    private fun call(fn: String, args: String = "", done: (JSONObject?) -> Unit = {}) {
        eval("window.__M ? __M.$fn($args) : '{\"ok\":false,\"msg\":\"Chưa nạp bộ cheat\"}'") { raw ->
            done(runCatching {
                var v: Any? = JSONTokener(raw ?: "null").nextValue()
                if (v is String) v = JSONObject(v)
                v as? JSONObject
            }.getOrNull())
        }
    }

    fun show() { message = null; open = true; refresh() }
    fun close() { open = false }

    fun refresh() = call("detect") { o ->
        engine = o?.optString("engine") ?: "none"
        flags = o?.optJSONObject("flags")?.let { f -> f.keys().asSequence().associateWith { f.optDouble(it) } }.orEmpty()
        o?.optDouble("speed")?.takeIf { it > 0 }?.let { speed = it }
        if (engine == "tyrano") loadTyrano()
    }

    private fun report(o: JSONObject?) { message = o?.optString("msg")?.takeIf { it.isNotBlank() } ?: message }

    fun setToggle(key: String, on: Boolean) = call("toggle", "'$key',${if (on) 1 else 0}") { o -> if (o?.optBoolean("ok") == true) refresh() else report(o) }
    fun setMult(key: String, n: Int) = call("mult", "'$key',$n") { o -> if (o?.optBoolean("ok") == true) refresh() else report(o) }
    fun action(id: String, arg: Int? = null) = call("action", "'$id'" + (arg?.let { ",$it" } ?: "")) { report(it) }
    fun chooseSpeed(k: Double) { speed = k; call("speed", k.toString()) { report(it) } }

    fun setVar(id: Int, v: Int) = call("setVar", "$id,$v") { report(it) }
    fun setSwitch(id: Int, on: Boolean) = call("setSwitch", "$id,$on") { report(it) }
    fun readVar(id: Int) = call("getVar", "$id") { o ->
        varInfo = if (o?.optBoolean("ok") == true) "#$id ${o.optString("name")} = ${o.opt("value")}" else "Không đọc được"
    }

    fun loadTyrano() = call("tyranoVars") { o ->
        val arr = o?.optJSONArray("vars")
        tyrano = (0 until (arr?.length() ?: 0)).mapNotNull { arr?.optJSONObject(it) }.map { TVar(it.optString("scope"), it.optString("key"), it.optDouble("value")) }
    }

    fun tyranoAdd(v: TVar, delta: Double) = call("tyranoSet", "'${v.scope}','${v.key.replace("'", "\\'")}',${v.value + delta}") { report(it); loadTyrano() }
    fun tyranoMul(v: TVar, k: Double) = call("tyranoSet", "'${v.scope}','${v.key.replace("'", "\\'")}',${v.value * k}") { report(it); loadTyrano() }
}
