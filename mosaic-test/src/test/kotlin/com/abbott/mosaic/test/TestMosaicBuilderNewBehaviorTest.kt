package com.abbott.mosaic.test

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.MultiTile
import com.abbott.mosaic.SingleTile
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

private class StringSingleTile(mosaic: Mosaic) : SingleTile<String>(mosaic) {
  override suspend fun retrieve(): String = "real"
}

private class IntSingleTile(mosaic: Mosaic) : SingleTile<Int>(mosaic) {
  override suspend fun retrieve(): Int = 1
}

private class StringMultiTile(mosaic: Mosaic) : MultiTile<String, String>(mosaic) {
  override suspend fun retrieveByKeys(keys: List<String>): Map<String, String> = keys.associateWith { "real" }
}

class TestMosaicBuilderNewBehaviorTest {
  @Test
  fun `SingleTile failing throws provided Throwable`() {
    val ex = IllegalStateException("boom")
    val mosaic =
      TestMosaicBuilder()
        .withFailingTile<StringSingleTile, String>(ex)
        .build()

    val tile = mosaic.getTile<StringSingleTile>()
    val thrown =
      assertThrows(IllegalStateException::class.java) {
        runBlocking { tile.get() }
      }
    assertEquals("boom", thrown.message)
  }

  @Test
  fun `MultiTile failing throws provided Throwable`() {
    val ex = IllegalArgumentException("bad")
    val mosaic =
      TestMosaicBuilder()
        .withFailingMultiTile<StringMultiTile, String>(ex)
        .build()

    val tile = mosaic.getTile<StringMultiTile>()
    val thrown =
      assertThrows(IllegalArgumentException::class.java) {
        runBlocking { tile.getByKeys(listOf("a")) }
      }
    assertEquals("bad", thrown.message)
  }

  @Test
  fun `SingleTile custom invokes lambda`() =
    runBlocking {
      val mosaic =
        TestMosaicBuilder()
          .withCustomTile<StringSingleTile, String> { "computed" }
          .build()

      val tile = mosaic.getTile<StringSingleTile>()
      assertEquals("computed", tile.get())
    }

  @Test
  fun `MultiTile custom invokes lambda with keys`() =
    runBlocking {
      val mosaic =
        TestMosaicBuilder()
          .withCustomMultiTile<StringMultiTile, String> { keys ->
            keys.associateWith { k -> "v:$k" }
          }
          .build()

      val tile = mosaic.getTile<StringMultiTile>()
      val result = tile.getByKeys(listOf("x", "y", "z"))
      assertEquals(mapOf("x" to "v:x", "y" to "v:y", "z" to "v:z"), result)
    }

  @Test
  fun `SingleTile success returns typed data`() =
    runBlocking {
      val mosaic =
        TestMosaicBuilder()
          .withMockTile<StringSingleTile, String>("hello")
          .build()

      val tile = mosaic.getTile<StringSingleTile>()
      assertEquals("hello", tile.get())
    }

  @Test
  fun `MultiTile delay returns typed data after delay`() =
    runBlocking {
      val data = mapOf("a" to "a1", "b" to "b2")
      val mosaic =
        TestMosaicBuilder()
          .withDelayedMultiTile<StringMultiTile, String>(returnData = data)
          .build()

      val tile = mosaic.getTile<StringMultiTile>()
      val result = tile.getByKeys(listOf("a", "b"))
      assertEquals(mapOf("a" to "a1", "b" to "b2"), result)
    }

  @Test
  fun `SingleTile type-safety compiles with correct R`() {
    val mosaic =
      TestMosaicBuilder()
        .withMockTile<IntSingleTile, Int>(42)
        .build()

    val tile = mosaic.getTile<IntSingleTile>()
    val v = runBlocking { tile.get() }
    assertEquals(42, v)
  }
}
