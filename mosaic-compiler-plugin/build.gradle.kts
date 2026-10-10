import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.process.CommandLineArgumentProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

fun Jar.bundleIntrospector(compilerApi: String) {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
  dependsOn(bundledCompilerRuntime)
  duplicatesStrategy = DuplicatesStrategy.FAIL
  exclude("META-INF/versions/**/module-info.class")
  manifest.attributes("Implementation-Version" to project.version.toString())
  manifest.attributes("Mosaic-Compiler-API" to compilerApi)
  from({
    bundledCompilerRuntime.filter { it.extension == "jar" }.map { zipTree(it) }
  })
}

tasks.jar { bundleIntrospector(libs.versions.kotlin.get()) }

// Recompile the same compiler-facing sources against each demonstrated ABI boundary.
// The host compiler, contract kernel, bundled runtime, and production admission stay fixed.
val introspectorJars =
  tasks.register("introspectorJars") {
    group = "build"
    description = "Build the shared-source introspector jars for the measured compiler ABI boundaries"
    dependsOn(tasks.jar)
  }
listOf("2.3.0", "2.3.20").forEach { compilerApi ->
  val suffix = compilerApi.replace(".", "_")
  val profileSources = sourceSets.create("introspector$suffix")
  kotlin.sourceSets.named(profileSources.name) { kotlin.srcDir("src/main/kotlin") }
  dependencies {
    add(profileSources.compileOnlyConfigurationName, "org.jetbrains.kotlin:kotlin-compiler-embeddable:$compilerApi")
    add(profileSources.compileOnlyConfigurationName, project(":mosaic-analysis-core"))
  }
  val compile =
    tasks.named<KotlinCompile>(profileSources.getCompileTaskName("kotlin")) {
      compilerOptions {
        moduleName.set(project.name)
        jvmTarget.set(JvmTarget.JVM_17)
        languageVersion.set(KotlinVersion.KOTLIN_2_4)
        apiVersion.set(KotlinVersion.KOTLIN_2_4)
      }
    }
  val jar =
    tasks.register<Jar>("introspectorJar$suffix") {
      bundleIntrospector(compilerApi)
      archiveClassifier.set("kotlin-$compilerApi")
      from(compile.flatMap { it.destinationDirectory })
      from(sourceSets.main.get().output.resourcesDir)
      dependsOn(tasks.processResources)
    }
  introspectorJars.configure { dependsOn(jar) }
}
tasks.assemble { dependsOn(introspectorJars) }

tasks.test {
  useJUnitPlatform()
  dependsOn(tasks.jar, ":mosaic-core:jar")
  val fixtureArguments = objects.newInstance<CompilerFixtureArguments>()
  fixtureArguments.pluginJar.set(tasks.jar.flatMap { it.archiveFile })
  fixtureArguments.fixtureClasspath.from(classpath.filter { !it.path.contains("mosaic-core/build/classes") })
  fixtureArguments.fixtureClasspath.from(
    project(":mosaic-core").layout.buildDirectory.file("libs/mosaic-core-${project.version}.jar"),
  )
  jvmArgumentProviders.add(fixtureArguments)
}
