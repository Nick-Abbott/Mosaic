package com.buildmosaic.gradle.plugin

import com.google.devtools.ksp.gradle.KspTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

class MosaicConsumerPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    // Apply required plugins
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("com.google.devtools.ksp")

    // Register the merge+generate task
    val mergeTask =
      project.tasks.register(
        "mergeMosaicTileCatalogs",
        MergeMosaicTileCatalogs::class.java,
      ) {
        description = "Merges META-INF/mosaic/mosaic-catalog.list from compileClasspath" +
          " and generates LibraryTileRegistry.kt"
        group = "mosaic"

        compileClasspath.from(project.configurations.getByName("compileClasspath"))
        outputDir.set(project.layout.buildDirectory.dir("generated/mosaic/kotlin"))
      }

    val generatedDir = mergeTask.flatMap { it.outputDir }

    project.extensions.configure(KotlinProjectExtension::class.java) {
      sourceSets.getByName("main").kotlin.srcDir(generatedDir)
    }

    project.tasks.named("compileKotlin").configure { dependsOn(mergeTask) }
    project.tasks.withType(KspTask::class.java).configureEach { dependsOn(mergeTask) }

    // Apply the BOM
    project.dependencies.add(
      "implementation",
      project.dependencies.platform(
        "com.buildmosaic:mosaic-bom:$MOSAIC_VERSION",
      ),
    )

    // Add KSP dependency
    project.dependencies.add("ksp", "com.buildmosaic:mosaic-build-ksp")

    // Apply mosaic dependencies
    project.dependencies.add("implementation", "com.buildmosaic:mosaic-core")
    project.dependencies.add("testImplementation", "com.buildmosaic:mosaic-test")

    // Configure KSP
    project.afterEvaluate {
      try {
        val kspExtension = project.extensions.findByName("ksp")
        if (kspExtension != null) {
          val argMethod =
            kspExtension.javaClass
              .getMethod("arg", String::class.java, String::class.java)
          argMethod.invoke(kspExtension, "mosaic.tileBase", "com.buildmosaic.core.Tile")
          argMethod.invoke(kspExtension, "mosaic.callLibraryRegistry", "true")
        }
      } catch (e: ReflectiveOperationException) {
        project.logger.warn("Could not configure KSP: ${e.message}")
      }
    }
  }
}
