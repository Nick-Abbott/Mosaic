package org.buildmosaic.core.injection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.buildmosaic.core.exception.MosaicMissingKeyException
import org.buildmosaic.core.source
import org.buildmosaic.core.sourceOr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@Suppress("LargeClass", "FunctionMaxLength")
class CanvasConstructionTest {
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

  class DependentService(private val repository: TestRepository) : TestService {
    override fun getValue(): String = repository.getData()
  }

  @Test
  fun `registration overloads route exact types and distinct qualifiers`() =
    runTest {
      val unqualified = TestServiceImpl("unqualified")
      val empty = TestServiceImpl("empty")
      val primary = TestServiceImpl("primary")
      val secondary = TestServiceImpl("secondary")
      val keyed = CanvasKey(TestService::class, "keyed")
      val value = TestServiceImpl("keyed")
      val testCanvas =
        canvas {
          single<TestService>(null) { unqualified }
          single<TestService>("") { empty }
          single<TestService>("primary") { primary }
          single(CanvasKey(TestService::class, "secondary")) { secondary }
          single(keyed) { value }
        }
      assertSame(unqualified, testCanvas.source<TestService>())
      assertSame(unqualified, testCanvas.source(TestService::class, null))
      assertSame(empty, testCanvas.source(TestService::class, ""))
      assertSame(primary, testCanvas.source(TestService::class, "primary"))
      assertSame(secondary, testCanvas.source(TestService::class, "secondary"))
      assertSame(value, testCanvas.source(keyed))
      assertNull(testCanvas.sourceOr<TestServiceImpl>())
      assertNull(testCanvas.sourceOr(TestService::class, "missing"))
    }

  @Test
  fun `should paint exact parent instances using reified and qualified keys`() =
    runTest {
      val parentService = TestServiceImpl("parent-service")
      val parentRepository = TestRepositoryImpl("parent-repository")
      val childCanvas =
        canvas(
          canvas {
            single<TestService> { parentService }
            single<TestRepository>("qualified") { parentRepository }
          },
        ) {
          single<TestService>("consumer") {
            val service = paint<TestService>()
            val repository = paint(CanvasKey(TestRepository::class, "qualified"))
            TestServiceImpl("${service.getValue()}-${repository.getData()}")
          }
        }

      val consumer = childCanvas.source(TestService::class, "consumer")

      assertEquals("parent-service-parent-repository", consumer.getValue())
      assertSame(parentService, childCanvas.source<TestService>())
      assertSame(parentRepository, childCanvas.source(TestRepository::class, "qualified"))
    }

  @Test
  fun `should paint through ancestors`() =
    runTest {
      val grandparentService = TestServiceImpl("grandparent-service")
      val nearestService = TestServiceImpl("nearest-service")
      val grandparentCanvas = canvas { single<TestService> { grandparentService } }
      val parentCanvas =
        grandparentCanvas.withLayer {
          single<TestService> { nearestService }
        }
      val childCanvas =
        canvas(parentCanvas) {
          single<TestRepository> { TestRepositoryImpl(paint<TestService>().getValue()) }
        }

      assertEquals("nearest-service", childCanvas.source<TestRepository>().getData())
    }

  @Test
  fun `should prefer local paint binding even when registered after its consumer`() =
    runTest {
      val parentRepository = TestRepositoryImpl("parent-repository")
      val parentCanvas =
        canvas {
          single<TestRepository> { parentRepository }
          single<TestService> { DependentService(paint<TestRepository>()) }
        }
      val parentService = parentCanvas.source<TestService>()
      val childService = TestServiceImpl("child-service")
      val childCanvas =
        canvas(parentCanvas) {
          single<TestRepository>("consumer") {
            TestRepositoryImpl(paint<TestService>().getValue())
          }
          single<TestService> { childService }
        }

      assertSame(childService, childCanvas.source<TestService>())
      assertEquals("child-service", childCanvas.source(TestRepository::class, "consumer").getData())
      assertSame(parentService, parentCanvas.source<TestService>())
      assertEquals("parent-repository", parentService.getValue())
    }

