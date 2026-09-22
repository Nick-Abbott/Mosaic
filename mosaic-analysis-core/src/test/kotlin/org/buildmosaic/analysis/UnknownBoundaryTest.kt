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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength", "LargeClass", "MaxLineLength")
class UnknownBoundaryTest {
  @Test
  fun `known local match verifies over unknown parent while other key stays unverified`() {
    val canvas =
      CanvasExpression.Layer(
        "request",
        CanvasExpression.Unknown("framework-injected parent", site("handler")),
        listOf(binding(request, "request-binding")),
        site = site("request-layer"),
      )
    val requested = tile("RequestTile", lookup("request", request), lookup("metrics", metrics))
    val result = analyze(canvas, requested)

    assertEquals(Certainty.VERIFIED, result.findings.single { it.key == request }.certainty)
    assertEquals(Certainty.UNVERIFIED, result.findings.single { it.key == metrics }.certainty)
  }

  @Test
  fun `missing external Tile and helper contracts remain explicit unknown effects`() {
    val missingTile =
      Effect.Compose(
        "missing-tile",
        CanvasExpression.Empty,
        TileReference.Stable("dependency.ExternalTile"),
        site = site("missing-tile"),
      )
    val missingHelper = Effect.Call("missing-helper", "dependency.helper", site = site("missing-helper"))
    val result = report(ModuleContract("app", callables = listOf(entry("entry", missingTile, missingHelper))))

    assertEquals(2, result.findings.size)
    assertTrue(result.findings.all { it.kind == FindingKind.UNKNOWN_BOUNDARY })
    assertTrue(result.findings.any { it.reason.contains("Tile contract") })
    assertTrue(result.findings.any { it.reason.contains("callable target") })
  }

  @Test
  fun `unknown registrations do not hide local facts or certify ancestor lookup`() {
    val canvas =
      CanvasExpression.Layer(
        "dynamic",
        CanvasExpression.Empty,
        listOf(binding(request, "request-binding")),
        listOf(UnknownRegistration("dynamic key", site("dynamic-registration"))),
        site("dynamic"),
      )
    val requested = tile("BothTile", lookup("request", request), lookup("metrics", metrics))
    val result = analyze(canvas, requested)

    assertEquals(Certainty.VERIFIED, result.findings.single { it.key == request }.certainty)
    assertEquals(Certainty.UNVERIFIED, result.findings.single { it.key == metrics }.certainty)
    assertTrue(result.findings.any { it.reason.contains("unknown key") })
  }

  @Test
  fun `verified missing and unknown findings coexist across independent roots`() {
    val knownTile = tile("RequestTile", lookup("request", request))
    val missingTile = tile("PlatformTile", lookup("platform", platform))
    val knownEntry = entry("knownEntry", compose("known", layerWith(request), knownTile.id))
    val missingEntry = entry("missingEntry", compose("missing", CanvasExpression.Empty, missingTile.id))
    val unknownEntry =
      entry(
        "unknownEntry",
        Effect.Compose(
          "unknown",
          CanvasExpression.Empty,
          TileReference.Unknown("external Tile body unavailable", site("unknown")),
          site = site("unknown"),
        ),
      )
    val module =
      ModuleContract(
        "app",
        tiles = listOf(knownTile, missingTile),
        callables = listOf(knownEntry, missingEntry, unknownEntry),
      )
    val result =
      report(
        module,
        roots =
          listOf(
            SelectedRoot("known", "knownEntry"),
            SelectedRoot("missing", "missingEntry"),
            SelectedRoot("unknown", "unknownEntry"),
          ),
      )

    assertEquals(
      setOf(Certainty.VERIFIED, Certainty.MISSING, Certainty.UNVERIFIED),
      result.findings.map {
        it.certainty
      }.toSet(),
    )
  }

  @Test
  fun `conflicting selected definitions affect only referenced boundary`() {
    val localTile = tile("LocalTile", lookup("request", request))
    val local = compose("local", layerWith(request), localTile.id)
    val conflict =
      Effect.Compose(
        "conflict",
        CanvasExpression.Empty,
        TileReference.Stable("SharedTile"),
        site = site("conflict"),
      )
    val app = ModuleContract("app", tiles = listOf(localTile), callables = listOf(entry("entry", local, conflict)))
    val first = ModuleContract("first", tiles = listOf(tile("SharedTile")))
    val second = ModuleContract("second", tiles = listOf(tile("SharedTile")))
    val result = report(app, dependencies = listOf(first, second))

    assertTrue(result.findings.any { it.certainty == Certainty.VERIFIED })
    assertTrue(result.findings.any { it.kind == FindingKind.CONTRACT_CONFLICT })
  }

  @Test
  fun `unknown binding key is reported and cannot guarantee a requested exact key`() {
    val unknownBinding = Binding(Fact.Unknown("computed key", site("computed")), site = site("binding"))
    val canvas =
      CanvasExpression.Layer(
        "layer",
        CanvasExpression.Empty,
        listOf(unknownBinding),
        site = site("layer"),
      )
    val result = analyze(canvas, tile("MetricsTile", lookup("metrics", metrics)))

    assertFalse(result.policyDecision.passed)
    assertTrue(result.findings.all { it.certainty == Certainty.UNVERIFIED })
  }

  private fun analyze(
    canvas: CanvasExpression,
    tile: TileContract,
  ) = report(
    ModuleContract(
      "app",
      tiles = listOf(tile),
      callables = listOf(entry("entry", compose("compose", canvas, tile.id))),
    ),
    policy = AnalysisPolicy.STRICT,
  )

  private fun layerWith(key: CanvasKeyIdentity) =
    CanvasExpression.Layer("local", CanvasExpression.Empty, listOf(binding(key)), site = site("local"))
}
