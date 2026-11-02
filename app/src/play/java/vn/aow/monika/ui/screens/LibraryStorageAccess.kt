package vn.aow.monika.ui.screens
import androidx.compose.runtime.Composable
import vn.aow.monika.Prefs
/** Nút Thêm game của Thư viện mở SAF, không xin quyền toàn bộ bộ nhớ. */
@Composable
internal fun LibraryStorageAccess(granted: Boolean, prefs: Prefs, onRequest: () -> Unit, onManual: () -> Unit) = Unit
