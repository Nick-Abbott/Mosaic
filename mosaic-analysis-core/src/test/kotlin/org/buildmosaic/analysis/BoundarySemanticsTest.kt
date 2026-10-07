package org.buildmosaic.analysis

import org.buildmosaic.analysis.AnalysisFixtures.binding
import org.buildmosaic.analysis.AnalysisFixtures.compose
import org.buildmosaic.analysis.AnalysisFixtures.entry
import org.buildmosaic.analysis.AnalysisFixtures.lookup
import org.buildmosaic.analysis.AnalysisFixtures.metrics
import org.buildmosaic.analysis.AnalysisFixtures.report
import org.buildmosaic.analysis.AnalysisFixtures.site
import org.buildmosaic.analysis.AnalysisFixtures.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength", "LargeClass", "MaxLineLength")
class BoundarySemanticsTest {
  @Test
  fun `explicit resolved transfer substitutes Canvas and Boolean arguments`() {
    val handlerCanvas = ContractParameter("handler", "canvas", ParameterKind.CANVAS)
    val handlerFlag = ContractParameter("handler", "enabled", ParameterKind.BOOLEAN)
    val entryFlag = ContractParameter("entry", "enabled", ParameterKind.BOOLEAN)
    val requested = tile("MetricsTile", lookup("metrics", metrics))
    val handler =
      entry(
        "handler",
        Effect.Branch(
          "negated-flag",
          Guard.BooleanParameter(handlerFlag, expected = false),
          listOf(compose("compose", CanvasExpression.ParameterValue(handlerCanvas), requested.id)),
          site = site("negated-flag"),
        ),
        parameters = listOf(handlerCanvas, handlerFlag),
      )
    val transfer =
      Effect.Call(
        "transfer",
        handler.id,
        CallArguments(
          mapOf(
            handlerCanvas to ArgumentExpression.Canvas(metricsCanvas()),
            handlerFlag to ArgumentExpression.BooleanValue(BooleanExpression.ParameterValue(entryFlag)),
          ),
        ),
        site("transfer"),
      )
    val app =
      ModuleContract(
        "app",
        tiles = listOf(requested),
        callables = listOf(handler, entry("entry", transfer, parameters = listOf(entryFlag))),
      )
    val root =
      SelectedRoot(
        "root",
        "entry",
        CallArguments(mapOf(entryFlag to ArgumentExpression.BooleanValue(BooleanExpression.Constant(false)))),
      )
    val result = report(app, roots = listOf(root))

    assertEquals(Certainty.VERIFIED, result.findings.single().certainty)
    assertTrue("handler" in result.roots.single().specializedContracts)
  }

  @Test
  fun `opaque actual and constant guard retain conservative branch semantics`() {
    val helperFlag = ContractParameter("helper", "flag", ParameterKind.BOOLEAN)
    val helper =
      entry(
        "helper",
        Effect.Branch(
          "opaque",
          Guard.BooleanParameter(helperFlag),
          listOf(Effect.Unknown("unknown", "opaque branch", site("unknown"))),
          site = site("opaque"),
        ),
        Effect.Branch(
          "constant",
          Guard.Constant(false),
          listOf(Effect.Unknown("unreachable", "must not run", site("unreachable"))),
          site = site("constant"),
        ),
        parameters = listOf(helperFlag),
      )
    val call =
      Effect.Call(
        "call",
        helper.id,
        CallArguments(
          mapOf(
            helperFlag to
              ArgumentExpression.BooleanValue(
                BooleanExpression.Opaque("runtime flag", site("runtime-flag")),
              ),
          ),
        ),
        site("call"),
      )
    val result = report(ModuleContract("app", callables = listOf(helper, entry("entry", call))))

    assertEquals(1, result.findings.size)
    assertEquals(Certainty.UNVERIFIED, result.findings.single().certainty)
  }

