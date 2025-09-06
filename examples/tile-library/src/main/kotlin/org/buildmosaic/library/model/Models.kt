package org.buildmosaic.library.model

import kotlinx.serialization.Serializable

// Domain models used throughout the example tiles

@Serializable
data class Order(
  val id: String,
  val customerId: String,
  val items: List<OrderLineItem>,
)

@Serializable
data class OrderLineItem(
  val productId: String,
  val sku: String,
  val quantity: Int,
)

@Serializable
data class Customer(
  val id: String,
  val name: String,
)

@Serializable
data class Product(
  val id: String,
  val name: String,
)

@Serializable
data class Price(
  val sku: String,
  val amount: Double,
)

@Serializable
data class LineItemDetail(
  val product: Product,
  val price: Price,
  val quantity: Int,
)

@Serializable
data class OrderSummary(
  val order: Order,
  val customer: Customer,
  val lineItems: List<LineItemDetail>,
)

@Serializable
data class Address(
  val street: String,
  val city: String,
)

@Serializable
data class Quote(
  val carrier: String,
  val cost: Double,
)

@Serializable
data class Logistics(
  val address: Address,
  val carrierQuotes: Map<String, Quote>,
)

@Serializable
data class OrderPage(
  val summary: OrderSummary,
  val logistics: Logistics,
)
