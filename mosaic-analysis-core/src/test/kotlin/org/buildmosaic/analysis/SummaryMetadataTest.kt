package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class SummaryMetadataTest {
  @Test
  fun `summary round trips deterministically`() {
    val site = SourceLocation("sample.tile", "Tiles.kt", 3, 4)
    val module =
      ModuleContract(
        "sample",
        tiles =
          listOf(
            TileContract(
              "sample.tile",
              listOf(
                Effect.Lookup(
                  "lookup",
                  CanvasExpression.Current,
                  Fact.Known(CanvasKeyIdentity("sample.Metrics")),
                  LookupKind.REQUIRED,
                  site,
                ),
              ),
              site,
            ),
          ),
      )
    val bytes = SummaryCodec.encode(module)
    assertTrue(bytes.decodeToString().contains("\"complete\":true"))
    assertEquals(module, SummaryCodec.decode(bytes).module)
    assertTrue(bytes.contentEquals(SummaryCodec.encode(module)))
  }

  @Test
  fun `invalid summaries are rejected`() {
    val bytes = SummaryCodec.encode(ModuleContract("sample")).decodeToString()
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode("broken".toByteArray()) }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"complete\":true", "\"complete\":false").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"schemaMajor\":1", "\"schemaMajor\":2").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"schemaMinor\":1", "\"schemaMinor\":0").toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(
        bytes.replace("\"toolVersion\":\"prototype-4\"", "\"toolVersion\":\"prototype-3\"").toByteArray(),
      )
    }
    assertFailsWith<IllegalArgumentException> {
      SummaryCodec.decode(bytes.replace("\"sample\"", "\"changed\"").toByteArray())
    }
  }

  @Test
  fun `override slot and receiver provenance survive binary metadata`() {
    val site = SourceLocation("sample", "Sample.kt", 1, 1)
    val base = ContractParameter("Base.respond", "left", ParameterKind.CANVAS)
    val implementation = ContractParameter("Impl.respond", "right", ParameterKind.CANVAS)
    val module =
      ModuleContract(
        "sample",
        callables =
          listOf(
            CallableContract(
              "Base.handle",
              effects =
                listOf(
                  Effect.Call(
                    "hook",
                    "Base.respond",
                    site = site,
                    receiver = DispatchReceiver.Forwarded,
                    virtualDispatch = true,
                  ),
                ),
              site = site,
            ),
          ),
        overrides =
          listOf(
            ResolvedOverride("Impl", "Base.respond", "Impl.respond", listOf(OverrideSlot(base, implementation, 0))),
          ),
      )
    assertEquals(module, SummaryCodec.decode(SummaryCodec.encode(module)).module)
  }
}