  @Test
  fun `missing call arguments and absent current Canvas stay unknown`() {
    val canvas = ContractParameter("handler", "canvas", ParameterKind.CANVAS)
    val flag = ContractParameter("handler", "flag", ParameterKind.BOOLEAN)
    val handler =
      entry(
        "handler",
        Effect.Branch(
          "branch",
          Guard.BooleanParameter(flag),
          listOf(
            Effect.Lookup(
              "lookup",
              CanvasExpression.ParameterValue(canvas),
              Fact.Known(metrics),
              LookupKind.REQUIRED,
              site("lookup"),
            ),
          ),
          listOf(lookup("current", metrics)),
          site("branch"),
        ),
        parameters = listOf(canvas, flag),
      )
    val result =
      report(
        ModuleContract(
          "app",
          callables = listOf(handler, entry("entry", Effect.Call("call", handler.id, site = site("call")))),
        ),
      )

    assertTrue(result.findings.any { it.reason.contains("Missing Canvas argument") })
    assertTrue(result.findings.any { it.reason.contains("No current Mosaic Canvas") })
  }

  @Test
  fun `missing conflicting and unfaithful Canvas boundaries preserve local binding`() {
    val local =
      CanvasExpression.Layer(
        "local",
        CanvasExpression.RuntimeCall("externalCanvas", site = site("runtime-call")),
        listOf(binding(metrics)),
        site = site("local"),
      )
    val captured =
      CanvasExpression.Captured(
        "capture",
        CaptureOrigin.Inline("external", "artifact", "hash"),
        CanvasExpression.Empty,
        faithfullyCaptured = false,
        site = site("capture"),
      )
    val requested = tile("MetricsTile", lookup("metrics", metrics))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(requested),
        callables =
          listOf(
            entry("missingEntry", compose("missing", local, requested.id)),
            entry("capturedEntry", compose("captured", captured, requested.id)),
          ),
      )
    val missing = report(app, roots = listOf(SelectedRoot("missing", "missingEntry")))
    val unfaithful = report(app, roots = listOf(SelectedRoot("capture", "capturedEntry")))

