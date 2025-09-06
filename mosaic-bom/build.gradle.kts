plugins {
    `java-platform`
}

group = "org.buildmosaic"
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
        api("org.buildmosaic:mosaic-core:${mosaicVersion}")
        api("org.buildmosaic:mosaic-test:${mosaicVersion}")
        
        // KSP dependencies
        api("org.buildmosaic:mosaic-catalog-ksp:${mosaicVersion}")
        api("org.buildmosaic:mosaic-build-ksp:${mosaicVersion}")
        
        // Build plugin (as a platform, not a direct dependency)
        api("org.buildmosaic:mosaic-build-plugin:${mosaicVersion}")
    }
}
