import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("java-library")
    kotlin("jvm") version("2.2.20")

    id("architectury-plugin") version("3.4-SNAPSHOT") apply false
    id("dev.architectury.loom") version("1.11-SNAPSHOT") apply false
}

group = property("maven_group")!!
version = property("mod_version")!!

allprojects {
    version = property("mod_version")!!
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenLocal()
        mavenCentral()

        maven("https://maven.neoforged.net/releases/")
        maven("https://thedarkcolour.github.io/KotlinForForge/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        maven("https://maven.impactdev.net/repository/development/")
        exclusiveContent {
            forRepository {
                maven("https://api.modrinth.com/maven")
            }
            filter {
                includeGroup("maven.modrinth")
                includeGroup("remapped.maven.modrinth")
                includeGroupByRegex(".*maven\\.modrinth.*")
            }
        }
        maven("https://maven.terraformersmc.com/releases/") {
            content {
                includeGroup("com.terraformersmc")
            }
        }
        maven("https://maven.shedaniel.me/")
        maven("https://artefacts.cobblemon.com/releases/")
    }

    tasks {
        java {
            withSourcesJar()
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }

        compileJava {
            options.release = 21
        }

        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }
    }
}