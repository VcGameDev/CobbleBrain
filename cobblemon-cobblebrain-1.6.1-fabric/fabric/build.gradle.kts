plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

// bundle para incluir o common no jar final
val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
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

    implementation(project(":common", configuration = "namedElements"))
    "developmentFabric"(project(":common", configuration = "namedElements"))

    shadowBundle(project(":common", configuration = "transformProductionFabric"))
}

tasks {

    processResources {
        inputs.property("version", project.version)

        filesMatching("fabric.mod.json") {
            expand(project.properties)
        }
    }

    jar {
        archiveBaseName.set("${rootProject.property("archives_base_name")}-fabric")
    }

    // shadow jar (inclui common)
    shadowJar {
        archiveClassifier.set("dev-shadow")
        configurations = listOf(shadowBundle)
    }

    // remap final usando shadow
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.flatMap { it.archiveFile })

        archiveBaseName.set("${rootProject.property("archives_base_name")}-fabric")
        archiveVersion.set("${project.version}")
    }
}