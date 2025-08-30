package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.LineItemDetail
import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.examples.spring.orders.model.Product
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class OrderTotalTileTest {
  @Test
  fun `order total tile sums line item prices`() =
    runBlocking {
      val lineItems =
        listOf(
          LineItemDetail(Product("product-1", "Coffee Mug"), Price("sku-1", 12.99), 2),
          LineItemDetail(Product("product-2", "Tea Kettle"), Price("sku-2", 29.99), 1),
        )
      val expected = lineItems.sumOf { it.price.amount * it.quantity }
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(LineItemsTile::class, lineItems)
          .build()
      testMosaic.assertEquals(OrderTotalTile::class, expected)
    }
}
