@file:Suppress("LargeClass")

package org.buildmosaic.gradle

import org.buildmosaic.analysis.CompatibilityProtocol
import org.buildmosaic.analysis.MosaicRule
import org.buildmosaic.analysis.MosaicRuleSeverity
import org.buildmosaic.analysis.SourceShardPaths
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.util.Properties
import javax.inject.Inject

private const val MOSAIC_SUMMARY_ARTIFACT_TYPE = "mosaic-analysis-summary"

abstract class MosaicAnalysisExtension
  @Inject
  constructor(objects: ObjectFactory) {
    val rules = MosaicAnalysisRules(objects)

    fun rules(action: Action<MosaicAnalysisRules>) = action.execute(rules)

    val roots: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val role: Property<MosaicAnalysisRole> =
      objects.property(MosaicAnalysisRole::class.java).convention(MosaicAnalysisRole.APPLICATION)
    val enforcement: Property<MosaicAnalysisEnforcement> =
      objects.property(MosaicAnalysisEnforcement::class.java).convention(MosaicAnalysisEnforcement.STANDARD)
  }

class MosaicAnalysisRules internal constructor(objects: ObjectFactory) {
  internal val severities: MapProperty<String, MosaicRuleSeverity> =
    objects.mapProperty(String::class.java, MosaicRuleSeverity::class.java).convention(emptyMap())

  fun severity(
    ruleId: String,
    severity: MosaicRuleSeverity,
  ) {
    MosaicRule.configurable(ruleId)
    severities.put(ruleId, severity)
  }
}

enum class MosaicAnalysisRole {
  APPLICATION,
  LIBRARY,
}

enum class MosaicAnalysisEnforcement {
  STANDARD,
  STRICT,
}

class MosaicAnalysisPlugin : KotlinCompilerPluginSupportPlugin {
  private lateinit var project: Project
  private lateinit var mosaicVersion: String

  override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean =
    kotlinCompilation.name == "main" && kotlinCompilation.platformType == KotlinPlatformType.jvm &&
      kotlinCompilation.project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")

  override fun getCompilerPluginId(): String = "org.buildmosaic.analysis"

  override fun getPluginArtifact(): SubpluginArtifact =
    SubpluginArtifact("org.buildmosaic", "mosaic-compiler-plugin", mosaicVersion)

  override fun applyToCompilation(
    kotlinCompilation: KotlinCompilation<*>,
  ): org.gradle.api.provider.Provider<List<SubpluginOption>> =
    project.provider {
      val output = project.layout.buildDirectory.dir("mosaic-analysis/main/shards").get().asFile
      val root = project.file("src/main/kotlin")
      listOf(
        FilesSubpluginOption("output", listOf(output)),
        FilesSubpluginOption("sourceRoot", listOf(root)),
        SubpluginOption("mode", "shards"),
        FilesSubpluginOption(
          "context",
          listOf(project.layout.buildDirectory.file("mosaic-compatibility/context.json").get().asFile),
        ),
        SubpluginOption("module", project.group.toString() + ":" + project.name),
      )
    }

  override fun apply(target: Project) {
    val project = target
    this.project = target
    registerAnalysisTransforms(project)
    val extension = project.extensions.create("mosaicAnalysis", MosaicAnalysisExtension::class.java)
    mosaicVersion = installedMosaicVersion()
    project.afterEvaluate {
      if (!project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) {
        throw GradleException("Mosaic analysis requires a pure Kotlin/JVM project")
      }
    }
    project.plugins.withId("org.jetbrains.kotlin.jvm") {
      registerMain(project, extension)
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
  ) {
    val compile = project.tasks.named("compileKotlin", KotlinJvmCompile::class.java)
    val admission = registerAdmission(project, compile)
    val cleanup = registerCompilationCleanup(project, compile, admission)
    val shardDirectory = project.layout.buildDirectory.dir("mosaic-analysis/main/shards")
    compile.configure { it.outputs.dir(shardDirectory).withPropertyName("mosaicSourceShards") }
    val extract =
      registerExtraction(project, compile, admission, cleanup)
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
        task.ruleSeverities.set(extension.rules.severities)
        task.reportFile.set(project.layout.buildDirectory.file("reports/mosaic-analysis/main.txt"))
        task.dependsOn(extract)
      }
    project.tasks.register("mosaicGraph", MosaicGraphTask::class.java) { task ->
      task.group = "documentation"
      task.description = "Render local Mosaic contracts and selected root findings as a Mermaid graph"
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
      task.ruleSeverities.set(extension.rules.severities)
      task.graphFile.set(project.layout.buildDirectory.file("reports/mosaic-analysis/graph.md"))
      task.dependsOn(extract)
    }
    val aggregate =
      project.tasks.register("verifyMosaic") { task ->
        task.group = "verification"
        task.dependsOn(verify)
      }
    project.tasks.named("check") { it.dependsOn(aggregate) }
  }

