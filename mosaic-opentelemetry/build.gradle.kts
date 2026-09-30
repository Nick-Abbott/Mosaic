description = "OpenTelemetry tracing for Mosaic executions"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
  id("library.convention")
}

dependencies {
  api(project(":mosaic-core"))
  api(libs.opentelemetry.api)
  implementation(libs.opentelemetry.kotlin)
  testImplementation(libs.opentelemetry.sdk)
  testImplementation(libs.opentelemetry.sdk.testing)
}
