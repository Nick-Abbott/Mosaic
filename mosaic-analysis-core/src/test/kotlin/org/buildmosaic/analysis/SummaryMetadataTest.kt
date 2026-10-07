package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Suppress("FunctionMaxLength")
class SummaryMetadataTest {
  @Test
  fun `absent required metadata cannot acquire optimistic defaults`() {
    val bytes = SummaryCodec.encode(ModuleContract("sample")).decodeToString()
    for (field in listOf(
      "complete",
      "toolVersion",
      "formatVersion",
      "semanticsVersion",
      "moduleId",
      "payloadHash",
      "kotlinCompilerVersion",
      "sourceSet",
    )) {
      val partial = bytes.replace(Regex("\"$field\":(?:\"[^\"]*\"|true|[0-9]+),?"), "").replace(",}", "}")
      assertFailsWith<IllegalArgumentException>(field) { SummaryCodec.decode(partial.toByteArray()) }
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"payload\":", "\"missingPayload\":").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("2.4.20", "2.2.10").toByteArray())
    }
  }

  @Test
  fun `ordered effects actuals and evaluated value references round trip`() {
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
    val restored = SummaryCodec.decode(SummaryCodec.encode(module)).module
    assertEquals(module, restored)
    val plan = restored.canvases.single().result as CanvasExpression.WithEffects
    assertEquals(listOf("allocation", "later"), plan.effects.map { it.id })
    assertEquals(listOf(second, first), (plan.result as CanvasExpression.RuntimeCall).arguments.values.keys.toList())
  }

  @Test
  fun `incomplete incompatible and inconsistent metadata headers are rejected`() {
    val bytes = SummaryCodec.encode(ModuleContract("sample")).decodeToString()
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"complete\":true", "\"complete\":false").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(
        bytes.replace("\"semanticsVersion\":\"analysis-contract-3\"", "\"semanticsVersion\":\"other\"").toByteArray(),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(
        bytes.replace("\"toolVersion\":\"prototype-11\"", "\"toolVersion\":\"\"").toByteArray(),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"moduleId\":\"sample\"", "\"moduleId\":\"changed\"").toByteArray())
    }
  }
}
