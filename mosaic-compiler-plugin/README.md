# Mosaic compiler extraction prototype

This unpublished module is a read-only Kotlin 2.2.10 K2 compiler plugin. It uses
`CompilerPluginRegistrar` and `IrGenerationExtension` before IR lowering. It
does not transform Kotlin or run Mosaic code. A separate full compiler invocation
emits one complete main-source-set summary; ordinary `compileKotlin` does not
produce the snapshot.

The supported extraction path covers reified `singleTile`, `source`, `sourceOr`,
`canvas`, `single`, `withLayer`, `paint`, `create`, `compose`, and `composeAsync` in
the tested shapes; immutable local Canvas and Tile aliases; parameterized Canvas
helpers; ordinary resolved calls; and the direct final template method to
protected abstract suspend override transfer. The phase-zero tests also inspect
`multiTile` and `perKeyTile` IR shapes. Their nested callbacks are not generally
interpreted. Unsupported control flow and capability callbacks produce unknown
effects. This is a narrow extractor, not a general Kotlin interpreter.

An external const qualifier arrives in this phase as a literal. Its value is
retained in the caller's summary. The original external const declaration is
not reliably recoverable, so the summary records a provenance limitation.
External inline helper bodies are unavailable before backend inlining; a helper
that can affect Mosaic is explicitly unknown. The extractor never substitutes
the newest helper body into an already compiled caller.

The output is deterministic UTF-8 JSON at
`META-INF/mosaic-analysis/v1/summary.json` when packaged by the Gradle plugin.
The schema, compiler option names, and symbol-locator strategy are internal and
provisional. This module is not published or included in the BOM.
