package org.buildmosaic.test.vtwo

import kotlinx.coroutines.runBlocking
import org.buildmosaic.core.vtwo.multiTile
import org.buildmosaic.core.vtwo.singleTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

class ExampleTest {
  // Sample data classes
  data class User(val id: Int, val name: String, val email: String)

  data class Order(val id: Int, val userId: Int, val total: Double)

  data class OrderDetails(val order: Order, val user: User)

  // Define our tiles
  private val userTile =
    singleTile<User> {
      // In a real app, this would fetch from a service
      error("Not implemented in tests")
    }

  private val orderTile =
    singleTile<Order> {
      // In a real app, this would fetch from a service
      error("Not implemented in tests")
    }

  // A tile that composes other tiles
  private val orderDetailsTile =
    singleTile<OrderDetails> {
      val order = get(orderTile)
      val user = get(userTile)
      OrderDetails(order, user)
    }

  @Test
  fun `test order details composition`() =
    runBlocking {
      // Given
      val testUser = User(1, "Test User", "test@example.com")
      val testOrder = Order(100, testUser.id, 99.99)

      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(userTile, testUser)
          .withMockTile(orderTile, testOrder)
          .build()

      // When
      val result = testMosaic.get(orderDetailsTile)

      // Then
      assertEquals(testOrder, result.order)
      assertEquals(testUser, result.user)
    }

  @Test
  fun `test multi tile with multiple keys`() =
    runBlocking {
      // Given
      val users =
        mapOf(
          1 to User(1, "User 1", "user1@example.com"),
          2 to User(2, "User 2", "user2@example.com"),
        )

      val usersTile =
        multiTile<Int, User> { ids ->
          // In a real app, this would fetch multiple users at once
          ids.associateWith { id -> users[id] ?: error("User $id not found") }
        }

      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(usersTile, users)
          .build()

      // When
      val result = testMosaic.get(usersTile, 1, 2)

      // Then
      assertEquals(2, result.size)
      assertEquals("User 1", result[1]?.name)
      assertEquals("User 2", result[2]?.name)
    }

  @Test
  fun `test delayed tile`() =
    runBlocking {
      // Given
      val delayedTile = singleTile { "Delayed Result" }
      val testMosaic =
        TestMosaicBuilder()
          .withDelayedTile(delayedTile, "Delayed Result", 100.milliseconds.toLong(DurationUnit.MILLISECONDS))
          .build()

      // When
      val startTime = System.currentTimeMillis()
      val result = testMosaic.get(delayedTile)
      val duration = System.currentTimeMillis() - startTime

      // Then
      assertEquals("Delayed Result", result)
      assertTrue(duration >= 100, "Should respect the delay")
    }

  @Test
  fun `test error handling`() =
    runBlocking {
      // Given
      val errorTile =
        singleTile<String> {
          error("Intentional error for testing")
        }

      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(errorTile, RuntimeException("Test Error"))
          .build()

      // When/Then
      val exception = testMosaic.assertThrows(errorTile, RuntimeException::class)
      assertEquals("Test Error", exception.message)
    }
}
