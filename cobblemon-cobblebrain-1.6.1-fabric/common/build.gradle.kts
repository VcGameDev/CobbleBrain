plugins {
    id("org.jetbrains.kotlin.jvm")
    id("dev.architectury.loom")
    id("architectury-plugin")
}

architectury {
    common("fabric", "neoforge")
}

loom {
    silentMojangMappingsLicense()
}

dependencies {

    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    // Cobblemon base (loader agnostic)
    modImplementation("com.cobblemon:mod:${property("cobblemon_version")}") {
        isTransitive = false
    }

    // Cloth Config (COMMON - compile only)
    modCompileOnly("me.shedaniel.cloth:cloth-config:15.0.140")

}

tasks.test {
    useJUnitPlatform()
}