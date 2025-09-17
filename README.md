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
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.2.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
  testImplementation("org.buildmosaic:mosaic-test:0.2.0")
  testImplementation(kotlin("test"))
}
```

### **Your First Tile**

```kotlin
// A simple tile that fetches and caches data
val CustomerTile = singleTile {
  val customerId = source(CustomerIdKey) // Or source<String>("customerId")
  CustomerService.getCustomer(customerId)
}

// Parallel composition: These tiles run concurrently
val OrderSummaryTile = singleTile {
  // These run in parallel automatically!
  val orderDeferred = composeAsync(OrderTile)
  val customerDeferred = composeAsync(CustomerTile)
  val lineItemsDeferred = composeAsync(LineItemsTile)
  
  OrderSummary(
    order = orderDeferred.await(),
    customer = customerDeferred.await(),
    lineItems = lineItemsDeferred.await()
  )
}

// Sequential composition: Choose tiles based on previous results
val PaymentProcessorTile = singleTile {
  val customer = compose(CustomerTile)
  
  // Choose processor based on customer tier
  when (customer.tier) {
    CustomerTier.PREMIUM -> compose(PremiumProcessorTile)
    CustomerTier.BUSINESS -> compose(BusinessProcessorTile)
    else -> compose(StandardProcessorTile)
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
val OrderPageTile = singleTile {
  val summaryDeferred = composeAsync(OrderSummaryTile)
  val logisticsDeferred = composeAsync(LogisticsTile)
  
  OrderPage(
    summary = summaryDeferred.await(),
    logistics = logisticsDeferred.await()
  )
}

// Each tile knows how to get what it needs - no coupling!
val OrderSummaryTile = singleTile {
  val orderDeferred = composeAsync(OrderTile)
  val customerDeferred = composeAsync(CustomerTile)
  val lineItemsDeferred = composeAsync(LineItemsTile)
  
  OrderSummary(
    order = orderDeferred.await(),
    customer = customerDeferred.await(),
    lineItems = lineItemsDeferred.await()
  )
}
```

## 🧩 **Deep Composition**

Mosaic shines when composing tiles multiple levels deep. Each tile focuses on one responsibility:

```kotlin
// Level 1: Entry point tile
val OrderPageTile = singleTile {
  // Parallel execution of two major components
  val summaryDeferred = composeAsync(OrderSummaryTile)
  val logisticsDeferred = composeAsync(LogisticsTile)
  
  OrderPage(summaryDeferred.await(), logisticsDeferred.await())
}

// Level 2: Summary aggregates order data
val OrderSummaryTile = singleTile {
  // These three tiles run in parallel
  val orderDeferred = composeAsync(OrderTile)
  val customerDeferred = composeAsync(CustomerTile)
  val lineItemsDeferred = composeAsync(LineItemsTile)
  
  OrderSummary(
    order = orderDeferred.await(),
    customer = customerDeferred.await(),
    lineItems = lineItemsDeferred.await()
  )
}

// Level 3: Line items enriches with product and pricing data
val LineItemsTile = singleTile {
  val order = compose(OrderTile)
  
  // Batch fetch products and prices in parallel
  val productsDeferred = composeAsync(ProductsByIdTile, order.productIds)
  val pricesDeferred = composeAsync(PricingBySkuTile, order.skus)
  val products = productsDeferred.await()
  val prices = pricesDeferred.await()
      
  order.items.map { item ->
    LineItemDetail(
      product = products[item.productId],
      price = prices[item.sku],
      quantity = item.quantity
    )
  }
}
```

## ⚡ **Zero Duplication**

Call the same tile from multiple places without redundant fetches:

```kotlin
val OrderTotalTile = singleTile {
  // This calls LineItemsTile
  val lineItems = compose(LineItemsTile)
  lineItems.sumOf { it.price.amount * it.quantity }
}

val TaxCalculatorTile = singleTile {
  // Also calls LineItemsTile - but it's already cached!
  val lineItems = compose(LineItemsTile)
  val address = compose(AddressTile)
  TaxService.calculate(lineItems, address)
}

// In your controller:
val orderPage = mosaic.compose(OrderPageTile)    // Fetches LineItemsTile
val orderTotal = mosaic.compose(OrderTotalTile)  // Uses cached LineItemsTile
val tax = mosaic.compose(TaxCalculatorTile)      // Uses cached LineItemsTile
// LineItemsTile was only fetched ONCE!
```

## 🏗️ **Dependency Injection with Canvas**

Canvas provides hierarchical dependency injection that separates application-level dependencies from request-specific data. This enables clean separation of concerns and efficient resource management.

### **Creating the Application Canvas**

```kotlin
// Create your main application canvas with long-lived dependencies
val applicationCanvas = canvas {
  // Database connections
  single<DataSource> { 
    HikariDataSource().apply {
      jdbcUrl = "jdbc:postgresql://localhost:5432/myapp"
      username = "user"
      password = "password"
    }
  }
  
  // Services that depend on the database
  single<UserService> { 
    UserServiceImpl(source<DataSource>()) 
  }
  
  single<OrderService> { 
    OrderServiceImpl(source<DataSource>()) 
  }
  
  // External API clients
  single<PaymentClient> {
    PaymentClientImpl(apiKey = System.getenv("PAYMENT_API_KEY"))
  }
  
  // Configuration
  single<AppConfig> { loadAppConfig() }
}
```

### **Adding Request-Specific Layers**

```kotlin
// In your controller/handler, add request-specific data as a layer
suspend fun handleOrderRequest(orderId: String, userId: String) {
  val requestMosaic = applicationCanvas.withLayer {
    // Request-specific data
    single<String>("orderId") { orderId }
    single<String>("userId") { userId }
    single<Instant>("requestTime") { Instant.now() }
    
    // You can also override application dependencies for testing
    // single<PaymentClient> { MockPaymentClient() }
  }.create()
  
  // Use the mosaic with both application and request dependencies
  val orderPage = requestMosaic.compose(OrderPageTile)
  return orderPage
}
```

### **Accessing Dependencies in Tiles**

```kotlin
// Tiles can access both application and request dependencies
val OrderTile = singleTile {
  val orderId = source<String>("orderId")
  val orderService = source<OrderService>()  // From application canvas
  orderService.getOrder(orderId)
}

val CustomerTile = singleTile {
  val userId = source<String>("userId")
  val userService = source<UserService>()    // From application canvas
  userService.getUser(userId)
}

val PaymentTile = singleTile {
  val order = compose(OrderTile)
  val paymentClient = source<PaymentClient>() // From application canvas
  val requestTime = source<Instant>("requestTime") // From request layer
  
  paymentClient.getPaymentStatus(order.paymentId, requestTime)
}

// Complex tile that uses multiple dependencies
val OrderSummaryTile = singleTile {
  val orderDeferred = composeAsync(OrderTile)
  val customerDeferred = composeAsync(CustomerTile) 
  val paymentDeferred = composeAsync(PaymentTile)
  
  // All tiles have access to the same dependency context
  OrderSummary(
    order = orderDeferred.await(),
    customer = customerDeferred.await(),
    payment = paymentDeferred.await()
  )
}
```

### **Typed Keys for Better Safety**

```kotlin
// Define typed keys for better compile-time safety
object OrderIdKey : CanvasKey<String>(String::class, "orderId")
object UserIdKey : CanvasKey<String>(String::class, "userId")

// Use in canvas configuration
val requestMosaic = applicationCanvas.withLayer {
  single(OrderIdKey) { orderId }
  single(UserIdKey) { userId }
}.create()

// Use in tiles
val OrderTile = singleTile {
  val orderId = source(OrderIdKey)  // Type-safe!
  val orderService = source<OrderService>()
  orderService.getOrder(orderId)
}
```

### **Canvas Hierarchy Benefits**

- **Separation of Concerns**: Application dependencies separate from request data
- **Resource Efficiency**: Database connections and services created once, reused across requests
- **Testing Flexibility**: Override any dependency at any layer for testing
- **Type Safety**: Compile-time guarantees for dependency resolution
- **Automatic Cleanup**: Canvas implements `AutoCloseable` for resource management

## 🔧 **Batch Operations with MultiTile**

MultiTile abstracts batching strategy from consumers. **Key insight: if you request the same key multiple times, even in different lists, Mosaic automatically deduplicates and only fetches uncached keys.**

```kotlin
// Strategy 1: Large batch operations (efficient for bulk APIs)
val PricingBySkuTile = multiTile { skus ->
  // Single bulk API call - efficient for services that support batch operations
  PricingService.getBulkPrices(skus.toList())
}

// Strategy 2: Individual requests (for APIs without batch support)
val ProductByIdTile = perKeyTile { productId ->
  // Make individual calls concurrently when no batch API exists
  ProductService.getProduct(productId)
}

// Strategy 3: Chunked requests (respect API rate limits)
val InventoryBySkuTile = chunkedMultiTile(10) { skus ->
  // API only allows 10 items per request - chunk to respect limits
  InventoryService.getInventory(skus)
}

// Consumer code - batching is completely abstracted:
val prices1 = mosaic.compose(PricingBySkuTile, listOf("SKU1", "SKU2"))
val prices2 = mosaic.compose(PricingBySkuTile, listOf("SKU2", "SKU3"))
// SKU2 is only fetched ONCE - automatically deduplicated!
```

## 🧪 **Testing: The Game Changer**

**Testing complex APIs is hard. Mosaic makes it trivial.**

In traditional backends, testing requires intricate mocking of repositories, services, and data flow. With Mosaic, you mock individual tiles and test compositions in complete isolation.

```kotlin
// Test a complex 3-level composition by mocking just the dependencies
@Test
fun `order page composes correctly`() = runTest {
  val testMosaic = TestMosaicBuilder(this)
    .withMockTile(OrderSummaryTile, mockSummary)
    .withMockTile(LogisticsTile, mockLogistics)
    .build()
  
  // Test the composition logic without any external dependencies
  testMosaic.assertEquals(
    tile = OrderPageTile,
    expected = OrderPage(mockSummary, mockLogistics)
  )
}

// Test error propagation through the composition chain
@Test
fun `handles service failures gracefully`() = runTest {
  val testMosaic = TestMosaicBuilder(this)
    .withMockTile(OrderTile, mockOrder)
    .withFailedTile(CustomerTile, CustomerServiceException("Service down"))
    .withMockTile(LineItemsTile, mockLineItems)
    .build()
  
  // Verify the error bubbles up correctly
  testMosaic.assertThrows(
    tile = OrderSummaryTile,
    expectedException = CustomerServiceException::class
  )
}

// Test performance characteristics and timeouts
@Test  
fun `handles slow external services`() = runTest {
  val testMosaic = TestMosaicBuilder(this)
    .withDelayedTile(ExternalApiTile, mockData, delayMs = 500)
    .build()
  
  val startTime = System.currentTimeMillis()
  testMosaic.assertEquals(ExternalApiTile, mockData)
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
  fun mosaicCanvas(): Canvas = runBlocking {
    canvas {
      // Register your dependencies here
      single<UserService> { UserServiceImpl() }
      single<DatabaseConfig> { loadConfig() }
    }
  }
}

@RestController
class OrderController(private val canvas: Canvas) {
  @GetMapping("/orders/{id}")
  fun getOrder(@PathVariable id: String): OrderPage = runBlocking {
    val mosaic = canvas.withLayer {
      single(OrderKey.qualifier) { id }
    }.create()
    mosaic.compose(OrderPageTile)
  }
    
  @GetMapping("/orders/{id}/total")
  fun getOrderTotal(@PathVariable id: String): Double = runBlocking {
    val mosaic = canvas.withLayer {
      single(OrderKey.qualifier) { id }
    }.create()
    mosaic.compose(OrderTotalTile)
  }
}
```

### **Ktor**

```kotlin
fun Application.module() {
  install(ContentNegotiation) { json() }
  
  val canvas = runBlocking {
    canvas {
      // Register your dependencies here
      single<UserService> { UserServiceImpl() }
      single<DatabaseConfig> { loadConfig() }
    }
  }
  
  routing {
    get("/orders/{id}") {
      val orderId = call.parameters["id"] ?: error("Missing order ID")
      val mosaic = canvas.withLayer {
        single(OrderKey.qualifier) { orderId }
      }.create()
      val orderPage = mosaic.compose(OrderPageTile)
      call.respond(orderPage)
    }
    
    get("/orders/{id}/total") {
      val orderId = call.parameters["id"] ?: error("Missing order ID")
      val mosaic = canvas.withLayer {
        single(OrderKey.qualifier) { orderId }
      }.create()
      val total = mosaic.compose(OrderTotalTile)
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
  fun mosaicCanvas(): Canvas = runBlocking {
    canvas {
      // Register your dependencies here
      single<UserService> { UserServiceImpl() }
      single<DatabaseConfig> { loadConfig() }
    }
  }
}

@Controller("/orders")
class OrderController(private val canvas: Canvas) {
    
  @Get("/{id}")
  fun getOrder(@PathVariable id: String): OrderPage = runBlocking {
    val mosaic = canvas.withLayer {
      single(OrderKey.qualifier) { id }
    }.create()
    mosaic.compose(OrderPageTile)
  }
  
  @Get("/{id}/total")
  fun getOrderTotal(@PathVariable id: String): Map<String, Double> = runBlocking {
    val mosaic = canvas.withLayer {
      single(OrderKey.qualifier) { id }
    }.create()
    val total = mosaic.compose(OrderTotalTile)
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
