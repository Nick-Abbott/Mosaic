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
  id("library.convention")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
  compileOnly(project(":mosaic-analysis-core"))
  testImplementation(project(":mosaic-analysis-core"))
  testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
  testImplementation(project(":mosaic-core"))
  testImplementation(kotlin("test"))
}

val bundledCompilerRuntime =
  configurations.create("bundledCompilerRuntime") {
    isCanBeConsumed = false
    isCanBeResolved = true
  }
dependencies { add(bundledCompilerRuntime.name, project(":mosaic-analysis-core")) }

tasks.jar {
  dependsOn(bundledCompilerRuntime)
  duplicatesStrategy = DuplicatesStrategy.FAIL
  exclude("META-INF/versions/**/module-info.class")
  manifest.attributes("Implementation-Version" to project.version.toString())
  from({
    bundledCompilerRuntime.filter { it.extension == "jar" }.map { zipTree(it) }
  })
}

tasks.test {
  useJUnitPlatform()
  dependsOn(tasks.jar, ":mosaic-core:jar")
  val fixtureArguments = objects.newInstance<CompilerFixtureArguments>()
  fixtureArguments.pluginJar.set(tasks.jar.flatMap { it.archiveFile })
  fixtureArguments.fixtureClasspath.from(
    classpath.elements.map { entries ->
      entries.map { it.asFile }.filterNot { it.path.contains("mosaic-core/build/") }
    },
  )
  fixtureArguments.fixtureClasspath.from(
    rootProject.layout.projectDirectory.file("mosaic-core/build/libs/mosaic-core-${project.version}.jar"),
  )
  jvmArgumentProviders.add(fixtureArguments)
}

val compilerVersionMetadata =
  tasks.register<WriteProperties>("generateCompilerAnalysisVersion") {
    destinationFile =
      layout.buildDirectory.file("generated/mosaic-compiler/org/buildmosaic/compiler/version.properties").get().asFile
    property("version", project.version.toString())
  }
tasks.processResources {
  dependsOn(compilerVersionMetadata)
  from(layout.buildDirectory.dir("generated/mosaic-compiler"))
}
