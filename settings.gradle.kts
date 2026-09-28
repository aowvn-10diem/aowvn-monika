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
// J2ME Loader nhúng sẵn (Apache-2.0) để chạy game Java ngay trong app.
include(":j2me", ":dexlib")
