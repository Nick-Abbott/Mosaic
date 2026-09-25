plugins { alias(libs.plugins.kotlin.jvm) }

dependencies {
  testImplementation(project(":shared"))
  testImplementation(project(":direct-app"))
  testImplementation(project(":mosaic-app"))
  testImplementation("io.ktor:ktor-server-test-host:3.6.0")
  testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(21) }
tasks.withType<Test> { useJUnitPlatform() }
