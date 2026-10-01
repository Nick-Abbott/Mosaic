package org.buildmosaic.core.injection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CanvasConstructionFailureTest {
  @Test fun failureClosesEarlierAndPaintedLocalResourcesOnce() =
    runTest {
      val closed = mutableListOf<String>()
      val parentResource = Resource { closed.add("parent") }
      val parent = canvas { single { parentResource } }
      val failure = IllegalStateException("construction failed")
      var constructions = 0
      val thrown =
        assertFailsWith<IllegalStateException> {
          parent.withLayer {
            single<Resource>("earlier") { Resource { closed.add("earlier") } }
            single<String> {
              assertSame(parentResource, paint<Resource>())
              assertSame(paint<Resource>("painted"), paint<Resource>("painted"))
              throw failure
            }
            single<Resource>("painted") {
              constructions++
              Resource { closed.add("painted") }
            }
          }
        }
      assertSame(failure, thrown)
      assertEquals(1, constructions)
      assertEquals(listOf("painted", "earlier"), closed)
      parent.close()
      assertEquals(listOf("painted", "earlier", "parent"), closed)
    }

  @Test fun cancellationPreservesFailureAndFinishesCleanup() =
    runTest {
      val closed = mutableListOf<String>()
      val failure = CancellationException("construction cancelled")
      val cleanup = IllegalStateException("close failed")
      val thrown =
        assertFailsWith<CancellationException> {
          canvas {
            single<Resource>("first") { Resource { closed.add("first") } }
            single<Resource>("failing-close") {
              Resource {
                closed.add("failing-close")
                throw cleanup
              }
            }
            single<String> { throw failure }
          }
        }
      assertSame(failure, thrown)
      assertEquals(listOf(cleanup), thrown.suppressed.toList())
      assertEquals(listOf("failing-close", "first"), closed)
    }

  private class Resource(private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
  }
}
