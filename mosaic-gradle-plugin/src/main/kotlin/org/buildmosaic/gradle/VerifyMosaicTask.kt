package org.buildmosaic.gradle

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SelectedRoot
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.jar.JarFile

abstract class VerifyMosaicTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val summaryFile: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val dependencyJars: ConfigurableFileCollection

  @get:Input
  abstract val roots: ListProperty<String>

  @get:OutputFile
  abstract val reportFile: RegularFileProperty

  @Suppress("TooGenericExceptionCaught", "MaxLineLength", "ktlint:standard:max-line-length")
  @TaskAction
  fun verify() {
    val output = reportFile.get().asFile
    output.delete()
    validateDependencyArtifacts()
    val program = SummaryCodec.decode(summaryFile.get().asFile.readBytes()).module
    val problems = mutableListOf<String>()
    val dependencies =
      dependencyJars.files.filter { it.isFile && it.extension == "jar" }.sortedBy { it.name }.mapNotNull { jar ->
        try {
          JarFile(jar).use { archive ->
            val resource = archive.getJarEntry(SUMMARY_PATH) ?: return@use null
            SummaryCodec.decode(archive.getInputStream(resource).readBytes()).module
          }
        } catch (error: Exception) {
          problems += "${jar.name}: invalid Mosaic summary: ${error.message}"
          null
        }
      }
    val owners =
      (listOf(program) + dependencies).flatMap { module ->
        (module.canvases.map { it.id } + module.tiles.map { it.id } + module.callables.map { it.id }).map { it to module.id }
      }.groupBy({ it.first }, { it.second })
    val conflict = owners.entries.firstOrNull { it.value.size > 1 }
    if (conflict != null) {
      throw GradleException("Conflicting selected Mosaic owners for ${conflict.key}: ${conflict.value.joinToString()}")
    }
    val rootsList = roots.get().map { SelectedRoot(it, it) }
    val report =
      MosaicAnalyzer().analyze(
        AnalysisRequest(program, dependencies, rootsList, policy = AnalysisPolicy.STRICT),
      )
    val text =
      buildString {
        appendLine("Mosaic analysis: ${report.configurationStatus}")
        appendLine("Selected roots: ${rootsList.joinToString { it.target }}")
        report.roots.forEach {
            root ->
          appendLine("Root ${root.root.target}: ${root.status}; contracts ${root.specializedContracts.joinToString()}")
        }
        report.findings.forEach { finding ->
          appendLine(
            "${finding.certainty} ${finding.kind} ${finding.key ?: ""} at ${finding.site.path}:${finding.site.line} (${finding.site.owner})",
          )
          appendLine("  ${finding.reason}")
          appendLine("  dependency: ${finding.dependencyPath.joinToString(" -> ") { it.label }}")
          appendLine(
            "  canvas: ${finding.canvasPath.joinToString(" -> ") { it.layerId + "@" + (it.site?.path ?: "unknown") }}",
          )
        }
        problems.forEach { appendLine("Dependency metadata: $it") }
      }
    output.parentFile.mkdirs()
    output.writeText(text)
    logger.lifecycle(text)
    if (!report.policyDecision.passed) throw GradleException("Mosaic verification failed; see ${output.absolutePath}")
  }

  private fun validateDependencyArtifacts() {
    val directories = dependencyJars.files.filter { it.isDirectory }
    if (directories.isNotEmpty()) {
      throw GradleException("Mosaic prototype verification requires dependency JARs, not class directories")
    }
  }
}
