package org.buildmosaic.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
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
    project.afterEvaluate {
      if (!project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
        throw GradleException("Mosaic analysis prototype requires a pure Kotlin/JVM project")
      }
    }
    project.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerMain(project, extension, compilerConfiguration)
    }
  }

  private fun registerMain(
    project: Project,
    extension: MosaicAnalysisExtension,
    compilerConfiguration: Configuration,
  ) {
    val compile = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java)
    val extract = registerExtraction(project, extension, compilerConfiguration, compile)
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

  private fun registerExtraction(
    project: Project,
    extension: MosaicAnalysisExtension,
    compilerConfiguration: Configuration,
    compile: TaskProvider<KotlinJvmCompile>,
  ): TaskProvider<ExtractMosaicTask> {
    val kotlinPluginVersion =
      project.plugins.findPlugin("org.jetbrains.kotlin.jvm")?.javaClass?.`package`?.implementationVersion ?: "unknown"
    val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
    val launcher = project.extensions.getByType(JavaToolchainService::class.java).launcherFor(javaExtension.toolchain)
    val mainSources =
      project.extensions.getByType(
        KotlinJvmProjectExtension::class.java,
      ).sourceSets.getByName("main").kotlin
    return project.tasks.register("extractMosaicMain", ExtractMosaicTask::class.java) { task ->
      task.group = "verification"
      task.description = "Extract a complete Mosaic main summary in a separate Kotlin compiler process"
      task.sources.from(mainSources)
      task.javaSources.from(project.fileTree("src/main/java") { it.include("**/*.java") })
      task.supportedSourceRoot.set(project.file("src/main/kotlin").absolutePath)
      task.compileClasspath.from(project.configurations.getByName("compileClasspath"))
      task.compilerClasspath.from(compilerConfiguration)
      task.additionalCompilerPlugins.from(compile.map { it.pluginClasspath })
      task.friendPaths.from(compile.map { it.friendPaths })
      task.compilerPluginJar.set(extension.compilerPluginJar)
      task.moduleId.set(project.provider { project.group.toString() + ":" + project.name })
      task.kotlinModuleName.set(compile.flatMap { it.compilerOptions.moduleName })
      task.jvmTarget.set(compile.flatMap { it.compilerOptions.jvmTarget }.map { it.target })
      task.languageVersion.set(compile.map { it.compilerOptions.languageVersion.orNull?.version.orEmpty() })
      task.apiVersion.set(compile.map { it.compilerOptions.apiVersion.orNull?.version.orEmpty() })
      task.productionCompilerVersion.set(kotlinPluginVersion)
      task.unsupportedCompilerOptions.set(compile.map(::unsupportedCompilerOptions))
      task.unsupportedProjectPlugins.set(project.provider { unsupportedProjectPlugins(project) })
      task.javaExecutable.set(launcher.map { it.executablePath })
      task.selectedJavaVersion.set(launcher.map { it.metadata.languageVersion.asInt().toString() })
      task.expectedJavaVersion.set(
        compile.flatMap {
          it.kotlinJavaToolchainProvider
        }.flatMap { it.javaVersion }.map { it.majorVersion },
      )
      task.summaryFile.set(project.layout.buildDirectory.file("mosaic-analysis/main/summary.json"))
    }
  }

  private fun unsupportedCompilerOptions(taskCompile: KotlinJvmCompile): List<String> =
    buildList {
      taskCompile.pluginOptions.orNull.orEmpty().flatMap { it.allOptions().entries }.forEach { (id, options) ->
        if (options.isNotEmpty()) add("plugin:$id:${options.size}")
      }
      taskCompile.compilerOptions.freeCompilerArgs.orNull.orEmpty().forEach { add("free:$it") }
      taskCompile.compilerOptions.optIn.orNull.orEmpty().forEach { add("optIn:$it") }
      if (taskCompile.compilerOptions.progressiveMode.orNull == true) add("progressiveMode")
      if (taskCompile.compilerOptions.noJdk.orNull == true) add("noJdk")
      taskCompile.compilerOptions.jvmDefault.orNull?.let { add("jvmDefault:$it") }
      if (taskCompile.multiPlatformEnabled.orNull == true) add("multiPlatformEnabled")
    }

  private fun unsupportedProjectPlugins(project: Project): List<String> =
    listOf(
      "org.jetbrains.kotlin.multiplatform",
      "com.android.application",
      "com.android.library",
      "com.google.devtools.ksp",
    )
      .filter(project.plugins::hasPlugin)
}
