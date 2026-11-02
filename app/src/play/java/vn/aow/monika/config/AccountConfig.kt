package vn.aow.monika.config
import kotlinx.serialization.Serializable
@Serializable
data class AccountConfig(
    val enabled: Boolean = true,
    val apiKey: String = "",
    val databaseUrl: String = "",
    val loginUrl: String = "https://www.aow.vn/p/device-login.html",
)
