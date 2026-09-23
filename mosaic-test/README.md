# Mosaic Test Framework

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](../LICENSE)

**Test your DSL-based Mosaic tiles with confidence.**

Mosaic-test provides a fluent testing API designed specifically for the new DSL-based tile approach. Mock dependencies, simulate failures, and verify behavior with type-safe assertions - all while working seamlessly with the natural tile composition patterns.

Inside `runTest`, call `mosaicBuilder()` to use the enclosing `TestScope`
and its scheduler. The examples use typed keys such as
`CanvasKey(String::class, "userId")` for request values.

```kotlin
val UserIdKey = CanvasKey(String::class, "userId")
val CustomerIdKey = CanvasKey(String::class, "customerId")
val OrderIdKey = CanvasKey(String::class, "orderId")
val LocaleKey = CanvasKey(String::class, "locale")
```

## 🚀 **Quick Start**

### **Installation**

```kotlin
dependencies {
  testImplementation("org.buildmosaic:mosaic-test:0.3.0")
}
```

### **Your First DSL Tile Test**

```kotlin
@Test
fun `user tile composes mocked data`() = runTest {
  val UserIdKey = CanvasKey(String::class, "userId")
  val userIdTile = singleTile { source(UserIdKey) }
  val userTile = singleTile { User(compose(userIdTile), "John Doe") }

  val testMosaic = mosaicBuilder()
    .withMockTile(userIdTile, "123")
    .build()

  testMosaic.assertEquals(userTile, User("123", "John Doe"))
}
```

## 🧪 **DSL Testing Patterns**

### **Mock Tile Dependencies**

Test tiles in isolation by mocking their dependencies:

```kotlin
@Test
fun `order summary composes multiple data sources`() = runTest {
  val orderTile = singleTile<Order> {
    val orderId = source(OrderIdKey)
    OrderService.getOrder(orderId)
  }
  val customerTile = singleTile<Customer> {
    val customerId = source(CustomerIdKey)
    CustomerService.getCustomer(customerId)
  }
  val lineItemsTile = multiTile<String, LineItem> { skus ->
    LineItemService.getLineItems(skus)
  }

  val orderSummaryTile = singleTile<OrderSummary> {
    val order = compose(orderTile)
    val customer = compose(customerTile)
    val lineItems = compose(lineItemsTile, order.skus)

    OrderSummary(order, customer, lineItems.values.toList())
  }

  val mockOrder = Order("order-1", "customer-1", listOf("sku-1", "sku-2"))
  val mockCustomer = Customer("customer-1", "Jane Doe")
  val mockLineItems = mapOf(
    "sku-1" to LineItem("sku-1", "Product 1", 10.99),
    "sku-2" to LineItem("sku-2", "Product 2", 25.50)
  )

  val testMosaic = mosaicBuilder()
    .withCanvasSource(OrderIdKey, "order-1")
    .withCanvasSource(CustomerIdKey, "customer-1")
    .withMockTile(orderTile, mockOrder)
    .withMockTile(customerTile, mockCustomer)
    .withMockTile(lineItemsTile, mockLineItems)
    .build()

  val expected = OrderSummary(mockOrder, mockCustomer, mockLineItems.values.toList())
  testMosaic.assertEquals(orderSummaryTile, expected)
}
```

### **Test Error Handling in DSL Tiles**

Verify your tiles handle failures gracefully:

```kotlin
@Test
fun `order page handles customer service failure`() = runTest {
  val orderTile = singleTile<Order> {
    val orderId = source(OrderIdKey)
    OrderService.getOrder(orderId)
  }
  val customerTile = singleTile<Customer> {
    val customerId = source(CustomerIdKey)
    CustomerService.getCustomer(customerId)
  }

  val orderPageTile = singleTile<OrderPage> {
    val order = compose(orderTile)
    val customer = compose(customerTile) // This will fail

    OrderPage(order, customer)
  }

  val mockOrder = Order("order-1", "customer-1", emptyList())
  val testMosaic = mosaicBuilder()
    .withCanvasSource(OrderIdKey, "order-1")
    .withCanvasSource(CustomerIdKey, "customer-1")
    .withMockTile(orderTile, mockOrder)
    .withFailedTile(customerTile, CustomerServiceException("Service unavailable"))
    .build()

  testMosaic.assertThrows(orderPageTile, CustomerServiceException::class)
}
```

### **Test Multi-Tile Batch Operations**

