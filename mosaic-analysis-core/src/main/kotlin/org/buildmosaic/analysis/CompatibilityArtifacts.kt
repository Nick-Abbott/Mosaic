package org.buildmosaic.analysis

import java.io.File
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.Manifest

/** Reads selected artifacts, never an arbitrary classloader resource. Shared by compiler and Gradle. */
object CompatibilityArtifacts {
  private val markers =
    mapOf(
      "org.buildmosaic:mosaic-core" to "org/buildmosaic/core/injection/Canvas.class",
      "org.buildmosaic:mosaic-test" to "org/buildmosaic/test/TestMosaic.class",
      "org.buildmosaic:mosaic-opentelemetry" to "org/buildmosaic/opentelemetry/OpenTelemetryObserver.class",
    )

  fun runtimes(artifacts: Collection<File>): List<RuntimeRequirement> {
    val selected =
      artifacts.distinctBy { it.canonicalPath }.flatMap { artifact ->
        readArtifact(artifact) { exists, read ->
          val owned = markers.filterValues(exists).keys
          val bytes = read(RUNTIME_DESCRIPTOR_PATH)
          require(owned.size <= 1) { "Conflicting Mosaic Runtime modules in ${artifact.name}" }
          if (bytes == null) {
            require(owned.isEmpty()) {
              "Recognized Mosaic Runtime ${owned.single()} in ${artifact.name} has no compatibility descriptor. " +
                "Select a protocol-aware Runtime and align compile/runtime dependencies."
            }
            emptyList()
          } else {
            val descriptor = CompatibilityProtocol.decodeDescriptor(bytes)
            read("META-INF/MANIFEST.MF")?.let { manifestBytes ->
              val attributes = Manifest(manifestBytes.inputStream()).mainAttributes
              attributes.getValue("Implementation-Title")?.let {
                require(
                  it == descriptor.module,
                ) { "Inconsistent Mosaic Runtime publication module in ${artifact.name}" }
              }
              attributes.getValue("Implementation-Version")?.let {
                require(
                  it == descriptor.runtimeVersion,
                ) { "Inconsistent Mosaic Runtime publication version in ${artifact.name}" }
              }
            }
            require(
              owned == setOf(descriptor.module),
            ) { "Inconsistent Mosaic Runtime module identity in ${artifact.name}" }
            listOf(descriptor.requirement())
          }
        }
      }
    CompatibilityProtocol.admit(selected)
    require(selected.map { it.module }.distinct().size == selected.size) { "Duplicate selected Mosaic Runtime modules" }
    return CompatibilityProtocol.merge(selected)
  }

  fun summaries(artifacts: Collection<File>): List<SummaryMetadata> =
    artifacts.distinctBy { it.canonicalPath }.flatMap { artifact ->
      readArtifact(artifact) { _, read -> read(SUMMARY_PATH)?.let { listOf(SummaryCodec.decode(it)) } ?: emptyList() }
    }.also { summaries ->
      requireUniqueContractOwners(summaries.map { it.module })
      require(
        summaries.map {
          it.moduleId
        }.distinct().size == summaries.size,
      ) { "Duplicate selected Mosaic summary module identities" }
    }

  fun requireAligned(
    compile: List<RuntimeRequirement>,
    runtime: List<RuntimeRequirement>,
  ) {
    require(compile == runtime) {
      "Mosaic compile/runtime Runtime selection mismatch. " +
        "Align compileClasspath and runtimeClasspath Mosaic dependencies. " +
        "Compile: $compile; runtime: $runtime"
    }
  }

  fun readBounded(input: InputStream): ByteArray =
    input.readNBytes(ProtocolJson.MAX_BYTES + 1).also {
      require(it.size <= ProtocolJson.MAX_BYTES) { "Mosaic compatibility resource exceeds size limit" }
    }

  private fun <T> readArtifact(
    artifact: File,
    action: ((String) -> Boolean, (String) -> ByteArray?) -> T,
  ): T {
    // Kotlin classpaths may contain absent resource directories, which own no bytecode.
    if (!artifact.exists() && artifact.extension != "jar") return action({ false }, { null })
    if (artifact.isDirectory) {
      return action({ File(artifact, it).isFile }, { path ->
        File(artifact, path).takeIf { it.isFile }?.inputStream()?.use(::readBounded)
      })
    }
    require(artifact.isFile && artifact.extension == "jar") {
      "Mosaic compatibility requires selected JVM JARs or compiler class directories: ${artifact.name}"
    }
    return JarFile(artifact).use { jar ->
      val resources =
        jar.entries().asSequence()
          .filter { it.name in setOf(RUNTIME_DESCRIPTOR_PATH, SUMMARY_PATH) }.toList()
      require(
        resources.map {
          it.name
        }.distinct().size == resources.size,
      ) { "Duplicate Mosaic compatibility resources in ${artifact.name}" }
      action({
        jar.getJarEntry(it) != null
      }, { path -> jar.getJarEntry(path)?.let { jar.getInputStream(it).use(::readBounded) } })
    }
  }
}
