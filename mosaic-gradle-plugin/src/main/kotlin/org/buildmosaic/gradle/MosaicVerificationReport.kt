package org.buildmosaic.gradle

import org.buildmosaic.analysis.AnalysisReport
import org.buildmosaic.analysis.Finding

internal object MosaicVerificationReport {
  fun library(enforcement: MosaicAnalysisEnforcement): String =
    """
      |Mosaic analysis
      |Role: LIBRARY
      |Enforcement: $enforcement
      |Roots selected: none; application verification not requested
      |Result: EXPORT_ONLY; local summary is complete and valid
      |
    """.trimMargin().trim()

  fun application(
    report: AnalysisReport,
    role: MosaicAnalysisRole,
    enforcement: MosaicAnalysisEnforcement,
    roots: List<String>,
  ): String {
    val result =
      when {
        !report.policyDecision.passed -> "FAILED"
        report.policyDecision.warnings.isNotEmpty() -> "PASSED WITH WARNINGS; selected roots remain UNVERIFIED"
        else -> "FULLY VERIFIED"
      }
    return buildString {
      appendLine("Mosaic analysis: ${report.configurationStatus}")
      appendLine("Role: $role")
      appendLine("Enforcement: $enforcement")
      appendLine("Roots selected: ${roots.joinToString()}")
      appendLine("Result: $result")
      report.roots.forEach { root ->
        appendLine("Root ${root.root.target}: ${root.status}; contracts ${root.specializedContracts.joinToString()}")
      }
      report.findings.forEach { finding -> appendFinding(finding) }
    }
  }

  fun failure(
    role: MosaicAnalysisRole,
    enforcement: MosaicAnalysisEnforcement,
    roots: List<String>,
    category: String,
    reason: String?,
  ): String =
    """
      |Mosaic analysis
      |Role: $role
      |Enforcement: $enforcement
      |Roots selected: ${roots.joinToString().ifEmpty { "none" }}
      |Result: FAILED
      |$category: $reason
      |
    """.trimMargin().trim()

  private fun StringBuilder.appendFinding(finding: Finding) {
    appendLine(
      "${finding.certainty} ${finding.kind} ${finding.key ?: ""} at " +
        "${finding.site.path}:${finding.site.line} (${finding.site.owner})",
    )
    appendLine("  ${finding.reason}")
    appendLine("  dependency: ${finding.dependencyPath.joinToString(" -> ") { it.label }}")
    appendLine(
      "  canvas: ${finding.canvasPath.joinToString(" -> ") { it.layerId + "@" + (it.site?.path ?: "unknown") }}",
    )
  }
}
