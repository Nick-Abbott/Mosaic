package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.OrderRequest
import org.buildmosaic.library.exception.OrderNotFoundException
import org.buildmosaic.library.model.Order
import org.buildmosaic.library.model.OrderLineItem
import org.buildmosaic.test.TestMosaicBuilder
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
