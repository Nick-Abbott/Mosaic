# Mosaic BOM (Bill of Materials)

This module provides a Bill of Materials (BOM) for Mosaic, making it easier to manage versions of Mosaic dependencies.

## Usage

### Gradle (Kotlin DSL)

```kotlin
// In an existing Kotlin/JVM project's build.gradle.kts

dependencies {
    // Import the BOM (replace 0.4.0 with the desired version)
    implementation(platform("org.buildmosaic:mosaic-bom:0.4.0"))
    
    // Add Mosaic dependencies without version numbers
    implementation("org.buildmosaic:mosaic-core")
    testImplementation("org.buildmosaic:mosaic-test")
}
```

### Gradle (Groovy DSL)

```groovy
// In an existing Kotlin/JVM project's build.gradle

dependencies {
    // Import the BOM
    implementation platform('org.buildmosaic:mosaic-bom:0.4.0')
    
    // Add Mosaic dependencies without version numbers
    implementation 'org.buildmosaic:mosaic-core'
    testImplementation 'org.buildmosaic:mosaic-test'
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
                <version>0.4.0</version>
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

Mosaic uses ordinary library dependencies. The BOM aligns `mosaic-core` and
`mosaic-test`; no Mosaic-specific registration plugin or processor is needed.
The Kotlin 2.2.10 restriction applies to the optional analysis plugin, not to
the BOM itself.

## Versioning

The BOM follows [Semantic Versioning](https://semver.org/). The version number of the BOM should match the version of the Mosaic components it includes.
