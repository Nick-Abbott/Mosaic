package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.model.Address
import org.buildmosaic.library.model.Customer
import org.buildmosaic.library.model.Logistics
import org.buildmosaic.library.model.Order
import org.buildmosaic.library.model.OrderPage
import org.buildmosaic.library.model.OrderSummary
import org.buildmosaic.test.TestMosaicBuilder
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
