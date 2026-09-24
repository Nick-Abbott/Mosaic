@file:Suppress("LargeClass", "LongMethod", "FunctionMaxLength")

package org.buildmosaic.analysis

import org.buildmosaic.analysis.AnalysisFixtures.compose
import org.buildmosaic.analysis.AnalysisFixtures.entry
import org.buildmosaic.analysis.AnalysisFixtures.lookup
import org.buildmosaic.analysis.AnalysisFixtures.metrics
import org.buildmosaic.analysis.AnalysisFixtures.site
import org.buildmosaic.analysis.AnalysisFixtures.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MosaicGraphTest {
  @Test fun `overview includes disconnected local contracts and only referenced dependencies`() {
    val local =
      ModuleContract(
        "local",
        canvases = listOf(CanvasContract("emptyCanvas", result = CanvasExpression.Empty, site = site("canvas"))),
        tiles = listOf(tile("idle"), tile("connected", compose("reuse", CanvasExpression.Empty, "shared"))),
        callables = listOf(entry("entry", compose("begin", CanvasExpression.Empty, "connected"))),
      )
    val dependency =
      ModuleContract("dependency", tiles = listOf(tile("shared", lookup("required", metrics)), tile("unused")))
    val request = AnalysisRequest(local, listOf(dependency))
    val graph = MosaicGraph.render(request)
    assertEquals(graph, MosaicGraph.render(request))
    assertTrue(graph.contains("Tile: idle"))
    assertTrue(graph.contains("Canvas: emptyCanvas"))
    assertTrue(graph.contains("Tile: shared [dependency: dependency]"))
    assertFalse(graph.contains("Tile: unused"))
    assertFalse(graph.contains("Status:"))
    assertFalse(graph.contains("No verification roots"))
  }

  @Test fun `root findings stay in focused section and show conditional and qualified Canvas details`() {
    val key = CanvasKeyIdentity("example.Metrics", "primary")
    val layer =
      CanvasExpression.Layer(
        "providers",
        CanvasExpression.Empty,
        listOf(Binding(Fact.Known(key, site("provider-key")), site = site("binding"))),
        site = site("layer"),
      )
    val local =
      ModuleContract(
        "local",
        tiles = listOf(tile("worker", lookup("need", key))),
        callables =
          listOf(
            entry("entry", Effect.Compose("compose", layer, TileReference.Stable("worker"), site = site("compose"))),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(local, roots = listOf(SelectedRoot("entry", "entry"))))
    assertTrue(graph.contains("## Root: entry"))
    assertTrue(graph.contains("Canvas layer: providers"))
    assertTrue(graph.contains("Binding: example.Metrics [primary]"))
    assertTrue(graph.contains("provider-key.kt:1"))
    assertTrue(graph.contains("### Findings for entry"))
    assertTrue(graph.contains("need"))
    assertTrue(graph.contains("Status: VERIFIED"))
  }

  @Test fun `shared references and cycles have one contract node each`() {
    val first =
      tile(
        "first",
        compose("to-second", CanvasExpression.Empty, "second"),
        compose("also-second", CanvasExpression.Empty, "second"),
      )
    val second = tile("second", compose("back", CanvasExpression.Empty, "first"))
    val graph = MosaicGraph.render(AnalysisRequest(ModuleContract("local", tiles = listOf(first, second))))
    assertEquals(1, Regex("Tile: first\\\"").findAll(graph).count())
    assertEquals(1, Regex("Tile: second\\\"").findAll(graph).count())
  }

  @Test fun `unknown runtime calls render as unknown nodes`() {
    val local =
      ModuleContract(
        "local",
        callables =
          listOf(
            entry(
              "entry",
              Effect.Call("missing-call", "missing.call()", site = site("call")),
              Effect.ConstructCanvas(
                "missing-canvas",
                CanvasExpression.RuntimeCall("missing.canvas()", site = site("canvas")),
                site = site("construct"),
              ),
            ),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(local))
    assertTrue(graph.contains("Unknown callable: missing.call()"))
    assertTrue(graph.contains("Unknown Canvas: missing.canvas()"))
  }

  @Test fun `empty callable indirection is collapsed`() {
    val module =
      ModuleContract(
        "local",
        callables =
          listOf(
            entry(
              "entry",
              Effect.Call("helper-call", "helper", site = site("helper-call")),
              Effect.Call("missing-call", "missing", site = site("missing-call")),
            ),
            entry("helper"),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(module))
    assertFalse(graph.contains("Callable: helper"))
    assertTrue(graph.contains("Unknown callable: missing"))
  }

  @Test fun `multitile conditions unknown and special labels remain visible and escaped`() {
    val multi =
      tile(
        "multi\"|`",
        Effect.Branch(
          "conditional",
          Guard.Opaque("dynamic", site("guard")),
          listOf(Effect.Unknown("unknown", "not modeled", site("unknown"))),
          site = site("branch"),
        ),
      ).copy(multi = true)
    val graph = MosaicGraph.render(AnalysisRequest(ModuleContract("local", tiles = listOf(multi))))
    assertTrue(graph.contains("MultiTile: multi&quot;&#124;&#96;"))
    assertTrue(graph.contains("Condition: unknown (dynamic)"))
    assertTrue(graph.contains("Unknown: not modeled"))
    assertTrue(graph.contains("Legend: Tile and MultiTile"))
  }

  @Test fun `two roots retain independent findings and status`() {
    val worker = tile("worker", lookup("required", metrics))
    val provided =
      CanvasExpression.Layer(
        "providers",
        CanvasExpression.Empty,
        listOf(Binding(Fact.Known(metrics), site = site("binding"))),
        site = site("layer"),
      )
    val local =
      ModuleContract(
        "local",
        tiles = listOf(worker),
        callables =
          listOf(
            entry("good", compose("good-compose", provided, "worker")),
            entry("bad", compose("bad-compose", CanvasExpression.Empty, "worker")),
          ),
      )
    val graph =
      MosaicGraph.render(
        AnalysisRequest(local, roots = listOf(SelectedRoot("good", "good"), SelectedRoot("bad", "bad"))),
      )
    val badSection = graph.substringAfter("## Root: bad").substringBefore("## Root: good")
    val goodSection = graph.substringAfter("## Root: good")
    assertTrue(badSection.contains("Status: FAILED"))
    assertTrue(badSection.contains("MISSING"))
    assertTrue(goodSection.contains("Status: VERIFIED"))
    assertTrue(goodSection.contains("VERIFIED"))
    assertFalse(goodSection.contains("| MISSING |"))
  }

  @Test fun `constructed Canvas value is shared with later compose reference`() {
    val layer = CanvasExpression.Layer("providers", CanvasExpression.Empty, site = site("layer"))
    val local =
      ModuleContract(
        "local",
        tiles = listOf(tile("worker")),
        callables =
          listOf(
            entry(
              "entry",
              Effect.ConstructCanvas("shared", CanvasExpression.Alias("shared", layer), site("construct")),
              compose("use", CanvasExpression.ValueReference("shared", site("reference")), "worker"),
            ),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(local))
    assertEquals(1, Regex("Canvas value: shared\\\"").findAll(graph).count())
    assertTrue(graph.contains("Canvas layer: providers"))
    assertTrue(graph.contains("construct Canvas"))
    assertTrue(graph.contains("Compose: use (COMPOSE)"))
    assertTrue(graph.contains("Canvas value: shared"))
  }

  @Test fun `compose nodes preserve each Tile and Canvas association`() {
    val firstCanvas = CanvasExpression.Layer("first-layer", CanvasExpression.Empty, site = site("first-layer"))
    val secondCanvas = CanvasExpression.Layer("second-layer", CanvasExpression.Empty, site = site("second-layer"))
    val graph =
      MosaicGraph.render(
        AnalysisRequest(
          ModuleContract(
            "local",
            tiles = listOf(tile("worker")),
            callables =
              listOf(
                entry("entry", compose("first", firstCanvas, "worker"), compose("second", secondCanvas, "worker")),
              ),
          ),
        ),
      )
    val first = nodeId(graph, "Compose: first (COMPOSE)")
    val second = nodeId(graph, "Compose: second (COMPOSE)")
    val worker = nodeId(graph, "Tile: worker")
    val firstLayer = nodeId(graph, "Canvas layer: first-layer")
    val secondLayer = nodeId(graph, "Canvas layer: second-layer")
    assertEdge(graph, first, worker, "Tile")
    assertEdge(graph, second, worker, "Tile")
    assertEdge(graph, first, firstLayer, "Canvas")
    assertEdge(graph, second, secondLayer, "Canvas")
    assertFalse(graph.contains("  $first -->|Canvas| $secondLayer"))
    assertFalse(graph.contains("  $second -->|Canvas| $firstLayer"))
  }

  @Test fun `branch arm nodes label both outcomes`() {
    val graph =
      MosaicGraph.render(
        AnalysisRequest(
          ModuleContract(
            "local",
            callables =
              listOf(
                entry(
                  "entry",
                  Effect.Branch(
                    "choice",
                    Guard.Opaque("runtime", site("guard")),
                    listOf(Effect.Unknown("yes", "true effect", site("yes"))),
                    listOf(Effect.Unknown("no", "false effect", site("no"))),
                    site("branch"),
                  ),
                ),
              ),
          ),
        ),
      )
    val condition = nodeId(graph, "Condition: unknown (runtime)")
    val trueArm = nodeId(graph, "When true")
    val falseArm = nodeId(graph, "When false")
    assertEdge(graph, condition, trueArm, "true")
    assertEdge(graph, condition, falseArm, "false")
    assertEdge(graph, trueArm, nodeId(graph, "Unknown: true effect"), "unknown")
    assertEdge(graph, falseArm, nodeId(graph, "Unknown: false effect"), "unknown")
  }

  @Test fun `concrete virtual calls show implementation contracts and their lookups`() {
    val module =
      ModuleContract(
        "local",
        callables =
          listOf(
            entry(
              "entry",
              Effect.Call(
                "dispatch",
                "Base.run",
                site = site("dispatch"),
                receiver = DispatchReceiver.Concrete("Impl"),
                virtualDispatch = true,
              ),
              Effect.ConstructCanvas(
                "canvas",
                CanvasExpression.RuntimeCall(
                  "Base.canvas",
                  site = site("canvas"),
                  receiver = DispatchReceiver.Concrete("Impl"),
                  virtualDispatch = true,
                ),
                site("construct"),
              ),
            ),
            entry("Impl.run", lookup("needed", metrics)),
          ),
        canvases =
          listOf(
            CanvasContract(
              "Impl.canvas",
              result = CanvasExpression.Layer("implementation-layer", CanvasExpression.Empty, site = site("layer")),
              site = site("canvas-contract"),
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride("Impl", "Base.run", "Impl.run"),
            ResolvedOverride("Impl", "Base.canvas", "Impl.canvas"),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(module, roots = listOf(SelectedRoot("entry", "entry"))))
    val overview = graph.substringBefore("## Root: entry")
    val focused = graph.substringAfter("## Root: entry")
    for (section in listOf(overview, focused)) {
      assertTrue(section.contains("Callable: Impl.run"))
      assertTrue(section.contains("REQUIRED lookup: example.Metrics"))
      assertTrue(section.contains("Canvas: Impl.canvas"))
      assertTrue(section.contains("Canvas layer: implementation-layer"))
      assertTrue(section.contains("possible override (Impl)"))
    }
    assertFalse(focused.contains("Unknown callable: Base.run"))
    assertFalse(focused.contains("Unknown Canvas: Base.canvas"))
  }

  @Test fun `unresolved virtual receiver shows selected candidates and boundary`() {
    val module =
      ModuleContract(
        "local",
        callables =
          listOf(
            entry(
              "entry",
              Effect.Call(
                "dispatch",
                "Base.run",
                site = site("dispatch"),
                receiver = DispatchReceiver.Forwarded,
                virtualDispatch = true,
              ),
              Effect.ConstructCanvas(
                "canvas",
                CanvasExpression.RuntimeCall(
                  "Base.canvas",
                  site = site("canvas"),
                  receiver = DispatchReceiver.Unknown("parameter"),
                  virtualDispatch = true,
                ),
                site("construct"),
              ),
            ),
            entry("First.run", lookup("first-need", metrics)),
            entry("Second.run", lookup("second-need", metrics)),
          ),
        canvases =
          listOf(
            CanvasContract(
              "First.canvas",
              result = CanvasExpression.Layer("first-canvas", CanvasExpression.Empty, site = site("first-canvas")),
              site = site("canvas-contract"),
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride("First", "Base.run", "First.run"),
            ResolvedOverride("Second", "Base.run", "Second.run"),
            ResolvedOverride("First", "Base.canvas", "First.canvas"),
          ),
      )
    val graph = MosaicGraph.render(AnalysisRequest(module))
    val call = nodeId(graph, "Virtual call: Base.run")
    assertEdge(graph, call, nodeId(graph, "Callable: First.run"), "possible override (First)")
    assertEdge(graph, call, nodeId(graph, "Callable: Second.run"), "possible override (Second)")
    assertEdge(
      graph,
      call,
      nodeId(graph, "Receiver forwarded by caller at Base.run; selected implementation depends on caller context"),
      "context dependent",
    )
    val canvasCall = nodeId(graph, "Virtual call: Base.canvas")
    assertEdge(graph, canvasCall, nodeId(graph, "Canvas: First.canvas"), "possible override (First)")
    assertEdge(
      graph,
      canvasCall,
      nodeId(graph, "Unknown dispatch: Unknown receiver at Base.canvas: parameter"),
      "unresolved",
    )
  }

  @Test fun `forwarded receiver remains context dependent in a verified root`() {
    val module =
      ModuleContract(
        "local",
        callables =
          listOf(
            entry(
              "entry",
              Effect.Call("enter", "helper", site = site("enter"), receiver = DispatchReceiver.Concrete("First")),
            ),
            entry(
              "helper",
              Effect.Call(
                "dispatch",
                "Base.run",
                site = site("dispatch"),
                receiver = DispatchReceiver.Forwarded,
                virtualDispatch = true,
              ),
            ),
            entry("First.run"),
          ),
        overrides = listOf(ResolvedOverride("First", "Base.run", "First.run")),
      )
    val graph = MosaicGraph.render(AnalysisRequest(module, roots = listOf(SelectedRoot("entry", "entry"))))
    val focused = graph.substringAfter("## Root: entry")
    assertTrue(focused.contains("Status: VERIFIED"))
    assertTrue(focused.contains("possible override (First)"))
    assertTrue(focused.contains("context dependent"))
    assertFalse(focused.contains("Unknown dispatch: Forwarded"))
  }

  @Test fun `exported keys show resolved identity or unknown boundary`() {
    val exported = Fact.ExportedKey("dependency.key", site("reference"))
    val module =
      ModuleContract(
        "local",
        tiles =
          listOf(
            tile(
              "worker",
              Effect.Lookup("known", CanvasExpression.Current, exported, LookupKind.REQUIRED, site("known")),
              Effect.Lookup(
                "missing",
                CanvasExpression.Current,
                Fact.ExportedKey("missing.key", site("missing")),
                LookupKind.REQUIRED,
                site("missing"),
              ),
            ),
          ),
      )
    val dependency =
      ModuleContract(
        "dependency",
        keys = listOf(KeyContract("dependency.key", CanvasKeyIdentity("example.Metrics", "primary"), site("key"))),
      )
    val graph = MosaicGraph.render(AnalysisRequest(module, selectedDependencies = listOf(dependency)))
    assertTrue(graph.contains("REQUIRED lookup: example.Metrics [primary]"))
    assertTrue(graph.contains("REQUIRED lookup: unknown (unresolved exported key missing.key)"))
  }

  private fun nodeId(
    graph: String,
    label: String,
  ): String = graph.lineSequence().first { it.endsWith("[\"$label\"]") }.trim().substringBefore('[')

  private fun assertEdge(
    graph: String,
    from: String,
    to: String,
    label: String,
  ) {
    assertTrue(graph.contains("  $from -->|$label| $to"))
  }
}
