<picture>
  <source media="(prefers-color-scheme: dark)" srcset="./.github/images/mosaic-logo-dark.png">
  <source media="(prefers-color-scheme: light)" srcset="./.github/images/mosaic-logo-light.png">
  <img alt="Mosaic logo" src="./.github/images/mosaic-logo-light.png">
</picture>

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Think from the response up, not the database down.**

Mosaic is a Kotlin framework that transforms backend development through **composable tiles** that automatically handle caching, concurrency, and dependency resolution. Build complex responses by composing simple, testable pieces.

## 🚀 **Why Mosaic?**

- **🧩 Type-Safe Composition**: Compile-time guarantees for all your data dependencies
- **⚡ Zero Duplication**: Call the same tile from anywhere - it fetches only once
- **🔄 Out-of-the-Box Concurrency**: Automatic parallel execution without the complexity
- **🧪 Natural Testability**: Mock any tile, test in isolation
- **📦 Response-First Design**: Build what you need, not how to get it

## 🏁 **Quick Start**

### **Installation**

Add Mosaic to your Gradle project:

```kotlin
// For applications using tiles
plugins {
  kotlin("jvm")                                                    // Kotlin JVM plugin
  id("com.google.devtools.ksp")                                    // Kotlin Symbol Processing
  id("org.buildmosaic.consumer") version "0.1.0"                   // Applies mosaic dependencies and KSP processors
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")  // Recommended for advanced coroutine usage
  testImplementation(kotlin("test"))                               // Kotlin test framework
}
```

```kotlin
// For tile libraries
plugins {
  kotlin("jvm")                                                    // Kotlin JVM plugin
  id("com.google.devtools.ksp")                                    // Kotlin Symbol Processing
  id("org.buildmosaic.catalog") version "0.1.0"                    // Applies mosaic dependencies and KSP processors
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")  // Recommended for advanced coroutine usage
  testImplementation(kotlin("test"))                               // Kotlin test framework
}
```

### **Your First Tile**

```kotlin
// A simple tile that fetches and caches data
class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val customerId = (mosaic.request as OrderRequest).customerId
    return CustomerService.fetchCustomer(customerId)
  }
}

// Parallel composition: These tiles run concurrently
class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary = coroutineScope {
    // These run in parallel automatically!
    val orderDeferred = async { mosaic.getTile<OrderTile>().get() }
    val customerDeferred = async { mosaic.getTile<CustomerTile>().get() }
    val lineItemsDeferred = async { mosaic.getTile<LineItemsTile>().get() }
    
    OrderSummary(
      order = orderDeferred.await(),
      customer = customerDeferred.await(),
      lineItems = lineItemsDeferred.await()
    )
  }
}

// Sequential composition: Choose tiles based on previous results
class PaymentProcessorTile(mosaic: Mosaic) : SingleTile<PaymentProcessor>(mosaic) {
  override suspend fun retrieve(): PaymentProcessor {
    val customer = mosaic.getTile<CustomerTile>().get()
    
    // Choose processor based on customer tier
    return when (customer.tier) {
      CustomerTier.PREMIUM -> mosaic.getTile<PremiumProcessorTile>().get()
      CustomerTier.BUSINESS -> mosaic.getTile<BusinessProcessorTile>().get()
      else -> mosaic.getTile<StandardProcessorTile>().get()
    }
  }
}
```

## 🎯 **Response-First Design**

### **Traditional Approach (Database Down)**
```kotlin
// Imperative: manually orchestrating queries, passing data between functions
val order = orderRepository.findById(orderId)
val customer = customerRepository.findById(order.customerId) 
val lineItems = lineItemRepository.findByOrderId(orderId)
val productIds = lineItems.map { it.productId }
val products = productRepository.findByIds(productIds)
val prices = pricingService.getPrices(lineItems.map { it.sku })

// Data gets passed around everywhere - coupling and complexity
val enrichedItems = enrichLineItems(lineItems, products, prices)
val summary = buildOrderSummary(order, customer, enrichedItems)
val logistics = calculateLogistics(order, customer, enrichedItems)
// ... manual assembly, error handling, caching logic ...
```

