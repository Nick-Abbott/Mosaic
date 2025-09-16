# Mosaic Core V2

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**The next-generation DSL-based framework for composable backend orchestration.**

Mosaic-core-v2 introduces a revolutionary DSL approach that eliminates boilerplate and makes tile composition as natural as writing sequential code. Build complex data orchestrations with simple, expressive syntax.

## 🚀 **Quick Start**

### **Installation**

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core-v2:0.1.0")
}
```

### **Your First Tile**

```kotlin
val userTile = singleTile<User> {
  val userId = source(UserIdKey)
  UserService.fetchUser(userId)
}

val ordersTile = multiTile<String, Order> { orderIds ->
  OrderService.fetchOrders(orderIds)
}
```

## 🧩 **Core DSL Functions**

### **singleTile**

Creates a tile that returns a single value with automatic caching:

```kotlin
val customerTile = singleTile<Customer> {
  val customerId = source(CustomerIdKey)
  CustomerService.fetchCustomer(customerId)
}
```

### **multiTile**

Creates a tile that efficiently batches multiple requests:

```kotlin
val pricingTile = multiTile<String, Price> { skus ->
  // Automatically batches requests for multiple SKUs
  PricingService.getBulkPrices(skus)
}
```

### **perKeyTile**

Creates a tile that processes each key individually but with shared caching:

```kotlin
val productTile = perKeyTile<String, Product> { sku ->
  // Called once per unique SKU, results are cached
  ProductService.getProduct(sku)
}
```

### **chunkedMultiTile**

Creates a tile that processes requests in configurable chunks:

```kotlin
val inventoryTile = chunkedMultiTile<String, Inventory>(chunkSize = 50) { skus ->
  // Processes up to 50 SKUs at a time to respect API limits
  InventoryService.checkInventory(skus)
}
```

## ⚡ **DSL-Powered Composition**

### **Natural Data Flow**

Compose tiles using simple `get()` calls - no complex class hierarchies:

```kotlin
val orderSummaryTile = singleTile<OrderSummary> {
  // These run concurrently automatically
  val order = get(orderTile)
  val customer = get(customerTile) 
  val lineItems = get(lineItemsTile)
  
  OrderSummary(order, customer, lineItems)
}
```

### **Multi-Tile Integration**

Seamlessly mix single and multi tiles:

```kotlin
val enrichedOrderTile = singleTile<EnrichedOrder> {
  val order = get(orderTile)
  
  // Batch fetch all required data
  val products = get(productTile, order.skus)
  val prices = get(pricingTile, order.skus)
  val inventory = get(inventoryTile, order.skus)
  
  EnrichedOrder(order, products, prices, inventory)
}
```

### **Conditional Logic**

Use standard Kotlin control flow within tiles:

```kotlin
val paymentProcessorTile = singleTile<PaymentProcessor> {
  val customer = get(customerTile)
  
  when (customer.tier) {
    CustomerTier.PREMIUM -> get(premiumProcessorTile)
    CustomerTier.BUSINESS -> get(businessProcessorTile)
    else -> get(standardProcessorTile)
  }
}
```

## 🔧 **Advanced Patterns**

### **Parallel Execution**

The DSL automatically optimizes for concurrency:

```kotlin
val dashboardTile = singleTile<Dashboard> {
  // All these tiles start executing immediately in parallel
  val user = get(userTile)
  val orders = get(recentOrdersTile)
  val recommendations = get(recommendationsTile)
  val notifications = get(notificationsTile)
  
  // Results are awaited only when accessed
  Dashboard(user, orders, recommendations, notifications)
}
```

### **Dynamic Key Generation**

Generate keys dynamically based on other tile results:

```kotlin
val relatedProductsTile = singleTile<List<Product>> {
  val order = get(orderTile)
  val categoryIds = order.items.map { it.categoryId }.distinct()
  
  // Dynamic multi-tile call based on order contents
  val productsByCategory = get(productsByCategoryTile, categoryIds)
  productsByCategory.values.flatten().take(10)
}
```

### **Error Handling**

Standard Kotlin exception handling works naturally:

```kotlin
val resilientDataTile = singleTile<Data> {
  try {
    get(primaryDataTile)
  } catch (e: PrimaryServiceException) {
    // Fallback to secondary source
    get(fallbackDataTile)
  }
}
```

## 🏗 **Mosaic Context**

### **Canvas-Based Dependency Injection**

Access dependencies and data sources through the Canvas system:

```kotlin
// Define your data sources as keys
object UserIdKey : SourceKey<String>
object LocaleKey : SourceKey<String>

