package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

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
      SummaryCodec.decode(bytes.replace("\"sample\"", "\"changed\"").toByteArray())
    }
  }
}
