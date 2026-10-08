import java.util.Properties

plugins { java }

val capabilityFile = rootProject.file("compatibility/runtime-capabilities.properties")
val capability = Properties().apply { capabilityFile.inputStream().use(::load) }.getProperty("canvasAnalysis")
val descriptor = tasks.register("generateRuntimeCompatibility") {
  val module = "${project.group}:${project.name}"
  val runtimeVersion = project.version.toString()
  val requirements = if (project.name == "mosaic-core") listOf(capability) else emptyList()
  inputs.file(capabilityFile)
  inputs.property("module", module)
  inputs.property("runtimeVersion", runtimeVersion)
  inputs.property("requires", requirements)
  val output = layout.buildDirectory.file("generated/runtime-compatibility/META-INF/mosaic/runtime-compatibility.json")
  outputs.file(output)
  doLast {
    val file = output.get().asFile
    file.parentFile.mkdirs()
    file.writeText(
      """{"descriptorVersion":1,"module":"$module","runtimeVersion":"$runtimeVersion","requires":[${requirements.joinToString(",") { "\"$it\"" }}]}""" + "\n",
    )
  }
}
tasks.processResources {
  dependsOn(descriptor)
  from(layout.buildDirectory.dir("generated/runtime-compatibility"))
}
tasks.jar {
  manifest.attributes("Implementation-Title" to "${project.group}:${project.name}", "Implementation-Version" to project.version.toString())
}
