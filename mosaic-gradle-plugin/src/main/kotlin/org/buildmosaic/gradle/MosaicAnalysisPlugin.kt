package org.buildmosaic.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties
import javax.inject.Inject

private const val MOSAIC_SUMMARY_ARTIFACT_TYPE = "mosaic-analysis-summary"
private const val MOSAIC_RESOLUTION_ARTIFACT_TYPE = "mosaic-source-resolution"

abstract class MosaicAnalysisExtension
  @Inject
  constructor(objects: ObjectFactory) {
    val roots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val role: Property<MosaicAnalysisRole> =
      objects.property(MosaicAnalysisRole::class.java).convention(MosaicAnalysisRole.APPLICATION)
    val enforcement: Property<MosaicAnalysisEnforcement> =
      objects.property(MosaicAnalysisEnforcement::class.java).convention(MosaicAnalysisEnforcement.STANDARD)
  }

enum class MosaicAnalysisRole {
  APPLICATION,
  LIBRARY,
}

enum class MosaicAnalysisEnforcement {
  STANDARD,
  STRICT,
}

class MosaicAnalysisPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    registerAnalysisTransforms(project)
    val extension = project.extensions.create("mosaicAnalysis", MosaicAnalysisExtension::class.java)
    val mosaicVersion = installedMosaicVersion()
    val compilerConfiguration =
      project.configurations.create("mosaicAnalysisCompiler") {
        it.isVisible = false
        it.isCanBeConsumed = false
        it.isCanBeResolved = true
      }
    project.dependencies.add(compilerConfiguration.name, "org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
    val compilerPluginConfiguration =
      project.configurations.create("mosaicAnalysisCompilerPlugin") {
        it.isVisible = false
        it.isCanBeConsumed = false
        it.isCanBeResolved = true
        it.isTransitive = false
      }
    project.dependencies.add(
      compilerPluginConfiguration.name,
      "org.buildmosaic:mosaic-compiler-plugin:$mosaicVersion",
    )
    project.afterEvaluate {
      if (!project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
        throw GradleException("Mosaic analysis prototype requires a pure Kotlin/JVM project")
      }
    }
    project.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerMain(project, extension, compilerConfiguration, compilerPluginConfiguration, mosaicVersion)
    }
  }

  private fun installedMosaicVersion(): String {
    val stream =
      javaClass.getResourceAsStream("version.properties")
        ?: throw GradleException("Mosaic analysis plugin is missing its generated version metadata")
    val properties = Properties().apply { stream.use(::load) }
    return properties.getProperty("version")?.takeIf(String::isNotBlank)
      ?: throw GradleException("Mosaic analysis plugin has invalid version metadata")
  }

  private fun registerMain(
    project: Project,
    extension: MosaicAnalysisExtension,
    compilerConfiguration: Configuration,
    compilerPluginConfiguration: Configuration,
    mosaicVersion: String,
  ) {
    val compile = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java)
    val extract =
      registerExtraction(project, compilerConfiguration, compilerPluginConfiguration, mosaicVersion, compile)
    project.tasks.named("jar", Jar::class.java) { jar ->
      jar.dependsOn(extract)
      jar.from(extract.flatMap { it.summaryFile }) {
        it.into("META-INF/mosaic-analysis/v1")
      }
    }
    val verify =
      project.tasks.register("verifyMosaicMain", VerifyMosaicTask::class.java) { task ->
        task.group = "verification"
        task.description = "Verify selected Mosaic main roots against selected dependency summaries"
        task.summaryFile.set(extract.flatMap { it.summaryFile })
        task.dependencyArtifacts.from(project.configurations.getByName("compileClasspath"))
        task.dependencySummaries.from(
          project.configurations.getByName("compileClasspath").incoming.artifactView { view ->
            view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, MOSAIC_SUMMARY_ARTIFACT_TYPE)
          }.files,
        )
        task.roots.set(extension.roots)
        task.role.set(extension.role)
        task.enforcement.set(extension.enforcement)
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
    compilerConfiguration: Configuration,
    compilerPluginConfiguration: Configuration,
    mosaicVersion: String,
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
      val resolutionArtifacts =
        project.configurations.getByName("compileClasspath").incoming.artifactView { view ->
          view.attributes.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, MOSAIC_RESOLUTION_ARTIFACT_TYPE)
        }.files
      task.sourceResolutionAbi.from(resolutionArtifacts.filter { it.name.endsWith(".resolution.jar") })
      task.kotlinModuleMetadata.from(resolutionArtifacts.filter { it.name.endsWith(".kotlin-modules.bin") })
      task.compilerClasspath.from(compilerConfiguration)
      task.additionalCompilerPlugins.from(compile.map { it.pluginClasspath })
      task.friendPaths.from(compile.map { it.friendPaths })
      task.compilerPluginJar.set(
        project.layout.file(compilerPluginConfiguration.elements.map { elements -> elements.single().asFile }),
      )
      task.mosaicVersion.set(mosaicVersion)
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
      task.selectedJavaVendor.set(launcher.map { it.metadata.vendor })
      task.selectedJavaRuntimeVersion.set(launcher.map { it.metadata.javaRuntimeVersion })
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
}

private fun unsupportedProjectPlugins(project: Project): List<String> =
  listOf(
    "org.jetbrains.kotlin.multiplatform",
    "com.android.application",
    "com.android.library",
    "com.google.devtools.ksp",
  ).filter(project.plugins::hasPlugin)

private fun registerAnalysisTransforms(project: Project) {
  project.dependencies.registerTransform(MosaicSummaryTransform::class.java) { transform ->
    transform.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    transform.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, MOSAIC_SUMMARY_ARTIFACT_TYPE)
  }
  project.dependencies.registerTransform(SourceResolutionTransform::class.java) { transform ->
    transform.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    transform.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, MOSAIC_RESOLUTION_ARTIFACT_TYPE)
  }
}
