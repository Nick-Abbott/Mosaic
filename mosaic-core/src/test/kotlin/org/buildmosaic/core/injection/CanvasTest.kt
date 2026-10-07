package org.buildmosaic.core.injection

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.exception.MosaicMissingKeyException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Suppress("FunctionMaxLength")
class CanvasTest {
  // Test interfaces for dependency injection
  interface TestService {
    fun getValue(): String
  }

  // Simple implementations
  class TestServiceImpl(private val value: String) : TestService {
    override fun getValue(): String = value
  }

  @Test
  fun `required and optional source overloads return the registered instance`() =
    runTest {
      val value = TestServiceImpl("direct")
      val key = CanvasKey(TestService::class, "direct-key")
      val testCanvas =
        canvas {
          single(key) { value }
          single<TestService> { value }
        }
      assertSame(value, testCanvas.source(TestService::class, "direct-key"))
      assertSame(value, testCanvas.source(key))
      assertSame(value, testCanvas.source<TestService>())
      assertSame(value, testCanvas.sourceOr(key))
      assertSame(value, testCanvas.sourceOr<TestService>())
    }

  @Test
  fun `required source failures identify the requested type and qualifier`() =
    runTest {
      val testCanvas = canvas {}
      val key = CanvasKey(TestService::class, "missing")
      val failure = assertFailsWith<MosaicMissingKeyException> { testCanvas.source(key) }
      assertEquals(key, failure.key)
      assertTrue(failure.message.orEmpty().contains("TestService"))
      assertTrue(failure.message.orEmpty().contains("missing"))
      assertFailsWith<MosaicMissingKeyException> { testCanvas.source<String>() }
      assertFailsWith<MosaicMissingKeyException> { testCanvas.source(String::class) }
    }

  @Test
  fun `should return null when source is not found in sourceOr`() =
    runTest {
      val testCanvas: Canvas =
        canvas {
          single<TestService>("direct-key") { TestServiceImpl("direct-test") }
        }

      assertNull(testCanvas.sourceOr<String>())
      assertNull(testCanvas.sourceOr(CanvasKey(String::class)))
    }
}
