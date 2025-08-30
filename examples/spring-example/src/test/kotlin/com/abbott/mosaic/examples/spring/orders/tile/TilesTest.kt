package com.abbott.mosaic.examples.spring.orders.tile

import com.abbott.mosaic.examples.spring.orders.OrderRequest
import com.abbott.mosaic.examples.spring.orders.model.Address
import com.abbott.mosaic.examples.spring.orders.model.Customer
import com.abbott.mosaic.examples.spring.orders.model.LineItemDetail
import com.abbott.mosaic.examples.spring.orders.model.Logistics
import com.abbott.mosaic.examples.spring.orders.model.Order
import com.abbott.mosaic.examples.spring.orders.model.OrderLineItem
import com.abbott.mosaic.examples.spring.orders.model.OrderPage
import com.abbott.mosaic.examples.spring.orders.model.OrderSummary
import com.abbott.mosaic.examples.spring.orders.model.Price
import com.abbott.mosaic.examples.spring.orders.model.Product
import com.abbott.mosaic.examples.spring.orders.model.Quote
import com.abbott.mosaic.test.TestMosaicBuilder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class TilesTest {
  @Test
  fun `order tile returns order from request`() =
    runBlocking {
      val expected =
        Order(
          id = "order-1",
          customerId = "customer-1",
          items =
            listOf(
              OrderLineItem("product-1", "sku-1", 2),
              OrderLineItem("product-2", "sku-2", 1),
            ),
        )
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("order-1")).build()
      testMosaic.assertEquals(OrderTile::class, expected)
    }

  @Test
  fun `customer tile uses order tile`() =
    runBlocking {
      val order = Order("order-1", "customer-1", emptyList())
      val expected = Customer("customer-1", "Jane Doe")
      val testMosaic = TestMosaicBuilder().withMockTile(OrderTile::class, order).build()
      testMosaic.assertEquals(CustomerTile::class, expected)
    }

  @Test
  fun `products tile fetches products`() =
    runBlocking {
      val keys = listOf("product-1", "product-2")
      val expected =
        mapOf(
          "product-1" to Product("product-1", "Coffee Mug"),
          "product-2" to Product("product-2", "Tea Kettle"),
        )
      val testMosaic = TestMosaicBuilder().build()
      testMosaic.assertEquals(ProductsByIdTile::class, keys, expected)
    }

  @Test
  fun `pricing tile fetches prices`() =
    runBlocking {
      val keys = listOf("sku-1", "sku-2")
      val expected =
        mapOf(
          "sku-1" to Price("sku-1", 12.99),
          "sku-2" to Price("sku-2", 29.99),
        )
      val testMosaic = TestMosaicBuilder().build()
      testMosaic.assertEquals(PricingBySkuTile::class, keys, expected)
    }

  @Test
  fun `address tile uses request id`() =
    runBlocking {
      val testMosaic = TestMosaicBuilder().withRequest(OrderRequest("order-1")).build()
      val expected = Address("123 Main St", "Springfield")
      testMosaic.assertEquals(AddressTile::class, expected)
    }

  @Test
  fun `carrier quotes tile uses address tile`() =
    runBlocking {
      val address = Address("123 Main St", "Springfield")
      val carriers = listOf("UPS", "FEDEX")
      val quotes =
        mapOf(
          "UPS" to Quote("UPS", 5.99),
          "FEDEX" to Quote("FEDEX", 7.49),
        )
      val testMosaic = TestMosaicBuilder().withMockTile(AddressTile::class, address).build()
      testMosaic.assertEquals(CarrierQuotesTile::class, carriers, quotes)
    }

  @Test
  fun `logistics tile combines address and quotes`() =
    runBlocking {
      val address = Address("123 Main St", "Springfield")
      val quotes =
        mapOf(
          "UPS" to Quote("UPS", 5.99),
          "FEDEX" to Quote("FEDEX", 7.49),
          "DHL" to Quote("DHL", 6.49),
        )
      val expected = Logistics(address, quotes)
      val testMosaic =
        TestMosaicBuilder()
          .withMockTile(AddressTile::class, address)
          .withMockMultiTile(CarrierQuotesTile::class, quotes)
          .build()
      testMosaic.assertEquals(LogisticsTile::class, expected)
    }

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
}
