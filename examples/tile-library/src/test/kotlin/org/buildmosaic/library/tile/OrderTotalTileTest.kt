package org.buildmosaic.library.tile

import kotlinx.coroutines.test.runTest
import org.buildmosaic.library.model.LineItemDetail
import org.buildmosaic.library.model.Price
import org.buildmosaic.library.model.Product
import org.buildmosaic.test.TestMosaicBuilder
import kotlin.test.Test

class OrderTotalTileTest {
  @Test
  fun `order total tile sums line item prices`() =
    runTest {
      val lineItems =
        listOf(
          LineItemDetail(Product("product-1", "Coffee Mug"), Price("sku-1", 12.99), 2),
          LineItemDetail(Product("product-2", "Tea Kettle"), Price("sku-2", 29.99), 1),
        )
      val expected = lineItems.sumOf { it.price.amount * it.quantity }
      val testMosaic =
        TestMosaicBuilder(this)
          .withMockTile(LineItemsTile, lineItems)
          .build()
      testMosaic.assertEquals(OrderTotalTile, expected)
    }

  @Test
  fun `order total tile fails when line items fail`() =
    runTest {
      val testMosaic =
        TestMosaicBuilder(this)
          .withFailedTile(LineItemsTile, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(OrderTotalTile, RuntimeException::class)
    }
}