  @Test
  fun `should preserve missing key details and local constructor failures`() =
    runTest {
      val requestedKey = CanvasKey(TestService::class, "missing")
      val parentCanvas = canvas { single<TestService> { TestServiceImpl("parent") } }
      val missingException =
        assertFailsWith<MosaicMissingKeyException> {
          canvas(parentCanvas) {
            single<TestRepository> {
              paint(requestedKey)
              TestRepositoryImpl("unreachable")
            }
          }
        }
      assertEquals(requestedKey, missingException.key)

      var constructorContinued = false
      val failure =
        assertFailsWith<IllegalStateException> {
          canvas(parentCanvas) {
            single<TestRepository> {
              paint<TestService>()
              constructorContinued = true
              TestRepositoryImpl("unreachable")
            }
            single<TestService> { error("local failure") }
          }
        }

      assertEquals("local failure", failure.message)
      assertEquals(false, constructorContinued)
    }

  // Hierarchical resolution tests
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

  @Test
  fun `should close resources in reverse creation order`() =
    runTest {
      val events = mutableListOf<String>()
      val parentResource = Resource { events.add("parent") }
      val parent = canvas { single { parentResource } }
      parent.withLayer {
        single<Resource>("first") {
          events.add("create first")
          Resource { events.add("close first") }
        }
        single<Resource>("consumer") {
          assertSame(parentResource, paint<Resource>())
          paint<Resource>("dependency")
          events.add("create consumer")
          Resource { events.add("close consumer") }
        }
        single<Resource>("dependency") {
          events.add("create dependency")
          Resource { events.add("close dependency") }
        }
      }.use {
        assertEquals(listOf("create first", "create dependency", "create consumer"), events)
      }
      assertEquals(
        listOf(
          "create first",
          "create dependency",
          "create consumer",
          "close consumer",
          "close dependency",
          "close first",
        ),
        events,
      )
      parent.close()
      assertEquals("parent", events.last())
    }

  @Test
  fun `should roll back created resources and suppress cleanup failures`() =
    runTest {
      val closed = mutableListOf<String>()
      val parentResource = Resource { closed.add("parent") }
      val parent = canvas { single { parentResource } }
      val failure = AssertionError("failed construction")
      val firstCleanup = IllegalStateException("first cleanup")
      val paintedCleanup = IllegalArgumentException("painted cleanup")
      var paintedCount = 0
      val thrown =
        assertFailsWith<AssertionError> {
          parent.withLayer {
            single<Resource>("first") {
              Resource {
                closed.add("first")
                throw firstCleanup
              }
            }
            single<Resource>("consumer") {
              assertSame(parentResource, paint<Resource>())
              assertSame(paint<Resource>("painted"), paint<Resource>("painted"))
              Resource {
                closed.add("consumer")
                throw failure
              }
            }
            single<String> { throw failure }
            single<Resource>("painted") {
              paintedCount++
              Resource {
                closed.add("painted")
                throw paintedCleanup
              }
            }
            single<Int> { error("must not be constructed") }
          }
        }

      assertSame(failure, thrown)
      assertEquals(1, paintedCount)
      assertEquals(listOf(paintedCleanup, firstCleanup), thrown.suppressed.toList())
      assertEquals(listOf("consumer", "painted", "first"), closed)
      parent.close()
      assertEquals(listOf("consumer", "painted", "first", "parent"), closed)
    }

