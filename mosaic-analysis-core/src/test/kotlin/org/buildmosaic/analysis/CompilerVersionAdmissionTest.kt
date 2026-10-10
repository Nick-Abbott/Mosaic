package org.buildmosaic.analysis

import kotlin.test.Test
import kotlin.test.assertFailsWith

class CompilerVersionAdmissionTest {
  @Test
  fun `production admission remains exact`() =
    withProbe(null) {
      CompilerVersionAdmission.requireSupported("2.4.20")
      CompilerVersionAdmission.requireSupported("2.4.20-release-123")
      for (version in listOf("2.3.0", "2.4.0", "2.4.21", "2.4.20-RC1", "")) {
        assertFailsWith<IllegalArgumentException> { CompilerVersionAdmission.requireSupported(version) }
      }
    }

  @Test
  fun `probing requires explicit true`() {
    for (value in listOf("false", "TRUE", "1", "")) {
      withProbe(value) {
        assertFailsWith<IllegalArgumentException> { CompilerVersionAdmission.requireSupported("2.3.0") }
      }
    }
    withProbe("true") { CompilerVersionAdmission.requireSupported("2.3.0") }
  }

  private fun withProbe(
    value: String?,
    block: () -> Unit,
  ) {
    val property = CompilerVersionAdmission.COMPATIBILITY_PROBE_PROPERTY
    val original = System.getProperty(property)
    try {
      if (value == null) System.clearProperty(property) else System.setProperty(property, value)
      block()
    } finally {
      if (original == null) System.clearProperty(property) else System.setProperty(property, original)
    }
  }
}
