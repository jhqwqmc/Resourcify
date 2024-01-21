plugins {
    alias(libs.plugins.kotlin)
    id("dev.architectury.loom") version "1.4-SNAPSHOT"
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("com.replaymod.preprocess")
    alias(libs.plugins.shadow)
    alias(libs.plugins.blossom)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.cursegradle)
}

group = "dev.dediamondpro"

java.toolchain.languageVersion = JavaLanguageVersion.of(17)

val mod_name: String by project
val mod_version: String by project
val mod_id: String by project

blossom {
    replaceToken("@NAME@", mod_name)
    replaceToken("@ID@", mod_id)
    replaceToken("@VER@", mod_version)
}

version = mod_version
group = "dev.dediamondpro"
base {
    archivesName.set(mod_id)
}

preprocess {
    vars.put("MC", 12004)
    vars.put("FABRIC", 0)
    vars.put("FORGE", 1)
    vars.put("NEOFORGE", 1)
    vars.put("MODERN", 1)
}

repositories {
    maven("https://repo.essential.gg/repository/maven-public/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.dediamondpro.dev/releases")
    maven("https://repo.spongepowered.org/maven/")
    maven("https://maven.neoforged.net/releases")
    maven("https://api.modrinth.com/maven")
    mavenCentral()
    mavenLocal()
}

val shade: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

loom {
    accessWidenerPath = project.file("src/main/resources/resourcify.accesswidener")
}

dependencies {
    minecraft("com.mojang:minecraft:1.20.4")
    neoForge("net.neoforged:neoforge:20.4.83-beta")
    mappings(loom.officialMojangMappings())

    implementation("thedarkcolour:kotlinforforge-neoforge:4.10.0")
    compileOnly(project(":1.20.4-forge"))

    shade("gg.essential:universalcraft-1.20.4-forge:DIAMOND-1") {
        isTransitive = false
    }
    // Always shade elementa since we use a custom version, relocate to avoid conflicts
    shade("gg.essential:elementa-1.18.1-forge:${libs.versions.elementa.get()}") {
        isTransitive = false
    }
    // Since elementa is relocated, and MineMark doesn't guarantee backwards compatibility, we need to shade this
    shade(libs.bundles.markdown) {
        isTransitive = false
    }

    modCompileOnly("maven.modrinth:oculus:1.20.1-1.6.13a")
}

tasks {
    processResources {
        println("here")
        inputs.property("id", mod_id)
        inputs.property("name", mod_name)
        inputs.property("java", 17)
        inputs.property("java_level", "JAVA_17")
        inputs.property("version", mod_version)
        inputs.property("mcVersionStr", "1.20.4")
        filesMatching(listOf("mcmod.info", "mods.toml", "fabric.mod.json")) {
            expand(
                mapOf(
                    "id" to mod_id,
                    "name" to mod_name,
                    "java" to java,
                    "java_level" to "JAVA_17",
                    "version" to mod_version,
                    "mcVersionStr" to "1.20.4"
                )
            )
        }
    }
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("dev")
        configurations = listOf(shade)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("META-INF/versions/9/**")
        exclude("fabric.mod.json", "fabric.mixins.${mod_id}.json", "mcmod.info")

        mergeServiceFiles()
        relocate("gg.essential.elementa", "dev.dediamondpro.resourcify.libs.elementa")
        relocate("dev.dediamondpro.minemark", "dev.dediamondpro.resourcify.libs.minemark")
        relocate("org.commonmark", "dev.dediamondpro.resourcify.libs.commonmark")
        relocate("org.ccil.cowan.tagsoup", "dev.dediamondpro.resourcify.libs.tagsoup")
        relocate("gg.essential.universal", "dev.dediamondpro.resourcify.libs.universal")
    }
    remapJar {
        input.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
        finalizedBy("copyJar")
        archiveFileName.set("$mod_name (1.20.4-neoforge)-${mod_version}.jar")
        atAccessWideners.add("resourcify.accesswidener")
    }
    register<Copy>("copyJar") {
        File("${project.rootDir}/jars").mkdir()
        from(remapJar.get().archiveFile)
        into("${project.rootDir}/jars")
    }
    clean { delete("${project.rootDir}/jars") }
}