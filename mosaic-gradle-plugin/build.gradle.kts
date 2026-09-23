import org.gradle.api.tasks.WriteProperties
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata

description = "Mosaic contract analysis for Kotlin/JVM Gradle builds"

plugins {
  id("kotlin.convention")
  id("quality.convention")
  id("java-gradle-plugin")
  id("publish.convention")
  alias(libs.plugins.plugin.publish)
}

val pluginVersionMetadata =
  tasks.register<WriteProperties>("generateMosaicAnalysisVersion") {
    destinationFile =
      layout.buildDirectory.file("generated/mosaic-analysis/org/buildmosaic/gradle/version.properties").get().asFile
    property("version", project.version.toString())
  }
tasks.processResources {
  dependsOn(pluginVersionMetadata)
  from(layout.buildDirectory.dir("generated/mosaic-analysis"))
}

val testKitKotlinPlugin =
  configurations.create("mosaicTestKitKotlinPlugin") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }

dependencies {
  implementation("org.ow2.asm:asm:9.7.1")
  implementation(project(":mosaic-analysis-core"))
  compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
  add(testKitKotlinPlugin.name, "org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10")
  testImplementation(project(":mosaic-core"))
  testImplementation(project(":mosaic-compiler-plugin"))
  testImplementation(gradleTestKit())
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
  pluginClasspath.from(testKitKotlinPlugin)
}

gradlePlugin {
  plugins {
    create("mosaicAnalysis") {
      id = "org.buildmosaic.analysis"
      displayName = "Mosaic Analysis"
      description = "Kotlin/JVM Mosaic contract extraction and verification"
      tags.set(listOf("kotlin", "analysis"))
      implementationClass = "org.buildmosaic.gradle.MosaicAnalysisPlugin"
    }
  }
}

gradlePlugin {
  website = "https://github.com/Nick-Abbott/Mosaic"
  vcsUrl = "https://github.com/Nick-Abbott/Mosaic.git"
}

tasks.test {
  dependsOn(":mosaic-compiler-plugin:jar", ":mosaic-core:jar")
}
