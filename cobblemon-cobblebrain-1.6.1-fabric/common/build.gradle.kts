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

}

tasks.test {
    useJUnitPlatform()
}