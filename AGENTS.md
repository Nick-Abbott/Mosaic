# AGENTS.md

## Setup Commands

### Minimal Setup (Fastest)
```bash
# No special setup required - Gradle wrapper handles everything
# Just ensure Java is available (JDK 21+ recommended)
```

### Verification Build
```bash
# Full clean build to verify all changes work correctly
./gradlew clean build
```

## Repository Context

### Project Overview
**Mosaic** is a Kotlin framework for **composable backend orchestration** that enables "response-first" thinking rather than "database-first" thinking. It provides intelligent caching, concurrent safety, and type-safe tile composition for building high-performance data access layers.

### Key Concepts for AI Understanding

#### Core Architecture
- **Tiles**: The fundamental building blocks that cache and retrieve data
  - `Tile<T>`: Created with `singleTile { }` DSL for single values
  - `MultiTile<K, V>`: Created with `multiTile { }`, `perKeyTile { }`, or `chunkedMultiTile { }` DSL for key-value mappings
- **Canvas**: Dependency injection system using hierarchical scoping
- **Mosaic**: Per-request execution context that manages tile caching and concurrency

#### Response-First Philosophy
Instead of thinking "what database queries do I need?", developers think "what response do I want to build?" and compose tiles to create that response.

#### Intelligent Caching
- Tiles automatically cache results and share concurrent requests
- Dependencies are resolved automatically and cached transitively
- No manual cache management required

### Project Structure

```
Mosaic/
├── mosaic-core/          # Core framework (Tile, MultiTile, Mosaic, Canvas)
├── mosaic-test/          # Testing framework (TestMosaic, TestMosaicBuilder, etc.)
├── examples/             # Example implementations
├── buildSrc/             # Gradle convention plugins
└── build.gradle.kts      # Root build configuration
```

### Technology Stack
- **Language**: Kotlin 2.2.0
- **Build System**: Gradle with Kotlin DSL
- **Concurrency**: Kotlin Coroutines with `Deferred` for caching
- **Testing**: Kotlin Test with Kover for coverage (Micronaut example uses JUnit 5)
- **Code Quality**: ktlint (formatting), detekt (static analysis)

### Development Patterns

#### Tile Implementation Pattern
```kotlin
val UserTile = singleTile {
    val userId = source<String>("userId")
    userService.getUser(userId)
}
```

#### Composition Pattern
```kotlin
val UserProfileTile = singleTile {
    val user = compose(UserTile)
    val preferences = compose(PreferencesTile)
    UserProfile(user, preferences)
}
```

#### MultiTile Patterns
```kotlin
// Batch API call
val ProductsByIdTile = multiTile { productIds ->
    productService.getProducts(productIds.toList())
}

// Per-key fetching
val UserByIdTile = perKeyTile { userId ->
    userService.getUser(userId)
}

// Chunked requests
val InventoryBySkuTile = chunkedMultiTile(50) { skus ->
    inventoryService.getBatch(skus)
}
```

## AI Agent Guidelines

### Code Style & Standards
- Follow Kotlin conventions enforced by ktlint
- Use meaningful tile names ending with "Tile"
- Prefer composition over inheritance
- Use suspend functions for async operations
- Follow the existing package structure (`org.buildmosaic.*`)

### Testing Best Practices
- Use the `mosaic-test` framework for testing tiles
- Isolate tiles from their dependent tiles by using `TestMosaicBuilder`
- Test both success and error scenarios
- Use `TestMosaicBuilder` helpers (`withMockTile`, `withFailedTile`, etc.) for different test scenarios
- Use `runTest` from kotlinx-coroutines-test for coroutine testing
- Pass `TestScope` to `TestMosaicBuilder` constructor
- Aim for 80%+ code coverage for core and test modules (enforced by Kover)

### Common Operations

#### Running Tests
```bash
# All tests
./gradlew test

# Specific module
./gradlew :mosaic-core:test

# With coverage
./gradlew koverHtmlReport
```

#### Code Quality Checks
```bash
# All quality checks
./gradlew check

# Style checking
./gradlew ktlintCheck

# Static analysis
./gradlew detekt

# Auto-fix formatting
./gradlew ktlintFormat
```

### Module-Specific Notes

#### mosaic-core
- Contains the main framework classes: Mosaic, Canvas, Tile DSL functions
- Dependency injection via Canvas with hierarchical scoping
- Automatic caching and concurrency management
- Focus on performance and thread safety
- Minimal external dependencies

#### mosaic-test
- Comprehensive testing utilities
- Mock tile implementations with configurable behaviors
- Test assertions specific to tile testing
- Integration with kotlinx-coroutines-test

#### examples/
- Demonstrates real-world usage patterns
- Spring Boot, Ktor, and Micronaut integration examples available
- Tile library for reuse between different examples
- Good reference for best practices
- Is a separate gradle project. Gradle commands on examples must be run using `-p examples` flag

### Performance Considerations
- Tiles should be lightweight and focused
- Use `MultiTile` for batch operations when possible
- Avoid blocking operations in tile DSL blocks
- Use `composeAsync()` for parallel execution of multiple tiles
- Dependencies are injected via Canvas, not passed through request context
- Prefer `composeAsync().await()` pattern for parallel composition

### Error Handling
- Tiles should let exceptions bubble up naturally
- Use proper Kotlin exception handling patterns
- The framework handles concurrent error scenarios automatically

### When Making Changes
1. Always run the full verification build: `./gradlew clean build`
2. Ensure tests pass and coverage remains above 80%
3. Fix any ktlint/detekt issues before submitting
4. Consider impact on existing tile compositions
5. Update tests when adding new functionality
6. Test examples with `./gradlew clean build -p examples` to verify integration

### Debugging Tips
- Use the test framework to isolate tile behavior
- Check the Canvas configuration for dependency injection issues
- Verify Canvas layers are properly configured with required dependencies
- Use IDE debugging with suspend functions carefully (coroutines)
- Check tile caching behavior using TestMosaic assertions

## Quick Reference

### Essential Commands
```bash
# Verify everything works
./gradlew clean build

# Run tests with coverage
./gradlew test koverHtmlReport

# Fix code style
./gradlew ktlintFormat

# Run all quality checks
./gradlew check
```

### Key Files to Understand
- `mosaic-core/src/main/kotlin/org/buildmosaic/core/Mosaic.kt` - Main framework interface
- `mosaic-core/src/main/kotlin/org/buildmosaic/core/TileDsl.kt` - Tile DSL functions
- `mosaic-core/src/main/kotlin/org/buildmosaic/core/injection/Canvas.kt` - Dependency injection
- `mosaic-test/src/main/kotlin/org/buildmosaic/test/` - Testing framework
- `examples/` - Usage examples and patterns

This framework excels at building complex, high-performance data access layers through simple, composable tiles using functional DSL syntax. Think in terms of the response you want to build, then compose tiles to create that response efficiently. Version 2.0 eliminates the need for Gradle plugins and KSP processors, making it a standalone framework with simple dependency management.
