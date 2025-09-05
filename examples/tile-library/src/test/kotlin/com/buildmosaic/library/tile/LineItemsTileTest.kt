package com.buildmosaic.library.tile

import com.buildmosaic.library.model.LineItemDetail
import com.buildmosaic.library.model.Order
import com.buildmosaic.library.model.OrderLineItem
import com.buildmosaic.library.model.Price
import com.buildmosaic.library.model.Product
import com.buildmosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class LineItemsTileTest {
  @Test
  fun `line items tile combines other tiles`() =
    runBlocking {
      val order =
        Order(
          id = "order-1",
          customerId = "customer-1",
          items = listOf(OrderLineItem("product-1", "sku-1", 2)),
        )
      val products = mapOf("product-1" to Product("product-1", "Coffee Mug"))
      val prices = mapOf("sku-1" to Price("sku-1", 12.99))
      val expected =
        listOf(
          LineItemDetail(
            product = products.getValue("product-1"),
            price = prices.getValue("sku-1"),
            quantity = 2,
          ),
        )
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(OrderTile::class, order)
          .withMockTile(ProductsByIdTile::class, products)
          .withMockTile(PricingBySkuTile::class, prices)
          .build()
      testMosaic.assertEquals(LineItemsTile::class, expected)
    }

  @Test
  fun `line items tile fails when products tile fails`() =
    runBlocking {
      val order =
        Order(
          id = "order-1",
          customerId = "customer-1",
          items = listOf(OrderLineItem("product-1", "sku-1", 1)),
        )
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(OrderTile::class, order)
          .withFailedTile(ProductsByIdTile::class, RuntimeException("boom"))
          .build()
      testMosaic.assertThrows(LineItemsTile::class, RuntimeException::class)
    }
}
