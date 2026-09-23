description = "Experimental Gradle integration for Mosaic analysis"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("java-gradle-plugin")
}

dependencies {
  implementation(project(":mosaic-analysis-core"))
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
  testImplementation(project(":mosaic-core"))
  testImplementation(project(":mosaic-compiler-plugin"))
  testImplementation(gradleTestKit())
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

gradlePlugin {
  plugins {
    create("mosaicAnalysis") {
      id = "org.buildmosaic.analysis"
      implementationClass = "org.buildmosaic.gradle.MosaicAnalysisPlugin"
    }
  }
}

tasks.test {
  dependsOn(":mosaic-compiler-plugin:jar", ":mosaic-core:jar")
}
