package org.buildmosaic.gradle

import org.buildmosaic.analysis.SourceShardCodec
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile

/** Assembles only current Kotlin sources. Kotlin's own compilation owns extraction and invalidation. */
@CacheableTask
abstract class ExtractMosaicTask : DefaultTask() {
  @get:Internal abstract val sources: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val javaSources: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val shardFiles: ConfigurableFileCollection

  @get:Internal abstract val shardDirectory: DirectoryProperty

  @get:Classpath abstract val additionalCompilerPlugins: ConfigurableFileCollection

  @get:Classpath abstract val friendPaths: ConfigurableFileCollection

  @get:Input abstract val mosaicVersion: Property<String>

  @get:Input abstract val moduleId: Property<String>

  @get:Input abstract val productionCompilerVersion: Property<String>

  @get:Input abstract val unsupportedCompilerOptions: ListProperty<String>

  @get:Input abstract val unsupportedProjectPlugins: ListProperty<String>

  @get:Input abstract val expectedJavaVersion: Property<String>

  @get:Input abstract val selectedJavaVersion: Property<String>

  @get:Internal abstract val supportedSourceRoot: Property<String>

  @get:OutputFile abstract val summaryFile: RegularFileProperty

  @get:Input
  val sourceLayout: List<String>
    get() {
      val root = File(supportedSourceRoot.get()).canonicalFile.toPath()
      return sources.files.map { source ->
        val path = source.canonicalFile.toPath()
        if (path.startsWith(root)) {
          root.relativize(path).toString().replace(File.separatorChar, '/')
        } else {
          "<unsupported>:${source.name}"
        }
      }.sorted()
    }

  @TaskAction
  @Suppress("TooGenericExceptionCaught")
  fun extract() {
    val output = summaryFile.get().asFile
    output.delete()
    validateConfiguration()
    validateSources()
    val current = sourceLayout.filter { it.endsWith(".kt") }
    requireSupported(current.size == current.distinct().size, "Duplicate Mosaic source identities")
    val root = shardDirectory.get().asFile
    val shards =
      current.map { sourceId ->
        val shard = File(root, "$sourceId.shard.json")
        if (!shard.isFile) throw GradleException("Missing Mosaic compiler shard for current source $sourceId")
        try {
          SourceShardCodec.decode(shard.readBytes()).also {
            require(it.sourceId == sourceId) { "Mosaic shard identity mismatch for $sourceId" }
          }
        } catch (error: Exception) {
          throw GradleException("Invalid Mosaic compiler shard for $sourceId", error)
        }
      }
    val bytes = SourceShardCodec.assemble(moduleId.get(), shards)
    SummaryCodec.decode(bytes)
    output.parentFile.mkdirs()
    val pending = File(output.parentFile, "${output.name}.pending")
    pending.writeBytes(bytes)
    Files.move(pending.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
  }

  private fun validateSources() {
    requireSupported(
      sources.files.none { it.extension == "kts" },
      "Mosaic extraction does not support Kotlin scripts",
    )
    requireSupported(
      javaSources.files.isEmpty(),
      "Mosaic extraction does not support mixed Java/Kotlin sources",
    )
    requireSupported(
      sourceLayout.none {
        it.startsWith("<unsupported>:")
      },
      "Mosaic extraction supports only src/main/kotlin sources",
    )
  }

  private fun validateConfiguration() {
    requireSupported(
      productionCompilerVersion.get().matches(Regex("2\\.2\\.10(?:-release-[0-9]+)?")),
      "Mosaic extraction requires Kotlin Gradle plugin 2.2.10; found ${productionCompilerVersion.get()}",
    )
    val compilerPlugins = additionalCompilerPlugins.files.filter { it.name.startsWith("mosaic-compiler-plugin-") }
    requireSupported(compilerPlugins.size == 1, "Mosaic compiler plugin artifact was not resolved exactly once")
    validateCompilerPluginVersion(compilerPlugins.single(), mosaicVersion.get())
    val unsupported =
      additionalCompilerPlugins.files.filterNot {
        it in compilerPlugins || isDefaultKotlinCompilerArtifact(it)
      }
    requireSupported(
      unsupported.isEmpty(),
      "Mosaic extraction does not support additional Kotlin compiler plugins: ${unsupported.joinToString { it.name }}",
    )
    requireSupported(friendPaths.files.isEmpty(), "Mosaic extraction does not support Kotlin friend paths")
    requireSupported(
      unsupportedCompilerOptions.get().isEmpty(),
      "Mosaic extraction does not support compiler options: ${unsupportedCompilerOptions.get().joinToString()}",
    )
    requireSupported(
      unsupportedProjectPlugins.get().isEmpty(),
      "Mosaic extraction does not support project plugins: ${unsupportedProjectPlugins.get().joinToString()}",
    )
    requireSupported(
      expectedJavaVersion.get() == selectedJavaVersion.get(),
      "Mosaic extraction toolchain mismatch",
    )
  }
}

internal fun validateCompilerPluginVersion(
  jar: File,
  installedVersion: String,
) {
  val artifactVersion = JarFile(jar).use { it.manifest?.mainAttributes?.getValue("Implementation-Version") }
  requireSupported(
    artifactVersion == installedVersion,
    "Mosaic compiler plugin version mismatch: installed Gradle plugin is $installedVersion, " +
      "compiler artifact is ${artifactVersion ?: "unversioned"}",
  )
}

private fun requireSupported(
  condition: Boolean,
  reason: String,
) {
  if (!condition) throw GradleException(reason)
}

private fun isDefaultKotlinCompilerArtifact(file: File): Boolean =
  file.name in
    setOf(
      "kotlin-scripting-compiler-embeddable-2.2.10.jar",
      "kotlin-scripting-compiler-impl-embeddable-2.2.10.jar",
      "kotlin-scripting-jvm-2.2.10.jar",
      "kotlin-scripting-common-2.2.10.jar",
      "kotlin-stdlib-2.2.10.jar",
      "kotlin-script-runtime-2.2.10.jar",
      "annotations-13.0.jar",
    )
