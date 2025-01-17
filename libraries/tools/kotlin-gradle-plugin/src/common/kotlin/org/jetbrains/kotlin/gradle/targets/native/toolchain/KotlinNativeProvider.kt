/*
 * Copyright 2010-2023 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.targets.native.toolchain

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.jetbrains.kotlin.compilerRunner.konanDataDir
import org.jetbrains.kotlin.compilerRunner.konanHome
import org.jetbrains.kotlin.compilerRunner.kotlinNativeToolchainEnabled
import org.jetbrains.kotlin.gradle.plugin.KOTLIN_NATIVE_BUNDLE_CONFIGURATION_NAME
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.Companion.kotlinPropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.enabledOnCurrentHost
import org.jetbrains.kotlin.gradle.utils.NativeCompilerDownloader
import org.jetbrains.kotlin.gradle.utils.filesProvider
import org.jetbrains.kotlin.gradle.utils.property
import org.jetbrains.kotlin.konan.properties.KonanPropertiesLoader
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.target.loadConfigurables

/**
 * This is a nested provider for all native tasks
 */
internal class KotlinNativeProvider(
    project: Project,
    konanTargets: Set<KonanTarget>,
    kotlinNativeBundleBuildService: Provider<KotlinNativeBundleBuildService>,
    enableDependenciesDownloading: Boolean = true,
) {
    constructor(
        project: Project,
        konanTarget: KonanTarget,
        kotlinNativeBundleBuildService: Provider<KotlinNativeBundleBuildService>,
    ) : this(project, setOf(konanTarget), kotlinNativeBundleBuildService)

    @get:Internal
    val konanDataDir: Provider<String?> = project.provider { project.konanDataDir }

    @get:Internal
    val bundleDirectory: DirectoryProperty = project.objects.directoryProperty().fileProvider(
        project.provider {
            project.konanHome
        }
    )

    @get:Internal
    val reinstallBundle: Property<Boolean> = project.objects.property(project.kotlinPropertiesProvider.nativeReinstall)

    @get:Input
    internal val kotlinNativeBundleVersion: Provider<String> = bundleDirectory.zip(reinstallBundle) { bundleDir, reinstallFlag ->
        val kotlinNativeVersion = NativeCompilerDownloader.getDependencyNameWithOsAndVersion(project)
        if (project.kotlinNativeToolchainEnabled) {
            kotlinNativeBundleBuildService.get().prepareKotlinNativeBundle(
                project,
                kotlinNativeCompilerConfiguration,
                kotlinNativeVersion,
                bundleDir.asFile,
                reinstallFlag,
                konanTargets
            )
        }
        kotlinNativeVersion
    }

    @get:Input
    val kotlinNativeDependencies: Provider<Set<String>> =
        kotlinNativeBundleVersion
            .zip(bundleDirectory) { _, bundleDir ->
                val requiredDependencies = mutableSetOf<String>()
                if (project.kotlinNativeToolchainEnabled && enableDependenciesDownloading) {
                    val distribution = Distribution(bundleDir.asFile.absolutePath, konanDataDir = konanDataDir.orNull)
                    konanTargets.forEach { konanTarget ->
                        if (konanTarget.enabledOnCurrentHost) {
                            val konanPropertiesLoader = loadConfigurables(
                                konanTarget,
                                distribution.properties,
                                distribution.dependenciesDir,
                                progressCallback = { url, currentBytes, totalBytes ->
                                    project.logger.info("Downloading dependency for Kotlin Native: $url (${currentBytes}/${totalBytes}). ")
                                }
                            ) as KonanPropertiesLoader

                            requiredDependencies.addAll(konanPropertiesLoader.dependencies)
                            konanPropertiesLoader.downloadDependencies()
                        }
                    }
                }
                requiredDependencies
            }

    // Gradle tries to evaluate this val during configuration cache,
    // which lead to resolving configuration, even if k/n bundle is in konan home directory.
    @Transient
    private val kotlinNativeCompilerConfiguration: ConfigurableFileCollection = project.filesProvider {
        // without enabled there is no configuration with this name, so we should return empty provider to support configuration cache
        if (project.kotlinNativeToolchainEnabled) {
            project.configurations.named(
                KOTLIN_NATIVE_BUNDLE_CONFIGURATION_NAME
            )
        } else {
            null
        }
    }

}