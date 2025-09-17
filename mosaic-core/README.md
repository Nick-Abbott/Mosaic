# Mosaic Core

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**The next-generation DSL-based framework for composable backend orchestration.**

Mosaic-core introduces a revolutionary DSL approach that eliminates boilerplate and makes tile composition as natural as writing sequential code. Build complex data orchestrations with simple, expressive syntax.

## 🚀 **Quick Start**

### **Installation**

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.1.0")
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

Compose tiles using simple `compose()` calls - no complex class hierarchies:

```kotlin
val orderSummaryTile = singleTile<OrderSummary> {
  // These run concurrently automatically
  val order = compose(orderTile)
  val customer = compose(customerTile) 
  val lineItems = compose(lineItemsTile)
  
  OrderSummary(order, customer, lineItems)
}
```

### **Multi-Tile Integration**

Seamlessly mix single and multi tiles:

```kotlin
val enrichedOrderTile = singleTile<EnrichedOrder> {
  val order = compose(orderTile)
  
  // Batch fetch all required data
  val products = compose(productTile, order.skus)
  val prices = compose(pricingTile, order.skus)
  val inventory = compose(inventoryTile, order.skus)
  
  EnrichedOrder(order, products, prices, inventory)
}
```

### **Conditional Logic**

Use standard Kotlin control flow within tiles:

```kotlin
val paymentProcessorTile = singleTile<PaymentProcessor> {
  val customer = compose(customerTile)
  
  when (customer.tier) {
    CustomerTier.PREMIUM -> compose(premiumProcessorTile)
    CustomerTier.BUSINESS -> compose(businessProcessorTile)
    else -> compose(standardProcessorTile)
  }
}
```

## 🔧 **Advanced Patterns**

### **Parallel Execution**

The DSL automatically optimizes for concurrency:

```kotlin
val dashboardTile = singleTile<Dashboard> {
  // All these tiles start executing immediately in parallel
  val user = compose(userTile)
  val orders = compose(recentOrdersTile)
  val recommendations = compose(recommendationsTile)
  val notifications = compose(notificationsTile)
  
  // Results are awaited only when accessed
  Dashboard(user, orders, recommendations, notifications)
}
```

### **Dynamic Key Generation**

Generate keys dynamically based on other tile results:

```kotlin
val relatedProductsTile = singleTile<List<Product>> {
  val order = compose(orderTile)
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
    compose(primaryDataTile)
  } catch (e: PrimaryServiceException) {
    // Fallback to secondary source
    compose(fallbackDataTile)
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
- Compose using simple `compose()` calls
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

## 🔗 **Related Modules**

- **[mosaic-test](../mosaic-test/README.md)**: DSL-based testing framework
- **[mosaic-core](../mosaic-core/README.md)**: Original class-based framework
- **[mosaic-consumer-plugin](../mosaic-consumer-plugin/)**: Gradle plugin for automatic tile registration
- **[mosaic-catalog-ksp](../mosaic-catalog-ksp/)**: KSP processor for tile catalog generation
