# Mosaic BOM (Bill of Materials)

This module provides a Bill of Materials (BOM) for Mosaic, making it easier to align versions of Mosaic runtime dependencies.

## Usage

### Gradle (Kotlin DSL)

```kotlin
// In an existing Kotlin/JVM project's build.gradle.kts

dependencies {
    // Import the BOM (replace 0.5.0 with the desired version)
    implementation(platform("org.buildmosaic:mosaic-bom:0.5.0"))
    
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
    implementation platform('org.buildmosaic:mosaic-bom:0.5.0')
    
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
                <version>0.5.0</version>
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

1. **Simplified Dependency Management**: One version for the included Mosaic runtime libraries
2. **Version Alignment**: Aligns Mosaic runtime module versions
3. **Easier Upgrades**: Update the included runtime libraries by changing one BOM version
4. **Reduced Configuration**: No need to specify versions for the included runtime dependencies

## Included Dependencies

The BOM includes the following Mosaic artifacts:

- `mosaic-core`: Core Mosaic functionality
- `mosaic-test`: Testing utilities for Mosaic
- `mosaic-opentelemetry`: Optional runtime tracing with application-owned OpenTelemetry (0.6.0 onward)

Mosaic uses ordinary library dependencies. The BOM aligns Mosaic runtime modules;
no Mosaic-specific registration plugin or processor is needed. Adding the BOM
does not install the optional tracing module or configure OpenTelemetry.
Exact Kotlin restrictions apply to the optional analysis plugin (2.2.10 for
released 0.5.0, 2.4.20 for current 0.6 development), not to the BOM itself.

## Versioning

The BOM follows [Semantic Versioning](https://semver.org/). The version number of the BOM should match the version of the Mosaic components it includes.
