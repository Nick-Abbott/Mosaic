plugins {
    `java-platform`
}

group = "com.buildmosaic"
version = project.property("mosaic.version") as String

val mosaicVersion = version

// Configure the BOM
javaPlatform {
    allowDependencies()
}

dependencies {
    // Define constraints for all the dependencies that will be used in the BOM
    constraints {
        // Core Mosaic dependencies
        api("com.buildmosaic:mosaic-core:${mosaicVersion}")
        api("com.buildmosaic:mosaic-test:${mosaicVersion}")
        
        // KSP dependencies
        api("com.buildmosaic:mosaic-catalog-ksp:${mosaicVersion}")
        api("com.buildmosaic:mosaic-build-ksp:${mosaicVersion}")
        
        // Build plugin (as a platform, not a direct dependency)
        api("com.buildmosaic:mosaic-build-plugin:${mosaicVersion}")
    }
}
