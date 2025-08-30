package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.Address
import com.abbott.mosaic.examples.spring.orders.model.Customer
import com.abbott.mosaic.examples.spring.orders.model.Logistics
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.model.OrderPage
import com.abbott.mosaic.examples.spring.orders.model.OrderSummary
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class OrderPageTileTest {
  @Test
  fun `order page tile merges summary and logistics`() =
    runBlocking {
      val summary =
        OrderSummary(
          order = Order("order-1", "customer-1", emptyList()),
          customer = Customer("customer-1", "Jane Doe"),
          lineItems = emptyList(),
        )
      val logistics = Logistics(Address("123 Main St", "Springfield"), emptyMap())
      val expected = OrderPage(summary, logistics)
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(OrderSummaryTile::class, summary)
          .withMockTile(LogisticsTile::class, logistics)
          .build()
      testMosaic.assertEquals(OrderPageTile::class, expected)
    }

  @Test
  fun `order page tile fails when logistics tile fails`() =
    runBlocking {
      val summary =
        OrderSummary(
          order = Order("order-1", "customer-1", emptyList()),
          customer = Customer("customer-1", "Jane Doe"),
          lineItems = emptyList(),
        )
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(OrderSummaryTile::class, summary)
          .withFailedTile(LogisticsTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(OrderPageTile::class, RuntimeException::class.java)
    }
}
