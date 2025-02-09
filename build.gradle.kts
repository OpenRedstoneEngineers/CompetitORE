import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.kotlin.kapt") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

group = ""
version = "1.1"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
    maven("https://repo.aikar.co/nexus/content/groups/aikar/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.codemc.org/repository/maven-public/")
    maven("https://libraries.minecraft.net")
    maven("https://repo.onarandombox.com/content/groups/public/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.6.0")
    implementation(platform("com.intellectualsites.bom:bom-newest:1.44"))
    compileOnly(group = "com.intellectualsites.plotsquared", name = "plotsquared-core", version = "7.3.8")
    compileOnly(group = "com.intellectualsites.plotsquared", name = "plotsquared-bukkit", version = "7.3.8")
    implementation(group = "co.aikar", name = "acf-paper", version = "0.5.1-SNAPSHOT")
    implementation(group = "org.jetbrains.exposed", name = "exposed-core", version = "0.30.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-jdbc", version = "0.30.1")
    implementation(group = "org.jetbrains.exposed", name = "exposed-java-time", version = "0.30.1")
    implementation(group = "com.fasterxml.jackson.dataformat", name = "jackson-dataformat-yaml", version = "2.14.3")
    implementation(group = "org.mariadb.jdbc", name = "mariadb-java-client", version = "3.5.1")
    implementation(group = "com.fasterxml.jackson.module", name = "jackson-module-kotlin", version = "2.11.0")

    compileOnly(group = "com.onarandombox.multiversecore", name = "Multiverse-Core", version = "4.3.1")
    compileOnly(group = "net.luckperms", name = "api", version = "5.2")
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
        jvmTarget = "17"
        javaParameters = true
        freeCompilerArgs = listOf(
            "-Xopt-in=kotlin.ExperimentalStdlibApi"
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
