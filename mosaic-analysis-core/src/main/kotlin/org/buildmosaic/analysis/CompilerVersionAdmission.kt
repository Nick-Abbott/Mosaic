package org.buildmosaic.analysis

/** Internal measured compiler mapping, shared by Gradle selection and compiler admission. */
object CompilerVersionAdmission {
  const val COMPATIBILITY_PROBE_PROPERTY = "mosaic.analysis.compatibilityProbe"

  private val compilersByApi =
    mapOf(
      "2.3.0" to setOf("2.2.0", "2.2.10", "2.2.20", "2.2.21", "2.3.0", "2.3.10"),
      "2.3.20" to setOf("2.3.20", "2.3.21"),
      "2.4.20" to setOf("2.4.0", "2.4.10", "2.4.20", "2.4.21"),
    )

  fun stableVersion(actualCompilerVersion: String): String =
    actualCompilerVersion.replace(Regex("-release-[0-9]+$"), "")

  fun compilerApi(actualCompilerVersion: String): String =
    compilersByApi.entries.singleOrNull { stableVersion(actualCompilerVersion) in it.value }?.key
      ?: error(
        "Mosaic analysis has not verified Kotlin compiler $actualCompilerVersion. " +
          "Supported compilers: ${compilersByApi.values.flatten().joinToString()}",
      )

  fun artifactId(compilerApi: String): String {
    require(compilerApi in compilersByApi) { "Unknown Mosaic introspector compiler API $compilerApi" }
    return if (compilerApi == ANALYSIS_KOTLIN_VERSION) {
      "mosaic-compiler-plugin"
    } else {
      "mosaic-compiler-plugin-kotlin-$compilerApi"
    }
  }

  fun requireSupported(
    actualCompilerVersion: String,
    compilerApi: String = ANALYSIS_KOTLIN_VERSION,
  ) {
    if (System.getProperty(COMPATIBILITY_PROBE_PROPERTY) == "true") {
      System.err.println("MOSAIC_COMPATIBILITY_PROBE: compiler-version admission bypassed for $actualCompilerVersion")
      return
    }
    require(stableVersion(actualCompilerVersion) in compilersByApi[compilerApi].orEmpty()) {
      "Mosaic introspector compiler API $compilerApi does not support Kotlin compiler $actualCompilerVersion. " +
        "Verified compilers: ${compilersByApi[compilerApi].orEmpty().joinToString()}"
    }
  }
}
