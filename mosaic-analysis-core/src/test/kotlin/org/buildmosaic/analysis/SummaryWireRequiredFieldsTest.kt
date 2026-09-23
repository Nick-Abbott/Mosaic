package org.buildmosaic.analysis

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class SummaryWireRequiredFieldsTest {
  @Test
  fun `argument order changes canonical payload`() {
    val site = SourceLocation("call", "Call.kt", 1, 1)
    val first = ContractParameter("target", "first", ParameterKind.CANVAS)
    val second = ContractParameter("target", "second", ParameterKind.CANVAS)

    fun encoded(parameters: List<ContractParameter>): ByteArray {
      val arguments =
        CallArguments(
          parameters.associateWithTo(linkedMapOf()) {
            ArgumentExpression.Canvas(CanvasExpression.Empty)
          },
        )
      val call = Effect.Call("invoke", "target", arguments, site)
      return SummaryCodec.encode(
        ModuleContract("sample", callables = listOf(CallableContract("call", effects = listOf(call), site = site))),
      )
    }
    assertTrue(!encoded(listOf(first, second)).contentEquals(encoded(listOf(second, first))))
  }

  @Test
  fun `bindings targets and effects are required`() {
    val site = SourceLocation("sample", "Sample.kt", 1, 1)
    val layer = CanvasExpression.Layer("layer", CanvasExpression.Empty, site = site)
    val module =
      ModuleContract(
        "sample",
        canvases = listOf(CanvasContract("canvas", result = layer, site = site)),
        tiles = listOf(TileContract("tile", emptyList(), site)),
        callables =
          listOf(
            CallableContract("call", effects = listOf(Effect.Call("invoke", "target", site = site)), site = site),
          ),
      )
    val text = SummaryCodec.encode(module).decodeToString()
    for (missing in listOf(
      text.replace("\"bindings\":[],", ""),
      text.replace("\"effects\":[],", ""),
      text.replace("\"target\":\"target\",", ""),
    )) {
      assertTrue(missing != text)
      assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(missing.toByteArray()) }
    }
  }

  @Test
  fun `metadata rejects absent semantic effects and duplicate actual parameters`() {
    val site = SourceLocation("sample", "Sample.kt", 1, 1)
    val parameter = ContractParameter("call", "canvas", ParameterKind.CANVAS)
    val module =
      ModuleContract(
        "sample",
        callables =
          listOf(
            CallableContract(
              "call",
              effects =
                listOf(
                  Effect.Call(
                    "invoke",
                    "target",
                    CallArguments(linkedMapOf(parameter to ArgumentExpression.Canvas(CanvasExpression.Empty))),
                    site,
                  ),
                ),
              site = site,
            ),
          ),
      )
    val text = SummaryCodec.encode(module).decodeToString()
    val withoutEffects = text.replace(Regex("\"effects\":\\[[^]]*]"), "")
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(withoutEffects.toByteArray()) }
    val argumentEntry =
      """{"parameter":{"owner":"call","name":"canvas","kind":"CANVAS"},"value":""" +
        """{"kind":"canvas","expression":{"kind":"empty"}}}"""
    assertTrue(text.contains(argumentEntry))
    val duplicate = text.replace(argumentEntry, "$argumentEntry,$argumentEntry")
    val error =
      assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(withPayloadHash(duplicate).toByteArray()) }
    assertTrue(error.message.orEmpty().contains("Duplicate argument"))
  }

  private fun withPayloadHash(text: String): String {
    val payload = Json.parseToJsonElement(text).jsonObject.getValue("payload")
    val hash =
      MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(payload).toByteArray())
        .joinToString("") { "%02x".format(it) }
    return text.replace(Regex("\"payloadHash\":\"[0-9a-f]+\""), "\"payloadHash\":\"$hash\"")
  }
}
