package org.buildmosaic.core.injection

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.exception.MosaicMissingKeyException
import org.buildmosaic.core.source
import org.buildmosaic.core.sourceOr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Suppress("LargeClass", "FunctionMaxLength")
class MosaicCanvasTest {
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
  fun `should register and retrieve a single dependency`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("test-value") }
        }

      val service = testCanvas.source<TestService>()
      assertNotNull(service)
      assertEquals("test-value", service.getValue())
    }

  @Test
  fun `should register and retrieve multiple different dependencies`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("service-value") }
          single<TestRepository> { TestRepositoryImpl("repo-data") }
        }

      val service = testCanvas.source<TestService>()
      val repository = testCanvas.source<TestRepository>()

      assertNotNull(service)
      assertNotNull(repository)
      assertEquals("service-value", service.getValue())
      assertEquals("repo-data", repository.getData())
    }

  @Test
  fun `should handle qualified dependencies`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("primary") { TestServiceImpl("primary-service") }
          single<TestService>("secondary") { TestServiceImpl("secondary-service") }
        }

      val primaryService = testCanvas.source(TestService::class, "primary")
      val secondaryService = testCanvas.source(TestService::class, "secondary")

      assertNotNull(primaryService)
      assertNotNull(secondaryService)
      assertEquals("primary-service", primaryService.getValue())
      assertEquals("secondary-service", secondaryService.getValue())
    }

  @Test
  fun `should return null for unregistered dependency`() =
    runTest {
      val testCanvas = canvas { }

      val service = testCanvas.sourceOr(CanvasKey(TestService::class, null))
      assertNull(service)
    }

  @Test
  fun `should return same instance for singleton dependencies`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("singleton-test") }
        }

      val service1 = testCanvas.source<TestService>()
      val service2 = testCanvas.source<TestService>()

      assertNotNull(service1)
      assertNotNull(service2)
      assertEquals(service1, service2) // Same instance
    }

  @Test
  fun `should support dependency injection in lambda`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestRepository> { TestRepositoryImpl("injected-data") }
          single<TestService> {
            val repo = paint<TestRepository>()
            TestServiceImpl("service-with-${repo.getData()}")
          }
        }

      val service = testCanvas.source<TestService>()
      assertNotNull(service)
      assertEquals("service-with-injected-data", service.getValue())
    }

  // Hierarchical resolution tests
  @Test
  fun `should fallback to parent canvas for missing dependencies`() =
    runTest {
      val parentCanvas =
        canvas {
          single<TestService> { TestServiceImpl("parent-service") }
        }

      val childCanvas =
        canvas(parentCanvas) {
          single<TestRepository> { TestRepositoryImpl("child-repo") }
        }

      val service = childCanvas.source<TestService>()
      val repository = childCanvas.source<TestRepository>()

      assertNotNull(service)
      assertNotNull(repository)
      assertEquals("parent-service", service.getValue())
      assertEquals("child-repo", repository.getData())
    }

  @Test
  fun `should override parent dependencies in child canvas`() =
    runTest {
      val parentCanvas =
        canvas {
          single<TestService> { TestServiceImpl("parent-service") }
          single<TestRepository> { TestRepositoryImpl("parent-repo") }
        }

      val childCanvas =
        canvas(parentCanvas) {
          single<TestService> { TestServiceImpl("child-service") }
        }

      val service = childCanvas.source<TestService>()
      val repository = childCanvas.source<TestRepository>()

      assertNotNull(service)
      assertNotNull(repository)
      assertEquals("child-service", service.getValue()) // Overridden
      assertEquals("parent-repo", repository.getData()) // From parent
    }

  @Test
  fun `should handle multi-level hierarchy`() =
    runTest {
      val grandparentCanvas =
        canvas {
          single<TestService> { TestServiceImpl("grandparent-service") }
        }

      val parentCanvas =
        canvas(grandparentCanvas) {
          single<TestRepository> { TestRepositoryImpl("parent-repo") }
        }

      val childCanvas =
        canvas(parentCanvas) {
          // No dependencies registered
        }

      val service = childCanvas.source<TestService>()
      val repository = childCanvas.source<TestRepository>()

      assertNotNull(service)
      assertNotNull(repository)
      assertEquals("grandparent-service", service.getValue())
      assertEquals("parent-repo", repository.getData())
    }

  @Test
  fun `should return null when dependency not found in hierarchy`() =
    runTest {
      val parentCanvas =
        canvas {
          single<TestRepository> { TestRepositoryImpl("parent-repo") }
        }

      val childCanvas =
        canvas(parentCanvas) {
          // No TestService registered anywhere
        }

      val service = childCanvas.sourceOr<TestService>()
      val repository = childCanvas.sourceOr<TestRepository>()

      assertNull(service)
      assertNotNull(repository)
      assertEquals("parent-repo", repository.getData())
    }

  // Lifecycle management tests
  class CloseableTestService(private val value: String) : TestService, AutoCloseable {
    var isClosed = false
      private set

    override fun getValue(): String = value

    override fun close() {
      isClosed = true
    }
  }

  class CloseableTestRepository(private val data: String) : TestRepository, AutoCloseable {
    var isClosed = false
      private set

    override fun getData(): String = data

    override fun close() {
      isClosed = true
    }
  }

  @Test
  fun `should close AutoCloseable dependencies when canvas is closed`() =
    runTest {
      val closeableService = CloseableTestService("closeable-service")
      val closeableRepo = CloseableTestRepository("closeable-repo")

      val testCanvas =
        canvas {
          single<TestService> { closeableService }
          single<TestRepository> { closeableRepo }
        }

      // Verify dependencies work normally
      val service = testCanvas.source<TestService>()
      val repository = testCanvas.source<TestRepository>()

      assertNotNull(service)
      assertNotNull(repository)
      assertEquals("closeable-service", service.getValue())
      assertEquals("closeable-repo", repository.getData())

      // Verify not closed yet
      assertEquals(false, closeableService.isClosed)
      assertEquals(false, closeableRepo.isClosed)

      // Close canvas
      testCanvas.close()

      // Verify dependencies are closed
      assertEquals(true, closeableService.isClosed)
      assertEquals(true, closeableRepo.isClosed)
    }

  @Test
  fun `should handle close errors gracefully`() =
    runTest {
      class FailingCloseableService : TestService, AutoCloseable {
        override fun getValue(): String = "failing-service"

        override fun close() {
          throw RuntimeException("Close failed!")
        }
      }

      val normalCloseable = CloseableTestService("normal-service")
      val failingCloseable = FailingCloseableService()

      val testCanvas =
        canvas {
          single<TestService>("normal") { normalCloseable }
          single<TestService>("failing") { failingCloseable }
        }

      // Verify dependencies work
      val normalService = testCanvas.source(TestService::class, "normal")
      val failingService = testCanvas.source(TestService::class, "failing")

      assertNotNull(normalService)
      assertNotNull(failingService)

      // Close should not throw even if one dependency fails to close
      testCanvas.close()

      // Normal dependency should still be closed
      assertEquals(true, normalCloseable.isClosed)
    }

  @Test
  fun `should not close non-AutoCloseable dependencies`() =
    runTest {
      val regularService = TestServiceImpl("regular-service")
      val closeableService = CloseableTestService("closeable-service")

      val testCanvas =
        canvas {
          single<TestService>("regular") { regularService }
          single<TestService>("closeable") { closeableService }
        }

      val service1 = testCanvas.source(TestService::class, "regular")
      val service2 = testCanvas.source(TestService::class, "closeable")

      assertNotNull(service1)
      assertNotNull(service2)

      testCanvas.close()

      // Only the closeable one should be closed
      assertEquals(true, closeableService.isClosed)
    }

  // Canvas DSL and suspend function tests
  @Test
  fun `should support suspend functions in dependency constructors`() =
    runTest {
      suspend fun createAsyncService(): TestService {
        delay(10) // Simulate async work
        return TestServiceImpl("async-service")
      }

      val testCanvas =
        canvas {
          single<TestService> { createAsyncService() }
        }

      val service = testCanvas.source<TestService>()
      assertNotNull(service)
      assertEquals("async-service", service.getValue())
    }

  @Test
  fun `should support withLayer for temporary dependency overrides`() =
    runTest {
      val baseCanvas =
        canvas {
          single<TestService> { TestServiceImpl("base-service") }
          single<TestRepository> { TestRepositoryImpl("base-repo") }
        }

      val layeredCanvas =
        baseCanvas.withLayer {
          single<TestService> { TestServiceImpl("layered-service") }
        }

      // Base canvas should remain unchanged
      val baseService = baseCanvas.source<TestService>()
      val baseRepo = baseCanvas.source<TestRepository>()
      assertEquals("base-service", baseService.getValue())
      assertEquals("base-repo", baseRepo.getData())

      // Layered canvas should have override
      val layeredService = layeredCanvas.source<TestService>()
      val layeredRepo = layeredCanvas.source<TestRepository>()
      assertEquals("layered-service", layeredService.getValue())
      assertEquals("base-repo", layeredRepo.getData()) // Inherited from base
    }

  @Test
  fun `should prevent duplicate bindings for both qualified and unqualified dependencies`() =
    runTest {
      assertFailsWith<IllegalStateException> {
        canvas {
          single<TestService> { TestServiceImpl("first") }
          single<TestService> { TestServiceImpl("duplicate") }
        }
      }

      assertFailsWith<IllegalStateException> {
        canvas {
          single(CanvasKey(TestService::class)) { TestServiceImpl("first") }
          single(CanvasKey(TestService::class)) { TestServiceImpl("duplicate") }
        }
      }

      assertFailsWith<IllegalStateException> {
        canvas {
          single<TestService>(null) { TestServiceImpl("first") }
          single<TestService>(null) { TestServiceImpl("duplicate") }
        }
      }

      assertFailsWith<IllegalStateException> {
        canvas {
          single<TestService>("same-qualifier") { TestServiceImpl("first") }
          single<TestService>("same-qualifier") { TestServiceImpl("duplicate") }
        }
      }

      assertFailsWith<IllegalStateException> {
        canvas {
          single(CanvasKey(TestService::class, "same-qualifier")) { TestServiceImpl("first") }
          single(CanvasKey(TestService::class, "same-qualifier")) { TestServiceImpl("duplicate") }
        }
      }
    }

  @Test
  fun `should allow same type with different qualifiers`() =
    runTest {
      canvas {
        single<TestService>("first") { TestServiceImpl("first-service") }
        single<TestService>("second") { TestServiceImpl("second-service") }
      }

      canvas {
        single(CanvasKey(TestService::class, "first")) { TestServiceImpl("first-service") }
        single(CanvasKey(TestService::class, "second")) { TestServiceImpl("second-service") }
      }

      canvas {
        single<TestService>("first") { TestServiceImpl("first-service") }
        single<TestService> { TestServiceImpl("second-service") }
      }

      canvas {
        single<TestService>("first") { TestServiceImpl("first-service") }
        single<TestService>(null) { TestServiceImpl("second-service") }
      }

      canvas {
        single(CanvasKey(TestService::class, "first")) { TestServiceImpl("first-service") }
        single(CanvasKey(TestService::class)) { TestServiceImpl("second-service") }
      }

      canvas {
        single<TestService> { TestServiceImpl("first-service") }
        single<TestService>("second") { TestServiceImpl("second-service") }
      }

      canvas {
        single<TestService>(null) { TestServiceImpl("first-service") }
        single<TestService>("second") { TestServiceImpl("second-service") }
      }

      canvas {
        single(CanvasKey(TestService::class)) { TestServiceImpl("first-service") }
        single(CanvasKey(TestService::class, "second")) { TestServiceImpl("second-service") }
      }
    }

  // Test interfaces for complex dependency graph
  interface DatabaseConfig {
    fun getUrl(): String
  }

  class DatabaseConfigImpl(private val url: String) : DatabaseConfig {
    override fun getUrl(): String = url
  }

  class DatabaseService(private val config: DatabaseConfig) : TestRepository {
    override fun getData(): String = "data-from-${config.getUrl()}"
  }

  class BusinessService(
    private val repository: TestRepository,
    private val config: DatabaseConfig,
  ) : TestService {
    override fun getValue(): String = "business-logic-${repository.getData()}-${config.getUrl()}"
  }

  @Test
  fun `should handle complex dependency graphs`() =
    runTest {
      val testCanvas =
        canvas {
          single<DatabaseConfig> { DatabaseConfigImpl("localhost:5432") }
          single<TestRepository> {
            val config = paint<DatabaseConfig>()
            DatabaseService(config)
          }
          single<TestService> {
            val repo = paint<TestRepository>()
            val config = paint<DatabaseConfig>()
            BusinessService(repo, config)
          }
        }

      val service = testCanvas.source<TestService>()
      assertNotNull(service)
      assertEquals("business-logic-data-from-localhost:5432-localhost:5432", service.getValue())
    }

  // Error handling and edge case tests
  @Test
  fun `should handle missing dependencies and throw appropriate exceptions`() =
    runTest {
      // Test missing unqualified dependency
      val emptyCanvas = canvas { }
      var exceptionThrown = false
      try {
        emptyCanvas.source<TestService>()
      } catch (e: MosaicMissingKeyException) {
        exceptionThrown = true
        assertEquals(true, e.message?.contains("TestService"))
      }
      assertEquals(true, exceptionThrown)

      // Test missing qualified dependency
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("unqualified") }
        }

      exceptionThrown = false
      try {
        testCanvas.source(TestService::class, "missing-qualifier")
      } catch (e: MosaicMissingKeyException) {
        exceptionThrown = true
        assertEquals(true, e.message?.contains("missing-qualifier"))
      }
      assertEquals(true, exceptionThrown)
    }

  @Test
  fun `should handle circular dependencies gracefully`() =
    runTest {
      assertFailsWith<IllegalStateException> {
        canvas {
          single<TestService> {
            val repo = paint<TestRepository>()
            TestServiceImpl("service-${repo.getData()}")
          }
          single<TestRepository> {
            val service = paint<TestService>()
            TestRepositoryImpl("repo-${service.getValue()}")
          }
        }
      }
    }

  @Test
  fun `should test convenience extension functions`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("extension-test") }
          single<TestRepository> { TestRepositoryImpl("repo-extension") }
        }

      // Test reified extension functions
      val service = testCanvas.source<TestService>()
      val repository = testCanvas.sourceOr<TestRepository>()
      val missing = testCanvas.sourceOr<String>()

      assertNotNull(service)
      assertEquals("extension-test", service.getValue())
      assertNotNull(repository)
      assertEquals("repo-extension", repository.getData())
      assertNull(missing)
    }

  @Test
  fun `should test Mosaic extension functions`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("mosaic-service") { TestServiceImpl("mosaic-test") }
          single<TestRepository> { TestRepositoryImpl("default-repo") }
        }

      val mosaic = testCanvas.create()

      // Test Mosaic extension functions
      val service = mosaic.source<TestService>("mosaic-service")
      val defaultRepo = mosaic.sourceOr<TestRepository>()
      val missing = mosaic.sourceOr<String>()
      val missingWithKey = mosaic.sourceOr(CanvasKey(TestService::class, "missing"))

      assertNotNull(service)
      assertEquals("mosaic-test", service.getValue())
      assertNotNull(defaultRepo)
      assertEquals("default-repo", defaultRepo.getData())
      assertNull(missing)
      assertNull(missingWithKey)
    }

  @Test
  fun `should handle canvas with only qualified dependencies`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("qualified") { TestServiceImpl("qualified-service") }
        }

      // Unqualified lookup should return null
      val unqualifiedService = testCanvas.sourceOr<TestService>()
      assertNull(unqualifiedService)

      // Qualified lookup should work
      val qualifiedService = testCanvas.source(TestService::class, "qualified")
      assertNotNull(qualifiedService)
      assertEquals("qualified-service", qualifiedService.getValue())
    }

  @Test
  fun `should handle exceptions in dependency constructors`() =
    runTest {
      var exceptionThrown = false
      try {
        canvas {
          single<TestService> {
            throw RuntimeException("Constructor failed!")
          }
        }
      } catch (e: RuntimeException) {
        exceptionThrown = true
        assertEquals("Constructor failed!", e.message)
      }
      assertEquals(true, exceptionThrown)
    }

  @Test
  fun `should handle null qualifiers consistently`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>(null) { TestServiceImpl("null-qualifier") }
          single<TestRepository> { TestRepositoryImpl("no-qualifier") }
        }

      val service1 = testCanvas.source<TestService>()
      val service2 = testCanvas.source(TestService::class, null)
      val repository = testCanvas.source<TestRepository>()

      assertNotNull(service1)
      assertNotNull(service2)
      assertNotNull(repository)
      assertEquals(service1, service2) // Should be the same instance
      assertEquals("null-qualifier", service1.getValue())
      assertEquals("no-qualifier", repository.getData())
    }

  @Test
  fun `should maintain thread safety for singleton access`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService> { TestServiceImpl("thread-safe-service") }
        }

      // Simulate concurrent access
      val results =
        coroutineScope {
          (1..10).map {
            async {
              testCanvas.source<TestService>()
            }
          }.awaitAll()
        }

      // All results should be the same instance
      results.forEach { result ->
        assertEquals(results.first(), result)
      }
    }

  @Test
  fun `should test CanvasBuilder single method edge cases`() =
    runTest {
      val testCanvas =
        canvas {
          // Test single with null qualifier (should use default)
          single<TestService>(null) { TestServiceImpl("null-qualifier") }

          // Test single with empty qualifier (should be treated as qualifier)
          single<TestRepository>("") { TestRepositoryImpl("empty-qualifier") }
        }

      // Verify both work
      val service = testCanvas.source<TestService>()
      val repository = testCanvas.source(TestRepository::class, "")

      assertNotNull(service)
      assertEquals("null-qualifier", service.getValue())
      assertNotNull(repository)
      assertEquals("empty-qualifier", repository.getData())
    }

  @Test
  fun `should test paint method with missing dependency`() =
    runTest {
      var exceptionThrown = false
      try {
        canvas {
          single<TestService> {
            // This will try to paint a missing dependency during canvas construction
            paint<TestRepository>()
            TestServiceImpl("should-not-reach")
          }
        }
      } catch (e: MosaicMissingKeyException) {
        exceptionThrown = true
        assertEquals(true, e.message?.contains("TestRepository"))
      }
      assertEquals(true, exceptionThrown)
    }

  @Test
  fun `should test CanvasBuilder single with empty string qualifier`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("") { TestServiceImpl("empty-qualifier") }
        }

      val service = testCanvas.source(TestService::class, "")
      assertNotNull(service)
      assertEquals("empty-qualifier", service.getValue())
    }
}
