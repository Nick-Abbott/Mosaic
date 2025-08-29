plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("testing.convention")
}

dependencies {
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.kotlinx.coroutines.test)
}
