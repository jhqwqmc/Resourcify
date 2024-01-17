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

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        maven("https://maven.fabricmc.net")
        maven("https://maven.architectury.dev/")
        maven("https://maven.minecraftforge.net")
        maven("https://repo.essential.gg/repository/maven-public")
        maven("https://jitpack.io")
        maven("https://maven.wagyourtail.xyz/releases")
        maven("https://maven.wagyourtail.xyz/snapshots")
        maven("https://maven.deftu.xyz/releases")
    }
    dependencyResolutionManagement {
        versionCatalogs {
            create("libs")
        }
        resolutionStrategy {
            eachPlugin  {
                when(requested.id.id) {
                    "dev.deftu.gradle.preprocess" -> {
                        useModule("dev.deftu:preprocessor:${requested.version}")
                    }
                    "dev.deftu.gradle.preprocess-root" -> {
                        useModule("dev.deftu:preprocessor:${requested.version}")
                    }
                }
            }
        }
    }
}

val mod_name: String by settings

rootProject.name = mod_name
rootProject.buildFileName = "root.gradle.kts"

listOf(
    "1.8.9-forge",
    "1.12.2-forge",
    "1.16.2-forge",
    "1.16.2-fabric",
    // "1.18.2-forge",
    // "1.18.2-fabric",
    // "1.19.2-fabric",
    // "1.19.2-forge",
    // "1.19.4-forge",
    // "1.19.4-fabric",
    // "1.20.1-forge",
    // "1.20.1-fabric",
    // "1.20.4-forge",
    // "1.20.4-fabric",
    // "1.20.4-neoforge",
).forEach { version ->
    include(":$version")
    project(":$version").apply {
        projectDir = file("versions/$version")
        buildFileName = "../../build.gradle.kts"
    }
}