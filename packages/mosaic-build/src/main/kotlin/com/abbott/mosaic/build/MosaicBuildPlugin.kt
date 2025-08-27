package com.abbott.mosaic.build

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import io.github.classgraph.ClassGraph
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction

abstract class GenerateMosaicRegistryTask : DefaultTask() {
  @get:InputFiles
  abstract val classpath: ConfigurableFileCollection

  @get:OutputDirectory
  abstract val outputDir: DirectoryProperty

  @TaskAction
  fun generate() {
    val scanResult =
      ClassGraph()
        .overrideClasspath(classpath.files)
        .enableClassInfo()
        .scan()

    val classNames =
      scanResult
        .getSubclasses("com.abbott.mosaic.Tile")
        .filterNot { it.isAbstract || it.isInterface }
        .mapTo(mutableSetOf()) { ClassName.bestGuess(it.name) }

    if (classNames.isEmpty()) return

    val registerFun =
      FunSpec
        .builder("registerGeneratedTiles")
        .receiver(ClassName("com.abbott.mosaic", "MosaicRegistry"))

    classNames.forEach { className ->
      registerFun.addStatement(
        "register(%T::class) { mosaic -> %T(mosaic) }",
        className,
        className,
      )
    }

    val fileSpec =
      FileSpec
        .builder("com.abbott.mosaic.generated", "GeneratedMosaicRegistry")
        .addFunction(registerFun.build())
        .build()

    val outDir = outputDir.get().asFile
    outDir.deleteRecursively()
    fileSpec.writeTo(outDir.toPath())
  }
}

class MosaicBuildPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.afterEvaluate {
      val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)

        val generateTask =
          project.tasks.register("generateMosaicRegistry", GenerateMosaicRegistryTask::class.java) { task ->
            task.outputDir.set(project.layout.buildDirectory.dir("generated/mosaic"))
            task.classpath.from(sourceSets.getByName("main").runtimeClasspath)
            task.dependsOn(project.tasks.named("classes"))
          }

        project.tasks.named("build").configure { it.dependsOn(generateTask) }
    }
  }
}
