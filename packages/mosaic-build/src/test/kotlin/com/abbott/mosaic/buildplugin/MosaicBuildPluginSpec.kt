package com.abbott.mosaic.buildplugin

import com.abbott.mosaic.TestTile
import com.abbott.mosaic.Tile
import java.io.File
import kotlin.io.path.exists
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import com.abbott.mosaic.build.GenerateMosaicRegistryTask
import org.gradle.api.internal.project.ProjectInternal

class MosaicBuildPluginSpec {
  @Test
  fun `plugin registers task and compile dependency`() {
    val project = ProjectBuilder.builder().build()
    project.plugins.apply("java")
    project.plugins.apply("com.abbott.mosaic.build")
    (project as ProjectInternal).evaluate()

    val generate = project.tasks.named("generateMosaicRegistry").get()
    val build = project.tasks.named("build").get()

    val generateDepends = generate.dependsOn.map { dep ->
      when (dep) {
        is org.gradle.api.Task -> dep.name
        is org.gradle.api.tasks.TaskProvider<*> -> dep.name
        else -> dep.toString()
      }
    }
    assertTrue("classes" in generateDepends)

    val buildDepends = build.dependsOn.map { dep ->
      when (dep) {
        is org.gradle.api.Task -> dep.name
        is org.gradle.api.tasks.TaskProvider<*> -> dep.name
        else -> dep.toString()
      }
    }
    assertTrue("generateMosaicRegistry" in buildDepends)
  }

  @Test
  fun `task generates registry for tiles`(@TempDir tempDir: Path) {
    val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
    val task = project.tasks.create("generate", GenerateMosaicRegistryTask::class.java)
    val outDir = tempDir.resolve("out").toFile()
    task.outputDir.set(outDir)
    task.classpath.from(
      File(TestTile::class.java.protectionDomain.codeSource.location.toURI()),
      File(Tile::class.java.protectionDomain.codeSource.location.toURI()),
    )

    task.generate()

    val generated = outDir.toPath().resolve("com/abbott/mosaic/generated/GeneratedMosaicRegistry.kt")
    assertTrue(generated.exists())
    val text = generated.toFile().readText()
    assertTrue(text.contains("register(TestTile::class) { mosaic -> TestTile(mosaic) }"))
  }

  @Test
  fun `task does nothing when no tiles found`(@TempDir tempDir: Path) {
    val project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
    val task = project.tasks.create("generate", GenerateMosaicRegistryTask::class.java)
    val outDir = tempDir.resolve("out").toFile()
    task.outputDir.set(outDir)
    task.classpath.from(outDir)

    task.generate()

    val generated = outDir.toPath().resolve("com/abbott/mosaic/generated/GeneratedMosaicRegistry.kt").toFile()
    assertFalse(generated.exists())
  }
}
