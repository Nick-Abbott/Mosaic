package org.buildmosaic.core.injection

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.MosaicImpl
import org.buildmosaic.core.RecordingInstrumentation
import org.buildmosaic.core.instrumentation.installInstrumentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class MosaicRuntimeConfigTest {
  @Test
  fun ordinaryLayersShareEmptyRuntimeConfiguration() =
    runTest {
      val parent = canvas { single<String> { "application dependency" } }
      val child = parent.withLayer { single<Int> { 7 } }
      val unrelated = canvas {}
      assertSame(MosaicRuntimeConfig.EMPTY, parent.runtimeConfig)
      assertSame(parent.runtimeConfig, child.runtimeConfig)
      assertSame(parent.runtimeConfig, unrelated.runtimeConfig)
      assertEquals("application dependency", child.source<String>())
      assertEquals(7, child.source<Int>())
      assertNull(child.sourceOr<MosaicRuntimeConfig>())
      assertEquals(MosaicImpl::class, child.create()::class)
    }

  @Test
  fun descendantsShareDurableConfigurationOutsideDi() =
    runTest {
      val provider = RecordingInstrumentation()
      val parent = canvas { installInstrumentation { provider } }
      val child = parent.withLayer {}
      val grandchild = canvas(child) { single<String> { "request dependency" } }
      assertSame(provider, parent.runtimeConfig.instrumentation)
      assertSame(parent.runtimeConfig, child.runtimeConfig)
      assertSame(parent.runtimeConfig, grandchild.runtimeConfig)
      assertNull(grandchild.sourceOr<MosaicRuntimeConfig>())
      assertNull(grandchild.sourceOr<RecordingInstrumentation>())
    }

  @Test
  fun failedInstallationLeavesEmptyRuntimeConfiguration() =
    runTest {
      val ordinary =
        canvas {
          assertFailsWith<IllegalArgumentException> {
            installInstrumentation { throw IllegalArgumentException("setup failed") }
          }
        }
      assertSame(MosaicRuntimeConfig.EMPTY, ordinary.runtimeConfig)
      assertSame(ordinary.runtimeConfig, ordinary.withLayer {}.runtimeConfig)
      assertEquals(MosaicImpl::class, ordinary.create()::class)
    }
}
