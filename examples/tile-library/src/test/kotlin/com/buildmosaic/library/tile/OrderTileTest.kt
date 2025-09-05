package com.buildmosaic.library.tile

import com.buildmosaic.library.OrderRequest
import com.buildmosaic.library.exception.OrderNotFoundException
import com.buildmosaic.library.model.Order
import com.buildmosaic.library.model.OrderLineItem
import com.buildmosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class OrderTileTest {
  @Test
  fun `order tile returns order from request`() =
    runBlocking {
      val expected =
        Order(
          id = "order-1",
          customerId = "customer-1",
          items =
            listOf(
              OrderLineItem("product-1", "sku-1", 2),
              OrderLineItem("product-2", "sku-2", 1),
            ),
        )
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("order-1")).build()
      testMosaic.assertEquals(OrderTile::class, expected)
    }

  @Test
  fun `order tile throws custom exception when missing`() =
    runBlocking {
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("missing")).build()
      testMosaic.assertThrows(OrderTile::class, OrderNotFoundException::class)
    }
}