### **Mosaic Approach (Response Up)**
```kotlin
// Declarative: tiles retrieve their own dependencies - no data passing!
class OrderPageTile(mosaic: Mosaic) : SingleTile<OrderPage>(mosaic) {
  override suspend fun retrieve(): OrderPage = coroutineScope {
    val summaryDeferred = async { mosaic.getTile<OrderSummaryTile>().get() }
    val logisticsDeferred = async { mosaic.getTile<LogisticsTile>().get() }
    
    OrderPage(
      summary = summaryDeferred.await(),
      logistics = logisticsDeferred.await()
    )
  }
}

// Each tile knows how to get what it needs - no coupling!
class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary = coroutineScope {
    val orderDeferred = async { mosaic.getTile<OrderTile>().get() }
    val customerDeferred = async { mosaic.getTile<CustomerTile>().get() }
    val lineItemsDeferred = async { mosaic.getTile<LineItemsTile>().get() }
    
    OrderSummary(
      order = orderDeferred.await(),
      customer = customerDeferred.await(),
      lineItems = lineItemsDeferred.await()
    )
  }
}
```

## 🧩 **Deep Composition**

Mosaic shines when composing tiles multiple levels deep. Each tile focuses on one responsibility:

```kotlin
// Level 1: Entry point tile
class OrderPageTile(mosaic: Mosaic) : SingleTile<OrderPage>(mosaic) {
  override suspend fun retrieve(): OrderPage = coroutineScope {
    // Parallel execution of two major components
    val summaryDeferred = async { mosaic.getTile<OrderSummaryTile>().get() }
    val logisticsDeferred = async { mosaic.getTile<LogisticsTile>().get() }
    
    OrderPage(summaryDeferred.await(), logisticsDeferred.await())
  }
}

// Level 2: Summary aggregates order data
class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary = coroutineScope {
    // These three tiles run in parallel
    val orderDeferred = async { mosaic.getTile<OrderTile>().get() }
    val customerDeferred = async { mosaic.getTile<CustomerTile>().get() }
    val lineItemsDeferred = async { mosaic.getTile<LineItemsTile>().get() }
    
    OrderSummary(
      order = orderDeferred.await(),
      customer = customerDeferred.await(),
      lineItems = lineItemsDeferred.await()
    )
  }
}

// Level 3: Line items enriches with product and pricing data
class LineItemsTile(mosaic: Mosaic) : SingleTile<List<LineItemDetail>>(mosaic) {
  override suspend fun retrieve(): List<LineItemDetail> {
    val order = mosaic.getTile<OrderTile>().get()
    
    // Batch fetch products and prices in parallel
    val (products, prices) = coroutineScope {
      val productsDeferred = async { 
        mosaic.getTile<ProductsByIdTile>().getByKeys(order.productIds) 
      }
      val pricesDeferred = async { 
        mosaic.getTile<PricingBySkuTile>().getByKeys(order.skus) 
      }
      productsDeferred.await() to pricesDeferred.await()
    }
        
    return order.items.map { item ->
      LineItemDetail(
        product = products[item.productId],
        price = prices[item.sku],
        quantity = item.quantity
      )
    }
  }
}
```

## ⚡ **Zero Duplication**

Call the same tile from multiple places without redundant fetches:

```kotlin
class OrderTotalTile(mosaic: Mosaic) : SingleTile<Double>(mosaic) {
  override suspend fun retrieve(): Double {
    // This calls LineItemsTile
    val lineItems = mosaic.getTile<LineItemsTile>().get()
    return lineItems.sumOf { it.price.amount * it.quantity }
  }
}

class TaxCalculatorTile(mosaic: Mosaic) : SingleTile<Tax>(mosaic) {
  override suspend fun retrieve(): Tax {
    // Also calls LineItemsTile - but it's already cached!
    val lineItems = mosaic.getTile<LineItemsTile>().get()
    val address = mosaic.getTile<AddressTile>().get()
    return TaxService.calculate(lineItems, address)
  }
}

// In your controller:
val orderPage = mosaic.getTile<OrderPageTile>().get()    // Fetches LineItemsTile
val orderTotal = mosaic.getTile<OrderTotalTile>().get()  // Uses cached LineItemsTile
val tax = mosaic.getTile<TaxCalculatorTile>().get()      // Uses cached LineItemsTile
// LineItemsTile was only fetched ONCE!
```

