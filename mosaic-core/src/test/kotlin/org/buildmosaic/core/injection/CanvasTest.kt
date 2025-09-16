package org.buildmosaic.core.injection

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
  fun `should test Canvas source with CanvasKey directly`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("direct-key") { TestServiceImpl("direct-test") }
        }

      // Test direct CanvasKey usage
      val key = CanvasKey(TestService::class, "direct-key")
      val service = testCanvas.source(key)

      assertNotNull(service)
      assertEquals("direct-test", service.getValue())
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
  fun `should test CanvasKey toString with and without qualifier`() {
    val keyWithQualifier = CanvasKey(TestService::class, "test-qualifier")
    val keyWithoutQualifier = CanvasKey(TestService::class, null)

    assertEquals(true, keyWithQualifier.toString().contains("test-qualifier"))
    assertEquals(true, keyWithoutQualifier.toString().contains("TestService"))
  }
}
