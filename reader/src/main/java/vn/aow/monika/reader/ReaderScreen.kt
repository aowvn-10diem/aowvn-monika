package vn.aow.monika.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Màn đọc PDF độc lập: tên tài liệu, khung trang, thanh điều hướng Trước/Sau,
 * và trạng thái lỗi (PDF hỏng / có mật khẩu / thiếu tệp) — không bao giờ văng app.
 *
 * [book] null + [error] null = đang mở. [initialPage] là trang đã lưu lần đọc trước.
 * Mỗi lần đổi trang gọi [onPageChanged] để nơi gọi ghi vào `Prefs` (nhớ trang đang đọc dở).
 */
@Composable
fun ReaderScreen(
    title: String,
    book: PdfBook?,
    error: ReaderError?,
    initialPage: Int,
    style: ReaderStyle,
    onPageChanged: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(style.background)) {
        ReaderHeader(title, style, onClose)
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            when {
                error != null -> ReaderErrorPanel(error, style, onClose)
                book == null -> ReaderLoading(style)
                book.pageCount <= 0 -> ReaderErrorPanel(ReaderError.CORRUPT, style, onClose)
                else -> ReaderPager(book, initialPage, style, onPageChanged)
            }
        }
    }
}

@Composable
private fun ReaderLoading(style: ReaderStyle) {
    CircularProgressIndicator(color = style.accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
}

/** Thanh trên: nút quay lại + tên tài liệu. */
@Composable
private fun ReaderHeader(title: String, style: ReaderStyle, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(style.surface).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(style.pill).background(style.chip)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", style = style.title, color = style.chipText)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title, style = style.title, color = style.text, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Khung trang + thanh điều hướng. Trang dựng trên luồng IO; đổi trang liên tục vẫn an toàn
 * vì [produceState] huỷ lần dựng cũ khi [page] đổi.
 */
@Composable
private fun ReaderPager(
    book: PdfBook,
    initialPage: Int,
    style: ReaderStyle,
    onPageChanged: (Int) -> Unit,
) {
    val last = book.pageCount - 1
    var page by remember(book) { mutableIntStateOf(initialPage.coerceIn(0, last)) }
    val rendered by produceState<ReaderPage?>(null, book, page) {
        value = withContext(Dispatchers.IO) { runCatching { book.openPage(page) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val current = rendered
            if (current == null) {
                ReaderLoading(style)
            } else {
                Image(
                    bitmap = current.bitmap.asImageBitmap(),
                    contentDescription = "Trang ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(style.surface).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReaderButton("Trước", style, enabled = page > 0, modifier = Modifier.weight(1f)) {
                val target = (page - 1).coerceAtLeast(0)
                if (target != page) { page = target; onPageChanged(target) }
            }
            Text(
                "Trang ${page + 1}/${book.pageCount}",
                style = style.body,
                color = style.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 92.dp),
            )
            ReaderButton("Sau", style, enabled = page < last, modifier = Modifier.weight(1f)) {
                val target = (page + 1).coerceAtMost(last)
                if (target != page) { page = target; onPageChanged(target) }
            }
        }
    }
}

/** Nút điều hướng: gradient khi bấm được, nền chip khi đã hết trang. */
@Composable
private fun ReaderButton(
    text: String,
    style: ReaderStyle,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(style.pill)
            .background(if (enabled) style.accentGradient else SolidColor(style.chip))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = style.button,
            color = if (enabled) style.onAccent else style.textSecondary,
            maxLines = 1,
        )
    }
}

/** Khối báo lỗi: nói rõ vì sao không mở được, chỉ có một nút Đóng. */
@Composable
private fun ReaderErrorPanel(error: ReaderError, style: ReaderStyle, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).clip(style.card).background(style.surface).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Chưa đọc được tài liệu", style = style.title, color = style.text)
        Text(error.message, style = style.body, color = style.textSecondary)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier.height(44.dp).clip(style.pill).background(style.accentGradient)
                .clickable(onClick = onClose).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Đóng", style = style.button, color = style.onAccent)
        }
    }
}
