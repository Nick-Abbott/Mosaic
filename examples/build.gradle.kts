plugins {
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.ksp) apply false
}

allprojects {
  repositories {
    mavenCentral()
  }
}
