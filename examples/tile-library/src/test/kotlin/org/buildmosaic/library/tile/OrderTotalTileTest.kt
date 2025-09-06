package org.buildmosaic.library.tile

import kotlinx.coroutines.runBlocking
import org.buildmosaic.library.model.LineItemDetail
import org.buildmosaic.library.model.Price
import org.buildmosaic.library.model.Product
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

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

  @Test
  fun `order total tile fails when line items fail`() =
    runBlocking {
      val testMosaic =
        TestMosaicBuilder()
          .withFailedTile(LineItemsTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(OrderTotalTile::class, RuntimeException::class)
    }
}
