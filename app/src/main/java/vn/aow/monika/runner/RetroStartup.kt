package vn.aow.monika.runner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.aow.monika.ui.theme.*

@Composable
internal fun RetroStartup(title: String, status: String, slow: Boolean, onCopy: () -> Unit,
    fallback: String? = null, onFallback: () -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(Monika.colors.surfaceDark), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(title, style = Monika.type.sectionTitle, color = Monika.colors.textOnDark, textAlign = TextAlign.Center)
            CircularProgressIndicator(color = Monika.colors.accentOrange)
            Text(status, style = Monika.type.body, color = Monika.colors.textOnDarkSecondary, textAlign = TextAlign.Center)
            if (slow) {
                Text("Khởi động lâu hơn 60 giây. Game vẫn đang nạp.", style = Monika.type.caption,
                    color = Monika.colors.textOnDarkSecondary, textAlign = TextAlign.Center)
                GradientButton("Sao chép chẩn đoán", onCopy)
            }
            fallback?.let { GradientButton(it, onFallback) }
        }
    }
}
