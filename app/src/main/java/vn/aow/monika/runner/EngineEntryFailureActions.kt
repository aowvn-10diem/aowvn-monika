package vn.aow.monika.runner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import vn.aow.monika.R
import vn.aow.monika.ui.theme.SheetAction

/** Cùng action của màn chuẩn bị, dễ kiểm UI mà không tải/chạy lõi native. */
internal fun engineEntryFailureActions(info: String?, report: SheetAction, onCopy: (String) -> Unit): List<SheetAction> = listOf(
    report.copy(keepOpen = true, enabled = info != null),
    SheetAction("Sao chép thông tin lỗi", R.drawable.ic_fluent_copy_24_regular,
        keepOpen = true, enabled = info != null) { info?.let(onCopy) },
)

internal fun copyEngineEntryInfo(context: Context, text: String): Boolean = runCatching {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(
        ClipData.newPlainText("Thông tin lỗi Kirikiri", text))
}.isSuccess
