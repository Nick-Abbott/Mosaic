package org.buildmosaic.gradle

import org.buildmosaic.analysis.SUMMARY_PATH
import org.gradle.api.GradleException
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.util.jar.JarFile

/** Copies the selected resource byte for byte; verification owns decoding and compatibility checks. */
@CacheableTransform
abstract class MosaicSummaryTransform : TransformAction<TransformParameters.None> {
  @get:InputArtifact
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val artifact = inputArtifact.get().asFile
    if (!artifact.isFile || artifact.extension != "jar") {
      throw GradleException("Mosaic verification requires dependency JARs, not ${artifact.absolutePath}")
    }
    JarFile(artifact).use { jar ->
      val resource = jar.getJarEntry(SUMMARY_PATH) ?: return
      val output = outputs.file("${artifact.name}.mosaic-summary.json")
      jar.getInputStream(resource).use { input -> output.outputStream().use(input::copyTo) }
    }
  }
}
