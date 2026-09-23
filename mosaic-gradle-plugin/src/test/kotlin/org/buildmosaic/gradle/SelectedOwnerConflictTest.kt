package org.buildmosaic.gradle

import org.buildmosaic.analysis.CallableContract
import org.buildmosaic.analysis.CanvasKeyIdentity
import org.buildmosaic.analysis.KeyContract
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.SourceLocation
import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class SelectedOwnerConflictTest {
  private val site = SourceLocation("owner", "Owner.kt", 1, 1)

  @Test fun `duplicate key owners fail before policy`() {
    val first =
      ModuleContract("first", keys = listOf(KeyContract("shared.Key", CanvasKeyIdentity("first.Metrics"), site)))
    val second =
      ModuleContract("second", keys = listOf(KeyContract("shared.Key", CanvasKeyIdentity("second.Metrics"), site)))

    val failure = assertFailsWith<GradleException> { requireUniqueSelectedOwners(listOf(first, second)) }
    assertContains(failure.message.orEmpty(), "Conflicting selected Mosaic owners")
    assertContains(failure.message.orEmpty(), "shared.Key")
  }

  @Test fun `declaration kinds retain independent namespaces`() {
    val key = ModuleContract("keys", keys = listOf(KeyContract("shared.Id", CanvasKeyIdentity("first.Metrics"), site)))
    val callable =
      ModuleContract("functions", callables = listOf(CallableContract("shared.Id", effects = emptyList(), site = site)))

    requireUniqueSelectedOwners(listOf(key, callable))
  }
}
