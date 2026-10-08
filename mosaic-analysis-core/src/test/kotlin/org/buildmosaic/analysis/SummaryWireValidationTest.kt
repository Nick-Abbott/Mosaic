package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class SummaryWireValidationTest {
  @Test
  fun `unsupported metadata format is rejected with regeneration guidance`() {
    val text = encodeFixtureSummary(ModuleContract("sample")).decodeToString()
    for (format in listOf(4, 999)) {
      val unsupported = text.replace(Regex("\"contractVersion\":[0-9]+"), "\"contractVersion\":$format")
      val error = assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(unsupported.toByteArray()) }
      assertTrue(error.message.orEmpty().contains("Unsupported Mosaic contract version"))
      assertTrue(
        error.message.orEmpty().contains("Regenerate dependency summaries with a compatible Mosaic Analysis version"),
      )
    }
  }

  @Test
  fun `unordered declarations canonicalize while effect sequence remains significant`() {
    val site = SourceLocation("sample", "Sample.kt", 1, 1)
    val first =
      TileContract("a", listOf(Effect.Unknown("first", "reason", site), Effect.Unknown("second", "reason", site)), site)
    val second = TileContract("b", emptyList(), site)
    val ordered = encodeFixtureSummary(ModuleContract("sample", tiles = listOf(first, second)))
    val reverseDeclarations = encodeFixtureSummary(ModuleContract("sample", tiles = listOf(second, first)))
    val reverseEffects =
      encodeFixtureSummary(
        ModuleContract("sample", tiles = listOf(first.copy(effects = first.effects.reversed()), second)),
      )
    assertTrue(ordered.contentEquals(reverseDeclarations))
    assertTrue(!ordered.contentEquals(reverseEffects))
  }

  @Test
  fun `codec round trip preserves representative analyzer findings`() {
    val site = SourceLocation("entry", "Entry.kt", 2, 1)
    val key = CanvasKeyIdentity("example.Required")
    val tile =
      TileContract(
        "tile",
        listOf(Effect.Lookup("lookup", CanvasExpression.Current, Fact.Known(key, site), LookupKind.REQUIRED, site)),
        site,
      )
    val entry =
      CallableContract(
        "entry",
        effects = listOf(Effect.Compose("compose", CanvasExpression.Empty, TileReference.Stable("tile"), site = site)),
        site = site,
      )
    val module = ModuleContract("sample", tiles = listOf(tile), callables = listOf(entry))
    val root = SelectedRoot("root", "entry")
    val before = MosaicAnalyzer().analyze(AnalysisRequest(module, roots = listOf(root)))
    val after =
      MosaicAnalyzer().analyze(
        AnalysisRequest(SummaryCodec.decode(encodeFixtureSummary(module)).module, roots = listOf(root)),
      )
    assertEquals(before, after)
    assertTrue(before.findings.isNotEmpty())
  }

  @Test
  fun `unknown effect variants fail semantic decoding with a valid checksum`() {
    val site = SourceLocation("tile", "Tile.kt", 1, 1)
    val module =
      ModuleContract(
        "sample",
        tiles = listOf(TileContract("tile", listOf(Effect.Unknown("unknown", "reason", site)), site)),
      )
    val text = encodeFixtureSummary(module).decodeToString()
    val mutated = withIntegrityHash(text.replace("\"kind\":\"unknown\"", "\"kind\":\"futureEffect\""))
    val error = assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(mutated.toByteArray()) }
    assertTrue(error.message.orEmpty().contains("futureEffect"), error.message)
  }

  @Test
  fun `checksum corruption and truncated JSON are rejected`() {
    val text = encodeFixtureSummary(ModuleContract("sample")).decodeToString()
    val corrupt = text.replace(Regex("\"integrityHash\":\"[0-9a-f]+\""), "\"integrityHash\":\"invalid\"")
    val error = assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(corrupt.toByteArray()) }
    assertTrue(error.message.orEmpty().contains("integrity hash mismatch"))
    assertFailsWith<IllegalArgumentException> { SummaryCodec.decode(text.dropLast(4).toByteArray()) }
  }
}
