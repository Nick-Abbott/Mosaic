package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.model.LineItemDetail
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.model.OrderLineItem
import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.examples.spring.orders.model.Product
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

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
          .withMockMultiTile(ProductsByIdTile::class, products)
          .withMockMultiTile(PricingBySkuTile::class, prices)
          .build()
      testMosaic.assertEquals(LineItemsTile::class, expected)
    }
}
