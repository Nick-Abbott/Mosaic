description = "Bill of Materials for Mosaic"

plugins {
    `java-platform`
    id("publish.convention")
}

val mosaicVersion = version

// Configure the BOM
javaPlatform {
    allowDependencies()
}

dependencies {
    // Define constraints for all the dependencies that will be used in the BOM
    constraints {
        // Core Mosaic dependencies
        api("org.buildmosaic:mosaic-core:${mosaicVersion}")
        api("org.buildmosaic:mosaic-test:${mosaicVersion}")
        
        // KSP dependencies
        api("org.buildmosaic:mosaic-catalog-ksp:${mosaicVersion}")
        api("org.buildmosaic:mosaic-consumer-ksp:${mosaicVersion}")
        
        // Build plugin (as a platform, not a direct dependency)
        api("org.buildmosaic:mosaic-consumer-plugin:${mosaicVersion}")
    }
}
