package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SourceShardTest {
  private val site = SourceLocation("fixture.Tile", "Foo.kt", 1, 1)

  @Test
  fun `shards assemble in stable order`() {
    val first =
      SourceShard(
        "north/Foo.kt",
        ModuleContract(
          "fixture:app",
          tiles =
            listOf(
              TileContract(
                "fixture.NorthTile",
                listOf(
                  Effect.Unknown("first", "first", site),
                  Effect.Unknown("second", "second", site),
                ),
                site,
              ),
            ),
        ),
        binaryLocators = mapOf("fixture.NorthTile" to "north/FooKt"),
      )
    val second =
      SourceShard(
        "south/Foo.kt",
        ModuleContract(
          "fixture:app",
          tiles =
            listOf(
              TileContract("fixture.SouthTile", emptyList(), site),
            ),
        ),
      )
    assertEquals(first, SourceShardCodec.decode(SourceShardCodec.encode(first)))
    val forward = SourceShardCodec.assemble("fixture:app", listOf(first, second))
    assertContentEquals(forward, SourceShardCodec.assemble("fixture:app", listOf(second, first)))
    val assembled = SummaryCodec.decode(forward)
    assertEquals(
      listOf("first", "second"),
      assembled.module.tiles.single { it.id == "fixture.NorthTile" }
        .effects.filterIsInstance<Effect.Unknown>().map { it.id },
    )
    assertEquals(2, assembled.module.tiles.size)
  }

  @Test
  fun `duplicate owners and malformed shards fail`() {
    val owner = TileContract("fixture.Tile", emptyList(), site)
    val shard = SourceShard("One.kt", ModuleContract("fixture:app", tiles = listOf(owner)))
    assertFailsWith<IllegalArgumentException> {
      SourceShardCodec.assemble("fixture:app", listOf(shard, shard.copy(sourceId = "Two.kt")))
    }
    assertFailsWith<Exception> { SourceShardCodec.decode("{malformed".toByteArray()) }
    assertFailsWith<IllegalArgumentException> {
      SourceShardCodec.encode(shard.copy(sourceId = "../outside.kt"))
    }
  }
}
