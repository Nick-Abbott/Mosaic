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
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar

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
      val mainSourceSet = sourceSets.getByName("main")
      val codegenOutput = project.layout.buildDirectory.dir("generated/mosaic")
      val generateTask = project.tasks.register(
        "generateMosaicRegistry",
        GenerateMosaicRegistryTask::class.java
      ) { task ->
        task.outputDir.set(codegenOutput)
        task.classpath.from(mainSourceSet.runtimeClasspath)
        task.dependsOn(project.tasks.named("classes"))
      }

      // Add generated sources to the jar task instead of the main source set
      project.tasks.named("jar", Jar::class.java) { jarTask ->
        jarTask.from(codegenOutput) {
          it.into("META-INF/generated-sources")
        }
        jarTask.dependsOn(generateTask)
      }
    }
  }
}
