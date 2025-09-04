package com.buildmosaic.library.service

import com.buildmosaic.library.model.Address
import com.buildmosaic.library.model.Customer
import com.buildmosaic.library.model.Order
import com.buildmosaic.library.model.OrderLineItem
import com.buildmosaic.library.model.Price
import com.buildmosaic.library.model.Product
import com.buildmosaic.library.model.Quote

/** Simple in-memory services simulating external calls */
object OrderService {
  private val orders =
    mapOf(
      "order-1" to
        Order(
          id = "order-1",
          customerId = "customer-1",
          items =
            listOf(
              OrderLineItem(productId = "product-1", sku = "sku-1", quantity = 2),
              OrderLineItem(productId = "product-2", sku = "sku-2", quantity = 1),
            ),
        ),
    )

  fun getOrder(id: String): Order = orders.getValue(id)
}

object CustomerService {
  private val customers =
    mapOf(
      "customer-1" to Customer("customer-1", "Jane Doe"),
    )

  fun getCustomer(id: String): Customer = customers.getValue(id)
}

object ProductService {
  private val products =
    mapOf(
      "product-1" to Product("product-1", "Coffee Mug"),
      "product-2" to Product("product-2", "Tea Kettle"),
    )

  fun getProducts(ids: List<String>): Map<String, Product> = ids.associateWith { products.getValue(it) }
}

object PricingService {
  private val prices =
    mapOf(
      "sku-1" to Price("sku-1", 12.99),
      "sku-2" to Price("sku-2", 29.99),
    )

  fun getPrices(skus: List<String>): Map<String, Price> = skus.associateWith { prices.getValue(it) }
}

object AddressService {
  @Suppress("UnusedParameter")
  fun getAddress(orderId: String): Address = Address("123 Main St", "Springfield")
}

object CarrierService {
  private val baseQuotes =
    mapOf(
      "UPS" to 5.99,
      "FEDEX" to 7.49,
      "DHL" to 6.49,
    )

  fun getAvailableCarriers(): List<String> = baseQuotes.keys.toList()

  @Suppress("UnusedParameter")
  fun getQuotes(
    address: Address,
    carriers: List<String>,
  ): Map<String, Quote> = carriers.associateWith { carrier -> Quote(carrier, baseQuotes.getValue(carrier)) }
}
