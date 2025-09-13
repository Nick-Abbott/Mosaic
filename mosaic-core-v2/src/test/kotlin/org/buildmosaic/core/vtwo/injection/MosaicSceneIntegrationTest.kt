package org.buildmosaic.core.vtwo.injection

import kotlinx.coroutines.test.runTest
import org.buildmosaic.core.vtwo.MockCanvas
import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.core.vtwo.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Integration tests demonstrating how tiles properly use MosaicScene for dependency injection
 * and request-scoped data access.
 */
@Suppress("LargeClass", "FunctionMaxLength")
class MosaicSceneIntegrationTest {
  // Test data classes
  data class User(val id: String, val name: String, val email: String)

  data class DatabaseConfig(val host: String, val port: Int, val database: String)

  data class RequestContext(val userId: String, val requestId: String, val timestamp: Long)

  // Scene keys for different types of dependencies
  private val userKey = SceneKey<User>("current-user")
  private val dbConfigKey = SceneKey<DatabaseConfig>("database-config")
  private val requestContextKey = SceneKey<RequestContext>("request-context")
  private val apiKeyKey = SceneKey<String>("api-key")
  private val featureFlagsKey = SceneKey<Set<String>>("feature-flags")

  @Test
  fun `single tile should access scene dependencies successfully`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")
      val dbConfig = DatabaseConfig("localhost", 5432, "testdb")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          .registerClaim(dbConfigKey, dbConfig)
          .build()

      val mosaic = MockCanvas().create(scene)

      val userProfileTile =
        singleTile {
          val currentUser = scene.claim(userKey)
          val config = scene.claim(dbConfigKey)
          "User ${currentUser.name} connected to ${config.database} at ${config.host}:${config.port}"
        }

