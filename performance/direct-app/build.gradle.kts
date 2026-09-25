plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

dependencies {
  implementation(project(":shared"))
  implementation("io.ktor:ktor-server-netty:3.6.0")
}

kotlin { jvmToolchain(21) }
application { mainClass.set("org.buildmosaic.performance.DirectMainKt") }

tasks.register("assertNoMosaicRuntime") {
  val runtime = configurations.runtimeClasspath
  inputs.files(runtime)
  doLast {
    val forbidden = runtime.get().resolvedConfiguration.resolvedArtifacts.filter {
      it.moduleVersion.id.group == "org.buildmosaic" || it.name.startsWith("mosaic-")
    }
    check(forbidden.isEmpty()) { "Direct runtime contains Mosaic: $forbidden" }
  }
}
tasks.named("check") { dependsOn("assertNoMosaicRuntime") }
