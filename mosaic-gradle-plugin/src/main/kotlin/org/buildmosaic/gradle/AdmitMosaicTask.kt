package org.buildmosaic.gradle

import org.buildmosaic.analysis.ANALYSIS_KOTLIN_VERSION
import org.buildmosaic.analysis.CompatibilityArtifacts
import org.buildmosaic.analysis.CompatibilityProtocol
import org.buildmosaic.analysis.ExtractionEnvironment
import org.buildmosaic.analysis.ExtractionEnvironmentCodec
import org.buildmosaic.analysis.ProducerIdentity
import org.buildmosaic.analysis.ProductionContext
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.jar.JarFile

/** Artifact admission is separate from source compilation and produces only environment inputs. */
abstract class AdmitMosaicTask : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val compileArtifacts: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val runtimeArtifacts: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val compilerArtifacts: ConfigurableFileCollection

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val analysisArtifacts: ConfigurableFileCollection

  @get:Input abstract val analysisVersion: Property<String>

  @get:OutputFile abstract val contextFile: RegularFileProperty

  @get:OutputFile abstract val requirementsFile: RegularFileProperty

  @get:Internal abstract val analysisDirectory: DirectoryProperty

  @get:Internal abstract val reportsDirectory: DirectoryProperty

  @get:Internal abstract val packagedJar: RegularFileProperty

  @TaskAction
  @Suppress("TooGenericExceptionCaught")
  fun admit() {
    try {
      admitArtifacts()
    } catch (failure: RuntimeException) {
      invalidate()
      throw GradleException("Mosaic compatibility failure: ${failure.message}", failure)
    } catch (failure: java.io.IOException) {
      invalidate()
      throw GradleException("Cannot read Mosaic compatibility artifacts: ${failure.message}", failure)
    }
  }

  private fun admitArtifacts() {
    val compiler =
      compilerArtifacts.files.filter { file ->
        file.extension == "jar" && JarFile(file).use { it.getJarEntry("META-INF/compiler.version") != null }
      }.singleOrNull() ?: throw GradleException("Mosaic requires exactly one resolved executing Kotlin compiler")
    val compilerVersion =
      JarFile(compiler).use { jar ->
        jar.getInputStream(jar.getJarEntry("META-INF/compiler.version")).use {
          CompatibilityArtifacts.readBounded(it).toString(Charsets.UTF_8).trim()
        }
      }
    require(compilerVersion == ANALYSIS_KOTLIN_VERSION) {
      "Mosaic analysis requires Kotlin compiler $ANALYSIS_KOTLIN_VERSION; resolved $compilerVersion"
    }
    val selected = CompatibilityArtifacts.runtimes(compileArtifacts.files)
    CompatibilityArtifacts.requireAligned(selected, CompatibilityArtifacts.runtimes(runtimeArtifacts.files))
    val dependencies = CompatibilityArtifacts.summaries(compileArtifacts.files)
    val context = ProductionContext(ProducerIdentity(analysisVersion.get(), compilerVersion), selected)
    val retained =
      dependencies.fold(
        selected,
      ) { requirements, dependency -> CompatibilityProtocol.merge(requirements, dependency.runtimes) }
    val extractor =
      analysisArtifacts.files.singleOrNull { it.name.startsWith("mosaic-compiler-plugin-") }
        ?: throw GradleException("Mosaic compiler plugin artifact was not resolved exactly once")
    validateCompilerPluginVersion(extractor, analysisVersion.get())
    val environment =
      ExtractionEnvironment(
        context,
        ExtractionEnvironmentCodec.artifactHash(extractor),
        ExtractionEnvironmentCodec.artifactHash(compiler),
      )
    writeIfChanged(contextFile.get().asFile, ExtractionEnvironmentCodec.encode(environment))
    writeIfChanged(
      requirementsFile.get().asFile,
      CompatibilityProtocol.encodeContext(context.copy(runtimes = retained)),
    )
  }

  private fun invalidate() {
    analysisDirectory.get().asFile.deleteRecursively()
    reportsDirectory.get().asFile.deleteRecursively()
    packagedJar.get().asFile.delete()
    contextFile.get().asFile.delete()
    requirementsFile.get().asFile.delete()
  }
}

private fun writeIfChanged(
  file: File,
  bytes: ByteArray,
) {
  if (file.isFile && file.readBytes().contentEquals(bytes)) return
  file.parentFile.mkdirs()
  file.writeBytes(bytes)
}
