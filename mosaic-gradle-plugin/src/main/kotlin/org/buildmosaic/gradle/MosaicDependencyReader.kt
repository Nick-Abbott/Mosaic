package org.buildmosaic.gradle

import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.SUMMARY_PATH
import org.buildmosaic.analysis.SummaryCodec
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import java.io.IOException
import java.util.jar.JarFile

internal object MosaicDependencyReader {
  fun read(dependencyJars: ConfigurableFileCollection): List<ModuleContract> {
    val directories = dependencyJars.files.filter { it.isDirectory }
    if (directories.isNotEmpty()) {
      throw GradleException("Mosaic verification requires dependency JARs, not class directories")
    }
    return dependencyJars.files.filter { it.isFile && it.extension == "jar" }.sortedBy { it.name }.mapNotNull(::read)
  }

  private fun read(jar: java.io.File): ModuleContract? =
    try {
      JarFile(jar).use { archive ->
        val resource = archive.getJarEntry(SUMMARY_PATH) ?: return@use null
        val summary = SummaryCodec.decode(archive.getInputStream(resource).readBytes())
        if (!summary.complete) throw GradleException("summary is partial")
        summary.module
      }
    } catch (failure: RuntimeException) {
      invalidMetadata(jar, failure)
    } catch (failure: IOException) {
      invalidMetadata(jar, failure)
    }

  private fun invalidMetadata(
    jar: java.io.File,
    failure: Exception,
  ): Nothing {
    throw GradleException("${jar.name}: invalid Mosaic summary: ${failure.message}", failure)
  }
}
