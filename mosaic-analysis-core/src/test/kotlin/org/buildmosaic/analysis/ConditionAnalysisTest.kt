package org.buildmosaic.analysis

import org.buildmosaic.analysis.AnalysisFixtures.binding
import org.buildmosaic.analysis.AnalysisFixtures.compose
import org.buildmosaic.analysis.AnalysisFixtures.entry
import org.buildmosaic.analysis.AnalysisFixtures.lookup
import org.buildmosaic.analysis.AnalysisFixtures.metrics
import org.buildmosaic.analysis.AnalysisFixtures.report
import org.buildmosaic.analysis.AnalysisFixtures.request
import org.buildmosaic.analysis.AnalysisFixtures.site
import org.buildmosaic.analysis.AnalysisFixtures.tile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength", "LargeClass", "MaxLineLength")
class ConditionAnalysisTest {
  private val flag = ContractParameter("entry", "flag", ParameterKind.BOOLEAN)
  private val metricsTile = tile("MetricsTile", lookup("metrics", metrics))

  @Test
  fun `known Boolean actuals specialize Canvas alternatives before lookup`() {
    val falseReport = conditionalCanvasReport(BooleanExpression.Constant(false))
    val trueReport = conditionalCanvasReport(BooleanExpression.Constant(true))

    assertEquals(Certainty.MISSING, falseReport.findings.single().certainty)
    assertEquals(Certainty.VERIFIED, trueReport.findings.single().certainty)
  }

  @Test
  fun `free Boolean input retains missing witness and successful path`() {
    val result = conditionalCanvasReport()

    assertEquals(setOf(Certainty.MISSING, Certainty.VERIFIED), result.findings.map { it.certainty }.toSet())
    assertTrue(result.findings.any { "flag=false" in it.pathCondition })
    assertTrue(result.findings.any { "flag=true" in it.pathCondition })
  }

  @Test
  fun `opaque registration guard cannot prove failure witness`() {
    val canvas =
      CanvasExpression.Choice(
        Guard.Opaque("environment predicate", site("predicate")),
        metricsCanvas(),
        CanvasExpression.Empty,
      )
    val result = simpleCompose(canvas)

    assertEquals(setOf(Certainty.VERIFIED, Certainty.UNVERIFIED), result.findings.map { it.certainty }.toSet())
    assertFalse(result.findings.any { it.certainty == Certainty.MISSING })
  }

  @Test
  fun `opaque conditional requirement verifies when provider exists and is unknown when absent`() {
    val guard = Guard.Opaque("runtime condition", site("condition"))
    val branch =
      Effect.Branch(
        "branch",
        guard,
        listOf(compose("conditional", metricsCanvas(), metricsTile.id)),
        site = site("branch"),
      )
    val absentBranch =
      branch.copy(whenTrue = listOf(compose("conditional", CanvasExpression.Empty, metricsTile.id)))
    val available =
      report(ModuleContract("app", tiles = listOf(metricsTile), callables = listOf(entry("entry", branch))))
    val absent =
      report(ModuleContract("app", tiles = listOf(metricsTile), callables = listOf(entry("entry", absentBranch))))

    assertEquals(Certainty.VERIFIED, available.findings.single().certainty)
    assertEquals(Certainty.UNVERIFIED, absent.findings.single().certainty)
  }

  @Test
  fun `same immutable flag correlates conditional registration and use`() {
    val canvas =
      CanvasExpression.Choice(
        Guard.BooleanParameter(flag),
        metricsCanvas(),
        CanvasExpression.Empty,
      )
    val branch =
      Effect.Branch(
        "use",
        Guard.BooleanParameter(flag),
        listOf(compose("compose", canvas, metricsTile.id)),
        site = site("use"),
      )
    val module =
      ModuleContract(
        "app",
        tiles = listOf(metricsTile),
        callables = listOf(entry("entry", branch, parameters = listOf(flag))),
      )
    val result = report(module)

    assertEquals(listOf(Certainty.VERIFIED), result.findings.map { it.certainty })
    assertEquals(listOf("flag=true"), result.findings.single().pathCondition)
  }

