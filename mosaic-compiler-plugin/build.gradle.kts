import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider

abstract class CompilerFixtureArguments : CommandLineArgumentProvider {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  abstract val pluginJar: RegularFileProperty

  @get:Classpath
  abstract val fixtureClasspath: ConfigurableFileCollection

  override fun asArguments(): Iterable<String> =
    listOf(
      "-Dmosaic.plugin.jar=${pluginJar.get().asFile.absolutePath}",
      "-Dmosaic.fixture.classpath=${fixtureClasspath.asPath}",
    )
}

description = "Experimental Kotlin IR extraction for Mosaic"

plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
  implementation(project(":mosaic-analysis-core"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
  testImplementation(project(":mosaic-core"))
  testImplementation(kotlin("test"))
}

tasks.jar {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  from({
    configurations.runtimeClasspath.get().filter { it.extension == "jar" }.map { zipTree(it) }
  })
}

tasks.test {
  useJUnitPlatform()
  dependsOn(tasks.jar)
  val fixtureArguments = objects.newInstance<CompilerFixtureArguments>()
  fixtureArguments.pluginJar.set(tasks.jar.flatMap { it.archiveFile })
  fixtureArguments.fixtureClasspath.from(classpath)
  jvmArgumentProviders.add(fixtureArguments)
}
