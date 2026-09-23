# Mosaic Core

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

**The next-generation DSL-based framework for composable backend orchestration.**

Mosaic-core introduces a revolutionary DSL approach that eliminates boilerplate and makes tile composition as natural as writing sequential code. Build complex data orchestrations with simple, expressive syntax.

## 🚀 **Quick Start**

### **Installation**

```kotlin
dependencies {
  implementation("org.buildmosaic:mosaic-core:0.3.0")
}
```

### **Your First Tile**

```kotlin
val UserIdKey = CanvasKey(String::class, "userId")

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
val inventoryTile = chunkedMultiTile<String, Inventory>(batchSize = 50) { skus ->
  // Processes up to 50 SKUs at a time to respect API limits
  InventoryService.checkInventory(skus)
}
```

## ⚡ **DSL-Powered Composition**

### **Natural Data Flow**

Compose tiles using simple `compose()` calls - no complex class hierarchies:

```kotlin
val orderSummaryTile = singleTile<OrderSummary> {
  // Start independent work before awaiting the results
  val order = composeAsync(orderTile)
  val customer = composeAsync(customerTile)
  val lineItems = composeAsync(lineItemsTile)

  OrderSummary(order.await(), customer.await(), lineItems.await())
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

Start independent tile work with `composeAsync`; sequential `compose` calls
remain sequential:

```kotlin
val dashboardTile = singleTile<Dashboard> {
  // All these tiles start executing immediately in parallel
  val user = composeAsync(userTile)
  val orders = composeAsync(recentOrdersTile)
  val recommendations = composeAsync(recommendationsTile)
  val notifications = composeAsync(notificationsTile)

  // Await the results after starting all four tiles
  Dashboard(user.await(), orders.await(), recommendations.await(), notifications.await())
}
```

### **Dynamic Key Generation**

Generate keys dynamically based on other tile results:

```kotlin
val relatedProductsTile = singleTile<List<Product>> {
  val order = compose(orderTile)
  val categoryIds = order.items.map { it.categoryId }.distinct()

  // Dynamic multi-tile call based on order contents
  val productsByCategory = compose(productsByCategoryTile, categoryIds)
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
val UserIdKey = CanvasKey(String::class, "userId")
val LocaleKey = CanvasKey(String::class, "locale")

val userPreferencesTile = singleTile<Preferences> {
  val userId = source(UserIdKey)
  val locale = sourceOr(LocaleKey) ?: "en-US"

  source<PreferencesService>().getPreferences(userId, locale)
}
```

### **Canvas Creation and Usage**

Create a Canvas with your dependencies and data sources:

```kotlin
suspend fun createApplicationCanvas(): MosaicCanvas = canvas {
  // Configure your dependency injection here
  single<UserService> { UserServiceImpl() }
  single<PreferencesService> { PreferencesServiceImpl() }
}

// Add request values and compose the response.
suspend fun loadPreferences(applicationCanvas: Canvas): Preferences =
  applicationCanvas.withLayer {
    single(UserIdKey) { "user-123" }
    single(LocaleKey) { "en-US" }
  }.create().compose(userPreferencesTile)
```

A Canvas closes only its locally created `AutoCloseable` bindings when closed;
closing a child does not close parent resources. Close a resource-owning
application Canvas at shutdown, and explicitly scope a child if it owns
resources needing cleanup.

## 🎯 **Key Advantages**

### **Zero Boilerplate**
- No abstract classes or inheritance hierarchies
- No manual cache management
- Use `composeAsync` when independent work should overlap

### **Natural Composition**
- Write tiles like regular suspend functions
- Compose using simple `compose()` calls
- Standard Kotlin control flow works everywhere

### **Automatic Optimization**
- MultiTile batching strategies and per-key deduplication
- Explicit `composeAsync` for overlapping independent work
- Request-scoped caching built-in

### **Type Safety**
- Full Kotlin type inference
- Typed Canvas lookups; missing bindings fail at runtime without optional analysis
- Generic type parameters preserved

For supported Kotlin/JVM application roots, the optional
[analysis plugin](../mosaic-gradle-plugin/README.md) can check Canvas bindings at
build time. Runtime composition does not require that plugin or Mosaic KSP.

## 📦 **Framework Integration**

### **Standalone Usage**

```kotlin
suspend fun createApplicationCanvas(): MosaicCanvas = canvas {
  single<UserService> { UserServiceImpl() }
  single<DashboardService> { DashboardServiceImpl() }
}

suspend fun dashboard(applicationCanvas: Canvas): Dashboard =
  applicationCanvas.withLayer {
    single(UserIdKey) { "123" }
  }.create().compose(userDashboardTile)
```

### **Spring Integration**

```kotlin
@Configuration
class MosaicConfig {
  @Bean
  fun mosaicCanvas(): Canvas = runBlocking {
    canvas {
      single<UserService> { UserServiceImpl() }
      single<DashboardService> { DashboardServiceImpl() }
    }
  }
}

@RestController
class UserController(private val canvas: Canvas) {

  @GetMapping("/users/{userId}/dashboard")
  fun getUserDashboard(@PathVariable userId: String): Dashboard =
    runBlocking {
      canvas.withLayer {
        single(UserIdKey) { userId }
      }.create().compose(userDashboardTile)
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
      val dashboard = canvas.withLayer {
        single(UserIdKey) { userId }
      }.create().compose(userDashboardTile)
      call.respond(dashboard)
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
- Multi-tiles group uncached keys for the fetch strategy you choose
- Chunked processing for large datasets
- Configurable batch sizes and strategies

### **Concurrency**
- `composeAsync` starts independent work before awaiting results
- Sequential `compose` calls execute sequentially
- Framework handles synchronization

## 🔗 **Related Modules**

- **[mosaic-test](../mosaic-test/README.md)**: DSL-based testing framework
- **[mosaic-bom](../mosaic-bom/README.md)**: Optional BOM for version alignment