  @Test
  fun `cancellation during a suspended constructor cleans local values and preserves cancellation`() =
    runTest {
      val closed = mutableListOf<String>()
      val entered = CompletableDeferred<Unit>()
      val cancellation = CancellationException("cancel construction")
      val cleanup = IllegalStateException("cleanup")
      var constructorFailure: CancellationException? = null
      var original: Throwable? = null
      val building =
        launch {
          try {
            canvas {
              single<Resource>("first") { Resource { closed.add("first") } }
              single<String> {
                paint<Resource>("painted")
                entered.complete(Unit)
                try {
                  awaitCancellation()
                } catch (failure: CancellationException) {
                  constructorFailure = failure
                  throw failure
                }
              }
              single<Resource>("painted") {
                Resource {
                  closed.add("painted")
                  throw cleanup
                }
              }
            }
          } catch (failure: CancellationException) {
            original = failure
            throw failure
          }
        }
      entered.await()
      building.cancel(cancellation)
      building.join()

      assertSame(constructorFailure, original)
      assertEquals(cancellation.message, original?.message)
      assertEquals(listOf(cleanup), original?.suppressed?.toList())
      assertEquals(listOf("painted", "first"), closed)
    }

  @Test
  fun `cancellation cannot transfer ownership after a constructor returns`() =
    runTest {
      var closed = false
      val building =
        launch {
          canvas {
            single {
              currentCoroutineContext()[Job]!!.cancel()
              Resource { closed = true }
            }
          }
          error("cancelled construction must not return a Canvas")
        }
      building.join()
      assertEquals(true, building.isCancelled)
      assertEquals(true, closed)
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
          single<TestService>("regular") { TestServiceImpl("regular") }
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
  fun `should share one construction across concurrent paint calls`() =
    runTest {
      var constructions = 0
      val expected = TestRepositoryImpl("shared")
      val testCanvas =
        canvas {
          single<TestService> {
            val repositories =
              coroutineScope {
                listOf(async { paint<TestRepository>() }, async { paint<TestRepository>() }).awaitAll()
              }
            repositories.forEach { assertSame(expected, it) }
            DependentService(repositories.first())
          }
          single<TestRepository> {
            constructions++
            delay(1)
            expected
          }
        }

      assertEquals(1, constructions)
      assertSame(expected, testCanvas.source<TestRepository>())
      assertEquals("shared", testCanvas.source<TestService>().getValue())
    }

  @Test
  fun `should reject circular dependencies between concurrent sibling constructors`() =
    runTest {
      val serviceStarted = CompletableDeferred<Unit>()
      val repositoryStarted = CompletableDeferred<Unit>()
      val failure =
        assertFailsWith<IllegalStateException> {
          withTimeout(1.seconds) {
            canvas {
              single<String> {
                coroutineScope {
                  listOf(async { paint<TestService>() }, async { paint<TestRepository>() }).awaitAll()
                }
                "root"
              }
              single<TestService> {
                serviceStarted.complete(Unit)
                repositoryStarted.await()
                DependentService(paint<TestRepository>())
              }
              single<TestRepository> {
                repositoryStarted.complete(Unit)
                serviceStarted.await()
                TestRepositoryImpl(paint<TestService>().getValue())
              }
            }
          }
        }
      assertTrue(failure.message.orEmpty().contains("Circular dependency"), failure.toString())
    }

  @Test
  fun `should reject recursive dependencies through a child coroutine`() =
    runTest {
      val failure =
        assertFailsWith<IllegalStateException> {
          withTimeout(1.seconds) {
            canvas {
              single<TestService> {
                val repo = paint<TestRepository>()
                TestServiceImpl("service-${repo.getData()}")
              }
              single<TestRepository> {
                val service = coroutineScope { async { paint<TestService>() }.await() }
                TestRepositoryImpl("repo-${service.getValue()}")
              }
            }
          }
        }
      assertTrue(failure.message.orEmpty().contains("Circular dependency"), failure.toString())
    }

  @Test
  fun `scoped Mosaic source helpers resolve its Canvas`() =
    runTest {
      val testCanvas =
        canvas {
          single<TestService>("mosaic-service") { TestServiceImpl("mosaic-test") }
          single<TestRepository> { TestRepositoryImpl("default-repo") }
        }

      testCanvas.withMosaic {
        val mosaic = this
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
    }

  private class Resource(private val onClose: () -> Unit) : AutoCloseable {
    override fun close() = onClose()
  }
}
