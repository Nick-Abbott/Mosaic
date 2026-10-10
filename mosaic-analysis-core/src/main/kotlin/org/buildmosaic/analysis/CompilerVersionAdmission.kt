package org.buildmosaic.analysis

/** Internal compiler admission. Probing does not establish production compatibility. */
object CompilerVersionAdmission {
  const val COMPATIBILITY_PROBE_PROPERTY = "mosaic.analysis.compatibilityProbe"

  fun requireSupported(actualCompilerVersion: String) {
    val probing = System.getProperty(COMPATIBILITY_PROBE_PROPERTY) == "true"
    if (probing) {
      System.err.println("MOSAIC_COMPATIBILITY_PROBE: compiler-version admission bypassed for $actualCompilerVersion")
    }
    val supportedVersion = Regex("${Regex.escape(ANALYSIS_KOTLIN_VERSION)}(?:-release-[0-9]+)?")
    require(probing || actualCompilerVersion.matches(supportedVersion)) {
      "Mosaic analysis requires Kotlin compiler $ANALYSIS_KOTLIN_VERSION; found $actualCompilerVersion"
    }
  }
}
