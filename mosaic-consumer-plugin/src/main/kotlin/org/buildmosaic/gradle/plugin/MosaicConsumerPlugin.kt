package org.buildmosaic.gradle.plugin

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
        "org.buildmosaic:mosaic-bom:$MOSAIC_VERSION",
      ),
    )

    // Add KSP dependency
    project.dependencies.add("ksp", "org.buildmosaic:mosaic-consumer-ksp")

    // Apply mosaic dependencies
    project.dependencies.add("implementation", "org.buildmosaic:mosaic-core")
    project.dependencies.add("testImplementation", "org.buildmosaic:mosaic-test")
  }
}
