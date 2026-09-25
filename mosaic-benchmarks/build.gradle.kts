plugins {
  id("kotlin.convention")
  id("me.champeau.jmh") version "0.7.3"
}

dependencies {
  implementation(project(":mosaic-core"))
  testImplementation(kotlin("test"))
  testImplementation(libs.kotlinx.coroutines.core)
}

tasks.withType<Test> {
  useJUnitPlatform()
}

tasks.named("check") {
  dependsOn(tasks.named("jmhClasses"))
}

jmh {
  jmhVersion = "1.37"
  benchmarkMode = listOf("avgt")
  warmupIterations = 3
  iterations = 5
  warmup = "1s"
  timeOnIteration = "1s"
  fork = 2
  timeUnit = "us"
  resultFormat = "JSON"
  failOnError = true
}
