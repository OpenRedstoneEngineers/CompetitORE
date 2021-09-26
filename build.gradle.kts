import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    kotlin("kapt") version "1.4.21"
    id("com.github.johnrengelman.shadow") version "2.0.4"
}

group = ""
version = "1.0"

repositories {

    jcenter()

    mavenCentral()
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        content {
            includeGroup("org.bukkit")
            includeGroup("org.spigotmc")
        }
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }
    maven {
        url = uri("https://oss.sonatype.org/content/repositories/central")
    }
    maven {
        name = "enginehub-maven"
        url = uri("https://maven.enginehub.org/repo/")
    }
    maven {
        name = "myndocs-oauth2"
        url = uri("https://dl.bintray.com/adhesivee/oauth2-server")
    }
    maven {
        name = "aikar"
        url = uri("https://repo.aikar.co/content/groups/aikar/")
    }
    maven {
        name = "codemc-repo"
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    maven {
        name = "minecraft-repo"
        url = uri("https://libraries.minecraft.net")
    }
    maven {
        name = "kotlin-exposed"
        url = uri("https://dl.bintray.com/kotlin/exposed")
    }
    maven {
        url = uri("https://mvn.intellectualsites.com/content/groups/public/")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(group = "co.aikar", name = "acf-paper", version = "0.5.0-SNAPSHOT")

    implementation(group = "org.jetbrains", name = "kotlin-css", version = "1.0.0-pre.104-kotlin-1.3.72")
    implementation(group = "org.jetbrains.exposed", name = "exposed-core", version = "0.28.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-jdbc", version = "0.28.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-java-time", version = "0.28.1")

    implementation(group = "com.uchuhimo", name = "konf", version = "0.22.1")

    implementation(group = "mysql", name = "mysql-connector-java", version = "8.0.19")

    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.11.0")

//    implementation(group = "com.google.guava", name = "guava", version = "30.1-jre")

    compileOnly(group = "net.luckperms", name = "api", version = "5.2")
    compileOnly(group = "com.plotsquared", name = "PlotSquared-Core", version = "5.13.3")
    compileOnly(group = "com.plotsquared", name = "PlotSquared-Bukkit", version = "5.13.3")
    compileOnly(group = "com.comphenix.protocol", name = "ProtocolLib", version = "4.5.0")
    compileOnly(group = "org.spigotmc", name = "spigot-api", version = "1.16.2-R0.1-SNAPSHOT")
    compileOnly(group = "org.bukkit", name = "bukkit", version = "1.16.2-R0.1-SNAPSHOT")
    compileOnly(group = "com.sk89q.worldedit", name = "worldedit-bukkit", version = "7.2.0-SNAPSHOT")
}

tasks.shadowJar {
    relocate("co.aikar.commands", "boater.acf")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        javaParameters = true
        freeCompilerArgs = listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi"
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
