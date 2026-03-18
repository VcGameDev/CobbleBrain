plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    kotlin("jvm")
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

loom {
    silentMojangMappingsLicense()

    mixin {
        useLegacyMixinAp = true
        defaultRefmapName.set("mixins.cobblebrain.refmap.json")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    // NeoForge loader
    neoForge("net.neoforged:neoforge:${property("neoforge_version")}")

    // Kotlin (versão NeoForge)
    forgeRuntimeLibrary("thedarkcolour:kotlinforforge-neoforge:${property("kotlinforforge_version")}")

    // Cobblemon (NeoForge)
    modImplementation("com.cobblemon:neoforge:${property("cobblemon_version")}")

    // Conectar com o common
    implementation(project(":common", configuration = "namedElements"))

    "developmentNeoForge"(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }
}