  @Test
  fun `mutually exclusive branches never combine requirements with other branch absence`() {
    val requestTile = tile("RequestTile", lookup("request", request))
    val canvas =
      CanvasExpression.Choice(
        Guard.BooleanParameter(flag),
        metricsCanvas(),
        CanvasExpression.Layer(
          "request",
          CanvasExpression.Empty,
          listOf(binding(request)),
          site = site("request"),
        ),
      )
    val branch =
      Effect.Branch(
        "exclusive",
        Guard.BooleanParameter(flag),
        listOf(compose("metrics-compose", canvas, metricsTile.id)),
        listOf(compose("request-compose", canvas, requestTile.id)),
        site("exclusive"),
      )
    val result =
      report(
        ModuleContract(
          "app",
          tiles = listOf(metricsTile, requestTile),
          callables = listOf(entry("entry", branch, parameters = listOf(flag))),
        ),
      )

    assertEquals(2, result.findings.size)
    assertTrue(result.findings.all { it.certainty == Certainty.VERIFIED })
  }

  @Test
  fun `alternative budget retains earlier facts and marks unexplored remainder incomplete`() {
    val always = tile("AlwaysTile", lookup("metrics", metrics))
    val branch =
      Effect.Branch(
        "limited",
        Guard.BooleanParameter(flag),
        listOf(Effect.Unknown("unknown", "unexplored", site("unknown"))),
        site = site("limited"),
      )
    val module =
      ModuleContract(
        "app",
        tiles = listOf(always),
        callables =
          listOf(
            entry(
              "entry",
              compose("verified", metricsCanvas(), always.id),
              branch,
              parameters = listOf(flag),
            ),
          ),
      )
    val result = report(module, limits = AnalysisLimits(alternativeBudget = 1))

    assertTrue(result.findings.any { it.certainty == Certainty.VERIFIED })
    assertTrue(result.findings.any { it.kind == FindingKind.INCOMPLETE_ANALYSIS })
  }

  @Test
  fun `opaque branch does not obscure later unconditional missing lookup`() {
    val opaque =
      Effect.Branch(
        "opaque",
        Guard.Opaque("unrelated predicate", site("opaque")),
        listOf(Effect.Unknown("conditional", "conditional unknown", site("conditional"))),
        site = site("opaque"),
      )
    val module =
      ModuleContract(
        "app",
        tiles = listOf(metricsTile),
        callables =
          listOf(
            entry(
              "entry",
              opaque,
              compose("unconditional", CanvasExpression.Empty, metricsTile.id),
            ),
          ),
      )
    val result = report(module)

    assertEquals(Certainty.MISSING, result.findings.single { it.key == metrics }.certainty)
  }

  private fun conditionalCanvasReport(actual: BooleanExpression? = null): AnalysisReport {
    val canvas =
      CanvasExpression.Choice(
        Guard.BooleanParameter(flag),
        metricsCanvas(),
        CanvasExpression.Empty,
      )
    val root =
      SelectedRoot(
        "root",
        "entry",
        if (actual == null) CallArguments() else CallArguments(mapOf(flag to ArgumentExpression.BooleanValue(actual))),
      )
    val module =
      ModuleContract(
        "app",
        tiles = listOf(metricsTile),
        callables = listOf(entry("entry", compose("compose", canvas, metricsTile.id), parameters = listOf(flag))),
      )
    return report(module, roots = listOf(root))
  }

  private fun simpleCompose(canvas: CanvasExpression): AnalysisReport =
    report(
      ModuleContract(
        "app",
        tiles = listOf(metricsTile),
        callables = listOf(entry("entry", compose("compose", canvas, metricsTile.id))),
      ),
    )

  private fun metricsCanvas() =
    CanvasExpression.Layer("metrics", CanvasExpression.Empty, listOf(binding(metrics)), site = site("metrics"))
}
