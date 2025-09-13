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
val USER_ID_KEY = SceneKey<String>("userId")

val userTile = singleTile<User> {
  val userId = scene.claim(USER_ID_KEY)
  val userService = canvas.source<UserService>()
  userService.fetchUser(userId)
}

val ordersTile = multiTile<String, Order> { orderIds ->
  val orderService = canvas.source<OrderService>()
  orderService.fetchOrders(orderIds)
}
```

## 🧩 **Core DSL Functions**

### **singleTile**

Creates a tile that returns a single value with automatic caching:

```kotlin
val CUSTOMER_ID_KEY = SceneKey<String>("customerId")

val customerTile = singleTile<Customer> {
  val customerId = scene.claim(CUSTOMER_ID_KEY)
  val customerService = canvas.source<CustomerService>()
  customerService.fetchCustomer(customerId)
}
```

### **multiTile**

Creates a tile that efficiently batches multiple requests:

```kotlin
val pricingTile = multiTile<String, Price> { skus ->
  val pricingService = canvas.source<PricingService>()
  // Automatically batches requests for multiple SKUs
  pricingService.getBulkPrices(skus)
}
```

### **perKeyTile**

Creates a tile that processes each key individually but with shared caching:

```kotlin
val productTile = perKeyTile<String, Product> { sku ->
  val productService = canvas.source<ProductService>()
  // Called once per unique SKU, results are cached
  productService.getProduct(sku)
}
```

### **chunkedMultiTile**

Creates a tile that processes requests in configurable chunks:

```kotlin
val inventoryTile = chunkedMultiTile<String, Inventory>(chunkSize = 50) { skus ->
  val inventoryService = canvas.source<InventoryService>()
  // Processes up to 50 SKUs at a time to respect API limits
  inventoryService.checkInventory(skus)
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

### **Scene Access**

Access request-scoped context anywhere in your tiles:

```kotlin
val USER_ID_KEY = SceneKey<String>("userId")
val LOCALE_KEY = SceneKey<String>("locale")

val userPreferencesTile = singleTile<Preferences> {
  val userId = scene.claim(USER_ID_KEY)
  val locale = scene.claimOr(LOCALE_KEY, "en-US")
  val preferencesService = canvas.source<PreferencesService>()
  
  preferencesService.getPreferences(userId, locale)
}
```

### **Canvas Injection**

Inject application-level services directly into tiles:

```kotlin
val ORDER_ID_KEY = SceneKey<String>("orderId")

val orderTile = singleTile<Order> {
  val orderService = canvas.source<OrderService>()
  val orderId = scene.claim(ORDER_ID_KEY)
  
  orderService.fetchOrder(orderId)
}
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
val USER_ID_KEY = SceneKey<String>("userId")

val scene = MosaicSceneBuilder()
  .registerClaim(USER_ID_KEY, "123")
  .build()

val canvas = MyCanvas() // implements Canvas interface
val mosaic = Mosaic(scene, canvas)

val result = mosaic.compose(userDashboardTile)
```

### **Spring Integration**

```kotlin
@RestController
class UserController(private val springCanvas: SpringCanvas) {
  
  @GetMapping("/users/{userId}/dashboard")
  suspend fun getUserDashboard(@PathVariable userId: String): Dashboard = 
    runBlocking {
      val scene = MosaicSceneBuilder()
        .registerClaim(USER_ID_KEY, userId)
        .build()
      val mosaic = Mosaic(scene, springCanvas)
      mosaic.compose(userDashboardTile)
    }
}
```

### **Ktor Integration**

```kotlin
routing {
  get("/users/{userId}/dashboard") {
    val userId = call.parameters["userId"]!!
    val scene = MosaicSceneBuilder()
      .registerClaim(USER_ID_KEY, userId)
      .build()
    val mosaic = Mosaic(scene, koinCanvas)
    
    call.respond(mosaic.compose(userDashboardTile))
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
// Note: Monitoring APIs are under development
val executionStats = mosaic.getExecutionStats()
val cacheHitRatio = mosaic.getCacheHitRatio()
```

## 🔗 **Related Modules**

- **[mosaic-test-v2](../mosaic-test-v2/README.md)**: DSL-based testing framework
- **[mosaic-core](../mosaic-core/README.md)**: Original class-based framework
- **[mosaic-consumer-plugin](../mosaic-consumer-plugin/)**: Gradle plugin for automatic tile registration
- **[mosaic-catalog-ksp](../mosaic-catalog-ksp/)**: KSP processor for tile catalog generation

## 🚀 **Migration from V1**

Migrating from mosaic-core to mosaic-core-v2 is straightforward:

```kotlin
// V1: Class-based approach
class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val customerId = (mosaic.request as OrderRequest).customerId
    return CustomerService.fetchCustomer(customerId)
  }
}

// V2: DSL approach with Canvas/Scene
val CUSTOMER_ID_KEY = SceneKey<String>("customerId")

val customerTile = singleTile<Customer> {
  val customerId = scene.claim(CUSTOMER_ID_KEY)
  val customerService = canvas.source<CustomerService>()
  customerService.fetchCustomer(customerId)
}
```

The DSL approach eliminates boilerplate while providing better type safety and cleaner separation of concerns through Canvas (application dependencies) and Scene (request context).
