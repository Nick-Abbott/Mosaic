package org.buildmosaic.core.injection

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class InstanceBindingTest {
  private class Resource : AutoCloseable {
    var closes = 0

    override fun close() {
      closes++
    }
  }

  @Test fun borrowedValuesResolveDuringConstruction() =
    runTest {
      val ordinary = Any()
      val qualified = Any()
      val keyed = Any()
      val key = CanvasKey(Any::class, "keyed")
      val canvas =
        canvas {
          single<List<Any>> { listOf(paint<Any>(), paint<Any>("primary"), paint(key)) }
          instance(ordinary)
          instance(qualified, qualifier = "primary")
          instance(key, keyed)
        }
      assertSame(ordinary, canvas.source<Any>())
      assertSame(qualified, canvas.source(Any::class, "primary"))
      assertSame(keyed, canvas.source(key))
      assertEquals(listOf(ordinary, qualified, keyed), canvas.source<List<Any>>())
    }

  @Test fun onlyOwnedResourcesAreClosed() =
    runTest {
      val borrowed = Resource()
      val owned = Resource()
      canvas {
        instance(borrowed)
        single("owned") { owned }
      }.close()
      assertEquals(0, borrowed.closes)
      assertEquals(1, owned.closes)
    }

  @Test fun rollbackOnlyClosesConstructedOwnedResources() =
    runTest {
      val borrowed = Resource()
      val owned = Resource()
      assertFailsWith<IllegalStateException> {
        canvas {
          instance(borrowed)
          single("owned") { owned }
          single<String> { error("construction failed") }
        }
      }
      assertEquals(0, borrowed.closes)
      assertEquals(1, owned.closes)
    }

  @Test fun duplicateInstancesAndMixedBindingsAreRejected() =
    runTest {
      val key = CanvasKey(Any::class)
      assertFailsWith<IllegalStateException> {
        canvas {
          instance(Any())
          instance(Any())
        }
      }
      assertFailsWith<IllegalStateException> {
        canvas {
          single(key) { Any() }
          instance(key, Any())
        }
      }
      assertFailsWith<IllegalStateException> {
        canvas {
          instance(key, Any())
          single(key) { Any() }
        }
      }
    }

  @Test fun childOverridesAndParentFallbackPreserveOwnership() =
    runTest {
      val inherited = Resource()
      val override = Resource()
      val parentOnly = Resource()
      val parent =
        canvas {
          instance(inherited)
          instance(parentOnly, "parent")
        }
      val child =
        parent.withLayer {
          instance(override)
          single<List<Resource>> { listOf(paint(), paint("parent")) }
        }
      assertSame(override, child.source<Resource>())
      assertSame(inherited, parent.source<Resource>())
      assertSame(parentOnly, child.source(Resource::class, "parent"))
      assertEquals(listOf(override, parentOnly), child.source<List<Resource>>())
      child.close()
      parent.close()
      assertEquals(listOf(0, 0, 0), listOf(inherited.closes, override.closes, parentOnly.closes))
    }
}