```kotlin
@Test
fun `pricing tile efficiently batches multiple SKUs`() = runTest {
  val pricingTile = multiTile<String, Price> { skus ->
    PricingService.getBulkPrices(skus)
  }

  val mockPrices = mapOf(
    "SKU1" to Price(10.99),
    "SKU2" to Price(25.50),
    "SKU3" to Price(5.00)
  )

  val testMosaic = mosaicBuilder()
    .withMockTile(pricingTile, mockPrices)
    .build()

  testMosaic.assertEquals(pricingTile, setOf("SKU1", "SKU2", "SKU3"), mockPrices)
}
```

### **Test Complex DSL Compositions**

```kotlin
@Test
fun `dashboard tile orchestrates multiple data sources`() = runTest {
  val userTile = singleTile<User> {
    val userId = source(UserIdKey)
    UserService.getUser(userId)
  }
  val ordersTile = multiTile<String, Order> { orderIds ->
    OrderService.getOrders(orderIds)
  }
  val recommendationsTile = singleTile<List<Product>> {
    val userId = source(UserIdKey)
    RecommendationService.getRecommendations(userId)
  }

  val dashboardTile = singleTile<Dashboard> {
    val user = compose(userTile)
    // After the user loads, independent work can overlap.
    val recommendations = composeAsync(recommendationsTile)
    val recentOrders = compose(ordersTile, user.recentOrderIds)

    Dashboard(user, recentOrders.values.toList(), recommendations.await())
  }

  val mockUser = User("user-1", "John", listOf("order-1", "order-2"))
  val mockOrders = mapOf(
    "order-1" to Order("order-1", "user-1", listOf("sku-1")),
    "order-2" to Order("order-2", "user-1", listOf("sku-2"))
  )
  val mockRecommendations = listOf(Product("rec-1", "Recommended Product"))

  val testMosaic = mosaicBuilder()
    .withCanvasSource(UserIdKey, "user-1")
    .withMockTile(userTile, mockUser)
    .withMockTile(ordersTile, mockOrders)
    .withMockTile(recommendationsTile, mockRecommendations)
    .build()

  val expected = Dashboard(mockUser, mockOrders.values.toList(), mockRecommendations)
  testMosaic.assertEquals(dashboardTile, expected)
}
```

### **Test Performance and Delays**

```kotlin
@Test
fun `handles slow external API calls`() = runTest {
  val externalApiTile = singleTile<ApiResponse> {
    ExternalApiService.fetchData()
  }

  val testMosaic = mosaicBuilder()
    .withDelayedTile(externalApiTile, ApiResponse("data"), delayMs = 200)
    .build()

  val startTime = currentTime
  testMosaic.assertEquals(externalApiTile, ApiResponse("data"))
  val elapsed = currentTime - startTime

  assertTrue(elapsed >= 200, "Should respect delay")
}
```

## 📋 **Mock Behaviors for DSL Tiles**

Control how your mocked tiles behave:

```kotlin
val testMosaic = mosaicBuilder()
  .withMockTile(userTile, mockUser) // Success: return data immediately
  .withFailedTile(failingTile, UserNotFoundException("User not found"))
  .withDelayedTile(externalApiTile, mockData, delayMs = 500)
  .withCustomTile(dynamicTile) {
    // The custom provider has a Mosaic receiver and can read Canvas sources.
    if (sourceOr<Boolean>("premium") == true) "premium-data" else "standard-data"
  }
  .build()
```

## 🔍 **DSL-Aware Assertion API**

Type-safe assertions that work naturally with DSL tiles:

```kotlin
// Test single-value tiles
testMosaic.assertEquals(userTile, expectedUser)

// Test multi-value tiles with specific keys
testMosaic.assertEquals(
  tile = pricingTile,
  keys = setOf("SKU1", "SKU2"),
  expected = mapOf("SKU1" to price1, "SKU2" to price2)
)

// Test exceptions with proper type safety
testMosaic.assertThrows(userTile, UserNotFoundException::class)

// Equality assertions support custom messages
testMosaic.assertEquals(
  tile = userTile,
  expected = expectedUser,
  message = "User tile should return the expected user data"
)
```

## 🧩 **Advanced DSL Testing**

### **Test Conditional Logic**

