package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength", "LargeClass") // Activation cases share contract-building fixtures.
class CallActivationTest {
  private fun site(
    n: String,
    line: Int = 1,
  ) = SourceLocation(n, "CallActivation.kt", line, 1)

  private val metrics = CanvasKeyIdentity("Metrics")
  private val service = CanvasKeyIdentity("Service")

  private fun binding(
    k: CanvasKeyIdentity,
    effects: List<Effect> = emptyList(),
  ) = Binding(Fact.Known(k), effects, site(k.classId))

  private fun layer(
    id: String,
    bindings: List<Binding>,
  ) = CanvasExpression.Layer(
    id,
    CanvasExpression.Empty,
    bindings,
    site = site(id),
  )

  private fun lookup(
    id: String,
    canvas: CanvasExpression = CanvasExpression.Current,
    key: CanvasKeyIdentity = metrics,
    kind: LookupKind = LookupKind.REQUIRED,
  ) = Effect.Lookup(
    id,
    canvas,
    Fact.Known(key),
    kind,
    site(id),
  )

  private val full = layer("full", listOf(binding(metrics)))
  private val empty = CanvasExpression.Empty

  private fun analyze(
    effects: List<Effect>,
    extra: List<CallableContract> = emptyList(),
    parameters: List<ContractParameter> = emptyList(),
  ): AnalysisReport =
    MosaicAnalyzer().analyze(
      AnalysisRequest(
        ModuleContract(
          "app",
          callables = listOf(CallableContract("entry", parameters, effects, site("entry"))) + extra,
        ),
        roots = listOf(SelectedRoot("root", "entry")),
        policy = AnalysisPolicy.STRICT,
      ),
    )

  @Test
  fun `recursive calls rebind Boolean actuals in a fresh environment`() {
    val a = ContractParameter("swap", "a", ParameterKind.BOOLEAN)
    val z = ContractParameter("swap", "binding", ParameterKind.BOOLEAN)
    val recurse =
      Effect.Call(
        "recurse",
        "swap",
        CallArguments(
          mapOf(
            a to ArgumentExpression.BooleanValue(BooleanExpression.ParameterValue(z)),
            z to ArgumentExpression.BooleanValue(BooleanExpression.ParameterValue(a)),
          ),
        ),
        site("recurse"),
      )
    val body =
      Effect.Branch(
        "if-a",
        Guard.BooleanParameter(a),
        listOf(
          Effect.Branch(
            "if-binding",
            Guard.BooleanParameter(z),
            emptyList(),
            listOf(lookup("should-be-missing", empty)),
            site("if-binding"),
          ),
        ),
        listOf(recurse),
        site("if-a"),
      )
    val swap = CallableContract("swap", listOf(a, z), listOf(body), site("swap"))
    val initial =
      Effect.Call(
        "start",
        "swap",
        CallArguments(
          mapOf(
            a to ArgumentExpression.BooleanValue(BooleanExpression.Constant(false)),
            z to ArgumentExpression.BooleanValue(BooleanExpression.Constant(true)),
          ),
        ),
        site("start"),
      )
    val r = analyze(listOf(initial), extra = listOf(swap))
    assertTrue(
      r.findings.any {
        it.key == metrics && it.certainty == Certainty.MISSING
      } && !r.policyDecision.passed,
    )
  }

  @Test
  fun `recursive calls rebind Canvas actuals in a fresh environment`() {
    val x = ContractParameter("swapCanvas", "x", ParameterKind.CANVAS)
    val y = ContractParameter("swapCanvas", "y", ParameterKind.CANVAS)
    val done = ContractParameter("swapCanvas", "done", ParameterKind.BOOLEAN)
    val recurseCanvas =
      Effect.Call(
        "recurseCanvas",
        "swapCanvas",
        CallArguments(
          mapOf(
            x to ArgumentExpression.Canvas(CanvasExpression.ParameterValue(y)),
            y to ArgumentExpression.Canvas(CanvasExpression.ParameterValue(x)),
            done to ArgumentExpression.BooleanValue(BooleanExpression.Constant(true)),
          ),
        ),
        site("recurseCanvas"),
      )
    val swapCanvas =
      CallableContract(
        "swapCanvas",
        listOf(x, y, done),
        listOf(
          Effect.Branch(
            "done",
            Guard.BooleanParameter(done),
            listOf(lookup("read-original-x", CanvasExpression.ParameterValue(y))),
            listOf(recurseCanvas),
            site("done"),
          ),
        ),
        site("swapCanvas"),
      )
    val initialCanvas =
      Effect.Call(
        "startCanvas",
        "swapCanvas",
        CallArguments(
          mapOf(
            x to ArgumentExpression.Canvas(empty),
            y to ArgumentExpression.Canvas(full),
            done to ArgumentExpression.BooleanValue(BooleanExpression.Constant(false)),
          ),
        ),
        site("startCanvas"),
      )
    val r = analyze(listOf(initialCanvas), extra = listOf(swapCanvas))
    assertTrue(
      r.findings.any {
        it.key == metrics && it.certainty == Certainty.MISSING
      } && !r.policyDecision.passed,
    )
  }

  @Test
  fun `unused unknown root inputs do not create obligations`() {
    val unused = ContractParameter("entry", "canvas", ParameterKind.CANVAS)
    val result = analyze(emptyList(), parameters = listOf(unused))
    assertTrue(result.policyDecision.passed)
    assertTrue(result.findings.isEmpty())
  }

  @Test
  fun `an allocated alias is not reconstructed after branch refinement`() {
    val flag = ContractParameter("entry", "flag", ParameterKind.BOOLEAN)
    val alias =
      CanvasExpression.Alias(
        "shared",
        layer("shared", listOf(binding(metrics), binding(service, listOf(lookup("init", kind = LookupKind.PAINT))))),
      )
    val branch =
      Effect.Branch(
        "unrelated",
        Guard.BooleanParameter(flag),
        listOf(lookup("yes", alias)),
        listOf(lookup("no", alias)),
        site("unrelated"),
      )
    val aliasReport =
      analyze(listOf(Effect.ConstructCanvas("let", alias, site("let")), branch), parameters = listOf(flag))
    assertEquals(1, aliasReport.findings.count { it.kind == FindingKind.CONSTRUCTION_LOOKUP })
    val consumers = aliasReport.findings.filter { it.kind == FindingKind.REQUIRED_LOOKUP }
    assertEquals(2, consumers.size)
    assertEquals(setOf(listOf("flag=false"), listOf("flag=true")), consumers.map { it.pathCondition }.toSet())
    assertTrue(aliasReport.policyDecision.passed)
  }
}
