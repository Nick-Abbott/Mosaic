package org.buildmosaic.analysis

import org.buildmosaic.analysis.AnalysisFixtures.binding
import org.buildmosaic.analysis.AnalysisFixtures.compose
import org.buildmosaic.analysis.AnalysisFixtures.entry
import org.buildmosaic.analysis.AnalysisFixtures.lookup
import org.buildmosaic.analysis.AnalysisFixtures.metrics
import org.buildmosaic.analysis.AnalysisFixtures.platform
import org.buildmosaic.analysis.AnalysisFixtures.report
import org.buildmosaic.analysis.AnalysisFixtures.request
import org.buildmosaic.analysis.AnalysisFixtures.site
import org.buildmosaic.analysis.AnalysisFixtures.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength", "LargeClass", "MaxLineLength")
class ReferenceOwnershipTest {
  @Test
  fun `unchanged adapter observes changed selected platform runtime contract`() {
    val adapter =
      ModuleContract(
        "adapter",
        canvases =
          listOf(
            CanvasContract(
              "applicationBase",
              result = CanvasExpression.RuntimeCall("platformCanvas", site = site("adapter-call")),
              site = site("applicationBase"),
            ),
          ),
      )
    val appTile = tile("PlatformTile", lookup("platform", platform))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(appTile),
        callables =
          listOf(
            entry(
              "entry",
              compose(
                "compose",
                CanvasExpression.RuntimeCall("applicationBase", site = site("entry-call")),
                appTile.id,
              ),
            ),
          ),
      )

    val removed = report(app, dependencies = listOf(adapter, platformModule(emptyList())))
    val restored = report(app, dependencies = listOf(adapter, platformModule(listOf(platform))))

    assertEquals(Certainty.MISSING, removed.findings.single().certainty)
    assertEquals(Certainty.VERIFIED, restored.findings.single().certainty)
    assertTrue("platformCanvas" in removed.roots.single().specializedContracts)
  }

  @Test
  fun `captured qualifier retains caller value when origin contract changes`() {
    val primary = CanvasKeyIdentity("example.Metrics", "primary")
    val capturedSite = site("qualifier-v1")
    val origin = CaptureOrigin.Constant("PRIMARY", "qualifier-v1", "primary")
    val capturedKey = Fact.Known(primary, capturedSite, EvidenceKind.CAPTURED_FACT, origin)
    val canvas =
      CanvasExpression.Layer(
        "captured-layer",
        CanvasExpression.Empty,
        listOf(Binding(capturedKey, site = site("captured-binding"))),
        site = site("captured-layer"),
      )
    val effect = Effect.Lookup("lookup", CanvasExpression.Current, capturedKey, LookupKind.REQUIRED, site("lookup"))
    val appTile = TileContract("CapturedTile", listOf(effect), site("CapturedTile"))
    val changedOrigin = platformModule(listOf(CanvasKeyIdentity("example.Metrics", "secondary")))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(appTile),
        callables = listOf(entry("entry", compose("compose", canvas, appTile.id))),
      )
    val finding = report(app, dependencies = listOf(changedOrigin)).findings.single()

    assertEquals(primary, finding.key)
    assertEquals(Certainty.VERIFIED, finding.certainty)
    assertEquals(capturedSite, finding.factProvenance)
    assertEquals(setOf(origin), finding.capturedOrigins)
    assertTrue(EvidenceKind.CAPTURED_FACT in finding.evidence)
  }

  @Test
  fun `captured effects keep local behavior while nested runtime calls use selected contracts`() {
    val bothTile = tile("BothTile", lookup("request", request), lookup("metrics", metrics))
    val nestedCanvas =
      CanvasExpression.Layer(
        "captured-request",
        CanvasExpression.RuntimeCall("platformCanvas", site = site("nested-platform-call")),
        listOf(binding(request, "captured-request-binding", evidence = EvidenceKind.CAPTURED_FACT)),
        site = site("captured-request"),
      )
    val captured =
      Effect.Captured(
        "captured-effects",
        "app.entry",
        CaptureOrigin.Inline("addRequest", "adapter-v1", "hash-v1"),
        listOf(compose("compose", nestedCanvas, bothTile.id)),
        faithfullyCaptured = true,
        site = site("captured-effects"),
      )
    val app = ModuleContract("app", tiles = listOf(bothTile), callables = listOf(entry("entry", captured)))
    val changed = report(app, dependencies = listOf(platformModule(emptyList())))

    assertEquals(Certainty.VERIFIED, changed.findings.single { it.key == request }.certainty)
    assertEquals(Certainty.MISSING, changed.findings.single { it.key == metrics }.certainty)
    assertTrue(changed.findings.all { it.dependencyPath.any { node -> node.label.startsWith("captured:") } })
  }

  @Test
  fun `captured Canvas expression retains body and resolves nested call dynamically`() {
    val canvas =
      CanvasExpression.Captured(
        "capturedCanvas",
        CaptureOrigin.Inline("makeCanvas", "adapter-v1", "hash-v1"),
        CanvasExpression.Layer(
          "captured-local",
          CanvasExpression.RuntimeCall("platformCanvas", site = site("runtime-call")),
          listOf(binding(request, evidence = EvidenceKind.CAPTURED_FACT)),
          site = site("captured-local"),
        ),
        faithfullyCaptured = true,
        site = site("capturedCanvas"),
      )
    val localTile = tile("RequestTile", lookup("request", request))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(localTile),
        callables = listOf(entry("entry", compose("compose", canvas, localTile.id))),
      )

    assertEquals(
      Certainty.VERIFIED,
      report(app, dependencies = listOf(platformModule(emptyList()))).findings.single().certainty,
    )
  }

  @Test
  fun `unsupported external capture remains explicit unknown`() {
    val captured =
      Effect.Captured(
        "capture",
        "entry",
        CaptureOrigin.Inline("externalInline", "external", "old-hash"),
        listOf(Effect.Unknown("body", "should not run", site("body"))),
        faithfullyCaptured = false,
        site = site("capture"),
      )
    val result = report(ModuleContract("app", callables = listOf(entry("entry", captured))))

    assertEquals(1, result.findings.size)
    assertTrue(result.findings.single().reason.contains("not faithfully captured"))
  }

  @Test
  fun `unrelated selected contract changes preserve local findings`() {
    val localTile = tile("RequestTile", lookup("request", request))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(localTile),
        callables = listOf(entry("entry", compose("compose", requestCanvas(), localTile.id))),
      )
    val first = report(app, dependencies = listOf(platformModule(listOf(platform))))
    val second = report(app, dependencies = listOf(platformModule(emptyList())))

    assertEquals(first.findings, second.findings)
  }

  private fun platformModule(keys: List<CanvasKeyIdentity>) =
    ModuleContract(
      "platform",
      canvases =
        listOf(
          CanvasContract(
            "platformCanvas",
            result =
              CanvasExpression.Layer(
                "platform",
                CanvasExpression.Empty,
                keys.map { binding(it, "platform-${it.classId}-${it.qualifier}") },
                site = site("platformCanvas"),
              ),
            site = site("platformCanvas"),
          ),
        ),
    )

  private fun requestCanvas() =
    CanvasExpression.Layer("request", CanvasExpression.Empty, listOf(binding(request)), site = site("request"))
}
