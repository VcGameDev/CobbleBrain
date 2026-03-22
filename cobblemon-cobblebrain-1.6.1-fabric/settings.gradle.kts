rootProject.name = "cobblebrain"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
}

listOf(
    "common",
    "neoforge",
    "fabric"
).forEach { include(it) }