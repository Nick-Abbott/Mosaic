package org.buildmosaic.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import javax.inject.Inject

abstract class MosaicAnalysisExtension
  @Inject
  constructor(objects: ObjectFactory) {
    val roots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val compilerPluginJar: RegularFileProperty = objects.fileProperty()
  }

class MosaicAnalysisPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("mosaicAnalysis", MosaicAnalysisExtension::class.java)
    val compilerConfiguration =
      project.configurations.create("mosaicAnalysisCompiler") {
        it.isVisible = false
        it.isCanBeConsumed = false
        it.isCanBeResolved = true
      }
    project.dependencies.add(compilerConfiguration.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
    project.plugins.withId("org.jetbrains.kotlin.jvm") {
      val compile = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java)
      val mainSources =
        project.extensions.getByType(
          KotlinJvmProjectExtension::class.java,
        ).sourceSets.getByName("main").kotlin
      val extract =
        project.tasks.register("extractMosaicMain", ExtractMosaicTask::class.java) { task ->
          task.group = "verification"
          task.description = "Extract a complete Mosaic main summary in a separate Kotlin compiler process"
          task.sources.from(mainSources)
          task.javaSources.from(project.fileTree("src/main/java") { it.include("**/*.java") })
          task.supportedSourceRoot.set(project.file("src/main/kotlin").absolutePath)
          task.compileClasspath.from(project.configurations.getByName("compileClasspath"))
          task.compilerClasspath.from(compilerConfiguration)
          task.compilerPluginJar.set(extension.compilerPluginJar)
          task.moduleId.set(project.provider { project.group.toString() + ":" + project.name })
          task.kotlinModuleName.set(compile.flatMap { it.compilerOptions.moduleName })
          task.jvmTarget.set(compile.flatMap { it.compilerOptions.jvmTarget }.map { it.target })
          task.languageVersion.set(compile.map { it.compilerOptions.languageVersion.orNull?.version.orEmpty() })
          task.apiVersion.set(compile.map { it.compilerOptions.apiVersion.orNull?.version.orEmpty() })
          task.summaryFile.set(project.layout.buildDirectory.file("mosaic-analysis/main/summary.json"))
        }
      project.tasks.named("jar", Jar::class.java) { jar ->
        jar.dependsOn(extract)
        jar.from(extract.flatMap { it.summaryFile }) {
          it.into("META-INF/mosaic-analysis/v1")
        }
      }
      val verify =
        project.tasks.register("verifyMosaicMain", VerifyMosaicTask::class.java) { task ->
          task.group = "verification"
          task.description = "Verify selected Mosaic main roots against selected dependency JAR summaries"
          task.summaryFile.set(extract.flatMap { it.summaryFile })
          task.dependencyJars.from(project.configurations.getByName("compileClasspath"))
          task.roots.set(extension.roots)
          task.reportFile.set(project.layout.buildDirectory.file("reports/mosaic-analysis/main.txt"))
          task.dependsOn(extract)
        }
      val aggregate =
        project.tasks.register("verifyMosaic") { task ->
          task.group = "verification"
          task.dependsOn(verify)
        }
      project.tasks.named("check") { it.dependsOn(aggregate) }
    }
  }
}
