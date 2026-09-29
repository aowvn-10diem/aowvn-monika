package vn.aow.monika.browser

/**
 * Bộ lọc quảng cáo kiểu uBlock/AdGuard rút gọn (định dạng Adblock Plus): luật mạng (đường dẫn, mẫu, tùy chọn loại tài nguyên,
 * bên thứ ba, giới hạn theo trang) + luật ẩn phần tử (CSS) theo trang. Bổ sung cho bộ lọc theo TÊN MIỀN của [AdBlock].
 *
 * Tra cứu nhanh bằng chỉ mục theo "từ khóa" (mỗi luật gắn 1 đoạn chữ số/chữ trong mẫu; URL được tách thành các từ khóa
 * rồi chỉ thử các luật cùng từ khóa) → hàng chục nghìn luật vẫn tra trong micro giây, máy yếu vẫn ổn.
 * Luật không hiểu được (regex, tùy chọn lạ) bị bỏ qua thay vì áp sai.
 */
class FilterEngine {
    class Net(
        val parts: Array<String>, val hostAnchor: Boolean, val startAnchor: Boolean, val endAnchor: Boolean,
        val third: Int, val types: Int, val inc: Array<String>?, val exc: Array<String>?,
    )

    private val block = HashMap<String, ArrayList<Net>>()
    private val allow = HashMap<String, ArrayList<Net>>()
    private val looseBlock = ArrayList<Net>()
    private val siteCss = HashMap<String, ArrayList<String>>()
    private val generic = LinkedHashSet<String>()

    var networkRules = 0; private set
    var cosmeticRules = 0; private set

