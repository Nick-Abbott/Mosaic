plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
}

dependencies {
  api("io.ktor:ktor-server-core:3.6.0")
  api("io.ktor:ktor-server-netty:3.6.0")
  api("io.ktor:ktor-server-content-negotiation:3.6.0")
  api("io.ktor:ktor-serialization-kotlinx-json:3.6.0")
  api(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  runtimeOnly("org.slf4j:slf4j-nop:2.0.19")
}

kotlin { jvmToolchain(21) }
