package org.buildmosaic.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * A Gradle plugin that configures a project to be a Mosaic tile library.
 *
 * This plugin applies the necessary plugins and dependencies for developing Mosaic tiles.
 * It configures the project with:
 * - Kotlin JVM plugin
 * - KSP plugin
 * - Mosaic BOM
 * - Required Mosaic dependencies
 * - KSP configuration for tile catalog generation
 */
class MosaicCatalogPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Apply required plugins
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("com.google.devtools.ksp")

    // Apply the BOM
    project.dependencies.add(
      "implementation",
      project.dependencies.platform(
        "org.buildmosaic:mosaic-bom:$MOSAIC_VERSION",
      ),
    )

    // Add Mosaic dependencies
    project.dependencies.add("implementation", "org.buildmosaic:mosaic-core")
    project.dependencies.add("ksp", "org.buildmosaic:mosaic-catalog-ksp")
    project.dependencies.add("testImplementation", "org.buildmosaic:mosaic-test")
  }
}
