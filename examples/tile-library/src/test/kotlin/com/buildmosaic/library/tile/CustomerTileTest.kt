package com.buildmosaic.library.tile

import com.buildmosaic.library.model.Customer
import com.buildmosaic.library.model.Order
import com.buildmosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class CustomerTileTest {
  @Test
  fun `customer tile uses order tile`() =
    runBlocking {
      val order = Order("order-1", "customer-1", emptyList())
      val expected = Customer("customer-1", "Jane Doe")
      val testMosaic = TestMosaicBuilder().withMockTile(OrderTile::class, order).build()
      testMosaic.assertEquals(CustomerTile::class, expected)
    }

  @Test
  fun `customer tile fails when order tile fails`() =
    runBlocking {
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(OrderTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(CustomerTile::class, RuntimeException::class)
    }
}
