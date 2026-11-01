package vn.aow.monika.ui.screens
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.aow.monika.BuildConfig
import vn.aow.monika.ui.theme.*
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null, header: (@Composable () -> Unit)? = null) {
    Screen {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            header?.invoke()
            Text("Cài đặt", style = Monika.type.sectionTitle, color = Monika.colors.text)
            Text("Aow Monika ${BuildConfig.VERSION_NAME}", style = Monika.type.body, color = Monika.colors.text)
            AccountCard()
            Text("Bạn có thể ủng hộ AowVN trên web aow.vn.", style = Monika.type.body, color = Monika.colors.textSecondary)
            onBack?.let { DarkButton("Về Thư viện", it) }
        }
    }
}
fun openUrl(context: Context, url: String) = vn.aow.monika.play.PlayLinks.open(context, url)