## 🔧 **Batch Operations with MultiTile**

MultiTile abstracts batching strategy from consumers. **Key insight: if you request the same key multiple times, even in different lists, Mosaic automatically deduplicates and only fetches uncached keys.**

```kotlin
// Strategy 1: Large batch operations (efficient for bulk APIs)
class PricingBySkuTile(mosaic: Mosaic) : MultiTile<Price, Map<String, Price>>(mosaic) {
  override suspend fun retrieveForKeys(skus: List<String>): Map<String, Price> {
    // Single bulk API call - efficient for services that support batch operations
    return PricingService.getBulkPrices(skus)
  }
  
  override fun normalize(sku: String, response: Map<String, Price>): Price = response.getValue(sku)
}

// Strategy 2: Individual requests (for APIs without batch support)
class ProductByIdTile(mosaic: Mosaic) : MultiTile<Product, List<Product>>(mosaic) {
  override suspend fun retrieveForKeys(productIds: List<String>): List<Product> {
    // Make individual calls concurrently when no batch API exists
    return coroutineScope {
      productIds.map { id ->
        async { ProductService.getProduct(id) }
      }.awaitAll()
    }
  }
  
  override fun normalize(productId: String, response: List<Product>): Product {
    return response.first { it.id == productId }
  }
}

// Strategy 3: Chunked requests (respect API rate limits)
class InventoryBySkuTile(mosaic: Mosaic) : MultiTile<Inventory, Map<String, Inventory>>(mosaic) {
  override suspend fun retrieveForKeys(skus: List<String>): Map<String, Inventory> {
    // API only allows 10 items per request - chunk to respect limits
    return skus.chunked(10).map { chunk ->
      InventoryService.getInventory(chunk)
    }.reduce { acc, map -> acc + map }
  }
    
  override fun normalize(sku: String, response: Map<String, Inventory>): Inventory = response.getValue(sku)
}

// Consumer code - batching is completely abstracted:
val prices1 = mosaic.getTile<PricingBySkuTile>().getByKeys(listOf("SKU1", "SKU2"))
val prices2 = mosaic.getTile<PricingBySkuTile>().getByKeys(listOf("SKU2", "SKU3"))
// SKU2 is only fetched ONCE - automatically deduplicated!
```

## 🧪 **Testing: The Game Changer**

**Testing complex APIs is hard. Mosaic makes it trivial.**

In traditional backends, testing requires intricate mocking of repositories, services, and data flow. With Mosaic, you mock individual tiles and test compositions in complete isolation.

```kotlin
// Test a complex 3-level composition by mocking just the dependencies
@Test
fun `order page composes correctly`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withMockTile(OrderSummaryTile::class, mockSummary)
    .withMockTile(LogisticsTile::class, mockLogistics)
    .build()
  
  // Test the composition logic without any external dependencies
  testMosaic.assertEquals(
    tileClass = OrderPageTile::class,
    expected = OrderPage(mockSummary, mockLogistics)
  )
}

// Test error propagation through the composition chain
@Test
fun `handles service failures gracefully`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withMockTile(OrderTile::class, mockOrder)
    .withFailedTile(CustomerTile::class, CustomerServiceException("Service down"))
    .withMockTile(LineItemsTile::class, mockLineItems)
    .build()
  
  // Verify the error bubbles up correctly
  testMosaic.assertThrows(
    tileClass = OrderSummaryTile::class,
    expectedException = CustomerServiceException::class.java
  )
}

// Test performance characteristics and timeouts
@Test  
fun `handles slow external services`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withDelayedTile(ExternalApiTile::class, mockData, delayMs = 500)
    .build()
  
  val startTime = System.currentTimeMillis()
  testMosaic.assertEquals(ExternalApiTile::class, mockData)
  val elapsed = System.currentTimeMillis() - startTime
  
  assertTrue(elapsed >= 500, "Should respect external service latency")
}
```

