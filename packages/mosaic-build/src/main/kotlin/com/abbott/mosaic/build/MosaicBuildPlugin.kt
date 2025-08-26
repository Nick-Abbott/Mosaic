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

    val tileClasses = scanResult.getSubclasses("com.abbott.mosaic.Tile")
    if (tileClasses.isEmpty()) return

    val registerFun =
      FunSpec
        .builder("registerGeneratedTiles")
        .receiver(ClassName("com.abbott.mosaic", "MosaicRegistry"))

    tileClasses.forEach { classInfo ->
      val className = ClassName.bestGuess(classInfo.name)
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
    val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
    val generateTask =
      project.tasks.register("generateMosaicRegistry", GenerateMosaicRegistryTask::class.java) { task ->
        task.outputDir.set(project.layout.buildDirectory.dir("generated/mosaic"))
        task.classpath.setFrom(project.configurations.getByName("compileClasspath"))
      }

    sourceSets.getByName("main").java.srcDir(generateTask.map { it.outputDir })

    project.tasks.named("compileKotlin").configure { it.dependsOn(generateTask) }
  }
}
