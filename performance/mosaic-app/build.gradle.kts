plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

val mosaicVersion = providers.fileContents(layout.projectDirectory.file("../../gradle.properties"))
  .asText.get().lineSequence().first { it.startsWith("mosaic.version=") }.substringAfter('=')

dependencies {
  implementation(project(":shared"))
  implementation("org.buildmosaic:mosaic-core:$mosaicVersion")
  implementation("io.ktor:ktor-server-netty:3.6.0")
}

kotlin { jvmToolchain(21) }
application { mainClass.set("org.buildmosaic.performance.MosaicMainKt") }