    assertTrue(missing.findings.any { it.certainty == Certainty.VERIFIED })
    assertTrue(missing.findings.any { it.reason.contains("Runtime Canvas target is missing") })
    assertTrue(unfaithful.findings.any { it.reason.contains("not faithfully captured") })
  }

  @Test
  fun `conflicting Canvas runtime reference is localized`() {
    val first =
      ModuleContract(
        "first",
        canvases = listOf(CanvasContract("shared", result = CanvasExpression.Empty, site = site("first"))),
      )
    val second =
      ModuleContract(
        "second",
        canvases = listOf(CanvasContract("shared", result = CanvasExpression.Empty, site = site("second"))),
      )
    val canvas =
      CanvasExpression.Layer(
        "local",
        CanvasExpression.RuntimeCall("shared", site = site("call")),
        listOf(binding(metrics)),
        site = site("local"),
      )
    val requested = tile("MetricsTile", lookup("metrics", metrics))
    val app =
      ModuleContract(
        "app",
        tiles = listOf(requested),
        callables = listOf(entry("entry", compose("compose", canvas, requested.id))),
      )
    val result = report(app, dependencies = listOf(first, second))

    assertTrue(result.findings.any { it.kind == FindingKind.CONTRACT_CONFLICT })
    assertTrue(result.findings.any { it.certainty == Certainty.VERIFIED })
  }

  @Test
  fun `missing and conflicting assumptions are explicit boundaries`() {
    val requested = tile("MetricsTile", lookup("metrics", metrics))
    val effect = compose("compose", CanvasExpression.Assumption("managed", site("handler")), requested.id)
    val app = ModuleContract("app", tiles = listOf(requested), callables = listOf(entry("entry", effect)))
    val missing = report(app)
    val conflicts =
      listOf(
        ExternalAssumption("managed", setOf(metrics), site("first-assumption")),
        ExternalAssumption("managed", setOf(metrics), site("second-assumption")),
      )
    val conflict = report(app, assumptions = conflicts)

    assertTrue(missing.findings.any { it.reason.contains("assumption is missing") })
    assertTrue(conflict.findings.any { it.kind == FindingKind.CONTRACT_CONFLICT })
  }

  @Test
  fun `missing or conflicting selected roots never appear verified`() {
    val first = ModuleContract("first", callables = listOf(entry("shared")))
    val second = ModuleContract("second", callables = listOf(entry("shared")))
    val conflict =
      report(
        ModuleContract("app"),
        roots = listOf(SelectedRoot("conflict", "shared")),
        dependencies = listOf(first, second),
      )
    val missing = report(ModuleContract("app"), roots = listOf(SelectedRoot("missing", "absent")))

    assertEquals(RootStatus.UNVERIFIED, conflict.roots.single().status)
    assertEquals(RootStatus.UNVERIFIED, missing.roots.single().status)
  }

  @Test
  fun `bounded call Canvas and Tile expansion emit incomplete findings`() {
    val recursiveCall = entry("recursive", Effect.Call("self", "recursive", site = site("self")))
    val callReport =
      report(
        ModuleContract("app", callables = listOf(recursiveCall)),
        roots = listOf(SelectedRoot("call", recursiveCall.id)),
        limits = AnalysisLimits(expansionDepth = 1),
      )
    val canvasContract =
      CanvasContract(
        "recursiveCanvas",
        result = CanvasExpression.RuntimeCall("recursiveCanvas", site = site("canvas-self")),
        site = site("recursiveCanvas"),
      )
    val canvasReport =
      report(
        ModuleContract("app", canvases = listOf(canvasContract)),
        roots = listOf(SelectedRoot("canvas", canvasContract.id)),
        limits = AnalysisLimits(expansionDepth = 0),
      )
    val requested = tile("MetricsTile", lookup("metrics", metrics))
    val tileReport =
      report(
        ModuleContract(
          "app",
          tiles = listOf(requested),
          callables = listOf(entry("entry", compose("compose", metricsCanvas(), requested.id))),
        ),
        limits = AnalysisLimits(expansionDepth = 0),
      )

    assertTrue(callReport.findings.any { it.kind == FindingKind.INCOMPLETE_ANALYSIS })
    assertTrue(canvasReport.findings.any { it.kind == FindingKind.INCOMPLETE_ANALYSIS })
    assertTrue(tileReport.findings.any { it.kind == FindingKind.INCOMPLETE_ANALYSIS })
  }

  @Test
  fun `fresh Tile references report missing and conflicting templates`() {
    val first = ModuleContract("first", tiles = listOf(tile("SharedTemplate")))
    val second = ModuleContract("second", tiles = listOf(tile("SharedTemplate")))
    val missing =
      Effect.Compose(
        "missing",
        CanvasExpression.Empty,
        TileReference.Fresh("MissingTemplate", "allocation", "call"),
        site = site("missing"),
      )
    val conflict =
      Effect.Compose(
        "conflict",
        CanvasExpression.Empty,
        TileReference.Fresh("SharedTemplate", "allocation", "call"),
        site = site("conflict"),
      )
    val result =
      report(
        ModuleContract("app", callables = listOf(entry("entry", missing, conflict))),
        dependencies = listOf(first, second),
      )

    assertTrue(result.findings.any { it.kind == FindingKind.UNKNOWN_BOUNDARY })
    assertTrue(result.findings.any { it.kind == FindingKind.CONTRACT_CONFLICT })
  }

  private fun metricsCanvas() =
    CanvasExpression.Alias(
      "metrics-alias",
      CanvasExpression.Layer(
        "metrics",
        CanvasExpression.Empty,
        listOf(binding(metrics)),
        site = site("metrics"),
      ),
    )
}
