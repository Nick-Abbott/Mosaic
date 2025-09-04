# Mosaic Test Framework

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Test your Mosaic tiles with confidence.**

Mosaic-test provides a fluent testing API that makes testing tile compositions simple and comprehensive. Mock dependencies, simulate failures, and verify behavior with type-safe assertions.

## 🚀 **Quick Start**

### **Installation**

```kotlin
dependencies {
  testImplementation("com.buildmosaic.test:mosaic-test:1.0.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}
```

### **Your First Test**

```kotlin
@Test
fun `customer tile fetches customer data`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withMockTile(CustomerTile::class, Customer("123", "John Doe"))
    .build()
  
  testMosaic.assertEquals(
    tileClass = CustomerTile::class,
    expected = Customer("123", "John Doe")
  )
}
```

## 🧪 **Testing Patterns**

### **Mock Dependencies**

Test tiles in isolation by mocking their dependencies:

```kotlin
@Test
fun `order summary combines order, customer, and line items`() = runBlocking {
  val mockOrder = Order("order-1", "customer-1", listOf("item-1"))
  val mockCustomer = Customer("customer-1", "Jane Doe")
  val mockLineItems = listOf(LineItemDetail(product, price, 2))
  
  val testMosaic = TestMosaicBuilder()
    .withMockTile(OrderTile::class, mockOrder)
    .withMockTile(CustomerTile::class, mockCustomer)
    .withMockTile(LineItemsTile::class, mockLineItems)
    .build()
  
  val expected = OrderSummary(mockOrder, mockCustomer, mockLineItems)
  testMosaic.assertEquals(OrderSummaryTile::class, expected)
}
```

### **Test Error Handling**

Verify your tiles handle failures gracefully:

```kotlin
@Test
fun `order page fails when customer service is down`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withMockTile(OrderTile::class, mockOrder)
    .withFailedTile(CustomerTile::class, CustomerServiceException("Service unavailable"))
    .withMockTile(LineItemsTile::class, mockLineItems)
    .build()
  
  testMosaic.assertThrows(
    tileClass = OrderSummaryTile::class,
    expectedException = CustomerServiceException::class.java
  )
}
```

### **Test MultiTile Batch Operations**

```kotlin
@Test
fun `pricing tile fetches multiple prices in batch`() = runBlocking {
  val mockPrices = mapOf(
    "SKU1" to Price(10.99),
    "SKU2" to Price(25.50),
    "SKU3" to Price(5.00)
  )
  
  val testMosaic = TestMosaicBuilder()
    .withMockTile(PricingBySkuTile::class, mockPrices)
    .build()
  
  testMosaic.assertEquals(
    tileClass = PricingBySkuTile::class,
    keys = listOf("SKU1", "SKU2", "SKU3"),
    expected = mockPrices
  )
}
```

### **Test Complex Compositions**

```kotlin
@Test
fun `order page tile composes summary and logistics`() = runBlocking {
  val mockSummary = OrderSummary(mockOrder, mockCustomer, mockLineItems)
  val mockLogistics = Logistics(mockAddress, mockCarrierQuotes)
  
  val testMosaic = TestMosaicBuilder()
    .withMockTile(OrderSummaryTile::class, mockSummary)
    .withMockTile(LogisticsTile::class, mockLogistics)
    .build()
  
  val expected = OrderPage(mockSummary, mockLogistics)
  testMosaic.assertEquals(OrderPageTile::class, expected)
}
```

### **Test Performance and Delays**

```kotlin
@Test
fun `handles slow external services`() = runBlocking {
  val testMosaic = TestMosaicBuilder()
    .withDelayedTile(ExternalApiTile::class, mockData, delayMs = 200)
    .build()
  
  val startTime = System.currentTimeMillis()
  testMosaic.assertEquals(ExternalApiTile::class, mockData)
  val elapsed = System.currentTimeMillis() - startTime
  
  assertTrue(elapsed >= 200, "Should respect delay")
}
```

## 📋 **Mock Behaviors**

Control how your mocked tiles behave:

```kotlin
// Success: Returns data immediately (default)
.withMockTile(CustomerTile::class, mockCustomer)

// Error: Throws exception when called
.withFailedTile(CustomerTile::class, CustomerNotFoundException("Customer not found"))

// Delay: Simulates slow external services
.withDelayedTile(ExternalApiTile::class, mockData, delayMs = 500)
```

## 🔍 **Assertion API**

Type-safe assertions for all tile types:

```kotlin
// Test SingleTile
testMosaic.assertEquals(
  tileClass = CustomerTile::class,
  expected = Customer("123", "John Doe")
)

// Test MultiTile with specific keys
testMosaic.assertEquals(
  tileClass = PricingBySkuTile::class,
  keys = listOf("SKU1", "SKU2"),
  expected = mapOf("SKU1" to price1, "SKU2" to price2)
)

// Test exceptions
testMosaic.assertThrows(
  tileClass = CustomerTile::class,
  expectedException = CustomerNotFoundException::class.java
)
```

## 🎯 **Best Practices**

- **Test in isolation**: Mock all dependencies to focus on the tile under test
- **Test error paths**: Verify your tiles handle failures gracefully
- **Test edge cases**: Empty results, null values, network timeouts
- **Use realistic data**: Mock data should resemble production data
- **Test composition**: Verify complex tiles compose their dependencies correctly

## 🌟 **Key Features**

- **🧪 Fluent Testing API**: Easy-to-use builder pattern for test setup
- **🎯 Type-Safe Assertions**: Compile-time guarantees for test correctness
- **🔄 Mock Behavior Control**: SUCCESS, ERROR, DELAY behaviors for comprehensive testing
- **⚡ Coroutine Support**: Full support for testing async tile operations
- **📊 Automatic Registration**: Works seamlessly with mosaic-build-plugin

## 🔗 **Related Modules**

- **[mosaic-core](../mosaic-core/README.md)**: The core framework for composable backend orchestration
