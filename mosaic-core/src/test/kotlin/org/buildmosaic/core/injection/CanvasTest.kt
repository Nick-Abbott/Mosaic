package org.buildmosaic.core.injection

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.exception.MosaicMissingKeyException
import org.buildmosaic.core.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("FunctionMaxLength")
class CanvasTest {
  // Test interfaces for dependency injection
  interface TestService {
    fun getValue(): String
  }

  interface TestRepository {
    fun getData(): String
  }

  // Simple implementations
  class TestServiceImpl(private val value: String) : TestService {
    override fun getValue(): String = value
  }

  class TestRepositoryImpl(private val data: String) : TestRepository {
    override fun getData(): String = data
  }

  @Test
  fun `should test Canvas withLayer method`() =
    runTest {
      val parentCanvas =
        canvas {
          single<TestService> { TestServiceImpl("parent-service") }
        }

      // Test withLayer method
      val childCanvas =
        parentCanvas.withLayer {
          single<TestRepository> { TestRepositoryImpl("child-repo") }
        }

      // Child should have both parent and child dependencies
      val service = childCanvas.source<TestService>()
      val repository = childCanvas.source<TestRepository>()

      assertNotNull(service)
      assertEquals("parent-service", service.getValue())
      assertNotNull(repository)
      assertEquals("child-repo", repository.getData())
    }

  @Test
  fun `should return a source if requested`() =
    runTest {
      val testValue = TestServiceImpl("direct-test")
      val testCanvas: Canvas =
        canvas {
          single<TestService>("direct-key") { testValue }
        }

      assertEquals(testCanvas.source(TestService::class, "direct-key"), testValue)
      assertEquals(testCanvas.source(CanvasKey(TestService::class, "direct-key")), testValue)

      // Test direct CanvasKey usage
      val key = CanvasKey(TestService::class, "direct-key")
      val service = testCanvas.source(key)

      assertNotNull(service)
      assertEquals("direct-test", service.getValue())
    }

  @Test
  fun `should throw error when source is not found`() =
    runTest {
      val testCanvas: Canvas =
        canvas {
          single<TestService>("direct-key") { TestServiceImpl("direct-test") }
        }

      assertFailsWith(MosaicMissingKeyException::class) { testCanvas.source<String>() }
      assertFailsWith(MosaicMissingKeyException::class) { testCanvas.source(CanvasKey(String::class)) }
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

  @Test
  fun `should test canvas create function`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("create-test") }
        }

      // Test the create() extension function
      val mosaic = testCanvas.create()
      val service = mosaic.source<TestService>()

      assertNotNull(service)
      assertEquals("create-test", service.getValue())
    }

  @Test
  fun `should test CanvasKey toString null qualifier branch`() {
    val key = CanvasKey(TestService::class, null)
    val result = key.toString()
    assertEquals("org.buildmosaic.core.injection.CanvasTest.TestService", result)
  }

  @Test
  fun `should test CanvasKey toString with non-null qualifier`() {
    val key = CanvasKey(TestService::class, "test-qualifier")
    val result = key.toString()
    assertEquals("org.buildmosaic.core.injection.CanvasTest.TestService[test-qualifier]", result)
  }

  @Test
  fun `should test CanvasKey toString with an anonymous class`() {
    class Local

    assertEquals(
      "anonymous[test-qualifier]",
      CanvasKey(Local::class, "test-qualifier").toString(),
    )

    assertEquals(
      "anonymous",
      CanvasKey(Local::class).toString(),
    )
  }
}
