import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription

plugins {
    kotlin("jvm") version "2.1.20-RC"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("net.minecrell.plugin-yml.bukkit") version "0.6.0"
}

group = "dev.idot"
version = "0.2.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.13-R+")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

kotlin {
    jvmToolchain(8)
}

tasks {
    jar { enabled = false }
    named<ShadowJar>("shadowJar") {
        archiveClassifier = ""
    }
    build {
        dependsOn("shadowJar")
    }
}

bukkit {
    main = "dev.idot.boxplugin.BoxPlugin"
    name = rootProject.name
    description = project.description
    version = project.version.toString()
    author = "audizian"
    apiVersion = "1.13"
    BukkitPluginDescription.Command("box").apply {
        description = "The main command for Box"
    }.let(commands::add)
}