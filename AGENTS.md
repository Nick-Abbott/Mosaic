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
  - `SingleTile<T>`: Caches single values (e.g., user profile)
  - `MultiTile<K, V>`: Caches key-value mappings (e.g., products by ID)
- **Mosaic Registry**: Dependency injection system for tile management
- **MosaicRequest**: Context object containing request-specific data (auth, headers, etc.)

#### Response-First Philosophy
Instead of thinking "what database queries do I need?", developers think "what response do I want to build?" and compose tiles to create that response.

#### Intelligent Caching
- Tiles automatically cache results and share concurrent requests
- Dependencies are resolved automatically and cached transitively
- No manual cache management required

### Project Structure

```
Mosaic/
├── mosaic-core/          # Core framework (SingleTile, MultiTile, Mosaic, Registry)
├── mosaic-test/          # Testing framework (TestMosaic, TestMosaicBuilder, etc.)
├── mosaic-consumer-plugin/  # Gradle build plugin for consumers
├── mosaic-consumer-ksp/   # KSP plugin to generate tile registration code
├── mosaic-catalog-ksp/   # KSP plugin to generate tile catalogs for tile libraries
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
class UserTile(mosaic: Mosaic) : SingleTile<User>(mosaic) {
    override suspend fun retrieve(): User {
        val userId = mosaic.request.userId
        return userService.getUser(userId)
    }
}
```

#### Composition Pattern
```kotlin
class UserProfileTile(mosaic: Mosaic) : SingleTile<UserProfile>(mosaic) {
    override suspend fun retrieve(): UserProfile {
        val user = mosaic.getTile<UserTile>().get()
        val preferences = mosaic.getTile<PreferencesTile>().get()
        return UserProfile(user, preferences)
    }
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
- Plugin, KSP, and BOM modules do not have unit tests
- Aim for 80%+ code coverage for other modules (enforced by Kover)

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
- Contains the main framework classes
- Focus on performance and thread safety
- Minimal external dependencies

#### mosaic-test
- Comprehensive testing utilities
- Mock tile implementations with configurable behaviors
- Test assertions specific to tile testing

#### examples/
- Demonstrates real-world usage patterns
- Spring Boot, Ktor, and Micronaut integration examples available
- Tile library for reuse between different examples
- Good reference for best practices
- Is a separate gradle project. Gradle commands on examples must be run using `-p examples` flag

### Performance Considerations
- Tiles should be lightweight and focused
- Use `MultiTile` for batch operations when possible
- Avoid blocking operations in tile `retrieve()` methods
- Request context is available but should be used sparingly
- If depending on multiple tiles, retrieve all tiles then execute them in parallel

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
6. If a ksp or gradle plugin has been modified, also test examples with `./gradlew clean build -p examples`

### Debugging Tips
- Use the test framework to isolate tile behavior
- Check the Mosaic registry configuration for tile resolution issues
- Verify request context is properly propagated
- Use IDE debugging with suspend functions carefully (coroutines)

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
- `mosaic-core/src/main/kotlin/com/abbott/mosaic/core/Mosaic.kt` - Main framework class
- `mosaic-core/src/main/kotlin/com/abbott/mosaic/core/tiles/` - Tile implementations
- `mosaic-test/src/main/kotlin/com/abbott/mosaic/test/` - Testing framework
- `examples/` - Usage examples and patterns

This framework excels at building complex, high-performance data access layers through simple, composable tiles. Think in terms of the response you want to build, then compose tiles to create that response efficiently.