      val result = mosaic.compose(userProfileTile)
      assertEquals("User John Doe connected to testdb at localhost:5432", result)
    }

  @Test
  fun `multi tile should access scene dependencies for batch operations`() =
    runTest {
      val requestContext = RequestContext("user123", "req456", System.currentTimeMillis())
      val apiKey = "secret-api-key-123"

      val scene =
        MosaicSceneBuilder()
          .registerClaim(requestContextKey, requestContext)
          .registerClaim(apiKeyKey, apiKey)
          .build()

      val mosaic = MockCanvas().create(scene)

      val userDataTile =
        multiTile<String, String> { userIds ->
          val context = scene.claim(requestContextKey)
          val key = scene.claim(apiKeyKey)

          userIds.associateWith { userId ->
            "Data for $userId (requested by ${context.userId} with key ${key.take(6)}...)"
          }
        }

      val userIds = setOf("user1", "user2", "user3")
      val result = mosaic.compose(userDataTile, userIds)

      assertEquals(3, result.size)
      assertEquals("Data for user1 (requested by user123 with key secret...)", result["user1"])
      assertEquals("Data for user2 (requested by user123 with key secret...)", result["user2"])
      assertEquals("Data for user3 (requested by user123 with key secret...)", result["user3"])
    }

  @Test
  fun `tiles should use peek for optional dependencies`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          // Note: not registering feature flags - they're optional
          .build()

      val mosaic = MockCanvas().create(scene)

      val featureAwareTile =
        singleTile {
          val currentUser = scene.claim(userKey)
          val features = scene.peek(featureFlagsKey) ?: emptySet()

          if ("premium" in features) {
            "Premium user: ${currentUser.name}"
          } else {
            "Standard user: ${currentUser.name}"
          }
        }

      val result = mosaic.compose(featureAwareTile)
      assertEquals("Standard user: John Doe", result)
    }

  @Test
  fun `tiles should use claimOr for dependencies with defaults`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          .build()

      val mosaic = MockCanvas().create(scene)

      val configAwareTile =
        singleTile {
          val currentUser = scene.claim(userKey)
          val defaultConfig = DatabaseConfig("default-host", 3306, "default-db")
          val config = scene.claimOr(dbConfigKey, defaultConfig)

          "${currentUser.name} will use database: ${config.database}"
        }

      val result = mosaic.compose(configAwareTile)
      assertEquals("John Doe will use database: default-db", result)
    }

  @Test
  fun `tiles should use claimOrCompute for lazy-computed defaults`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          .build()

      val mosaic = MockCanvas().create(scene)

      var computeCallCount = 0
      val dynamicConfigTile =
        singleTile {
          val currentUser = scene.claim(userKey)
          val config =
            scene.claimOrCompute(dbConfigKey) {
              computeCallCount++
              DatabaseConfig("computed-${currentUser.id}", 5432, "user-${currentUser.id}-db")
            }

          "Config for ${currentUser.name}: ${config.database}"
        }

      val result = mosaic.compose(dynamicConfigTile)
      assertEquals("Config for John Doe: user-user123-db", result)
      assertEquals(1, computeCallCount, "Compute function should be called exactly once")
    }

  @Test
  fun `tiles should handle missing required dependencies gracefully`() =
    runTest {
      val scene = MosaicSceneBuilder().build() // Empty scene
      val mosaic = MockCanvas().create(scene)

      val strictTile =
        singleTile {
          val user = scene.claim(userKey) // This will throw
          "User: ${user.name}"
        }

      assertFailsWith<org.buildmosaic.core.vtwo.exception.MosaicMissingKeyException> {
        mosaic.compose(strictTile)
      }
    }

  @Test
  fun `nested tiles should share the same scene context`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")
      val requestContext = RequestContext("user123", "req456", 1234567890L)

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          .registerClaim(requestContextKey, requestContext)
          .build()

      val mosaic = MockCanvas().create(scene)

      val innerTile =
        singleTile {
          val context = scene.claim(requestContextKey)
          "Request ${context.requestId} at ${context.timestamp}"
        }

      val outerTile =
        singleTile {
          val currentUser = scene.claim(userKey)
          val innerResult = compose(innerTile)
          "User ${currentUser.name}: $innerResult"
        }

      val result = mosaic.compose(outerTile)
      assertEquals("User John Doe: Request req456 at 1234567890", result)
    }

  @Test
  fun `tiles should work with complex dependency types`() =
    runTest {
      val complexData =
        mapOf(
          "metrics" to listOf(1.0, 2.5, 3.7),
          "tags" to listOf("important", "user-data", "analytics"),
        )
      val complexKey = SceneKey<Map<String, List<Any>>>("complex-data")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(complexKey, complexData)
          .build()

      val mosaic = MockCanvas().create(scene)

      val analyticsTile =
        singleTile {
          val data = scene.claim(complexKey)

          @Suppress("UNCHECKED_CAST")
          val metrics = data["metrics"] as List<Double>

          @Suppress("UNCHECKED_CAST")
          val tags = data["tags"] as List<String>

          "Analytics: ${metrics.sum()} total, tags: ${tags.joinToString(", ")}"
        }

      val result = mosaic.compose(analyticsTile)
      assertEquals("Analytics: 7.2 total, tags: important, user-data, analytics", result)
    }

  @Test
  fun `scene should maintain type safety across different tile compositions`() =
    runTest {
      val user = User("user123", "John Doe", "john@example.com")
      val dbConfig = DatabaseConfig("localhost", 5432, "testdb")
      val features = setOf("premium", "beta", "analytics")

      val scene =
        MosaicSceneBuilder()
          .registerClaim(userKey, user)
          .registerClaim(dbConfigKey, dbConfig)
          .registerClaim(featureFlagsKey, features)
          .build()

      val mosaic = MockCanvas().create(scene)

      // Test that each tile gets the correct types
      val userTile =
        singleTile {
          val u: User = scene.claim(userKey)
          u.name
        }

      val configTile =
        singleTile {
          val config: DatabaseConfig = scene.claim(dbConfigKey)
          config.port
        }

      val featureTile =
        singleTile {
          val flags: Set<String> = scene.claim(featureFlagsKey)
          flags.size
        }

      assertEquals("John Doe", mosaic.compose(userTile))
      assertEquals(5432, mosaic.compose(configTile))
      assertEquals(3, mosaic.compose(featureTile))
    }
}