**Why this matters:** In a traditional API with 20+ services, you'd need to mock databases, HTTP clients, message queues, and coordinate complex test data. With Mosaic, you mock 2-3 tiles and test your composition logic in isolation.

## 🌐 **Framework Integration**

### **Spring Boot**

```kotlin
@Configuration
class MosaicConfig {
  @Bean
  fun mosaicRegistry(): MosaicRegistry {
    val registry = MosaicRegistry()
    // Auto-registers all tiles via KSP-generated code
    registry.registerGeneratedTiles()
    return registry
  }
}

@RestController
class OrderController(private val registry: MosaicRegistry) {
  @GetMapping("/orders/{id}")
  fun getOrder(@PathVariable id: String): OrderPage = runBlocking {
    val mosaic = Mosaic(registry, OrderRequest(id))
    mosaic.getTile<OrderPageTile>().get()
  }
    
  @GetMapping("/orders/{id}/total")
  fun getOrderTotal(@PathVariable id: String): Double = runBlocking {
    val mosaic = Mosaic(registry, OrderRequest(id))
    mosaic.getTile<OrderTotalTile>().get()
  }
}
```

### **Ktor**

```kotlin
fun Application.module() {
  install(ContentNegotiation) { json() }
  
  val registry = MosaicRegistry()
  registry.registerGeneratedTiles()
  
  routing {
    get("/orders/{id}") {
      val orderId = call.parameters["id"] ?: error("Missing order ID")
      val mosaic = Mosaic(registry, OrderRequest(orderId))
      val orderPage = mosaic.getTile<OrderPageTile>().get()
      call.respond(orderPage)
    }
    
    get("/orders/{id}/total") {
      val orderId = call.parameters["id"] ?: error("Missing order ID")
      val mosaic = Mosaic(registry, OrderRequest(orderId))
      val total = mosaic.getTile<OrderTotalTile>().get()
      call.respond(mapOf("total" to total))
    }
  }
}
```

### **Micronaut**

```kotlin
@Factory
class MosaicConfiguration {
  @Bean
  @Singleton
  fun mosaicRegistry(): MosaicRegistry {
    val registry = MosaicRegistry()
    registry.registerGeneratedTiles()
    return registry
  }
}

@Controller("/orders")
class OrderController(private val registry: MosaicRegistry) {
    
  @Get("/{id}")
  fun getOrder(@PathVariable id: String): OrderPage = runBlocking {
    val mosaic = Mosaic(registry, OrderRequest(id))
    mosaic.getTile<OrderPageTile>().get()
  }
  
  @Get("/{id}/total")
  fun getOrderTotal(@PathVariable id: String): Map<String, Double> = runBlocking {
    val mosaic = Mosaic(registry, OrderRequest(id))
    val total = mosaic.getTile<OrderTotalTile>().get()
    mapOf("total" to total)
  }
}
```

## 🎯 **Perfect For**

- **🚀 High-performance APIs** requiring efficient data access
- **🔄 Complex backend orchestration** with multiple data sources  
- **🏗️ Microservices** that need to compose data from various services
- **📊 GraphQL resolvers** that benefit from intelligent caching
- **⚡ Real-time applications** requiring concurrent data access
- **🎨 Any system** where you want to think in terms of responses rather than queries

## 🌟 **Key Benefits**

- **🎯 Response-First**: Think from the response up, not database down
- **⚡ Zero Duplication**: Intelligent caching eliminates redundant fetches
- **🔄 Automatic Concurrency**: Parallel execution without complexity
- **🧩 Type Safety**: Compile-time guarantees for all dependencies
- **🧪 Natural Testability**: Mock any tile, test in isolation
- **📦 Production Ready**: Handles errors, edge cases, and performance optimization

Mosaic transforms backend development by making data composition as natural as function composition, with enterprise-grade performance and reliability.

## 🔗 **Related Modules**

- **[mosaic-core](../mosaic-core/README.md)**: The core framework for composable backend orchestration
- **[mosaic-test](../mosaic-test/README.md)**: Testing framework for tiles

## 📄 **License**

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

Copyright 2025 Nicholas Abbott
