package org.buildmosaic.compiler

import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import kotlin.test.Test

/** Compiles and runs an integration against the public runtime API, without friend-module access. */
class ObserverCompositionConsumerTest {
  @Test fun compositeProjectsComponentTokensUsingOnlyPublicApi() {
    val directory = Files.createTempDirectory("mosaic-observer-consumer").toFile()
    try {
      val source = File(directory, "Composite.kt")
      source.writeText(checkNotNull(javaClass.getResource("/fixtures/observer-composite.kt")).readText())
      val classes = File(directory, "classes")
      compile(source, classes)
      URLClassLoader(arrayOf(classes.toURI().toURL()), javaClass.classLoader).use { loader ->
        loader.loadClass("consumer.CompositeKt").getMethod("verifyComposite").invoke(null)
      }
    } finally {
      directory.deleteRecursively()
    }
  }
}
