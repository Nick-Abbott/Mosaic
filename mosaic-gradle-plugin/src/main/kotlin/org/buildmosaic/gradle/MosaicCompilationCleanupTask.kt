package org.buildmosaic.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

/** A pending marker survives only a failed compilation; finalizers also run after task failure. */
abstract class MosaicCompilationCleanupTask : DefaultTask() {
  @get:Internal abstract val pendingFile: RegularFileProperty

  @get:Internal abstract val trustedOutputs: ConfigurableFileCollection

  @TaskAction
  fun cleanup() {
    val pending = pendingFile.get().asFile
    if (!pending.exists()) return
    trustedOutputs.files.forEach { it.deleteRecursively() }
    pending.delete()
  }
}
