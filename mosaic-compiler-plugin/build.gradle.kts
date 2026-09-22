description = "Experimental Kotlin IR extraction for Mosaic"

plugins {
  id("kotlin.convention")
  id("quality.convention")
}

dependencies {
  compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.10")
  implementation(project(":mosaic-analysis-core"))
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.1")
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
  notCompatibleWithConfigurationCache(
    "The phase-zero compiler subprocess fixture resolves its compiler classpath at execution time",
  )
  doFirst {
    systemProperty("mosaic.plugin.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
    systemProperty("mosaic.fixture.classpath", classpath.asPath)
  }
}
