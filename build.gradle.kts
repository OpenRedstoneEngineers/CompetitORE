import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    kotlin("kapt") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = ""
version = "1.0"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.aikar.co/nexus/content/groups/aikar/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://libraries.minecraft.net")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation(group = "co.aikar", name = "acf-paper", version = "0.5.0-SNAPSHOT")
    implementation(group = "org.jetbrains.exposed", name = "exposed-core", version = "0.30.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-jdbc", version = "0.30.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-java-time", version = "0.30.1")
    implementation(group = "com.uchuhimo", name = "konf", version = "0.22.1")
    implementation(group = "mysql", name = "mysql-connector-java", version = "8.0.19")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.11.0")

    compileOnly(group = "net.luckperms", name = "api", version = "5.2")
    compileOnly(group = "com.plotsquared", name = "PlotSquared-Core", version = "6.4.0")
    compileOnly(group = "com.plotsquared", name = "PlotSquared-Bukkit", version = "6.4.0")
    compileOnly(group = "com.comphenix.protocol", name = "ProtocolLib", version = "4.5.0")
    compileOnly(group = "org.spigotmc", name = "spigot-api", version = "1.16.2-R0.1-SNAPSHOT")
    compileOnly(group = "com.sk89q.worldedit", name = "worldedit-bukkit", version = "7.2.0-SNAPSHOT")
}

tasks.shadowJar {
    relocate("co.aikar.commands", "boater.acf")
    relocate("com.google.inject", "com.plotsquared.google")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "16"
        javaParameters = true
        freeCompilerArgs = listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi"
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
