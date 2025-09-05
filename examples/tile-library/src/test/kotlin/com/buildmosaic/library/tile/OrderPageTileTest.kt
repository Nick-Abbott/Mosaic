package com.buildmosaic.library.tile

import com.buildmosaic.library.model.Address
import com.buildmosaic.library.model.Customer
import com.buildmosaic.library.model.Logistics
import com.buildmosaic.library.model.Order
import com.buildmosaic.library.model.OrderPage
import com.buildmosaic.library.model.OrderSummary
import com.buildmosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

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
      testMosaic.assertThrows(OrderPageTile::class, RuntimeException::class)
    }
}
