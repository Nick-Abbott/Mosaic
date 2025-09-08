# Mosaic BOM (Bill of Materials)

This module provides a Bill of Materials (BOM) for Mosaic, making it easier to manage versions of Mosaic dependencies.

## Usage

### Gradle (Kotlin DSL)

```kotlin
// In your build.gradle.kts
plugins {
    // Apply the Mosaic BOM
    id("java")
    kotlin("jvm") version "1.9.0" // or your preferred Kotlin version
}

dependencies {
    // Import the BOM (replace VERSION with the desired version)
    implementation(platform("org.buildmosaic:mosaic-bom:VERSION"))
    
    // Add Mosaic dependencies without version numbers
    implementation("org.buildmosaic:mosaic-core")
    testImplementation("org.buildmosaic:mosaic-test")
    
    // For KSP support
    ksp("org.buildmosaic:mosaic-catalog-ksp")
}

// If you're using the Mosaic Gradle plugin
plugins {
    id("org.buildmosaic.mosaic-consumer-plugin") version "VERSION"
}
```

### Gradle (Groovy DSL)

```groovy
// In your build.gradle
plugins {
    // Apply the Mosaic BOM
    id 'java'
    id 'org.jetbrains.kotlin.jvm' version '1.9.0' // or your preferred Kotlin version
}

dependencies {
    // Import the BOM
    implementation platform('org.buildmosaic:mosaic-bom:VERSION')
    
    // Add Mosaic dependencies without version numbers
    implementation 'org.buildmosaic:mosaic-core'
    testImplementation 'org.buildmosaic:mosaic-test'
    
    // For KSP support
    ksp 'org.buildmosaic:mosaic-catalog-ksp'
}

// If you're using the Mosaic Gradle plugin
plugins {
    id 'org.buildmosaic.mosaic-consumer-plugin' version 'VERSION'
}
```

### Maven

```xml
<project>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.buildmosaic</groupId>
                <artifactId>mosaic-bom</artifactId>
                <version>VERSION</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <dependencies>
        <dependency>
            <groupId>org.buildmosaic</groupId>
            <artifactId>mosaic-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.buildmosaic</groupId>
            <artifactId>mosaic-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <!-- For KSP support -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.jetbrains.kotlin</groupId>
                <artifactId>kotlin-maven-plugin</artifactId>
                <configuration>
                    <compilerPlugins>
                        <compilerPlugin>ksp</compilerPlugin>
                    </compilerPlugins>
                </configuration>
                <dependencies>
                    <dependency>
                        <groupId>org.buildmosaic</groupId>
                        <artifactId>mosaic-catalog-ksp</artifactId>
                        <version>${mosaic.version}</version>
                    </dependency>
                </dependencies>
            </plugin>
        </plugins>
    </build>
</project>
```

## Benefits of Using the BOM

1. **Simplified Dependency Management**: Single source of truth for all Mosaic dependencies
2. **Version Alignment**: Ensures all Mosaic components are compatible with each other
3. **Easier Upgrades**: Update all Mosaic dependencies by changing a single version number
4. **Reduced Configuration**: No need to specify versions for individual Mosaic dependencies

## Included Dependencies

The BOM includes the following Mosaic artifacts:

- `mosaic-core`: Core Mosaic functionality
- `mosaic-test`: Testing utilities for Mosaic
- `mosaic-catalog-ksp`: KSP processor for Mosaic catalogs
- `mosaic-consumer-ksp`: KSP integration for Mosaic
- `mosaic-consumer-plugin`: Gradle plugin for Mosaic

## Versioning

The BOM follows [Semantic Versioning](https://semver.org/). The version number of the BOM should match the version of the Mosaic components it includes.
