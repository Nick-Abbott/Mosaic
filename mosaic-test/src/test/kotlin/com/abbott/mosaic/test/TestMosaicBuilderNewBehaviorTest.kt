package com.abbott.mosaic.test

import com.abbott.mosaic.Mosaic
import com.abbott.mosaic.SingleTile
import com.abbott.mosaic.MultiTile
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
  fun singleTile_error_throwsProvidedThrowable() {
    val ex = IllegalStateException("boom")
    val mosaic = TestMosaicBuilder()
      .withMockTileError<StringSingleTile, String>(ex)
      .build()

    val tile = mosaic.getTile<StringSingleTile>()
    val thrown = assertThrows(IllegalStateException::class.java) {
      runBlocking { tile.get() }
    }
    assertEquals("boom", thrown.message)
  }

  @Test
  fun multiTile_error_throwsProvidedThrowable() {
    val ex = IllegalArgumentException("bad")
    val mosaic = TestMosaicBuilder()
      .withMockMultiTileError<StringMultiTile, String>(ex)
      .build()

    val tile = mosaic.getTile<StringMultiTile>()
    val thrown = assertThrows(IllegalArgumentException::class.java) {
      runBlocking { tile.getByKeys(listOf("a")) }
    }
    assertEquals("bad", thrown.message)
  }

  @Test
  fun singleTile_custom_invokesLambda() = runBlocking {
    val mosaic = TestMosaicBuilder()
      .withMockTileCustom<StringSingleTile, String> { "computed" }
      .build()

    val tile = mosaic.getTile<StringSingleTile>()
    assertEquals("computed", tile.get())
  }

  @Test
  fun multiTile_custom_invokesLambdaWithKeys() = runBlocking {
    val mosaic = TestMosaicBuilder()
      .withMockMultiTileCustom<StringMultiTile, String> { keys ->
        keys.associateWith { k -> "v:$k" }
      }
      .build()

    val tile = mosaic.getTile<StringMultiTile>()
    val result = tile.getByKeys(listOf("x", "y", "z"))
    assertEquals(mapOf("x" to "v:x", "y" to "v:y", "z" to "v:z"), result)
  }

  @Test
  fun singleTile_success_returnsTypedData() = runBlocking {
    val mosaic = TestMosaicBuilder()
      .withMockTile<StringSingleTile, String>("hello", MockBehavior.SUCCESS)
      .build()

    val tile = mosaic.getTile<StringSingleTile>()
    assertEquals("hello", tile.get())
  }

  @Test
  fun multiTile_delay_returnsTypedDataAfterDelay() = runBlocking {
    val data = mapOf("a" to 1, "b" to 2)
    val mosaic = TestMosaicBuilder()
      .withMockMultiTileDelay<StringMultiTile, String>(returnData = data.mapValues { it.key + it.value })
      .build()

    val tile = mosaic.getTile<StringMultiTile>()
    val result = tile.getByKeys(listOf("a", "b"))
    assertEquals(mapOf("a" to "a1", "b" to "b2"), result)
  }

  @Test
  fun singleTile_typeSafety_compilesWithCorrectR() {
    val mosaic = TestMosaicBuilder()
      .withMockTile<IntSingleTile, Int>(42, MockBehavior.SUCCESS)
      .build()

    val tile = mosaic.getTile<IntSingleTile>()
    val v = runBlocking { tile.get() }
    assertEquals(42, v)
  }
}
