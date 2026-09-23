package org.buildmosaic.gradle

import org.gradle.api.GradleException
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.DataOutputStream
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

/** Removes compiler-generated anonymous classes from Gradle's ABI snapshot, not from the K2 classpath. */
@CacheableTransform
abstract class SourceResolutionTransform : TransformAction<TransformParameters.None> {
  @get:InputArtifact
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val inputArtifact: Provider<FileSystemLocation>

  override fun transform(outputs: TransformOutputs) {
    val artifact = inputArtifact.get().asFile
    if (!artifact.isFile || artifact.extension != "jar") {
      throw GradleException("Mosaic extraction requires dependency JARs, not ${artifact.absolutePath}")
    }
    JarFile(artifact).use { jar ->
      val entries = jar.entries().asSequence().filterNot { it.isDirectory }.sortedBy { it.name }.toList()
      writeAbi(jar, entries, outputs.file("${artifact.name}.resolution.jar"))
      writeModules(jar, entries, artifact.name, outputs.file("${artifact.name}.kotlin-modules.bin"))
    }
  }

  private fun writeAbi(
    jar: JarFile,
    entries: List<JarEntry>,
    file: File,
  ) {
    JarOutputStream(file.outputStream()).use { output ->
      entries.filter { it.name.endsWith(".class") && !isAnonymousClass(it.name) }.forEach { entry ->
        output.putNextEntry(JarEntry(entry.name).apply { time = 0L })
        output.write(projectClass(jar.getInputStream(entry).use { it.readBytes() }))
        output.closeEntry()
      }
    }
  }

  private fun projectClass(bytes: ByteArray): ByteArray {
    val writer = ClassWriter(0)
    ClassReader(bytes).accept(
      object : ClassVisitor(Opcodes.ASM9, writer) {
        override fun visitAnnotation(
          descriptor: String,
          visible: Boolean,
        ): AnnotationVisitor? =
          if (descriptor == "Lkotlin/jvm/internal/SourceDebugExtension;") {
            null
          } else {
            super.visitAnnotation(descriptor, visible)
          }

        override fun visitSource(
          source: String?,
          debug: String?,
        ) {
          super.visitSource(source, null)
        }

        override fun visitInnerClass(
          name: String,
          outerName: String?,
          innerName: String?,
          access: Int,
        ) {
          if (!isAnonymousClass("$name.class")) super.visitInnerClass(name, outerName, innerName, access)
        }
      },
      0,
    )
    return writer.toByteArray()
  }

  // Gradle ignores resources in compile classpaths, but Kotlin resolves package parts from these files.
  private fun writeModules(
    jar: JarFile,
    entries: List<JarEntry>,
    artifactName: String,
    file: File,
  ) {
    DataOutputStream(file.outputStream().buffered()).use { output ->
      output.writeUTF(artifactName)
      entries.filter { it.name.startsWith("META-INF/") && it.name.endsWith(".kotlin_module") }.forEach { entry ->
        val bytes = jar.getInputStream(entry).use { it.readBytes() }
        output.writeUTF(entry.name)
        output.writeInt(bytes.size)
        output.write(bytes)
      }
    }
  }

  private fun isAnonymousClass(path: String): Boolean =
    path.removeSuffix(".class").substringAfterLast('$', "").all(Char::isDigit) &&
      path.removeSuffix(".class").contains('$')
}
