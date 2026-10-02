package vn.aow.monika.library

import vn.aow.monika.config.MonikaConfig

/** Một thành phần cần tải trước: lõi libretro (`core`) hoặc gói engine/module (`pack`). */
data class PackRef(val kind: Kind, val id: String) {
    enum class Kind { CORE, PACK }
}

/**
 * Đoán "phần đằng sau" cần tải từ tín hiệu rẻ (nhãn bài, tên/đuôi file) ngay khi người dùng bắt đầu tải.
 * Hàm thuần, không đụng mạng/đĩa → dễ test. Việc tải thật do CorePrefetchWorker / PackManager.
 */
object PrefetchPlanner {
    fun plan(config: MonikaConfig, labels: List<String>, fileName: String?): List<PackRef> {
        val out = LinkedHashSet<PackRef>()
        val wanted = labels.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        val ext = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()

        // Nhãn bài là tín hiệu chắc nhất về hệ máy; không có nhãn mới dùng đuôi file (đuôi .zip/.7z không nói gì về hệ).
        val systems = config.systems.filter { s -> s.labels.any { it.trim().lowercase() in wanted } }
            .ifEmpty { if (ext.isEmpty()) emptyList() else config.systems.filter { s -> ext in s.extensions.map { it.lowercase() } } }
        for (s in systems) {
            when {
                s.engine != null && config.modules.containsKey(s.engine) -> out += PackRef(PackRef.Kind.PACK, s.engine)
                s.runner == "libretro" && s.core != null -> out += PackRef(PackRef.Kind.CORE, s.core)
            }
        }
        // Bảng đuôi file → gói phụ trợ (bộ giải nén, game Java...) do config quyết định; chỉ nhận gói có trong modules.
        config.prefetchByExtension[ext]?.filter { config.modules.containsKey(it) }?.forEach { out += PackRef(PackRef.Kind.PACK, it) }
        return out.toList()
    }
}

/** Móc "tải trước": gọi ngay khi người dùng bắt đầu tải; lỗi bất kỳ không được ảnh hưởng việc tải chính. */
object Prefetch {
    fun onDownloadStart(context: android.content.Context, labels: List<String>, fileName: String?) {
        runCatching {
            val cfg = vn.aow.monika.AppGraph.config.current
            val coreIds = PrefetchPlanner.plan(cfg, labels, fileName).filter { it.kind == PackRef.Kind.CORE }.map { it.id }
            if (coreIds.isNotEmpty() && vn.aow.monika.AppGraph.cores.missing(coreIds).isNotEmpty())
                CorePrefetchWorker.enqueue(context, coreIds)
            val packIds = PrefetchPlanner.plan(cfg, labels, fileName).filter { it.kind == PackRef.Kind.PACK }.map { it.id }
            if (packIds.isNotEmpty()) vn.aow.monika.pack.PackManager.prefetch(context, packIds)
        }
    }
}
