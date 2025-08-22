# Mosaic Test Framework

[![Coverage](https://img.shields.io/badge/coverage-90%25%2B-brightgreen)](https://github.com/Nick-Abbott/Mosaic)
[![Tests](https://img.shields.io/badge/tests-passing-brightgreen)](https://github.com/Nick-Abbott/Mosaic)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A comprehensive testing framework for the Mosaic backend orchestration system. This module provides utilities, assertions, and testing patterns specifically designed for testing Tile implementations with excellent code coverage.

## 🎯 Purpose

The `mosaic-test` module is designed to make testing Tile implementations as easy and comprehensive as possible. It provides:

- **Test Utilities**: Helper functions for creating mock Mosaic instances and test fixtures
- **Assertion Helpers**: Specialized assertions for verifying Tile behavior and caching
- **Mock Behavior Control**: Configurable mock behaviors (SUCCESS, ERROR, DELAY, CUSTOM)
- **Comprehensive Coverage**: Excellent test coverage with enforced thresholds

## 🏗️ Module Structure

```
mosaic-test/
├── src/main/kotlin/com/abbott/mosaic/test/
│   ├── TestMosaic.kt              # Main test wrapper with assertion methods
│   ├── TestMosaicBuilder.kt       # Fluent builder for creating test scenarios
│   ├── MockBehavior.kt            # Enum defining mock tile behaviors
│   └── MockMosaicRequest.kt       # Default request implementation for testing
├── src/test/kotlin/com/abbott/mosaic/test/
│   ├── BaseTestMosaicTest.kt      # Base test class with common setup
│   ├── TestMosaicTest.kt          # Tests for TestMosaic functionality
│   ├── TestMosaicBuilderTest.kt   # Tests for TestMosaicBuilder functionality
│   ├── TestData.kt                # Sample tile implementations for testing
│   └── MockTileTest.kt            # Tests for mock tile behavior
└── build.gradle.kts               # Module build configuration
```

## 🔧 Dependencies

This module depends on:
- **mosaic-core**: The core Mosaic framework being tested
- **JUnit 5**: Modern testing framework
- **MockK**: Kotlin-first mocking library
- **Kotlin Coroutines Test**: Testing utilities for coroutines
- **Kover**: Code coverage tool

## 🚀 Usage

### Basic Tile Testing

```kotlin
@Test
fun `should test single tile with success behavior`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withMockTile(TestSingleTile::class, "test-data", MockBehavior.SUCCESS)
        .build()
    
    registry.register(TestSingleTile::class) { testMosaic.getMockTiles()[TestSingleTile::class] as TestSingleTile }
    
    testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "test-data")
}
```

### MultiTile Testing

```kotlin
@Test
fun `should test multi tile with delay behavior`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withMockMultiTile(TestMultiTile::class, mapOf("key1" to "value1"), MockBehavior.DELAY)
        .build()
    
    registry.register(TestMultiTile::class) { testMosaic.getMockTiles()[TestMultiTile::class] as TestMultiTile }
    
    testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("key1"),
        expected = mapOf("key1" to "value1")
    )
}
```

### Error Testing

```kotlin
@Test
fun `should test error behavior`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withMockTile(TestSingleTile::class, "error-data", MockBehavior.ERROR)
        .build()
    
    registry.register(TestSingleTile::class) { testMosaic.getMockTiles()[TestSingleTile::class] as TestSingleTile }
    
    testMosaic.assertThrows(tileClass = TestSingleTile::class, expectedException = RuntimeException::class.java)
}
```

### Integration Testing

```kotlin
@Test
fun `should test multiple tiles with different behaviors`() = runTestMosaicTest {
    val testMosaic = TestMosaicBuilder()
        .withMockTile(TestSingleTile::class, "success-data", MockBehavior.SUCCESS)
        .withMockMultiTile(TestMultiTile::class, mapOf("key1" to "delay-value"), MockBehavior.DELAY)
        .withRequest(MockMosaicRequest())
        .build()
    
    // Test both tiles
    registry.register(TestSingleTile::class) { testMosaic.getMockTiles()[TestSingleTile::class] as TestSingleTile }
    registry.register(TestMultiTile::class) { testMosaic.getMockTiles()[TestMultiTile::class] as TestMultiTile }
    
    testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "success-data")
    testMosaic.assertEquals(
        tileClass = TestMultiTile::class,
        keys = listOf("key1"),
        expected = mapOf("key1" to "delay-value")
    )
}
```

## 📋 Mock Behaviors

The framework supports four different mock behaviors:

### 1. SUCCESS
- Mock returns the specified data immediately
- Default behavior for most test scenarios

### 2. ERROR
- Mock throws a RuntimeException when called
- Useful for testing error handling paths

### 3. DELAY
- Mock delays for 100ms before returning data
- Useful for testing timeout and performance scenarios

### 4. CUSTOM
- Mock uses custom behavior (extensible for future use)
- Currently behaves like SUCCESS but allows for future customization

## 🔍 Assertion Utilities

The framework provides specialized assertions for common Tile testing scenarios:

```kotlin
// Verify SingleTile results
testMosaic.assertEquals(tileClass = TestSingleTile::class, expected = "expected-value")

// Verify MultiTile results
testMosaic.assertEquals(
    tileClass = TestMultiTile::class,
    keys = listOf("key1", "key2"),
    expected = mapOf("key1" to "value1", "key2" to "value2")
)

// Verify exceptions
testMosaic.assertThrows(tileClass = TestSingleTile::class, expectedException = RuntimeException::class.java)

// Get mock tiles for verification
val mockTiles = testMosaic.getMockTiles()
```

## 📊 Coverage Metrics

The framework maintains excellent code coverage with enforced thresholds:

Coverage thresholds are enforced in the build:
- Line coverage: 90% minimum
- Branch coverage: 80% minimum
- Instruction coverage: 90% minimum

Run `./gradlew :mosaic-test:koverHtmlReport` to generate detailed coverage reports.

## 🛠️ Development

### Adding New Test Utilities

1. Create the utility in the appropriate package under `src/main/kotlin/`
2. Add comprehensive tests in `src/test/kotlin/`
3. Update documentation and examples
4. Ensure all tests pass with coverage requirements

### Running Tests

```bash
# Run all tests
./gradlew :mosaic-test:test

# Run with coverage verification
./gradlew :mosaic-test:koverVerify

# Run specific test class
./gradlew :mosaic-test:test --tests "com.abbott.mosaic.test.TestMosaicBuilderTest"

# Generate coverage reports
./gradlew :mosaic-test:koverHtmlReport
```

### Code Quality

The project uses several tools to maintain code quality:

- **KtLint**: Code formatting and style enforcement
- **Detekt**: Static code analysis
- **Kover**: Code coverage analysis and enforcement

## 📚 Examples

The `src/test/kotlin/com/abbott/mosaic/test/` directory contains comprehensive examples:

- **TestMosaicTest.kt**: 836 lines of tests covering all TestMosaic functionality
- **TestMosaicBuilderTest.kt**: 604 lines of tests covering all builder scenarios
- **TestData.kt**: Sample tile implementations for testing
- **BaseTestMosaicTest.kt**: Base class with common testing utilities

## 🤝 Contributing

When contributing to the testing framework:

1. Follow the existing code style and patterns
2. Add tests for any new functionality
3. Update documentation for new features
4. Ensure all existing tests continue to pass
5. Maintain coverage thresholds (90% line, 80% branch, 90% instruction)
6. Consider backward compatibility for public APIs

## 🎯 Key Features

- **Fluent API**: Easy-to-use builder pattern for test setup
- **Comprehensive Coverage**: Excellent test coverage with enforced thresholds and dynamic monitoring
- **Mock Behavior Control**: Configurable mock behaviors for different test scenarios
- **Coroutine Support**: Full support for testing async Tile operations
- **Type Safety**: Leverages Kotlin's type system for compile-time safety
- **Extensible Design**: Easy to extend with new behaviors and utilities

## 📝 Note on Badges

The badges in this README are currently using placeholder URLs. To set up proper dynamic badges:

1. **Coverage Badge**: Use a service like [Shields.io](https://shields.io/) with your CI/CD pipeline
2. **Test Badge**: Configure your CI/CD to update badge status automatically
3. **Build Badge**: Set up GitHub Actions or similar to update build status

Example dynamic badge setup:
```markdown
[![Coverage](https://img.shields.io/badge/coverage-${COVERAGE}%25-brightgreen)](https://github.com/Nick-Abbott/Mosaic)
[![Tests](https://img.shields.io/badge/tests-${STATUS}-brightgreen)](https://github.com/Nick-Abbott/Mosaic)
```

