group = "com.abbott.mosaic"
version = "1.0.0"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  `kotlin-dsl`
  `java-gradle-plugin`
}

dependencies {
  implementation(libs.kotlinpoet)
  implementation(libs.kotlinpoet.ksp)
  implementation(libs.ksp)

  compileOnly(gradleApi())
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.10")
  compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.0.10-1.0.24")
}

gradlePlugin {
  plugins {
    create("mosaicConsumer") {
      id = "com.abbott.mosaic.build"
      implementationClass = "com.abbott.mosaic.build.plugin.MosaicConsumerPlugin"
      displayName = "Mosaic Consumer Plugin"
      description = "Merges META-INF/mosaic catalogs + wires KSP aggregator"
    }
  }
}