  private fun registerAdmission(
    project: Project,
    compile: TaskProvider<KotlinJvmCompile>,
  ): TaskProvider<AdmitMosaicTask> {
    // Use the selected JAR variant so resources and bytecode describe the same artifact.
    // Project dependencies are built upstream; local packaging never enters this classpath.
    listOf("compileClasspath", "runtimeClasspath").forEach { name ->
      project.configurations.named(name) {
        it.attributes.attribute(
          LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
          project.objects.named(LibraryElements::class.java, LibraryElements.JAR),
        )
      }
    }
    return project.tasks.register("admitMosaicMain", AdmitMosaicTask::class.java) { task ->
      task.compileArtifacts.from(project.configurations.getByName("compileClasspath"))
      task.runtimeArtifacts.from(
        project.configurations.getByName("runtimeClasspath").incoming.artifactView { view ->
          view.componentFilter { component ->
            when (component) {
              is ProjectComponentIdentifier ->
                "org.buildmosaic:${component.projectName}" in CompatibilityProtocol.runtimeModules
              // Inspect all binary selections: substitutions may use different Maven coordinates.
              else -> true
            }
          }
        }.files,
      )
      task.compilerArtifacts.from(project.configurations.getByName("kotlinCompilerClasspath"))
      task.analysisArtifacts.from(project.provider { compile.get().pluginClasspath })
      task.analysisVersion.set(mosaicVersion)
      task.contextFile.set(project.layout.buildDirectory.file("mosaic-compatibility/context.json"))
      task.requirementsFile.set(project.layout.buildDirectory.file("mosaic-compatibility/requirements.json"))
      task.analysisDirectory.set(project.layout.buildDirectory.dir("mosaic-analysis"))
      task.reportsDirectory.set(project.layout.buildDirectory.dir("reports/mosaic-analysis"))
      task.packagedJar.set(project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })
    }
  }

  private fun registerCompilationCleanup(
    project: Project,
    compile: TaskProvider<KotlinJvmCompile>,
    admission: TaskProvider<AdmitMosaicTask>,
  ): TaskProvider<MosaicCompilationCleanupTask> {
    val contextOutput = admission.flatMap { it.contextFile }
    val currentSources =
      project.files(
        project.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName("main").kotlin,
      )
    val shardOutput = project.layout.buildDirectory.dir("mosaic-analysis/main/shards")
    val sourceRoot = project.file("src/main/kotlin")
    val pendingOutput = project.layout.buildDirectory.file("mosaic-compatibility/compilation.pending")
    val cleanup =
      project.tasks.register("cleanupMosaicCompilation", MosaicCompilationCleanupTask::class.java) { task ->
        task.pendingFile.set(pendingOutput)
        task.trustedOutputs.from(project.layout.buildDirectory.dir("mosaic-analysis"))
        task.trustedOutputs.from(project.layout.buildDirectory.dir("reports/mosaic-analysis"))
        task.trustedOutputs.from(project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })
      }
    compile.configure {
      it.doFirst { task ->
        if (pendingOutput.get().asFile.exists() ||
          requiresFreshMosaicExtraction(
            contextOutput.get().asFile,
            shardOutput.get().asFile,
            sourceRoot,
            currentSources.files,
          )
        ) {
          (task as org.jetbrains.kotlin.gradle.tasks.KotlinCompile).incremental = false
        }
        pendingOutput.get().asFile.apply {
          parentFile.mkdirs()
          writeText("pending")
        }
      }
      it.doLast { pendingOutput.get().asFile.delete() }
      it.finalizedBy(cleanup)
      it.dependsOn(admission)
      it.inputs.files(
        pendingOutput,
      ).withPropertyName("mosaicPendingCompilation").withPathSensitivity(PathSensitivity.NONE)
      it.inputs.file(admission.flatMap { task -> task.contextFile }).withPropertyName("mosaicProductionContext")
        .withPathSensitivity(PathSensitivity.NONE)
    }
    return cleanup
  }

  private fun registerExtraction(
    project: Project,
    compile: TaskProvider<KotlinJvmCompile>,
    admission: TaskProvider<AdmitMosaicTask>,
    cleanup: TaskProvider<MosaicCompilationCleanupTask>,
  ): TaskProvider<ExtractMosaicTask> {
    val kotlinPluginVersion =
      project.plugins.findPlugin("org.jetbrains.kotlin.jvm")?.javaClass?.`package`?.implementationVersion ?: "unknown"
    val javaExtension = project.extensions.getByType(JavaPluginExtension::class.java)
    val launcher = project.extensions.getByType(JavaToolchainService::class.java).launcherFor(javaExtension.toolchain)
    val mainSources =
      project.extensions.getByType(
        KotlinJvmProjectExtension::class.java,
      ).sourceSets.getByName("main").kotlin
    val sourceRoot = project.file("src/main/kotlin")
    val shardDirectory = project.layout.buildDirectory.dir("mosaic-analysis/main/shards")
    return project.tasks.register("extractMosaicMain", ExtractMosaicTask::class.java) { task ->
      task.contextFile.set(admission.flatMap { it.contextFile })
      task.requirementsFile.set(admission.flatMap { it.requirementsFile })
      task.group = "verification"
      task.description = "Assemble compiler-produced Mosaic source shards into a complete main summary"
      task.sources.from(mainSources)
      task.javaSources.from(project.fileTree("src/main/java") { it.include("**/*.java") })
      task.supportedSourceRoot.set(sourceRoot.absolutePath)
      task.shardFiles.from(
        mainSources.elements.map { sources ->
          sources.map { it.asFile }.filter { it.extension == "kt" }.map { source ->
            SourceShardPaths.shardFile(shardDirectory.get().asFile, SourceShardPaths.sourceId(sourceRoot, source))
          }
        },
      )
      task.additionalCompilerPlugins.from(compile.map { it.pluginClasspath })
      task.friendPaths.from(compile.map { it.friendPaths })
      task.mosaicVersion.set(mosaicVersion)
      task.moduleId.set(project.provider { project.group.toString() + ":" + project.name })
      task.productionCompilerVersion.set(kotlinPluginVersion)
      task.unsupportedCompilerOptions.set(compile.map(::unsupportedCompilerOptions))
      task.unsupportedProjectPlugins.set(project.provider { unsupportedProjectPlugins(project) })
      task.selectedJavaVersion.set(launcher.map { it.metadata.languageVersion.asInt().toString() })
      task.expectedJavaVersion.set(
        compile.flatMap {
          it.kotlinJavaToolchainProvider
        }.flatMap { it.javaVersion }.map { it.majorVersion },
      )
      task.summaryFile.set(project.layout.buildDirectory.file("mosaic-analysis/main/summary.json"))
      task.shardDirectory.set(shardDirectory)
      task.invalidatedOutputs.from(project.layout.buildDirectory.dir("mosaic-analysis"))
      task.invalidatedOutputs.from(project.layout.buildDirectory.dir("reports/mosaic-analysis"))
      task.invalidatedOutputs.from(project.tasks.named("jar", Jar::class.java).flatMap { it.archiveFile })
      task.dependsOn(compile, admission, cleanup)
    }
  }
}

private fun unsupportedCompilerOptions(taskCompile: KotlinJvmCompile): List<String> =
  buildList {
    taskCompile.pluginOptions.orNull.orEmpty().flatMap { it.allOptions().entries }.forEach { (id, options) ->
      if (options.isNotEmpty() && id != "org.buildmosaic.analysis") add("plugin:$id:${options.size}")
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
  ).filter(project.plugins::hasPlugin)

private fun registerAnalysisTransforms(project: Project) {
  project.dependencies.registerTransform(MosaicSummaryTransform::class.java) { transform ->
    transform.from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
    transform.to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, MOSAIC_SUMMARY_ARTIFACT_TYPE)
  }
}
