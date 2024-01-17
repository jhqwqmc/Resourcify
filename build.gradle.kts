import com.google.gson.Gson
import groovy.lang.GroovyObjectSupport
import xyz.wagyourtail.unimined.api.task.ExportMappingsTask
import xyz.wagyourtail.unimined.api.task.RemapJarTask
import xyz.wagyourtail.unimined.internal.mapping.MappingsProvider
import xyz.wagyourtail.unimined.internal.mapping.task.ExportMappingsTaskImpl

/*
 * This file is part of Resourcify
 * Copyright (C) 2023 DeDiamondPro
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License Version 3 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    alias(libs.plugins.kotlin)
    id("xyz.wagyourtail.unimined") version "1.1.1"
    id("dev.deftu.gradle.preprocess")
    alias(libs.plugins.shadow)
    alias(libs.plugins.blossom)
    alias(libs.plugins.minotaur)
    alias(libs.plugins.cursegradle)
}

val mod_name: String by project
val mod_version: String by project
val mod_id: String by project

val platform = Platform.of(project)
extra.set("loom.platform", if (platform.isForge || platform.isNeoForge) "forge" else "fabric")

repositories {
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://repo.essential.gg/repository/maven-public/")
    maven("https://maven.dediamondpro.dev/releases")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    maven("https://repo.spongepowered.org/maven/")
    maven("https://maven.minecraftforge.net")
    maven("https://api.modrinth.com/maven")
    mavenCentral()
    mavenLocal()
}

val shade: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

blossom {
    replaceToken("@NAME@", mod_name)
    replaceToken("@ID@", mod_id)
    replaceToken("@VER@", mod_version)
}

preprocess {
    vars.put("MODERN", if (platform.mcMinor >= 16) 1 else 0)
    vars.put("MC", platform.mcVersion)
    vars.put("FABRIC", if (platform.isFabric) 1 else 0)
    vars.put("FORGE", if (platform.isForge) 1 else 0)
    vars.put("NEOFORGE", if (platform.isNeoForge) 1 else 0)
}

version = mod_version
group = "dev.dediamondpro"
base {
    archivesName.set(mod_id)
}

val javaVersion = when {
    platform.mcVersion >= 11800 -> JavaVersion.VERSION_17
    platform.mcVersion >= 11700 -> JavaVersion.VERSION_16
    else -> JavaVersion.VERSION_1_8
}

java {
    targetCompatibility = javaVersion
    sourceCompatibility = javaVersion
}

tasks.compileKotlin.configure {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs.filterNot {
            it.startsWith("-Xjvm-default=")
        } + listOf("-Xjvm-default=" + (if (platform.mcVersion >= 11400) "all" else "all-compatibility"))
        jvmTarget = javaVersion.toString()
    }
}

unimined.minecraft {
    version(platform.mcVersionStr)

    mappings {
        if (platform.mcVersion < 11700 && platform.isForge) {
            val mcp_channel: String? by project
            mcp(mcp_channel ?: "stable", project.property("mcp_version") as String)
            searge()
        } else {
            intermediary()
            if (platform.isForge || platform.isNeoForge) {
                mojmap()
                searge()
            } else {
                yarn(project.property("yarn_build") as String)
            }
        }
    }
    if (platform.isForge) minecraftForge {
        loader(project.property("forge_version") as String)
        mixinConfig("forge.mixins.$mod_id.json")
    }

    if (platform.isFabric) fabric {
        accessWidener = file("src/main/resources/resourcify.accesswidener")
        loader("0.15.5")
    }

    if (platform.isLegacy) runs.config("client") {
        this.args.add(0, "--tweakClass")
        this.args.add(1, "org.spongepowered.asm.launch.MixinTweaker")
    }

    defaultRemapJar = true
}

class LoomGradleExtension : GroovyObjectSupport() {
    var mappingConfiguration: Any? = null
}

fun Any.setGroovyProperty(name: String, value: Any) = withGroovyBuilder { metaClass }.setProperty(this, name, value)

val minecraft = unimined.minecrafts[sourceSets.main.get()]

val loom = LoomGradleExtension()
extensions.add("loom", loom)
val mappingsConfig = object {
    var tinyMappings: java.nio.file.Path? = null
    var tinyMappingsWithSrg: java.nio.file.Path? = null
}
loom.setGroovyProperty("mappingConfiguration", mappingsConfig)
val tinyMappings: File = file("${projectDir}/build/tmp/tinyMappings.tiny").also { file ->
    val export = ExportMappingsTaskImpl.ExportImpl(minecraft.mappings as MappingsProvider).apply {
        location = file
        type = ExportMappingsTask.MappingExportTypes.TINY_V2
        setSourceNamespace("official")
        if (platform.mcVersion < 11700 && platform.isForge) {
            setTargetNamespaces(listOf("mcp"))
            renameNs[minecraft.mappings.getNamespace("mcp")] = "named"
        } else {
            if (platform.isFabric) {
                setTargetNamespaces(listOf("intermediary", "yarn"))
                renameNs[minecraft.mappings.getNamespace("yarn")] = "named"
            } else {
                setTargetNamespaces(listOf("intermediary", "mojmap"))
                renameNs[minecraft.mappings.getNamespace("mojmap")] = "named"
            }
        }
    }
    export.validate()
    export.exportFunc((minecraft.mappings as MappingsProvider).mappingTree)
}
mappingsConfig.setGroovyProperty("tinyMappings", tinyMappings.toPath())
if (platform.isForge) {
    val tinyMappingsWithSrg: File = file("${projectDir}/build/tmp/tinyMappingsWithSrg.tiny").also { file ->
        val export = ExportMappingsTaskImpl.ExportImpl(minecraft.mappings as MappingsProvider).apply {
            location = file
            type = ExportMappingsTask.MappingExportTypes.TINY_V2
            setSourceNamespace("official")
            if (platform.mcVersion < 11700) {
                setTargetNamespaces(listOf("mcp", "searge"))
                renameNs[minecraft.mappings.getNamespace("mcp")] = "named"
            } else {
                setTargetNamespaces(listOf("intermediary", "searge", "mojmap"))
                renameNs[minecraft.mappings.getNamespace("mojmap")] = "named"
            }
            renameNs[minecraft.mappings.getNamespace("searge")] = "srg"
        }
        export.validate()
        export.exportFunc((minecraft.mappings as MappingsProvider).mappingTree)
    }
    mappingsConfig.setGroovyProperty("tinyMappingsWithSrg", tinyMappingsWithSrg.toPath())
}

val modCompileOnly: Configuration by configurations.creating {
    configurations.getByName("compileOnly").extendsFrom(this)
}

val modRuntimeOnly: Configuration by configurations.creating {
    configurations.getByName("runtimeOnly").extendsFrom(this)
}

minecraft.apply {
    mods.remap(modCompileOnly)
    mods.remap(modRuntimeOnly)
}

dependencies {
    val platformStr = "${platform.mcVersionStr}-${if (platform.isFabric) "fabric" else "forge"}"
    val elementaPlatform: String? by project
    val universalPlatform: String? by project
    if (platform.isFabric) {
        val fabricApiVersion: String by project
        "modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        "modImplementation"("net.fabricmc:fabric-language-kotlin:${libs.versions.fabric.language.kotlin.get()}")
        //"modImplementation"("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        //"modImplementation"("net.fabricmc:fabric-language-kotlin:${libs.versions.fabric.language.kotlin.get()}")
        modCompileOnly("gg.essential:elementa-${elementaPlatform ?: platformStr}:${libs.versions.elementa.get()}")
        modCompileOnly("gg.essential:universalcraft-${universalPlatform ?: platformStr}:${libs.versions.universal.get()}")
    } else if (platform.isForge || platform.isNeoForge) {
        if (platform.isLegacy) {
            shade(libs.bundles.kotlin) { isTransitive = false }
            shade(libs.mixin) { isTransitive = false }
        } else {
            val kotlinForForgeVersion: String by project
            implementation("thedarkcolour:kotlinforforge${if (platform.isNeoForge) "-neoforge" else ""}:$kotlinForForgeVersion")
        }
    }
    shade("gg.essential:universalcraft-${universalPlatform ?: platformStr}:${libs.versions.universal.get()}") {
        isTransitive = false
    }
    // Always shade elementa since we use a custom version, relocate to avoid conflicts
    shade("gg.essential:elementa-${elementaPlatform ?: platformStr}:${libs.versions.elementa.get()}") {
        isTransitive = false
    }
    // Since elementa is relocated, and MineMark doesn't guarantee backwards compatibility, we need to shade this
    shade(libs.bundles.markdown) {
        isTransitive = false
    }

    val irisVersion: String by project
    if (!platform.isLegacy) modCompileOnly(
        if (platform.isFabric) "maven.modrinth:iris:$irisVersion"
        else "maven.modrinth:oculus:$irisVersion"
    )
}

val remapJar by tasks.named("remapJar", RemapJarTask::class) {
    archiveClassifier.set("")
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.flatMap { it.archiveFile })
}

tasks {
    processResources {
        inputs.property("id", mod_id)
        inputs.property("name", mod_name)
        val java = if (platform.mcMinor >= 18) {
            17
        } else {
            if (platform.mcMinor == 17) 16 else 8
        }
        val compatLevel = "JAVA_${java}"
        inputs.property("java", java)
        inputs.property("java_level", compatLevel)
        inputs.property("version", mod_version)
        inputs.property("mcVersionStr", platform.mcVersionStr)
        filesMatching(listOf("mcmod.info", "mods.toml", "fabric.mod.json")) {
            expand(
                mapOf(
                    "id" to mod_id,
                    "name" to mod_name,
                    "java" to java,
                    "java_level" to compatLevel,
                    "version" to mod_version,
                    "mcVersionStr" to getInternalMcVersionStr()
                )
            )
        }

        if (platform.mcMinor <= 12) {
            dependsOn("generateLangFiles")
            from("${project.buildDir}/generated/lang") {
                into("assets/$mod_id/lang")
            }
            exclude("**/assets/$mod_id/lang/*.json")
        }
    }
    register("generateLangFiles") {
        val gson = Gson()
        val generatedDir = File(project.buildDir, "generated/lang")
        generatedDir.mkdirs()
        rootProject.file("src/main/resources/assets/$mod_id/lang").listFiles()?.filter {
            it.extension == "json"
        }?.forEach { jsonFile ->
            val map: Map<String, String> =
                gson.fromJson(jsonFile.reader(), Map::class.java) as Map<String, String>
            val fileName = jsonFile.nameWithoutExtension.split("_").let {
                "${it[0]}_${it[1].uppercase()}.lang"
            }
            val langFile = File(generatedDir, fileName)
            langFile.printWriter().use { out ->
                map.forEach { (key, value) ->
                    out.println("$key=$value")
                }
            }
        }
    }
    withType<Jar> {
        if (platform.isFabric) {
            exclude("mcmod.info", "mods.toml", "pack.mcmeta", "forge.mixins.${mod_id}.json")
        } else {
            exclude("fabric.mod.json", "fabric.mixins.${mod_id}.json")
            if (platform.isLegacy) {
                exclude("mods.toml", "pack.mcmeta")
            } else {
                exclude("mcmod.info")
            }
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("LICENSE.LESSER"))
    }
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("dev")
        configurations = listOf(shade)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("META-INF/versions/9/**")

        mergeServiceFiles()
        relocate("gg.essential.elementa", "dev.dediamondpro.resourcify.libs.elementa")
        relocate("dev.dediamondpro.minemark", "dev.dediamondpro.resourcify.libs.minemark")
        relocate("org.commonmark", "dev.dediamondpro.resourcify.libs.commonmark")
        relocate("org.ccil.cowan.tagsoup", "dev.dediamondpro.resourcify.libs.tagsoup")
        relocate("gg.essential.universal", "dev.dediamondpro.resourcify.libs.universal")
    }
    /*remapJar {
        input.set(shadowJar.get().archiveFile)
        archiveClassifier.set("")
        finalizedBy("copyJar")
        archiveFileName.set("$mod_name (${getMcVersionStr()}-${platform.loaderStr})-${mod_version}.jar")
    }*/
    jar {
        if (platform.isLegacy) {
            manifest {
                attributes(
                    mapOf(
                        "ModSide" to "CLIENT",
                        "TweakOrder" to "0",
                        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
                        "ForceLoadAsMod" to true
                    )
                )
            }
        }
        dependsOn(shadowJar)
        archiveClassifier.set("")
        enabled = false
    }
    /*register<Copy>("copyJar") {
        File("${project.rootDir}/jars").mkdir()
        from(remapJar.get().archiveFile)
        into("${project.rootDir}/jars")
    }
    clean { delete("${project.rootDir}/jars") }
    project.modrinth {
        token.set(System.getenv("MODRINTH_TOKEN"))
        projectId.set("resourcify")
        versionNumber.set(mod_version)
        versionName.set("[${getMcVersionStr()}-${platform.loaderStr}] Resourcify $mod_version")
        uploadFile.set(remapJar.get().archiveFile as Any)
        gameVersions.addAll(getMcVersionList())
        if (platform.isFabric) {
            loaders.add("fabric")
            loaders.add("quilt")
        } else if (platform.isForge) {
            loaders.add("forge")
            if (platform.mcMinor == 20 && platform.mcPatch == 1) loaders.add("neoforge")
        }
        changelog.set(file("../../changelog.md").readText())
        dependencies {
            if (platform.isForge && !platform.isLegacyForge) {
                required.project("kotlin-for-forge")
            } else if (!platform.isLegacyForge) {
                required.project("fabric-api")
                required.project("fabric-language-kotlin")
            }
        }
    }
    project.curseforge {
        project(closureOf<CurseProject> {
            apiKey = System.getenv("CURSEFORGE_TOKEN")
            id = "870076"
            changelog = file("../../changelog.md")
            changelogType = "markdown"
            if (!platform.isLegacyForge) relations(closureOf<CurseRelation> {
                if (platform.isForge && !platform.isLegacyForge) {
                    requiredDependency("kotlin-for-forge")
                } else if (!platform.isLegacyForge) {
                    requiredDependency("fabric-api")
                    requiredDependency("fabric-language-kotlin")
                }
            })
            gameVersionStrings.addAll(getMcVersionList())
            if (platform.isFabric) {
                addGameVersion("Fabric")
                addGameVersion("Quilt")
            } else if (platform.isForge) {
                addGameVersion("Forge")
                if (platform.mcMinor >= 20) addGameVersion("NeoForge")
            }
            releaseType = "release"
            mainArtifact(remapJar.get().archiveFile, closureOf<CurseArtifact> {
                displayName = "[${getMcVersionStr()}-${platform.loaderStr}] Resourcify $mod_version"
            })
        })
        options(closureOf<Options> {
            javaVersionAutoDetect = false
            javaIntegration = false
            forgeGradleIntegration = false
        })
    }
    register("publish") {
        dependsOn(modrinth)
        dependsOn(curseforge)
    }*/
}


