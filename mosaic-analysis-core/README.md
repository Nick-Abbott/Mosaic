# Mosaic analysis core

`mosaic-analysis-core` is an experimental, compiler-independent semantic kernel.
It accepts immutable, hand-authored contracts describing Canvas construction,
lookups, Tile composition, calls, conditions, and selected verification roots.
It verifies exact Canvas-key availability and eager local singleton construction
while preserving unknown boundaries and source provenance.

```kotlin
val site = SourceLocation("example.Application", "Application.kt", 1, 1)
val metrics = CanvasKeyIdentity("example.Metrics")
val tile = TileContract(
  id = "example.MetricsTile",
  effects = listOf(
    Effect.Lookup(
      id = "metrics-source",
      canvas = CanvasExpression.Current,
      key = Fact.Known(metrics),
      kind = LookupKind.REQUIRED,
      site = SourceLocation("example.MetricsTile", "Tiles.kt", 4, 3),
    ),
  ),
)
val entry = CallableContract(
  id = "example.entry",
  effects = listOf(
    Effect.Compose(
      id = "entry-compose",
      canvas = CanvasExpression.Layer(
        id = "application",
        parent = CanvasExpression.Empty,
        bindings = listOf(Binding(Fact.Known(metrics), site = site)),
        site = site,
      ),
      tile = TileReference.Stable(tile.id),
      site = site,
    ),
  ),
)
val report = MosaicAnalyzer().analyze(
  AnalysisRequest(
    program = ModuleContract("application", tiles = listOf(tile), callables = listOf(entry)),
    roots = listOf(SelectedRoot("application-entry", entry.id)),
  ),
)
```

Reusable contracts are only reported as deferred until a selected root reaches
and specializes them. Public visibility does not select a root, and selecting no
roots produces an `UNCONFIGURED` report.

The model intentionally supports only the expressions needed by the semantic
fixtures. It does not parse Kotlin source, inspect JARs, discover roots, infer
framework lifecycles or arbitrary dispatch, check generic types/subtypes, model
cache occupancy, or diagnose deadlocks. Unsupported represented behavior stays
explicitly `UNVERIFIED`. Callback or override transfers require an explicitly
resolved target; these fixtures do not prove Kotlin override inference. There is
no metadata or wire format yet, and this API is provisional.

The in-memory tests validate only these kernel semantics. They do not prove
source extraction, binary linking, or incremental-build invalidation.
