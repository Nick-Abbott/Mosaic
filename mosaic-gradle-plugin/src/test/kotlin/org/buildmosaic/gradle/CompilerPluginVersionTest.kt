package org.buildmosaic.gradle

import org.gradle.api.GradleException
import java.nio.file.Files
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

class CompilerPluginVersionTest {
  @Test
  fun `artifact manifest must match plugin version`() {
    val jar = Files.createTempFile("mosaic-compiler-version-", ".jar").toFile()
    val manifest = Manifest()
    manifest.mainAttributes.putValue("Manifest-Version", "1.0")
    manifest.mainAttributes.putValue("Implementation-Version", "9.9.9")
    JarOutputStream(jar.outputStream(), manifest).use { }

    validateCompilerPluginVersion(jar, "9.9.9")
    val failure = assertFailsWith<GradleException> { validateCompilerPluginVersion(jar, "0.2.0") }
    assertContains(failure.message.orEmpty(), "installed Gradle plugin is 0.2.0, compiler artifact is 9.9.9")
  }
}
