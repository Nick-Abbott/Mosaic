package org.buildmosaic.gradle

import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.requireUniqueContractOwners
import org.gradle.api.GradleException

internal fun requireUniqueSelectedOwners(modules: List<ModuleContract>) {
  try {
    requireUniqueContractOwners(modules)
  } catch (failure: IllegalArgumentException) {
    throw GradleException(failure.message, failure)
  }
}
