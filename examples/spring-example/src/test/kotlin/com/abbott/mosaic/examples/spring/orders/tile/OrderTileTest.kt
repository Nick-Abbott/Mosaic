package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.model.OrderLineItem
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
}