val userPreferencesTile = singleTile<Preferences> {
  val userId = source(UserIdKey)
  val locale = source(LocaleKey) ?: "en-US"
  
  PreferencesService.getPreferences(userId, locale)
}
```

### **Canvas Creation and Usage**

Create a Canvas with your dependencies and data sources:

```kotlin
val canvas = canvas {
  // Configure your dependency injection here
  single<UserService> { UserServiceImpl() }
  single<PreferencesService> { PreferencesServiceImpl() }
}

// Create a mosaic instance with specific data sources
val mosaic = canvas.withLayer {
  single(UserIdKey.qualifier) { "user-123" }
  single(LocaleKey.qualifier) { "en-US" }
}.create()

// Execute tiles
val preferences = mosaic.compose(userPreferencesTile)
```

## 🎯 **Key Advantages**

### **Zero Boilerplate**
- No abstract classes or inheritance hierarchies
- No manual cache management
- No explicit concurrency handling

### **Natural Composition**
- Write tiles like regular suspend functions
- Compose using simple `get()` calls
- Standard Kotlin control flow works everywhere

### **Automatic Optimization**
- Intelligent batching and deduplication
- Concurrent execution without manual async/await
- Request-scoped caching built-in

### **Type Safety**
- Full Kotlin type inference
- Compile-time dependency validation
- Generic type parameters preserved

## 📦 **Framework Integration**

### **Standalone Usage**

```kotlin
val canvas = canvas {
  single<UserService> { UserServiceImpl() }
  single<DashboardService> { DashboardServiceImpl() }
}

val mosaic = canvas.withLayer {
  single(UserIdKey.qualifier) { "123" }
}.create()

val result = mosaic.compose(userDashboardTile)
```

### **Spring Integration**

```kotlin
@Configuration
class MosaicConfig {
  @Bean
  suspend fun mosaicCanvas(): Canvas {
    return canvas {
      single<UserService> { UserServiceImpl() }
      single<DashboardService> { DashboardServiceImpl() }
    }
  }
}

@RestController
class UserController(private val canvas: Canvas) {
  
  @GetMapping("/users/{userId}/dashboard")
  suspend fun getUserDashboard(@PathVariable userId: String): Dashboard = 
    runBlocking {
      val mosaic = canvas.withLayer {
        single(UserIdKey.qualifier) { userId }
      }.create()
      mosaic.compose(userDashboardTile)
    }
}
```

### **Ktor Integration**

```kotlin
fun Application.module() {
  val canvas = runBlocking {
    canvas {
      single<UserService> { UserServiceImpl() }
      single<DashboardService> { DashboardServiceImpl() }
    }
  }

  routing {
    get("/users/{userId}/dashboard") {
      val userId = call.parameters["userId"]!!
      val mosaic = canvas.withLayer {
        single(UserIdKey.qualifier) { userId }
      }.create()
      
      call.respond(mosaic.compose(userDashboardTile))
    }
  }
}
```

## 🔍 **Performance Features**

### **Intelligent Caching**
- Results cached per request context
- Automatic deduplication of identical calls
- Concurrent access to same tile returns shared result

### **Batch Optimization**
- Multi-tiles automatically batch requests
- Chunked processing for large datasets
- Configurable batch sizes and strategies

### **Concurrency**
- Tiles execute concurrently by default
- No manual async/await required
- Framework handles synchronization

## 📊 **Monitoring & Observability**

The DSL approach maintains full observability:

```kotlin
// Tiles are introspectable for monitoring
val tileMetrics = mosaic.getExecutionMetrics()
val cacheStats = mosaic.getCacheStatistics()
```

## 🔗 **Related Modules**

- **[mosaic-test-v2](../mosaic-test-v2/README.md)**: DSL-based testing framework
- **[mosaic-core](../mosaic-core/README.md)**: Original class-based framework
- **[mosaic-consumer-plugin](../mosaic-consumer-plugin/)**: Gradle plugin for automatic tile registration
- **[mosaic-catalog-ksp](../mosaic-catalog-ksp/)**: KSP processor for tile catalog generation

## 🚀 **Migration from V1**

Migrating from mosaic-core to mosaic-core-v2 is straightforward:

```kotlin
// V1: Class-based approach with MosaicRegistry
class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val customerId = (mosaic.request as OrderRequest).customerId
    return CustomerService.fetchCustomer(customerId)
  }
}

// V2: DSL approach with Canvas-based DI
object CustomerIdKey : SourceKey<String>

val customerTile = singleTile<Customer> {
  val customerId = source(CustomerIdKey)
  CustomerService.fetchCustomer(customerId)
}

// Usage with Canvas
val canvas = canvas { /* configure DI */ }
val mosaic = canvas.withLayer {
  single(CustomerIdKey.qualifier) { "customer-123" }
}.create()
val result = mosaic.compose(customerTile)
```

The DSL approach eliminates boilerplate while maintaining all the power and performance of the original framework.
