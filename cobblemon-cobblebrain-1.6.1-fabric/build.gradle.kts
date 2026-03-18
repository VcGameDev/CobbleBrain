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

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenLocal()
        mavenCentral()

        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/")
        maven("https://maven.impactdev.net/repository/development/")
        maven("https://api.modrinth.com/maven")
        maven("https://maven.terraformersmc.com/releases/")
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