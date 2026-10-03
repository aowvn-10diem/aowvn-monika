pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // LibretroDroid
    }
}
rootProject.name = "AowVN-Monika"
include(":app")
// LibretroDroid 0.14.0 nhúng sẵn (GPLv3) để thêm RetroAchievements: docs/LIBRETRODROID.md
include(":libretrodroid")
// Kirikiri (Kirikiroid2Yuri) chạy ngay trong app: mã Java ở đây, lib native + tài nguyên là gói tải thêm.
include(":kirikiri")
// Lớp Java SDL 2.26.3 đã đổi gói cho mkxp-z (RPG Maker XP/VX/Ace); native là gói tải thêm.
include(":rgss")
// J2ME Loader nhúng sẵn (Apache-2.0) để chạy game Java ngay trong app.
include(":j2me", ":dexlib")
// Bộ nạp data chạy trong game đã chỉnh (Java thuần → .dex nhúng vào assets của app).
include(":loader")
