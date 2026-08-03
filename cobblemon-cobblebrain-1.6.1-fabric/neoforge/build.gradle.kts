plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

val shadowBundle: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings(loom.officialMojangMappings())

    neoForge("net.neoforged:neoforge:${property("neoforge_version")}")

    forgeRuntimeLibrary("thedarkcolour:kotlinforforge-neoforge:${property("kotlinforforge_version")}") {
        exclude("net.neoforged.fancymodloader", "loader")
    }

    modImplementation("com.cobblemon:neoforge:${property("cobblemon_version")}")
    modImplementation("me.shedaniel.cloth:cloth-config-neoforge:15.0.140")

    // MCMti (Speech-to-Text - Opcional)
    compileOnly("maven.modrinth:mcmti:3.0.1+26.1.2-neoforge")

    implementation(project(":common", configuration = "namedElements"))

    "developmentNeoForge"(project(":common", configuration = "namedElements")) {
        isTransitive = false
    }

    // ISSO QUE FAZ O COMMON ENTRAR NO JAR
    shadowBundle(project(":common", configuration = "transformProductionNeoForge"))
}
tasks {

    processResources {
        inputs.property("version", project.version)

        filesMatching("META-INF/neoforge.mods.toml") {
            expand(project.properties)
        }
    }

    jar {
        archiveBaseName.set("${rootProject.property("archives_base_name")}-neoforge")
    }

    // AGORA EXISTE
    shadowJar {
        archiveClassifier.set("dev-shadow")
        configurations = listOf(shadowBundle)
    }

    // ESSENCIAL
    remapJar {
        dependsOn(shadowJar)
        inputFile.set(shadowJar.flatMap { it.archiveFile })

        archiveBaseName.set("${rootProject.property("archives_base_name")}-neoforge")
        archiveVersion.set("${project.version}")
    }
}