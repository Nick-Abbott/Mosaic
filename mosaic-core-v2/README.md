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
  val userId = request.attributes["userId"] as String
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
  val customerId = request.attributes["customerId"] as String
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

### **Request Access**

Access request context anywhere in your tiles:

```kotlin
val userPreferencesTile = singleTile<Preferences> {
  val userId = request.attributes["userId"] as String
  val locale = request.attributes["locale"] as String? ?: "en-US"
  
  PreferencesService.getPreferences(userId, locale)
}
```

### **Dependency Injection**

Inject services directly into tiles:

```kotlin
val orderTile = singleTile<Order> {
  val orderService = inject<OrderService>()
  val orderId = request.attributes["orderId"] as String
  
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
val mosaic = Mosaic(
  request = MosaicRequest(mapOf("userId" to "123")),
  injector = MyInjector()
)

val result = mosaic.get(userDashboardTile)
```

### **Spring Integration**

```kotlin
@RestController
class UserController(private val mosaicRegistry: MosaicRegistry) {
  
  @GetMapping("/users/{userId}/dashboard")
  suspend fun getUserDashboard(@PathVariable userId: String): Dashboard = 
    runBlocking {
      val request = MosaicRequest(mapOf("userId" to userId))
      val mosaic = Mosaic(request, springInjector)
      mosaic.get(userDashboardTile)
    }
}
```

### **Ktor Integration**

```kotlin
routing {
  get("/users/{userId}/dashboard") {
    val userId = call.parameters["userId"]!!
    val request = MosaicRequest(mapOf("userId" to userId))
    val mosaic = Mosaic(request, koinInjector)
    
    call.respond(mosaic.get(userDashboardTile))
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
// V1: Class-based approach
class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val customerId = (mosaic.request as OrderRequest).customerId
    return CustomerService.fetchCustomer(customerId)
  }
}

// V2: DSL approach
val customerTile = singleTile<Customer> {
  val customerId = request.attributes["customerId"] as String
  CustomerService.fetchCustomer(customerId)
}
```

The DSL approach eliminates boilerplate while maintaining all the power and performance of the original framework.
