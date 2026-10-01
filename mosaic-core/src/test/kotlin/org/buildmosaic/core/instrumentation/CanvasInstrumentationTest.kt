package org.buildmosaic.core.instrumentation

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.RecordingInstrumentation
import org.buildmosaic.core.injection.Canvas
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.core.injection.runtimeConfig
import org.buildmosaic.core.injection.sourceOr
import org.buildmosaic.core.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CanvasInstrumentationTest {
  @Test
  fun providerIsInheritedOutsideApplicationBindings() =
    runTest {
      val provider = RecordingInstrumentation()
      var installations = 0
      val applicationCanvas: Canvas =
        canvas {
          installInstrumentation {
            installations++
            provider
          }
        }
      val child = applicationCanvas.withLayer { single<String> { "request" } }
      val requestCanvas = canvas(child) {}
      assertSame(provider, requestCanvas.runtimeConfig.instrumentation)
      assertNull(requestCanvas.sourceOr<MosaicInstrumentation>())
      assertNull(requestCanvas.sourceOr<RecordingInstrumentation>())
      val work by singleTile { 7 }
      assertEquals(7, requestCanvas.create().compose(work))
      assertEquals(7, requestCanvas.create().compose(work))
      assertEquals(1, installations)
      assertEquals(listOf("work", "work"), provider.executions.map { it.identity.name })
    }

  @Test
  fun ordinaryDiBindingDoesNotEnableInstrumentation() =
    runTest {
      val ordinary = canvas {}
      val provider = RecordingInstrumentation()
      val applicationBinding = canvas { single<MosaicInstrumentation> { provider } }
      assertNull(ordinary.runtimeConfig.instrumentation)
      assertNull(applicationBinding.runtimeConfig.instrumentation)
      assertEquals(MosaicImpl::class, ordinary.create()::class)
      val mosaic = applicationBinding.create()
      assertEquals(MosaicImpl::class, mosaic::class)
      assertEquals(7, mosaic.compose(singleTile { 7 }))
      assertTrue(provider.executions.isEmpty())
    }

  @Test
  fun duplicatesFailBeforeProviderSetup() =
    runTest {
      val provider = RecordingInstrumentation()
      val duplicate =
        assertFailsWith<IllegalStateException> {
          canvas {
            installInstrumentation { provider }
            installInstrumentation { error("Duplicate provider must not be constructed") }
          }
        }
      assertTrue(duplicate.message.orEmpty().contains("already configured"))
      val parent = canvas { installInstrumentation { provider } }
      val inherited =
        assertFailsWith<IllegalStateException> {
          parent.withLayer {
            installInstrumentation { error("Inherited provider must not be replaced") }
          }
        }
      assertEquals(duplicate.message, inherited.message)
      assertSame(provider, parent.runtimeConfig.instrumentation)
    }

  @Test
  fun failedSetupCanRetryAndCanvasDoesNotOwnShutdown() =
    runTest {
      val failure = IllegalArgumentException("provider setup failed")
      var closed = false
      val provider =
        object : MosaicInstrumentation by RecordingInstrumentation(), AutoCloseable {
          override fun close() {
            closed = true
          }
        }
      canvas {
        assertSame(failure, assertFailsWith<IllegalArgumentException> { installInstrumentation { throw failure } })
        installInstrumentation { provider }
      }.use { parent ->
        parent.withLayer {}.close()
        assertSame(provider, parent.runtimeConfig.instrumentation)
      }
      assertTrue(!closed)
    }

  @Test
  fun reentrantInstallationFailsClearly() =
    runTest {
      val provider = RecordingInstrumentation()
      val configured =
        canvas {
          val failure =
            assertFailsWith<IllegalStateException> {
              installInstrumentation {
                installInstrumentation { error("Reentrant provider must not be resolved") }
                error("Outer provider must fail")
              }
            }
          assertTrue(failure.message.orEmpty().contains("already configured"))
          installInstrumentation { provider }
        }
      assertSame(provider, configured.runtimeConfig.instrumentation)
    }
}