```kotlin
@Test
fun `payment processor chooses correct implementation`() = runTest {
  val customerTile = singleTile<Customer> {
    val customerId = source(CustomerIdKey)
    CustomerService.getCustomer(customerId)
  }
  val premiumProcessorTile = singleTile<PaymentProcessor> { PremiumProcessor() }
  val standardProcessorTile = singleTile<PaymentProcessor> { StandardProcessor() }

  val paymentProcessorTile = singleTile<PaymentProcessor> {
    val customer = compose(customerTile)

    when (customer.tier) {
      CustomerTier.PREMIUM -> compose(premiumProcessorTile)
      else -> compose(standardProcessorTile)
    }
  }

  val premiumCustomer = Customer("customer-1", "John", CustomerTier.PREMIUM)

  val testMosaic = mosaicBuilder()
    .withCanvasSource(CustomerIdKey, "customer-1")
    .withMockTile(customerTile, premiumCustomer)
    .withMockTile(premiumProcessorTile, PremiumProcessor())
    .withMockTile(standardProcessorTile, StandardProcessor())
    .build()

  val result = testMosaic.compose(paymentProcessorTile)
  assertIs<PremiumProcessor>(result)
}
```

### **Test Dynamic Key Generation**

```kotlin
@Test
fun `related products tile generates keys dynamically`() = runTest {
  val orderTile = singleTile<Order> {
    val orderId = source(OrderIdKey)
    OrderService.getOrder(orderId)
  }
  val productsByCategoryTile = multiTile<String, List<Product>> { categoryIds ->
    ProductService.getProductsByCategories(categoryIds)
  }

  val relatedProductsTile = singleTile<List<Product>> {
    val order = compose(orderTile)
    val categoryIds = order.items.map { it.categoryId }.distinct()

    val productsByCategory = compose(productsByCategoryTile, categoryIds.toSet())
    productsByCategory.values.flatten().take(10)
  }

  val mockOrder = Order("order-1", "customer-1", listOf(
    OrderItem("item-1", "category-A"),
    OrderItem("item-2", "category-B")
  ))
  val mockProductsByCategory = mapOf(
    "category-A" to listOf(Product("prod-1", "Product 1")),
    "category-B" to listOf(Product("prod-2", "Product 2"))
  )

  val testMosaic = mosaicBuilder()
    .withCanvasSource(OrderIdKey, "order-1")
    .withMockTile(orderTile, mockOrder)
    .withMockTile(productsByCategoryTile, mockProductsByCategory)
    .build()

  val expected = listOf(Product("prod-1", "Product 1"), Product("prod-2", "Product 2"))
  testMosaic.assertEquals(relatedProductsTile, expected)
}
```

### **Test Request Context Usage**

```kotlin
@Test
fun `user preferences tile uses canvas dependency injection`() = runTest {
  val userPreferencesTile = singleTile<Preferences> {
    val userId = source(UserIdKey)
    val locale = source(LocaleKey)

    Preferences(userId, locale)
  }

  val testMosaic = mosaicBuilder()
    .withCanvasSource(UserIdKey, "user-123")
    .withCanvasSource(LocaleKey, "es-ES")
    .build()

  testMosaic.assertEquals(userPreferencesTile, Preferences("user-123", "es-ES"))
}
```

## 🎯 **Best Practices for DSL Testing**

- **Test tile logic, not framework**: Focus on your business logic within tiles
- **Mock upstream Tiles**: Use `withMockTile` for Tile dependencies; use Canvas sources for request values
- **Test error scenarios**: Verify graceful handling of failures with `withFailedTile`
- **Test composition patterns**: Ensure tiles compose correctly with realistic data
- **Use realistic test data**: Mock data should resemble production scenarios
- **Test canvas dependency injection**: Verify tiles properly use injected dependencies

## 🌟 **Key Features**

- **🧪 DSL-Native Testing**: Designed specifically for DSL-based tile patterns
- **🎯 Type-Safe Assertions**: Full Kotlin type safety with coroutine support
- **🔄 Flexible Mocking**: SUCCESS, ERROR, DELAY, and CUSTOM behaviors
- **⚡ Concurrent Testing**: Proper support for testing concurrent tile execution
- **📊 Canvas Dependency Injection**: Test tiles that use Canvas-based dependency injection
- **🧩 Composition Testing**: Verify complex tile orchestrations work correctly

## 🔗 **Related Modules**

- **[mosaic-core](../mosaic-core/README.md)**: The DSL-based framework for composable backend orchestration
- **[mosaic-bom](../mosaic-bom/README.md)**: Optional runtime version alignment
