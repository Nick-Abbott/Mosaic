# Mosaic Core

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**The core framework for composable backend orchestration.**

Mosaic-core provides the fundamental building blocks for creating type-safe, cacheable, and composable data access patterns through tiles.

## 🧩 **Core Components**

### **SingleTile**

Caches a single value per request context:

```kotlin
abstract class SingleTile<T>(mosaic: Mosaic) : Tile(mosaic) {
  abstract suspend fun retrieve(): T
  suspend fun get(): T  // Returns cached value or calls retrieve()
}
```

**Usage:**
```kotlin
class CustomerTile(mosaic: Mosaic) : SingleTile<Customer>(mosaic) {
  override suspend fun retrieve(): Customer {
    val customerId = (mosaic.request as OrderRequest).customerId
    return CustomerService.fetchCustomer(customerId)
  }
}
```

### **MultiTile**

Caches multiple values with batch fetching optimization:

```kotlin
abstract class MultiTile<T, R>(mosaic: Mosaic) : Tile(mosaic) {
  abstract suspend fun retrieveForKeys(keys: List<String>): R
  abstract fun normalize(key: String, response: R): T
  suspend fun getByKeys(keys: List<String>): Map<String, T>
}
```

**Usage:**
```kotlin
class PricingBySkuTile(mosaic: Mosaic) : MultiTile<Price, Map<String, Price>>(mosaic) {
  override suspend fun retrieveForKeys(skus: List<String>): Map<String, Price> {
    return PricingService.getBulkPrices(skus)
  }
  
  override fun normalize(sku: String, response: Map<String, Price>): Price = 
    response.getValue(sku)
}
```

### **Mosaic Registry**

Dependency injection container for tiles:

```kotlin
class MosaicRegistry {
  fun <T : Tile> registerTile(tileClass: KClass<T>)
  fun <T : Tile> getTile(tileClass: KClass<T>, mosaic: Mosaic): T
}
```

**Usage:**
```kotlin
val registry = MosaicRegistry()
registry.registerTile(CustomerTile::class)
registry.registerTile(OrderTile::class)
// Or use KSP-generated registration:
registry.registerGeneratedTiles()
```

### **Mosaic**

Main orchestration class that manages tile lifecycle and caching:

```kotlin
class Mosaic(
  private val registry: MosaicRegistry,
  val request: MosaicRequest
) {
  suspend fun <T : SingleTile<*>> getTile(tileClass: KClass<T>): T
  suspend fun <T : MultiTile<*, *>> getTile(tileClass: KClass<T>): T
}
```

**Usage:**
```kotlin
val mosaic = Mosaic(registry, OrderRequest("order-123"))
val customer = mosaic.getTile<CustomerTile>().get()
val prices = mosaic.getTile<PricingBySkuTile>().getByKeys(listOf("SKU1", "SKU2"))
```

## ⚡ **Key Features**

### **Intelligent Caching**
- Tiles cache results automatically within a request context
- Multiple calls to the same tile return cached results
- Concurrent requests to the same tile are deduplicated

### **Automatic Concurrency**
- Tiles can be retrieved in parallel using `coroutineScope` and `async`
- Framework handles thread safety and concurrent access
- No manual synchronization required

### **Type Safety**
- Compile-time guarantees for tile dependencies
- Generic type parameters ensure correct data flow
- KClass-based tile resolution prevents runtime errors

### **Request Context Propagation**
- `MosaicRequest` carries request-specific data (auth, headers, etc.)
- Available to all tiles in the composition chain
- Enables context-aware data fetching

## 🔧 **Advanced Patterns**

### **Parallel Composition**
```kotlin
class OrderSummaryTile(mosaic: Mosaic) : SingleTile<OrderSummary>(mosaic) {
  override suspend fun retrieve(): OrderSummary = coroutineScope {
    // These three tiles run concurrently
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

### **Sequential Composition**
```kotlin
class PaymentProcessorTile(mosaic: Mosaic) : SingleTile<PaymentProcessor>(mosaic) {
  override suspend fun retrieve(): PaymentProcessor {
    val customer = mosaic.getTile<CustomerTile>().get()
    
    // Choose processor based on customer data
    return when (customer.tier) {
      CustomerTier.PREMIUM -> mosaic.getTile<PremiumProcessorTile>().get()
      CustomerTier.BUSINESS -> mosaic.getTile<BusinessProcessorTile>().get()
      else -> mosaic.getTile<StandardProcessorTile>().get()
    }
  }
}
```

### **Batch Deduplication**
```kotlin
// Multiple calls are automatically deduplicated
val prices1 = mosaic.getTile<PricingBySkuTile>().getByKeys(listOf("SKU1", "SKU2"))
val prices2 = mosaic.getTile<PricingBySkuTile>().getByKeys(listOf("SKU2", "SKU3"))
// Framework only calls retrieveForKeys(["SKU1", "SKU3"]) - SKU2 is cached!
```

## 🎯 **Design Principles**

- **Response-First**: Design from the response structure down to data sources
- **Composability**: Small, focused tiles that combine into complex responses
- **Zero Duplication**: Intelligent caching eliminates redundant data fetches
- **Type Safety**: Compile-time guarantees for all tile dependencies
- **Testability**: Easy isolation and mocking of tile dependencies

## 📦 **Dependencies**

- **Kotlin Coroutines**: For async/await and concurrent execution
- **Kotlin Reflection**: For KClass-based tile resolution
- **No external frameworks**: Core remains framework-agnostic

## 🔗 **Related Modules**

- **[mosaic-test](../mosaic-test/README.md)**: Testing framework for tiles
- **[mosaic-consumer-plugin](../mosaic-consumer-plugin/)**: Gradle plugin for automatic tile registration
- **[mosaic-catalog-ksp](../mosaic-catalog-ksp/)**: KSP processor for tile catalog generation
