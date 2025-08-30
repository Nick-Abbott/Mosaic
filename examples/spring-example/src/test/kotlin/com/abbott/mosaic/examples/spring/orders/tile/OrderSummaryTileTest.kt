package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.Customer
import com.abbott.mosaic.examples.spring.orders.model.LineItemDetail
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.model.OrderSummary
import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.examples.spring.orders.model.Product
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
}
