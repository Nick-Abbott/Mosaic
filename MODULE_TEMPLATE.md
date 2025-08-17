# Module Template

This document explains how to add new modules to the Mosaic project.

## Adding a New Module

### 1. Update settings.gradle.kts

Add your new module to `settings.gradle.kts`:

```kotlin
// Include all modules
include(":mosaic-core")
include(":your-new-module")  // Add this line
```

### 2. Create Module Directory Structure

Create the following directory structure for your new module:

```
your-new-module/
├── build.gradle.kts
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/
│   │           └── abbott/
│   │               └── mosaic/
│   │                   └── yournewmodule/
│   └── test/
│       └── kotlin/
│           └── com/
│               └── abbott/
│                   └── mosaic/
│                       └── yournewmodule/
```

**Note**: The `mosaic-core` module follows this same structure.
```

### 3. Create build.gradle.kts

Create a `build.gradle.kts` file in your module directory:

```kotlin
/*
 * Your New Module build.gradle.kts
 * This module inherits all configuration from the root build.gradle.kts
 */

// This module inherits all configuration from the root build.gradle.kts
// Additional module-specific configuration can be added here

dependencies {
    // Add any module-specific dependencies here
    // implementation("some-library:version")
    
    // If you need to depend on other modules in this project:
    // implementation(project(":mosaic-core"))
}

// Example: Override specific configurations for this module
// koverReport {
//     filters {
//         includes {
//             packages("com.abbott.mosaic.yournewmodule.*")
//         }
//     }
// }
```

### 4. Package Naming Convention

Use the following package naming convention:
- Main package: `com.abbott.mosaic.yournewmodule`
- Sub-packages: `com.abbott.mosaic.yournewmodule.subpackage`

### 5. Module Dependencies

If your new module needs to depend on other modules in this project:

```kotlin
dependencies {
    implementation(project(":mosaic-core"))
    // Add other module dependencies as needed
}
```

## Example Module Types

Here are some example module types you might want to add:

### API Module
```kotlin
// api/build.gradle.kts
dependencies {
    implementation(project(":mosaic-core"))
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.0")
}
```

### CLI Module
```kotlin
// cli/build.gradle.kts
dependencies {
    implementation(project(":mosaic-core"))
    implementation("com.github.ajalt:clikt:2.8.0")
}
```

### Utils Module
```kotlin
// utils/build.gradle.kts
dependencies {
    implementation(project(":mosaic-core"))
    implementation("org.apache.commons:commons-lang3:3.12.0")
}
```

## Running Tasks

All tasks from the root build.gradle.kts are available for each module:

- `./gradlew :mosaic-core:build` - Build only the mosaic-core module
- `./gradlew :your-new-module:test` - Run tests for your new module
- `./gradlew :your-new-module:ktlintCheck` - Check code style for your new module
- `./gradlew build` - Build all modules
- `./gradlew test` - Run tests for all modules

## Code Quality and Coverage

All modules automatically inherit:
- ktlint code style checking
- detekt static analysis
- kover code coverage (80% line and branch coverage required)
- JUnit 5 test framework
- Kotlin coroutines support

## Best Practices

1. **Keep modules focused**: Each module should have a single responsibility
2. **Minimize dependencies**: Only depend on what you actually need
3. **Follow naming conventions**: Use consistent package and module naming
4. **Test thoroughly**: Each module should have its own comprehensive test suite
5. **Document interfaces**: If your module provides APIs for other modules, document them well

