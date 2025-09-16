package org.buildmosaic.core.vtwo

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.vtwo.injection.Canvas
import org.buildmosaic.core.vtwo.injection.CanvasKey
import org.buildmosaic.core.vtwo.injection.source
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

class TileDslTest {
  @Test
  fun singleTileCachesAndInjects() =
    runTest {
      class Service {
        var count = 0
      }
      val service = Service()
      val mockCanvas =
        object : Canvas {
          override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key.type) {
              Service::class -> service as T
              else -> null
            }
          }
        }
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(mockCanvas, testDispatcher)
      val tile =
        singleTile {
          val s = canvas.source<Service>()
          s.count++
          "result"
        }
      val first = mosaic.compose(tile)
      val second = mosaic.compose(tile)
      assertEquals("result", first)
      assertEquals("result", second)
      assertEquals(1, service.count)
    }

  @Test
  fun singleTileDeduplicatesConcurrentRequests() =
    runTest {
      class Service {
        var calls = 0

        suspend fun fetch(): String {
          calls++
          delay(50)
          return "v"
        }
      }
      val service = Service()
      val mockCanvas =
        object : Canvas {
          override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key.type) {
              Service::class -> service as T
              else -> null
            }
          }
        }
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(mockCanvas, testDispatcher)
      val tile =
        singleTile {
          val svc = source<Service>()
          svc.fetch()
        }
      val first = async { mosaic.compose(tile) }
      val second = async { mosaic.compose(tile) }
      assertEquals("v", first.await())
      assertEquals("v", second.await())
      assertEquals(1, service.calls)
    }

  @Test
  fun multiTileBatchesAndCaches() =
    runTest {
      class Service {
        var calls = 0

        suspend fun fetch(ids: Set<String>): Map<String, String> {
          calls++
          return ids.associateWith { "v_$it" }
        }
      }
      val service = Service()
      val mockCanvas =
        object : Canvas {
          override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key.type) {
              Service::class -> service as T
              else -> null
            }
          }
        }
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(mockCanvas, testDispatcher)
      val tile =
        multiTile { ids ->
          val svc = source<Service>()
          svc.fetch(ids)
        }
      val first = mosaic.compose(tile, listOf("a", "b"))
      val second = mosaic.compose(tile, listOf("b", "c"))
      val third = mosaic.compose(tile, "a")
      assertEquals("v_a", first["a"])
      assertEquals("v_b", first["b"])
      assertEquals("v_b", second["b"])
      assertEquals("v_c", second["c"])
      assertEquals("v_a", third)
      assertEquals(2, service.calls)
    }

  @Test
  fun perKeyTileFetchesIndividually() =
    runTest {
      class Service {
        val calls = mutableListOf<String>()

        suspend fun fetch(id: String): String {
          calls += id
          return "v_$id"
        }
      }
      val service = Service()
      val mockCanvas =
        object : Canvas {
          override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key.type) {
              Service::class -> service as T
              else -> null
            }
          }
        }
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(mockCanvas, testDispatcher)
      val tile =
        perKeyTile<String, String> { id ->
          val svc = source<Service>()
          svc.fetch(id)
        }
      val first = mosaic.compose(tile, listOf("a", "b"))
      assertEquals(2, service.calls.size)
      val second = mosaic.compose(tile, listOf("b", "c"))
      assertEquals(3, service.calls.size)
      assertEquals(setOf("a", "b", "c"), service.calls.toSet())
      assertEquals("v_a", first["a"])
      assertEquals("v_b", first["b"])
      assertEquals("v_b", second["b"])
      assertEquals("v_c", second["c"])
    }

  @Test
  fun chunkedMultiTileSplitsBatchesInParallel() =
    runTest {
      class Service(private val scope: TestScope) {
        val batches = mutableListOf<List<String>>()
        val starts = mutableListOf<Long>()

        suspend fun fetch(ids: List<String>): Map<String, String> {
          batches += ids.toList()
          @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
          starts += scope.currentTime
          delay(10)
          return ids.associateWith { "v_$it" }
        }
      }
      val service = Service(this)
      val mockCanvas =
        object : Canvas {
          override fun <T : Any> sourceOr(key: CanvasKey<T>): T? {
            @Suppress("UNCHECKED_CAST")
            return when (key.type) {
              Service::class -> service as T
              else -> null
            }
          }
        }
      val testDispatcher = StandardTestDispatcher(testScheduler)
      val mosaic = MosaicImpl(mockCanvas, testDispatcher)
      val tile =
        chunkedMultiTile<String, String>(2) { ids ->
          val svc = source<Service>()
          svc.fetch(ids)
        }
      val first = mosaic.compose(tile, listOf("a", "b", "c", "d"))
      assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), service.batches)
      assertEquals(listOf(0L, 0L), service.starts)
      val second = mosaic.compose(tile, listOf("c", "e"))
      assertEquals(
        listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")),
        service.batches,
      )
      assertEquals("v_a", first["a"])
      assertEquals("v_b", first["b"])
      assertEquals("v_c", first["c"])
      assertEquals("v_d", first["d"])
      assertEquals("v_c", second["c"])
      assertEquals("v_e", second["e"])
    }
}
