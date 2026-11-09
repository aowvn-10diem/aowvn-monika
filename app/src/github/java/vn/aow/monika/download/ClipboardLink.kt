package vn.aow.monika.download

import android.content.ClipboardManager
import android.content.Context

/** V193 G2: đọc text người dùng vừa sao chép để dán vào ô link; rỗng/không có text → null. */
object ClipboardLink {
    fun read(context: Context): String? =
        context.getSystemService(ClipboardManager::class.java)
            ?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
