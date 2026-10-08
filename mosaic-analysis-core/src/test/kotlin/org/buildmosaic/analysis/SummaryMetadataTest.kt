package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("FunctionMaxLength")
class SummaryMetadataTest {
  @Test
  fun `H3 absent required metadata cannot acquire optimistic defaults`() {
    val bytes = encodeFixtureSummary(ModuleContract("sample")).decodeToString()
    for (field in listOf(
      "complete",
      "analysisVersion",
      "contractVersion",
      "compilerVersion",
      "moduleId",
      "integrityHash",
      "sourceSet",
    )) {
      val partial = bytes.replace(Regex("\"$field\":(?:\"[^\"]*\"|true|[0-9]+),?"), "").replace(",}", "}")
      assertFailsWith<IllegalArgumentException>(field) { SummaryCodec.decode(partial.toByteArray()) }
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"payload\":", "\"missingPayload\":").toByteArray())
    }
    val otherCompiler = withIntegrityHash(bytes.replace("2.4.20", "2.2.10"))
    assertEquals("2.2.10", SummaryCodec.decode(otherCompiler.toByteArray()).producer.compilerVersion)
  }

  @Test
  fun `H4 ordered effects actuals and evaluated value references round trip`() {
    val site = SourceLocation("entry", "Sample.kt", 1, 1)
    val second = ContractParameter("callee", "second", ParameterKind.CANVAS)
    val first = ContractParameter("callee", "first", ParameterKind.CANVAS)
    val arguments =
      CallArguments(
        linkedMapOf(
          second to ArgumentExpression.Canvas(CanvasExpression.Empty),
          first to ArgumentExpression.Canvas(CanvasExpression.ValueReference("allocation", site)),
        ),
      )
    val initialize =
      Effect.ConstructCanvas(
        "allocation",
        CanvasExpression.Alias("allocation", CanvasExpression.Empty),
        site,
      )
    val call =
      CanvasExpression.RuntimeCall(
        "callee",
        arguments,
        site,
        receiver = DispatchReceiver.Forwarded,
        virtualDispatch = true,
      )
    val expression = CanvasExpression.WithEffects(listOf(initialize, Effect.Unknown("later", "unknown", site)), call)
    val contract = CanvasContract("entry", result = expression, site = site)
    val module = ModuleContract("ordered", canvases = listOf(contract))
    val restored = SummaryCodec.decode(encodeFixtureSummary(module)).module
    assertEquals(module, restored)
    val plan = restored.canvases.single().result as CanvasExpression.WithEffects
    assertEquals(listOf("allocation", "later"), plan.effects.map { it.id })
    assertEquals(listOf(second, first), (plan.result as CanvasExpression.RuntimeCall).arguments.values.keys.toList())
  }

  @Test
  fun `invalid summaries are rejected`() {
    val bytes = encodeFixtureSummary(ModuleContract("sample")).decodeToString()
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"complete\":true", "\"complete\":false").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(
        bytes.replace("\"contractVersion\":6", "\"contractVersion\":7").toByteArray(),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(
        bytes.replace("\"analysisVersion\":\"fixture-analysis\"", "\"analysisVersion\":\"\"").toByteArray(),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"moduleId\":\"sample\"", "\"moduleId\":\"changed\"").toByteArray())
    }
  }
}
