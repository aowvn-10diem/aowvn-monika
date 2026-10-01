package vn.aow.monika.achievements

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mô hình dữ liệu RetroAchievements Web API (khóa JSON dạng PascalCase). Chỉ khai các trường app dùng. */

@Serializable
data class RaProfile(
    @SerialName("User") val user: String = "",
    @SerialName("ULID") val ulid: String = "",
    @SerialName("UserPic") val userPic: String = "",
    @SerialName("TotalPoints") val totalPoints: Int = 0,
    @SerialName("TotalSoftcorePoints") val totalSoftcorePoints: Int = 0,
    @SerialName("RichPresenceMsg") val richPresence: String? = null,
    @SerialName("LastGameID") val lastGameId: Int = 0,
    @SerialName("Motto") val motto: String? = null,
)

@Serializable
data class RaRecentGame(
    @SerialName("GameID") val gameId: Int = 0,
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("Title") val title: String = "",
    @SerialName("ImageIcon") val imageIcon: String = "",
    @SerialName("LastPlayed") val lastPlayed: String = "",
    @SerialName("NumPossibleAchievements") val numPossible: Int = 0,
    @SerialName("NumAchieved") val numAchieved: Int = 0,
    @SerialName("NumAchievedHardcore") val numAchievedHardcore: Int = 0,
    @SerialName("ScoreAchieved") val scoreAchieved: Int = 0,
    @SerialName("PossibleScore") val possibleScore: Int = 0,
)

@Serializable
data class RaUnlock(
    @SerialName("Date") val date: String = "",
    @SerialName("HardcoreMode") val hardcore: Int = 0,
    @SerialName("AchievementID") val achievementId: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("BadgeName") val badgeName: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("GameTitle") val gameTitle: String = "",
    @SerialName("GameID") val gameId: Int = 0,
    @SerialName("ConsoleName") val consoleName: String = "",
)

@Serializable
data class RaAchievement(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeName") val badgeName: String = "",
    @SerialName("DisplayOrder") val displayOrder: Int = 0,
    /** "progression" | "win_condition" | "missable" | null */
    @SerialName("type") val type: String? = null,
    @SerialName("DateEarned") val dateEarned: String? = null,
    @SerialName("DateEarnedHardcore") val dateEarnedHardcore: String? = null,
) {
    val earned get() = dateEarned != null || dateEarnedHardcore != null
}

@Serializable
data class RaGameProgress(
    @SerialName("ID") val id: Int = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("ImageIcon") val imageIcon: String = "",
    @SerialName("NumAchievements") val numAchievements: Int = 0,
    @SerialName("NumAwardedToUser") val numAwardedToUser: Int = 0,
    @SerialName("NumAwardedToUserHardcore") val numAwardedToUserHardcore: Int = 0,
    @SerialName("UserCompletion") val userCompletion: String? = null,
    @SerialName("Achievements") val achievements: Map<String, RaAchievement> = emptyMap(),
) {
    /** Theo thứ tự hiển thị của RA. */
    val ordered get() = achievements.values.sortedBy { it.displayOrder }
}

/** 1 dòng trong danh sách game của 1 hệ máy (kèm hash các bản ROM được hỗ trợ). */
@Serializable
data class RaGameListItem(
    @SerialName("Title") val title: String = "",
    @SerialName("ID") val id: Int = 0,
    @SerialName("NumAchievements") val numAchievements: Int = 0,
    @SerialName("Hashes") val hashes: List<String> = emptyList(),
)
