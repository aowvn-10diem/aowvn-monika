package vn.aow.monika.config
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
/** Catalog Play đã lọc khi dựng; không nhận lại liên kết tải ngoài từ cache/Worker. */
class ConfigRepository(context: Context, http: OkHttpClient) {
    private val state = MutableStateFlow(parse(context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }))
    val config: StateFlow<MonikaConfig> = state
    val current: MonikaConfig get() = state.value
    suspend fun refresh(force: Boolean = false): Result<MonikaConfig> = Result.success(current)
    companion object {
        const val MIN_REFRESH_MS = 6 * 3_600_000L
        const val ASSET_NAME = "monika-config.json"
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        fun parse(text: String): MonikaConfig = json.decodeFromString(MonikaConfig.serializer(), text)
    }
}
