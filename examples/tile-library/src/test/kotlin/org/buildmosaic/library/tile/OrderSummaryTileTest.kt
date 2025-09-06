package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.model.Customer
import org.buildmosaic.library.model.LineItemDetail
import org.buildmosaic.library.model.Order
import org.buildmosaic.library.model.OrderSummary
import org.buildmosaic.library.model.Price
import org.buildmosaic.library.model.Product
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

class OrderSummaryTileTest {
  @Test
  fun `order summary tile aggregates data`() =
    runBlocking {
      val order = Order("order-1", "customer-1", emptyList())
      val customer = Customer("customer-1", "Jane Doe")
      val lineItems =
        listOf(LineItemDetail(Product("product-1", "Coffee Mug"), Price("sku-1", 12.99), 2))
      val expected = OrderSummary(order, customer, lineItems)
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(OrderTile::class, order)
          .withMockTile(CustomerTile::class, customer)
          .withMockTile(LineItemsTile::class, lineItems)
          .build()
      testMosaic.assertEquals(OrderSummaryTile::class, expected)
    }

  @Test
  fun `order summary tile fails when order tile fails`() =
    runBlocking {
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(OrderTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(OrderSummaryTile::class, RuntimeException::class)
    }
}
