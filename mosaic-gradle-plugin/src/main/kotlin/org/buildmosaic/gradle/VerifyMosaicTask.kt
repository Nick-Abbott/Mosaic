package org.buildmosaic.gradle

import org.buildmosaic.analysis.AnalysisPolicy
import org.buildmosaic.analysis.AnalysisRequest
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.MosaicAnalyzer
import org.buildmosaic.analysis.SelectedRoot
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.IOException

abstract class VerifyMosaicTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val summaryFile: RegularFileProperty

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val dependencyJars: ConfigurableFileCollection

  @get:Input
  abstract val roots: ListProperty<String>

  @get:Input
  abstract val role: Property<MosaicAnalysisRole>

  @get:Input
  abstract val enforcement: Property<MosaicAnalysisEnforcement>

  @get:OutputFile
  abstract val reportFile: RegularFileProperty

  @TaskAction
  fun verify() {
    val output = reportFile.get().asFile
    output.delete()
    val selectedRoots = roots.get()
    validateAndReportConfiguration(output, selectedRoots)
    val program = readAndReportLocalSummary(output)
    if (role.get() == MosaicAnalysisRole.LIBRARY) {
      writeReport(output, MosaicVerificationReport.library(enforcement.get()))
    } else {
      verifyApplication(output, program, selectedRoots)
    }
  }

  private fun validateAndReportConfiguration(
    output: java.io.File,
    selectedRoots: List<String>,
  ) {
    val message = configurationError(selectedRoots) ?: return
    failWithDiagnostic(output, "Configuration error", GradleException(message))
  }

  private fun configurationError(selectedRoots: List<String>): String? =
    when (role.get()) {
      MosaicAnalysisRole.APPLICATION ->
        when {
          selectedRoots.any { it.isBlank() } -> "Mosaic APPLICATION roots must not be blank"
          selectedRoots.isEmpty() ->
            "Mosaic APPLICATION verification requires at least one root. " +
              "Configure mosaicAnalysis.roots or select role = MosaicAnalysisRole.LIBRARY."
          else -> null
        }
      MosaicAnalysisRole.LIBRARY ->
        if (selectedRoots.isNotEmpty()) {
          "Mosaic LIBRARY cannot select application roots. Remove mosaicAnalysis.roots or select " +
            "role = MosaicAnalysisRole.APPLICATION; applications also export contracts."
        } else {
          null
        }
    }

  private fun readAndReportLocalSummary(output: java.io.File): ModuleContract =
    try {
      val summary = SummaryCodec.decode(summaryFile.get().asFile.readBytes())
      if (!summary.complete) throw GradleException("Local Mosaic summary is partial")
      summary.module
    } catch (failure: RuntimeException) {
      invalidLocalSummary(output, failure)
    } catch (failure: IOException) {
      invalidLocalSummary(output, failure)
    }

  private fun invalidLocalSummary(
    output: java.io.File,
    failure: Exception,
  ): Nothing {
    val reason = "Invalid local Mosaic summary: ${failure.message}"
    failWithDiagnostic(output, "Local metadata error", GradleException(reason, failure))
  }

  private fun verifyApplication(
    output: java.io.File,
    program: ModuleContract,
    selectedRoots: List<String>,
  ) {
    val dependencies =
      try {
        MosaicDependencyReader.read(dependencyJars)
      } catch (failure: GradleException) {
        failWithDiagnostic(output, "Artifact metadata error", failure)
      }
    validateOwners(output, listOf(program) + dependencies)
    val policy =
      when (enforcement.get()) {
        MosaicAnalysisEnforcement.STANDARD -> AnalysisPolicy.DEFAULT
        MosaicAnalysisEnforcement.STRICT -> AnalysisPolicy.STRICT
      }
    val rootsList = selectedRoots.map { SelectedRoot(it, it) }
    val report = MosaicAnalyzer().analyze(AnalysisRequest(program, dependencies, rootsList, policy = policy))
    validateResolvedRoots(output, report)
    writeReport(output, MosaicVerificationReport.application(report, role.get(), enforcement.get(), selectedRoots))
    if (!report.policyDecision.passed) throw GradleException("Mosaic verification failed; see ${output.absolutePath}")
    if (report.policyDecision.warnings.isNotEmpty()) {
      logger.warn("Mosaic verification passed with warnings; see ${output.absolutePath}")
    }
  }

  private fun validateResolvedRoots(
    output: java.io.File,
    report: org.buildmosaic.analysis.AnalysisReport,
  ) {
    val unresolved = report.roots.firstOrNull { it.root.target !in it.specializedContracts } ?: return
    failWithDiagnostic(
      output,
      "Root selection error",
      GradleException(
        "Selected root '${unresolved.root.target}' does not resolve to a callable or Canvas contract " +
          "in the selected Mosaic contracts",
      ),
    )
  }

  private fun validateOwners(
    output: java.io.File,
    modules: List<ModuleContract>,
  ) {
    try {
      requireUniqueSelectedOwners(modules)
    } catch (failure: GradleException) {
      failWithDiagnostic(output, "Artifact/configuration error", failure)
    }
  }

  private fun writeReport(
    output: java.io.File,
    text: String,
  ) {
    output.parentFile.mkdirs()
    output.writeText(text)
    logger.lifecycle(text)
  }

  private fun failWithDiagnostic(
    output: java.io.File,
    category: String,
    failure: GradleException,
  ): Nothing {
    writeReport(
      output,
      MosaicVerificationReport.failure(role.get(), enforcement.get(), roots.get(), category, failure.message),
    )
    throw failure
  }
}
