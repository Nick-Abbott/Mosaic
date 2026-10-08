description = "OpenTelemetry tracing for Mosaic execution"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  id("library.convention")
  id("runtime-compatibility.convention")
}

dependencies {
  api(project(":mosaic-core"))
  api(libs.opentelemetry.api)
  testImplementation(libs.opentelemetry.sdk)
  testImplementation(libs.opentelemetry.sdk.testing)
  testImplementation(libs.opentelemetry.kotlin)
}
