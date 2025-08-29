package com.abbott.mosaic.examples.spring.orders.model

// Domain models used throughout the example tiles

data class Order(
  val id: String,
  val customerId: String,
  val items: List<OrderLineItem>,
)

data class OrderLineItem(
  val productId: String,
  val sku: String,
  val quantity: Int,
)

data class Customer(
  val id: String,
  val name: String,
)

data class Product(
  val id: String,
  val name: String,
)

data class Price(
  val sku: String,
  val amount: Double,
)

data class LineItemDetail(
  val product: Product,
  val price: Price,
  val quantity: Int,
)

data class OrderSummary(
  val order: Order,
  val customer: Customer,
  val lineItems: List<LineItemDetail>,
)

data class Address(
  val street: String,
  val city: String,
)

data class Quote(
  val carrier: String,
  val cost: Double,
)

data class Logistics(
  val address: Address,
  val carrierQuotes: Map<String, Quote>,
)

data class OrderPage(
  val summary: OrderSummary,
  val logistics: Logistics,
)
