plugins {
    id("dev.architectury.loom")
    id("architectury-plugin")
    kotlin("jvm")
}

architectury {
    platformSetupLoomIde()
    fabric()
}

loom {
    silentMojangMappingsLicense()

    mixin {
        defaultRefmapName.set("mixins.cobblebrain.refmap.json")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    // Fabric loader
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Kotlin
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Cobblemon
    modImplementation("com.cobblemon:fabric:${property("cobblemon_version")}")

    // UI mods
    modImplementation("com.terraformersmc:modmenu:${property("modmenu_version")}")
    modImplementation("me.shedaniel.cloth:cloth-config-fabric:${property("cloth_config_version")}")

    // Conectar com o common
    implementation(project(":common", configuration = "namedElements"))
    "developmentFabric"(project(":common", configuration = "namedElements"))
}