    /** Nạp 1 dòng bộ lọc. Trả về true nếu engine dùng được dòng này (để lưu lại), false nếu bỏ qua. */
    fun addLine(raw: String): Boolean {
        val l = raw.trim()
        if (l.isEmpty() || l[0] == '!' || l[0] == '[') return false
        if (l.indexOf('#') >= 0) COSMETIC.matchEntire(l)?.let { return addCosmetic(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
        return addNetwork(l)
    }

    // ---------- Luật mạng ----------

    private fun addNetwork(line: String): Boolean {
        var l = line
        val exception = l.startsWith("@@")
        if (exception) l = l.substring(2)
        var opts = ""
        val d = l.lastIndexOf('$')
        if (d >= 0 && !l.substring(d).contains('/')) { opts = l.substring(d + 1); l = l.substring(0, d) }
        if (l.isEmpty() || isRegex(l)) return false
        var third = 0; var types = 0; var neg = 0
        var inc: Array<String>? = null; var exc: Array<String>? = null
        if (opts.isNotEmpty()) for (o0 in opts.split(',')) {
            val o = o0.trim().lowercase()
            when {
                o == "third-party" || o == "3p" -> third = 1
                o == "~third-party" || o == "~3p" || o == "first-party" || o == "1p" -> third = -1
                o == "important" || o == "match-case" || o == "all" -> Unit
                o.startsWith("domain=") -> {
                    val ds = o.removePrefix("domain=").split('|').filter { it.isNotEmpty() }
                    inc = ds.filter { !it.startsWith("~") }.toTypedArray().takeIf { it.isNotEmpty() }
                    exc = ds.filter { it.startsWith("~") }.map { it.substring(1) }.toTypedArray().takeIf { it.isNotEmpty() }
                }
                else -> {
                    val n = o.startsWith("~")
                    val bit = TYPE[o.removePrefix("~")] ?: return false // tùy chọn lạ (popup, redirect, csp…) → bỏ luật
                    if (n) neg = neg or bit else types = types or bit
                }
            }
        }
        if (neg != 0 && types == 0) types = ALL_TYPES and neg.inv()
        var hostAnchor = false; var startAnchor = false; var endAnchor = false
        when {
            l.startsWith("||") -> { hostAnchor = true; l = l.substring(2) }
            l.startsWith("|") -> { startAnchor = true; l = l.substring(1) }
        }
        if (l.endsWith("|")) { endAnchor = true; l = l.dropLast(1) }
        l = l.lowercase()
        if (l.isEmpty() || l == "*") return false
        val net = Net(l.split('*').toTypedArray(), hostAnchor, startAnchor, endAnchor, third, types, inc, exc)
        val token = pickToken(l, hostAnchor || startAnchor, endAnchor)
        val map = if (exception) allow else block
        when {
            token != null -> map.getOrPut(token) { ArrayList(2) }.add(net)
            !exception && looseBlock.size < LOOSE_CAP -> looseBlock.add(net)
            else -> return false
        }
        networkRules++
        return true
    }

    /** Luật regex của ABP có dạng /.../ với ký tự regex bên trong; "/ads/" thuần là đường dẫn bình thường. */
    private fun isRegex(p: String): Boolean =
        p.startsWith("/") && p.endsWith("/") && p.length > 2 && p.substring(1, p.length - 1).any { it in "\\[](){}^+?|" } ||
            p.contains('\\') || p.contains('[') || p.contains('(') || p.contains('{')

    /** Đoạn chữ-số dài nhất có ranh giới rõ (không dính '*' hay nằm giữa từ) để làm khóa chỉ mục. */
    private fun pickToken(p: String, startAnchored: Boolean, endAnchored: Boolean): String? {
        var best: String? = null
        var i = 0
        while (i < p.length) {
            if (!p[i].isLetterOrDigit()) { i++; continue }
            var j = i
            while (j < p.length && p[j].isLetterOrDigit()) j++
            val leftOk = if (i == 0) startAnchored else p[i - 1] != '*'
            val rightOk = if (j == p.length) endAnchored else p[j] != '*'
            if (leftOk && rightOk && j - i >= 3 && (best == null || j - i > best.length)) best = p.substring(i, j)
            i = j
        }
        return best
    }

    // ---------- Luật ẩn phần tử ----------

    private fun addCosmetic(domains: String, kind: String, sel: String): Boolean {
        if (kind.isNotEmpty()) return false // #@# (ngoại lệ), #?#, #$#, #%# → bỏ qua
        val s = sel.trim()
        if (s.isEmpty() || s.length > 300 || BAD_SELECTOR.containsMatchIn(s) || s.any { it == '{' || it == '}' || it == ';' || it == '\n' }) return false
        if (domains.isEmpty()) {
            if (generic.size >= GENERIC_CAP) return false
            if (!generic.add(s)) return false
        } else {
            val ds = domains.lowercase().split(',').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("~") && !it.contains('*') }
            if (ds.isEmpty()) return false
            for (d in ds) siteCss.getOrPut(d) { ArrayList(2) }.add(s)
        }
        cosmeticRules++
        return true
    }

    /** CSS ẩn quảng cáo cho trang [pageHost]: luật riêng của trang (và tên miền cha) + nhóm luật chung. */
    fun cosmeticCss(pageHost: String, extra: Collection<String> = emptyList()): String {
        val sb = StringBuilder()
        var d = pageHost.lowercase()
        while (d.contains('.')) {
            siteCss[d]?.forEach { sb.append(it).append("{display:none!important}\n") }
            d = d.substringAfter('.')
        }
        for (s in extra) sb.append(s).append("{display:none!important}\n")
        for (s in generic) sb.append(s).append("{display:none!important}\n")
        return sb.toString()
    }

    // ---------- Tra cứu ----------

    /**
     * Có chặn yêu cầu [url] không. [reqHost] = tên miền của yêu cầu, [pageHost] = trang đang xem, [type] = loại tài nguyên
     * (0 = không rõ; luật có ràng buộc loại sẽ không áp khi không rõ loại, tránh chặn nhầm).
     */
    fun blocks(url: String, reqHost: String, pageHost: String, type: Int): Boolean {
        if (networkRules == 0) return false
        val u = url.lowercase()
        val third = registrable(reqHost) != registrable(pageHost)
        var hit = false
        var start = 0
        val n = u.length
        while (start < n && !hit) {
            while (start < n && !u[start].isLetterOrDigit()) start++
            var end = start
            while (end < n && u[end].isLetterOrDigit()) end++
            if (end > start) {
                val list = block[u.substring(start, end)]
                if (list != null) for (r in list) if (applies(r, pageHost, third, type) && matches(r, u)) { hit = true; break }
            }
            start = end
        }
        if (!hit) for (r in looseBlock) if (applies(r, pageHost, third, type) && matches(r, u)) { hit = true; break }
        if (!hit) return false
        // Ngoại lệ @@ thắng.
        start = 0
        while (start < n) {
            while (start < n && !u[start].isLetterOrDigit()) start++
            var end = start
            while (end < n && u[end].isLetterOrDigit()) end++
            if (end > start) allow[u.substring(start, end)]?.let { l -> for (r in l) if (applies(r, pageHost, third, type) && matches(r, u)) return false }
            start = end
        }
        return true
    }

    private fun applies(r: Net, pageHost: String, third: Boolean, type: Int): Boolean {
        if (r.third == 1 && !third) return false
        if (r.third == -1 && third) return false
        if (r.types != 0 && (type == 0 || r.types and type == 0)) return false
        val ph = pageHost.lowercase()
        r.inc?.let { inc -> if (inc.none { ph == it || ph.endsWith(".$it") }) return false }
        r.exc?.let { exc -> if (exc.any { ph == it || ph.endsWith(".$it") }) return false }
        return true
    }

    private fun matches(r: Net, url: String): Boolean {
        val parts = r.parts
        if (r.hostAnchor) {
            val schemeEnd = url.indexOf("://").let { if (it < 0) 0 else it + 3 }
            var hostEnd = schemeEnd
            while (hostEnd < url.length && url[hostEnd] != '/' && url[hostEnd] != '?' && url[hostEnd] != '#' && url[hostEnd] != ':') hostEnd++
            var pos = schemeEnd
            while (pos <= hostEnd) {
                if (matchFrom(r, url, pos, true)) return true
                val dot = url.indexOf('.', pos)
                if (dot < 0 || dot >= hostEnd) break
                pos = dot + 1
            }
            return false
        }
        if (r.startAnchor) return matchFrom(r, url, 0, true)
        val first = parts[0]
        if (first.isEmpty()) return matchFrom(r, url, 0, false)
        var from = 0
        while (from <= url.length) {
            val idx = find(url, first, from)
            if (idx < 0) return false
            if (matchFrom(r, url, idx, true)) return true
            from = idx + 1
        }
        return false
    }

    /** Khớp toàn bộ mẫu bắt đầu từ [pos0]; [anchored] = phần đầu phải nằm đúng tại pos0. */
    private fun matchFrom(r: Net, url: String, pos0: Int, anchored: Boolean): Boolean {
        val parts = r.parts
        val last = parts.indexOfLast { it.isNotEmpty() }
        if (last < 0) return true
        var pos = pos0
        for (i in parts.indices) {
            val p = parts[i]
            if (p.isEmpty()) continue
            val isLast = i == last
            if (i == 0 && anchored) {
                val e = partAt(url, p, pos)
                if (e < 0 || (isLast && r.endAnchor && e != url.length)) return false
                pos = e
            } else if (isLast && r.endAnchor) {
                // Phần cuối phải khớp sát cuối URL.
                var from = pos
                while (from <= url.length) {
                    val idx = find(url, p, from)
                    if (idx < 0) return false
                    if (partAt(url, p, idx) == url.length) return true
                    from = idx + 1
                }
                return false
            } else {
                val idx = find(url, p, pos)
                if (idx < 0) return false
                pos = partAt(url, p, idx)
            }
        }
        return true
    }

    private fun find(url: String, part: String, from: Int): Int {
        if (part.indexOf('^') < 0) return url.indexOf(part, from)
        var i = from
        while (i <= url.length) { if (partAt(url, part, i) >= 0) return i; i++ }
        return -1
    }

    /** Khớp [part] tại vị trí [at] ('^' = ký tự phân cách hoặc hết chuỗi). Trả về vị trí sau khi khớp, -1 nếu không khớp. */
    private fun partAt(url: String, part: String, at: Int): Int {
        var u = at
        for (c in part) {
            if (c == '^') {
                if (u >= url.length) continue
                val ch = url[u]
                if (ch.isLetterOrDigit() || ch == '_' || ch == '-' || ch == '.' || ch == '%') return -1
                u++
            } else {
                if (u >= url.length || url[u] != c) return -1
                u++
            }
        }
        return u
    }

    companion object {
        const val SCRIPT = 1; const val IMAGE = 2; const val STYLE = 4; const val XHR = 8
        const val SUBDOC = 16; const val MEDIA = 32; const val FONT = 64; const val OTHER = 128
        private const val ALL_TYPES = 255
        private const val LOOSE_CAP = 1500
        private const val GENERIC_CAP = 2500

        private val TYPE = mapOf(
            "script" to SCRIPT, "image" to IMAGE, "stylesheet" to STYLE, "css" to STYLE, "xmlhttprequest" to XHR, "xhr" to XHR,
            "subdocument" to SUBDOC, "frame" to SUBDOC, "media" to MEDIA, "font" to FONT, "other" to OTHER, "ping" to OTHER, "object" to OTHER,
        )
        private val COSMETIC = Regex("""^([^#]*)#(@|\?|\$|%)?#(.*)$""")
        private val BAD_SELECTOR = Regex(""":(has-text|-abp-|xpath|matches-|contains|upward|remove|style|nth-ancestor|min-text-length|watch-attr|others|-ext-|if|not\(:has-text)|\+js\(""")

        /** Tên miền đăng ký (2 nhãn cuối, hoặc 3 với đuôi 2 cấp như .com.vn) — đủ để phân biệt bên thứ ba. */
        fun registrable(host: String): String {
            val h = host.lowercase().trimEnd('.')
            val labels = h.split('.')
            if (labels.size <= 2) return h
            val last2 = labels.takeLast(2).joinToString(".")
            return if (last2 in SLD) labels.takeLast(3).joinToString(".") else last2
        }

        private val SLD = setOf("com.vn", "net.vn", "org.vn", "edu.vn", "gov.vn", "co.uk", "org.uk", "com.au", "co.jp", "com.br", "co.kr", "com.cn", "co.in", "com.sg", "com.tw", "com.hk")
    }
}