fun getMcVersionStr(): String {
    return when (platform.mcVersionStr) {
        in listOf("1.8.9", "1.12.2", "1.19.4") -> platform.mcVersionStr
        "1.18.2" -> if (platform.isFabric) "1.18.x" else "1.18.2"
        "1.19.2" -> "1.19.0-1.19.2"
        "1.20.1" -> "1.20-1.20.1"
        "1.20.4" -> "1.20.2+"
        else -> {
            val dots = platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${platform.mcVersionStr}.x"
            else "${platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getInternalMcVersionStr(): String {
    return when (platform.mcVersionStr) {
        in listOf("1.8.9", "1.12.2", "1.19.4") -> platform.mcVersionStr
        "1.19.2" -> ">=1.19 <=1.19.2"
        "1.20.1" -> ">=1.20 <=1.20.1"
        "1.20.4" -> ">=1.20.2"
        else -> {
            val dots = platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${platform.mcVersionStr}.x"
            else "${platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getMcVersionList(): List<String> {
    return when (platform.mcVersionStr) {
        "1.8.9" -> listOf("1.8.9")
        "1.12.2" -> listOf("1.12.2")
        "1.16.2" -> listOf("1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5")
        "1.17.1" -> listOf("1.17", "1.17.1")
        "1.18.2" -> if (platform.isFabric) listOf("1.18", "1.18.1", "1.18.2") else listOf("1.18.2")
        "1.19.2" -> listOf("1.19", "1.19.1", "1.19.2")
        "1.19.4" -> listOf("1.19.4")
        "1.20.1" -> listOf("1.20", "1.20.1")
        "1.20.4" -> listOf("1.20.2", "1.20.3", "1.20.4")
        else -> error("Unknown version")
    }
}


data class Platform(
    val mcMajor: Int,
    val mcMinor: Int,
    val mcPatch: Int,
    val loader: Loader
) {
    val mcVersion = mcMajor * 10000 + mcMinor * 100 + mcPatch
    val mcVersionStr = listOf(mcMajor, mcMinor, mcPatch).dropLastWhile { it == 0 }.joinToString(".")
    val loaderStr = loader.toString().lowercase()

    val isFabric = loader == Loader.Fabric
    val isForge = loader == Loader.Forge
    val isNeoForge = loader == Loader.NeoForge
    val isForgeLike = loader == Loader.Forge || loader == Loader.NeoForge
    val isLegacy = mcVersion <= 11202

    override fun toString(): String {
        return "$mcVersionStr-$loaderStr"
    }

    enum class Loader {
        Fabric,
        Forge,
        NeoForge
    }

    companion object {
        fun of(project: Project): Platform {
            val (versionStr, loaderStr) = project.name.split("-", limit = 2)
            val (major, minor, patch) = versionStr.split('.').map { it.toInt() } + listOf(0)
            val loader = Loader.values().first { it.name.lowercase() == loaderStr.lowercase() }
            return Platform(major, minor, patch, loader)
        }
    }
}

/*preprocess {
    vars.put("MODERN", if (project.platform.mcMinor >= 16) 1 else 0)
}

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

tasks.compileKotlin.configure {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs.filterNot {
            it.startsWith("-Xjvm-default=")
        } + listOf("-Xjvm-default=" + (if (platform.mcVersion >= 11400) "all" else "all-compatibility"))
    }
}

loom {
    if (project.platform.isLegacyForge) runConfigs {
        "client" { programArgs("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker") }
    }
    if (project.platform.mcVersion >= 12002) {
        accessWidenerPath = file("src/main/resources/resourcify.accesswidener")
    }
    if (project.platform.isForge) forge {
        mixinConfig("${project.platform.loaderStr}.mixins.${mod_id}.json")

        //if (project.platform.mcVersion >= 12002) {
        //    convertAccessWideners.set(true)
        //    println(accessWidenerPath.get().asFile.path)
        //    extraAccessWideners.add(accessWidenerPath.get().asFile.path)
        //}
    }

    mixin.defaultRefmapName.set("${project.platform.loaderStr}.mixins.${mod_id}.refmap.json")
}

dependencies {
    val platformStr = "${platform.mcVersionStr}-${if (platform.isFabric) "fabric" else "forge"}"
    val elementaPlatform: String? by project
    val universalPlatform: String? by project
    if (platform.isFabric) {
        val fabricApiVersion: String by project
        modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
        modImplementation("net.fabricmc:fabric-language-kotlin:${libs.versions.fabric.language.kotlin.get()}")
        modCompileOnly("gg.essential:elementa-${elementaPlatform ?: platformStr}:${libs.versions.elementa.get()}")
        modCompileOnly("gg.essential:universalcraft-${universalPlatform ?: platformStr}:${libs.versions.universal.get()}")
    } else if (platform.isForge || platform.isNeoForge) {
        if (platform.isLegacyForge) {
            shade(libs.bundles.kotlin) { isTransitive = false }
            shade(libs.mixin) { isTransitive = false }
            annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
        } else {
            val kotlinForForgeVersion: String by project
            implementation("thedarkcolour:kotlinforforge${if (platform.isNeoForge) "-neoforge" else ""}:$kotlinForForgeVersion")
        }
    }
    shade("gg.essential:universalcraft-${universalPlatform ?: platformStr}:${libs.versions.universal.get()}") {
        isTransitive = false
    }
    // Always shade elementa since we use a custom version, relocate to avoid conflicts
    shade("gg.essential:elementa-${elementaPlatform ?: platformStr}:${libs.versions.elementa.get()}") {
        isTransitive = false
    }
    // Since elementa is relocated, and MineMark doesn't guarantee backwards compatibility, we need to shade this
    shade(libs.bundles.markdown) {
        isTransitive = false
    }

    val irisVersion: String by project
    if (!platform.isLegacyForge) modCompileOnly(
        if (platform.isFabric) "maven.modrinth:iris:$irisVersion"
        else "maven.modrinth:oculus:$irisVersion"
    )
}



tasks {
    processResources {
        inputs.property("id", mod_id)
        inputs.property("name", mod_name)
        val java = if (project.platform.mcMinor >= 18) {
            17
        } else {
            if (project.platform.mcMinor == 17) 16 else 8
        }
        val compatLevel = "JAVA_${java}"
        inputs.property("java", java)
        inputs.property("java_level", compatLevel)
        inputs.property("version", mod_version)
        inputs.property("mcVersionStr", project.platform.mcVersionStr)
        filesMatching(listOf("mcmod.info", "mods.toml", "fabric.mod.json")) {
            expand(
                mapOf(
                    "id" to mod_id,
                    "name" to mod_name,
                    "java" to java,
                    "java_level" to compatLevel,
                    "version" to mod_version,
                    "mcVersionStr" to getInternalMcVersionStr()
                )
            )
        }

        if (project.platform.mcMinor <= 12) {
            dependsOn("generateLangFiles")
            from("${project.buildDir}/generated/lang") {
                into("assets/$mod_id/lang")
            }
            TODO: Add lang exclude back
        }
    }
    register("generateLangFiles") {
        val gson = Gson()
        val generatedDir = File(project.buildDir, "generated/lang")
        generatedDir.mkdirs()
        rootProject.file("src/main/resources/assets/$mod_id/lang").listFiles()?.filter {
            it.extension == "json"
        }?.forEach { jsonFile ->
            val map: Map<String, String> =
                gson.fromJson(jsonFile.reader(), Map::class.java) as Map<String, String>
            val fileName = jsonFile.nameWithoutExtension.split("_").let {
                "${it[0]}_${it[1].uppercase()}.lang"
            }
            val langFile = File(generatedDir, fileName)
            langFile.printWriter().use { out ->
                map.forEach { (key, value) ->
                    out.println("$key=$value")
                }
            }
        }
    }
    withType<Jar> {
        if (project.platform.isFabric) {
            exclude("mcmod.info", "mods.toml", "pack.mcmeta", "forge.mixins.${mod_id}.json")
        } else {
            exclude("fabric.mod.json", "fabric.mixins.${mod_id}.json")
            if (project.platform.isLegacyForge) {
                exclude("mods.toml", "pack.mcmeta")
            } else {
                exclude("mcmod.info")
            }
        }
        from(rootProject.file("LICENSE"))
        from(rootProject.file("LICENSE.LESSER"))
    }
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("dev")
        configurations = listOf(shade)
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        exclude("META-INF/versions/9/**")

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
        archiveFileName.set("$mod_name (${getMcVersionStr()}-${platform.loaderStr})-${mod_version}.jar")
    }
    jar {
        if (project.platform.isLegacyForge) {
            manifest {
                attributes(
                    mapOf(
                        "ModSide" to "CLIENT",
                        "TweakOrder" to "0",
                        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
                        "ForceLoadAsMod" to true
                    )
                )
            }
        }
        dependsOn(shadowJar)
        archiveClassifier.set("")
        enabled = false
    }
    register<Copy>("copyJar") {
        File("${project.rootDir}/jars").mkdir()
        from(remapJar.get().archiveFile)
        into("${project.rootDir}/jars")
    }
    clean { delete("${project.rootDir}/jars") }
    project.modrinth {
        token.set(System.getenv("MODRINTH_TOKEN"))
        projectId.set("resourcify")
        versionNumber.set(mod_version)
        versionName.set("[${getMcVersionStr()}-${platform.loaderStr}] Resourcify $mod_version")
        uploadFile.set(remapJar.get().archiveFile as Any)
        gameVersions.addAll(getMcVersionList())
        if (platform.isFabric) {
            loaders.add("fabric")
            loaders.add("quilt")
        } else if (platform.isForge) {
            loaders.add("forge")
            if (platform.mcMinor == 20 && platform.mcPatch == 1) loaders.add("neoforge")
        }
        changelog.set(file("../../changelog.md").readText())
        dependencies {
            if (platform.isForge && !platform.isLegacyForge) {
                required.project("kotlin-for-forge")
            } else if (!platform.isLegacyForge) {
                required.project("fabric-api")
                required.project("fabric-language-kotlin")
            }
        }
    }
    project.curseforge {
        project(closureOf<CurseProject> {
            apiKey = System.getenv("CURSEFORGE_TOKEN")
            id = "870076"
            changelog = file("../../changelog.md")
            changelogType = "markdown"
            if (!platform.isLegacyForge) relations(closureOf<CurseRelation> {
                if (platform.isForge && !platform.isLegacyForge) {
                    requiredDependency("kotlin-for-forge")
                } else if (!platform.isLegacyForge) {
                    requiredDependency("fabric-api")
                    requiredDependency("fabric-language-kotlin")
                }
            })
            gameVersionStrings.addAll(getMcVersionList())
            if (platform.isFabric) {
                addGameVersion("Fabric")
                addGameVersion("Quilt")
            } else if (platform.isForge) {
                addGameVersion("Forge")
                if (platform.mcMinor >= 20) addGameVersion("NeoForge")
            }
            releaseType = "release"
            mainArtifact(remapJar.get().archiveFile, closureOf<CurseArtifact> {
                displayName = "[${getMcVersionStr()}-${platform.loaderStr}] Resourcify $mod_version"
            })
        })
        options(closureOf<Options> {
            javaVersionAutoDetect = false
            javaIntegration = false
            forgeGradleIntegration = false
        })
    }
    register("publish") {
        dependsOn(modrinth)
        dependsOn(curseforge)
    }
}

fun getMcVersionStr(): String {
    return when (project.platform.mcVersionStr) {
        in listOf("1.8.9", "1.12.2", "1.19.4") -> project.platform.mcVersionStr
        "1.18.2" -> if (platform.isFabric) "1.18.x" else "1.18.2"
        "1.19.2" -> "1.19.0-1.19.2"
        "1.20.1" -> "1.20-1.20.1"
        "1.20.4" -> "1.20.2+"
        else -> {
            val dots = project.platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${project.platform.mcVersionStr}.x"
            else "${project.platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getInternalMcVersionStr(): String {
    return when (project.platform.mcVersionStr) {
        in listOf("1.8.9", "1.12.2", "1.19.4") -> project.platform.mcVersionStr
        "1.19.2" -> ">=1.19 <=1.19.2"
        "1.20.1" -> ">=1.20 <=1.20.1"
        "1.20.4" -> ">=1.20.2"
        else -> {
            val dots = project.platform.mcVersionStr.count { it == '.' }
            if (dots == 1) "${project.platform.mcVersionStr}.x"
            else "${project.platform.mcVersionStr.substringBeforeLast(".")}.x"
        }
    }
}

fun getMcVersionList(): List<String> {
    return when (project.platform.mcVersionStr) {
        "1.8.9" -> listOf("1.8.9")
        "1.12.2" -> listOf("1.12.2")
        "1.16.2" -> listOf("1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5")
        "1.17.1" -> listOf("1.17", "1.17.1")
        "1.18.2" -> if (platform.isFabric) listOf("1.18", "1.18.1", "1.18.2") else listOf("1.18.2")
        "1.19.2" -> listOf("1.19", "1.19.1", "1.19.2")
        "1.19.4" -> listOf("1.19.4")
        "1.20.1" -> listOf("1.20", "1.20.1")
        "1.20.4" -> listOf("1.20.2", "1.20.3", "1.20.4")
        else -> error("Unknown version")
    }
}*/