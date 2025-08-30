# Mosaic Framework

[![Tests](https://github.com/Nick-Abbott/Mosaic/workflows/Test%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Test+Badge%22)
[![Build](https://github.com/Nick-Abbott/Mosaic/workflows/Build%20Badge/badge.svg)](https://github.com/Nick-Abbott/Mosaic/actions?query=workflow%3A%22Build+Badge%22)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A powerful Kotlin framework for **composable backend orchestration** that enables you to think from the response up rather than database down. Mosaic provides intelligent caching, concurrent safety, and type-safe tile composition for building high-performance data access layers.

## 🎯 **Response-First Design Philosophy**

Mosaic transforms backend development from imperative data fetching to declarative response composition. Instead of thinking "what database queries do I need?", you think "what response do I want to build?"

### Traditional Approach (Database Down)
```kotlin
// Database-first thinking
val user = userRepository.findById(id)
val profile = profileRepository.findByUserId(user.id)
val preferences = preferenceRepository.findByUserId(user.id)
return UserResponse(user, profile, preferences)
```

### Mosaic Approach (Response Up)
```kotlin
// Response-first thinking
class UserResponseTile(mosaic: Mosaic) : SingleTile<UserResponse>(mosaic) {
    override suspend fun retrieve(): UserResponse {
        val user = mosaic.getTile<UserTile>().get()
        val profile = mosaic.getTile<ProfileTile>().get()
        val preferences = mosaic.getTile<PreferencesTile>().get()
        
        return UserResponse(user, profile, preferences)
    }
}
```

## 🏗️ **Project Structure**

This project uses a **multi-module Gradle structure** to organize code into focused, maintainable components:

```
Mosaic/
├── packages/
│   ├── build.gradle.kts    # Root build configuration and publishing setup
│   ├── settings.gradle.kts # Module definitions
│   ├── config/             # Static analysis configs
│   ├── mosaic-core/        # Core Mosaic framework
│   ├── mosaic-test/        # Testing framework
│   └── mosaic-build/       # Gradle build plugin
├── examples/               # Standalone example projects (includes Spring Boot sample with parallel tile aggregation)
│   ├── build.gradle.kts
│   └── settings.gradle.kts
└── MODULE_TEMPLATE.md      # Guide for adding new modules
```

### **Modules**

- **`mosaic-core`**: The main Mosaic framework with tile system, registry, and core functionality
- **`mosaic-test`**: Comprehensive testing framework with utilities, assertions, and mock behaviors for testing Tile implementations
- **`mosaic-build`**: Gradle plugin that automates Mosaic build configuration
- **Future modules**: API, CLI, web components, utils, etc.

### **Adding New Modules**

See [MODULE_TEMPLATE.md](MODULE_TEMPLATE.md) for detailed instructions on adding new modules to the project.

## 🧩 **Core Components**

### **Tile System**
- **SingleTile**: Caches single values with automatic retrieval and concurrent access handling
- **MultiTile**: Caches multiple key-value pairs with batch retrieval and normalization
- Both use `Deferred` for thread-safe caching and concurrent operation sharing

### **Mosaic Registry**
- **Dependency Injection**: Maps tile classes to their constructors
- **Type Safety**: Uses `KClass<T>` for compile-time type checking
- **Flexible Construction**: Supports custom constructor functions for each tile type

### **Request Context**
- **MosaicRequest**: Interface for request-specific data (headers, auth, parameters, etc.)
- **Context Propagation**: Tiles can access request data via `mosaic.request`
- **Extensible**: Easy to add new request properties as needed

## 🚀 **Tile Composition Power**

### **Automatic Dependency Resolution**
```kotlin
class OrderDetailsTile(mosaic: Mosaic) : SingleTile<OrderDetails>(mosaic) {
    override suspend fun retrieve(): OrderDetails {
        val order = mosaic.getTile<OrderTile>().get()
        val customer = mosaic.getTile<CustomerTile>().get()
        val products = mosaic.getTile<ProductTile>().getByKeys(order.productIds)
        val shipping = mosaic.getTile<ShippingTile>().getByKeys(listOf(order.shippingId))[order.shippingId]!!
        
        return OrderDetails(order, customer, products, shipping)
    }
}
```

### **Intelligent Caching Cascade**
- If `OrderDetailsTile` is called, it automatically caches all its dependencies
- Subsequent calls to `CustomerTile`, `ProductTile`, etc. return cached results
- The framework handles the entire dependency graph efficiently

### **Request Context Propagation**
```kotlin
class PersonalizedContentTile(mosaic: Mosaic) : SingleTile<Content>(mosaic) {
    override suspend fun retrieve(): Content {
        val userId = mosaic.request.userId // Access request context
        val user = mosaic.getTile<UserTile>().get()
        val preferences = mosaic.getTile<PreferencesTile>().get()
        
        return generatePersonalizedContent(user, preferences)
    }
}
```

## 🎨 **Composition Patterns**

### **Aggregation Tiles**
```kotlin
class SearchResultsTile(mosaic: Mosaic) : MultiTile<SearchResult, List<SearchResult>>(mosaic) {
    override suspend fun retrieveForKeys(queryIds: List<String>): List<SearchResult> {
        val queries = mosaic.getTile<QueryTile>().getByKeys(queryIds)
        val searchEngineIds = queries.values.map { it.id }
        val searchResults = mosaic.getTile<SearchEngineTile>().getByKeys(searchEngineIds)
        
        return queryIds.map { queryId ->
            val query = queries[queryId]!!
            val results = searchResults[query.id]!!
            SearchResult(query, results)
        }
    }
}
```

### **Transformation Tiles**
```kotlin
class EnrichedProductTile(mosaic: Mosaic) : SingleTile<EnrichedProduct>(mosaic) {
    override suspend fun retrieve(): EnrichedProduct {
        val product = mosaic.getTile<BasicProductTile>().get()
        val reviews = mosaic.getTile<ReviewTile>().getByKeys(product.reviewIds)
        val inventory = mosaic.getTile<InventoryTile>().getByKeys(listOf(product.id))[product.id]!!
        
        return EnrichedProduct(product, reviews, inventory)
    }
}
```

## ⚡ **Key Features**

### **Performance**
- **Intelligent Caching**: Only retrieves data once, subsequent calls return cached results
- **Concurrent Safety**: Multiple simultaneous requests share the same retrieval operation
- **Batch Operations**: MultiTile retrieves multiple keys in a single operation
- **Automatic Optimization**: Dependencies are deduplicated and executed in parallel when possible

### **Type Safety**
- **Reified Generics**: Compile-time type checking with `getTile<T>()`
- **KClass Integration**: Runtime type safety with `KClass<T>`
- **Generic Constraints**: All tiles must extend the base `Tile` class

### **Developer Experience**
- **Natural Composition**: Tiles compose like functions, building complex responses from simple pieces
- **Declarative Data Flow**: Define what you need, not how to get it
- **Request Context**: Full access to request data throughout the composition chain
- **Testability**: Each tile can be tested independently with mocked dependencies

## 📦 **Usage Example**

```kotlin
// Setup
val registry = MosaicRegistry()
registry.register(UserTile::class) { mosaic -> UserTile(mosaic) }
registry.register(ProductTile::class) { mosaic -> ProductTile(mosaic) }
registry.register(OrderTile::class) { mosaic -> OrderTile(mosaic) }

val request = MyRequest(userId = "123", headers = mapOf("auth" to "token"))
val mosaic = Mosaic(registry, request)

// Usage - automatic caching and concurrency handling
val user = mosaic.getTile<UserTile>().get() // Retrieves and caches
val products = mosaic.getTile<ProductTile>().getByKeys("prod1", "prod2") // Batch retrieval
val orderDetails = mosaic.getTile<OrderDetailsTile>().get() // Composed response
```

## 🧪 **Testing**

```kotlin
@Test
fun `should compose complex response`() = runTest {
    val registry = MosaicRegistry()
    registry.register(UserTile::class) { mosaic -> UserTile(mosaic) }
    registry.register(ProductTile::class) { mosaic -> ProductTile(mosaic) }
    
    val request = TestRequest(userId = "123")
    val mosaic = Mosaic(registry, request)
    
    val orderDetails = mosaic.getTile<OrderDetailsTile>().get()
    
    assertNotNull(orderDetails.user)
    assertNotNull(orderDetails.products)
    assertEquals(2, orderDetails.products.size)
}
```

## 🏗️ **Building**

```bash
# Build all modules
./gradlew -p packages build

# Build specific module
./gradlew -p packages :mosaic-core:build
```

## 🧪 **Testing**

The project includes a comprehensive testing framework in the `mosaic-test` module that provides utilities for testing Tile implementations with excellent coverage.

### **Testing Framework Features**
- **TestMosaic**: Main test wrapper with assertion methods for SingleTile and MultiTile
- **TestMosaicBuilder**: Fluent builder for creating test scenarios with mocked tiles
- **Comprehensive Coverage**: Excellent test coverage with enforced thresholds

### **Running Tests**

```bash
# Test all modules
./gradlew -p packages test

# Test specific module
./gradlew -p packages :mosaic-core:test
./gradlew -p packages :mosaic-test:test

# Test with coverage verification
./gradlew -p packages :mosaic-test:koverVerify

# Generate coverage reports
./gradlew -p packages :mosaic-test:koverHtmlReport
```

### **Testing Examples**

```kotlin
// Test SingleTile with mock response
@Test
fun `should test single tile`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withMockTile(TestSingleTile::class, "test-data")
        .build()

    registry.register(TestSingleTile::class) { testMosaic.getMockTiles()[TestSingleTile::class] as TestSingleTile }
    testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "test-data")
}

// Test MultiTile with failure
@Test
fun `should test error handling`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withFailedTile(TestMultiTile::class, RuntimeException("error"))
        .build()

    registry.register(TestMultiTile::class) { testMosaic.getMockTiles()[TestMultiTile::class] as TestMultiTile }
    testMosaic.assertThrows(tileClass = TestMultiTile::class, keys = listOf("key1"), expectedException = RuntimeException::class.java)
}
```

For detailed testing documentation, see [mosaic-test/README.md](mosaic-test/README.md).

## 🔍 **Code Quality**

This project uses **ktlint** and **detekt** to maintain high code quality standards.

### **ktlint** - Kotlin Linter
Enforces Kotlin coding conventions and formatting rules.

```bash
# Check code formatting
./gradlew -p packages ktlintCheck

# Auto-fix formatting issues
./gradlew -p packages ktlintFormat
```

### **detekt** - Static Code Analysis
Performs static code analysis to detect potential bugs, code smells, and complexity issues.

```bash
# Run static analysis
./gradlew -p packages detekt

# Run with auto-correction
./gradlew -p packages detektMain
```

### **Configuration**
- **ktlint**: Configured in `packages/build.gradle.kts` with version 1.0.1
- **detekt**: Configured in `packages/config/detekt/detekt.yml` with comprehensive rule sets
- **EditorConfig**: `.editorconfig` ensures consistent formatting across editors

### **CI/CD Integration**
The project includes GitHub Actions workflows that automatically run code quality checks on:
- Pull requests to `main` and `develop` branches
- Pushes to `main` and `develop` branches

Reports are generated and stored as artifacts for review.

## 🚀 **Gradle Lifecycle & Convenience Tasks**

The project includes an optimized Gradle lifecycle with intelligent task dependencies and convenient shortcuts.

### **Core Verification Tasks**

```bash
# Run all code style and quality checks
./gradlew -p packages styleCheck

# Run tests and verify coverage thresholds
./gradlew -p packages coverageCheck

# Run all verifications (style, quality, coverage)
./gradlew -p packages verifyAll

# Run the standard Gradle check (includes all verifications)
./gradlew -p packages check
```

### **Convenience Tasks**

```bash
# Complete build with verification
./gradlew -p packages fullBuild

# Generate all reports (tests, coverage, style checks)
./gradlew -p packages generateReports

# Auto-fix code style issues where possible
./gradlew -p packages fixCodeStyle
```

### **Task Dependencies**

The lifecycle is organized with intelligent dependencies:

- **`test`** → automatically generates HTML coverage report
- **`styleCheck`** → runs ktlint and detekt
- **`coverageCheck`** → runs tests and verifies coverage thresholds
- **`verifyAll`** → runs style checks and coverage verification
- **`check`** → includes all verifications
- **`fullBuild`** → clean, build, test, and verify everything

### **Task Groups**

- **`verification`**: Code quality and testing tasks
- **`build`**: Build and compilation tasks  
- **`reporting`**: Report generation tasks

## 📊 **Code Coverage**

This project uses **Kover** for code coverage analysis, providing comprehensive insights into test coverage without affecting Kotlin compilation.

### **Running Coverage Analysis**

```bash
# Generate coverage reports
./gradlew -p packages koverHtmlReport

# Generate XML report (useful for CI/CD)
./gradlew -p packages koverXmlReport

# Verify coverage meets minimum thresholds
./gradlew -p packages koverVerify

# Run all verification including coverage
./gradlew -p packages verifyAll
```

### **Coverage Configuration**

- **Minimum Thresholds**: 80% line and branch coverage
- **Coverage Scope**: All classes in `com.abbott.mosaic.*` packages
- **Exclusions**: Test classes and generated code are excluded
- **Reports**: HTML and XML formats available

### **Coverage Reports**

After running coverage analysis, reports are available at:
- **HTML Report**: `build/reports/kover/html/index.html`
- **XML Report**: `build/reports/kover/xml/report.xml`

### **CI/CD Integration**

Coverage verification is automatically included in the build process:
- Coverage reports are generated during CI builds
- Coverage thresholds are enforced to maintain quality standards
- Coverage data is available for trend analysis

## 💡 **Benefits**

1. **Efficient**: Eliminates redundant data fetching through intelligent caching
2. **Concurrent**: Handles multiple simultaneous requests gracefully
3. **Type Safe**: Compile-time guarantees for tile types and interactions
4. **Composable**: Natural function-like composition of data retrieval logic
5. **Extensible**: Easy to add new tile types and request properties
6. **Testable**: Comprehensive testing support with dependency mocking
7. **Production Ready**: Handles errors, edge cases, and performance optimization

## 🎯 **Perfect For**

- **High-performance APIs** requiring efficient data access
- **Complex backend orchestration** with multiple data sources
- **Microservices** that need to compose data from various services
- **GraphQL resolvers** that benefit from intelligent caching
- **Real-time applications** requiring concurrent data access
- **Any system** where you want to think in terms of responses rather than queries

Mosaic transforms the way you build backend systems by making data composition as natural as function composition, while providing enterprise-grade performance and reliability.

## 📄 **License**

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

Copyright 2025 Nicholas Abbott
