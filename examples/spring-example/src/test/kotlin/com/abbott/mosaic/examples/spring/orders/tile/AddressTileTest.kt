package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.Address
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class AddressTileTest {
  @Test
  fun `address tile uses request id`() =
    runBlocking {
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("order-1")).build()
      val expected = Address("123 Main St", "Springfield")
      testMosaic.assertEquals(AddressTile::class, expected)
    }

  @Test
  fun `address tile propagates failures`() =
    runBlocking {
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(AddressTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(AddressTile::class, RuntimeException::class.java)
    }
}
