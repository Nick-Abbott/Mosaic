# Mosaic Gradle analysis prototype

This unpublished plugin supports pure Kotlin/JVM `main` sources under
`src/main/kotlin` with Kotlin 2.2.10. Its provisional ID is
`org.buildmosaic.analysis`. The plugin does not apply Kotlin or add Mosaic
runtime dependencies. In this repository's binary integration test the plugin
is supplied through Gradle TestKit; there is no published marker yet.

```kotlin
plugins {
  kotlin("jvm") version "2.2.10"
  id("org.buildmosaic.analysis")
}

mosaicAnalysis {
  compilerPluginJar.set(file("/path/to/mosaic-compiler-plugin-0.2.0.jar"))
  roots.add("app.entry()")
}
```

`extractMosaicMain` runs the pinned embeddable JVM compiler in a fresh process
over the complete supported source set. It writes a complete summary, including
an empty summary after the final source is removed. It deletes prior output
before each invocation and on failure. Normal `compileKotlin` remains separate.
`jar` embeds the summary at `META-INF/mosaic-analysis/v1/summary.json`.
`verifyMosaicMain` reads summaries from the selected dependency JARs and writes
`build/reports/mosaic-analysis/main.txt`; `verifyMosaic` aggregates it and is
wired into `check`. Verification tracks full dependency JAR bytes, so a body-only
contract change invalidates it even when Kotlin compilation can reuse an ABI.

Roots are explicit callable IDs. No roots yields `UNCONFIGURED`; running
`verifyMosaicMain` or `build` then fails visibly. Library producers can run
`jar` to export contracts without selecting application roots. Strict selected
root verification fails on proven missing lookups and localized unknown effects.
An unrelated JAR without Mosaic metadata is ignored. Missing metadata for a
referenced declaration stays unknown. Invalid metadata is reported and its
declarations are not used. Conflicting selected owners are rejected.

Only the tested default Kotlin/JVM main layout is supported. Mixed Java/Kotlin
sources, nonstandard or generated Kotlin source paths, KSP sources, additional
compiler plugins, test sources, Android, and multiplatform builds are outside
this prototype. It does not discover roots, framework lifecycle callbacks, or
arbitrary virtual dispatch. The plugin ID, extension, tasks, schema, and report
format are provisional; none is published or included in the BOM.
