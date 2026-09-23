# Mosaic Gradle analysis prototype

This unpublished plugin supports pure Kotlin/JVM `main` sources under
`src/main/kotlin` with Kotlin Gradle plugin and compiler 2.2.10. Its provisional ID is
`org.buildmosaic.analysis`. The plugin does not apply Kotlin or add Mosaic
runtime dependencies. In this repository's binary integration test the plugin
is supplied through Gradle TestKit; there is no published marker yet.

```kotlin
import org.buildmosaic.gradle.MosaicAnalysisEnforcement
import org.buildmosaic.gradle.MosaicAnalysisRole

plugins {
  kotlin("jvm") version "2.2.10"
  id("org.buildmosaic.analysis")
}

mosaicAnalysis {
  compilerPluginJar.set(file("/path/to/mosaic-compiler-plugin-0.2.0.jar"))
  role = MosaicAnalysisRole.APPLICATION
  enforcement = MosaicAnalysisEnforcement.STANDARD
  roots.add("app.entry()")
}
```

`role` and `enforcement` are independent typed settings. The defaults are
`APPLICATION` and `STANDARD`. Applications must name one or more explicit
callable roots. Libraries can omit roots and still export the same complete
summary in their JAR:

```kotlin
mosaicAnalysis {
  compilerPluginJar.set(file("/path/to/mosaic-compiler-plugin-0.2.0.jar"))
  role = MosaicAnalysisRole.LIBRARY
}
```

Applications also export contracts. A library with roots is contradictory and
fails with a configuration error.

`extractMosaicMain` runs the pinned embeddable JVM compiler in a fresh process
over the complete supported source set. It writes a complete summary, including
an empty summary after the final source is removed. It deletes prior output
before each invocation and on failure. Normal `compileKotlin` remains separate.
`jar` embeds the summary at `META-INF/mosaic-analysis/v1/summary.json`.
`verifyMosaicMain` writes `build/reports/mosaic-analysis/main.txt`;
`verifyMosaic` aggregates it and is wired into `check` (and therefore ordinary
`build`). In APPLICATION mode it reads summaries from selected dependency JARs
and checks the configured roots. In LIBRARY mode it validates only the local
complete summary and reports `EXPORT_ONLY`; it does not claim application roots
or cross-application dependencies were verified. `jar` embeds the local summary
in either role. Verification tracks full dependency JAR bytes, so a body-only
contract change invalidates it even when Kotlin compilation can reuse an ABI.

Roots are explicit callable IDs. APPLICATION with no roots fails both
`verifyMosaicMain` and `check`/`build`, with guidance to configure roots or select
LIBRARY. LIBRARY with no roots passes ordinary `check`/`build` after extraction
and local summary validation. Explicit `verifyMosaicMain` and `verifyMosaic` use
the same role-specific behavior.

| Role | Enforcement | Result |
| --- | --- | --- |
| APPLICATION | STANDARD | Proven missing obligations fail; unverifiable boundaries pass with visible warnings and `UNVERIFIED` root status. |
| APPLICATION | STRICT | Proven missing obligations and unverifiable boundaries fail. |
| LIBRARY | STANDARD or STRICT | Validate/export the complete local summary; application verification is not requested. |

STANDARD maps to the analysis kernel's `DEFAULT` policy. A warning pass is not
a proof: its report says `PASSED WITH WARNINGS` and preserves `UNVERIFIED` root
status. Changing role, enforcement, or roots invalidates the verification task.

An unrelated JAR without Mosaic metadata is harmless. If an application reaches
a declaration whose dependency artifact has no summary, that boundary remains
unknown: STANDARD warns and STRICT fails. A summary that is present but malformed,
partial, incompatible, or integrity-invalid is an artifact error and fails in
both modes. Invalid local summaries also fail. Conflicting selected declaration
owners fail. These configuration and artifact errors are never converted into
STANDARD warnings.
Selected binary constructors use producer constructor contracts, including
capability-bearing initialization marked unknown. If the referenced constructor
summary is absent, verification is unknown. Referenced stored-property reads
likewise retain initialization work. This is not a general model of JVM class
initialization or exception safety. Used user defaults with unavailable binary
expressions and Mosaic extension-helper calls remain unknown. The schema stays
v1.1; summaries from older extractor versions are rejected because they may
have omitted accessor or constructor-default effects or conflated array keys.

Only the tested default Kotlin/JVM main layout is supported. Extraction uses the
configured Java toolchain and fails on a Kotlin/toolchain version mismatch. The
task rejects nonstandard or generated Kotlin source paths, Kotlin scripts,
mixed Java/Kotlin sources, friend paths, additional compiler plugins, plugin
options, opt-ins, free compiler arguments, progressive mode, nondefault JVM
interface mode, no-JDK compilation, and KSP. Applying
the plugin without Kotlin/JVM fails during project configuration. Android,
multiplatform, test sources, framework lifecycle callbacks, and arbitrary virtual
dispatch remain unsupported. A virtual call with an unresolved receiver is
UNVERIFIED. The plugin ID, extension, tasks, v1.1 schema, and report format are
provisional; none is published or included in the BOM.
