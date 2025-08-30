package com.abbott.mosaic.build

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.Modifier
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File

class MosaicSymbolProcessorTest {
  @Test
  fun `tiles from metadata files are registered`() {
    val metadata = File.createTempFile("tiles", ".mosaic-metadata")
    metadata.writeText("com.example.DependencyTile\n")

    val pkgName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.generated" }
    val ksFile = mockk<KSFile> {
      every { fileName } returns "tiles.mosaic-metadata"
      every { packageName } returns pkgName
      every { filePath } returns metadata.path
      every { declarations } returns emptySequence()
    }

    val resolver = mockk<Resolver> {
      every { getAllFiles() } answers { sequenceOf(ksFile) }
    }

    val output = ByteArrayOutputStream()
    val codeGenerator = mockk<CodeGenerator> {
      every { createNewFile(any(), any(), any(), any()) } returns output
    }

    MosaicSymbolProcessor(codeGenerator).process(resolver)

    assertTrue(output.toString().contains("DependencyTile"))
  }

  @Test
  fun `tile classes are registered`() {
    val output = ByteArrayOutputStream()
    val codeGenerator = mockk<CodeGenerator> {
      every { createNewFile(any(), any(), any(), any()) } returns output
    }

    val ksFile = mockk<KSFile>()
    val pkgName = mockk<KSName> { every { asString() } returns "com.example" }
    val simple = mockk<KSName> { every { asString() } returns "MyTile" }
    val tileClass = mockk<KSClassDeclaration>()
    every { tileClass.classKind } returns ClassKind.CLASS
    every { tileClass.modifiers } returns emptySet<Modifier>()
    every { tileClass.containingFile } returns ksFile
    every { tileClass.packageName } returns pkgName
    every { tileClass.simpleName } returns simple

    val superRef = mockk<com.google.devtools.ksp.symbol.KSTypeReference>()
    val superType = mockk<com.google.devtools.ksp.symbol.KSType>()
    val superDecl = mockk<KSClassDeclaration>()
    val superName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.Tile" }
    every { superDecl.qualifiedName } returns superName
    every { superType.declaration } returns superDecl
    every { superRef.resolve() } returns superType
    every { tileClass.superTypes } returns sequenceOf(superRef)

    every { ksFile.declarations } returns sequenceOf(tileClass)
    every { ksFile.fileName } returns "Source.kt"
    every { ksFile.packageName } returns pkgName
    every { ksFile.filePath } returns "Source.kt"

    val resolver = mockk<Resolver> { every { getAllFiles() } answers { sequenceOf(ksFile) } }

    MosaicSymbolProcessor(codeGenerator).process(resolver)

    assertTrue(output.toString().contains("MyTile"))
  }

  @Test
  fun `processor runs only once`() {
    val output = ByteArrayOutputStream()
    val codeGenerator = mockk<CodeGenerator> {
      every { createNewFile(any(), any(), any(), any()) } returns output
    }

    val ksFile = mockk<KSFile>()
    val pkgName = mockk<KSName> { every { asString() } returns "com.example" }
    val simple = mockk<KSName> { every { asString() } returns "MyTile" }
    val tileClass = mockk<KSClassDeclaration>()
    every { tileClass.classKind } returns ClassKind.CLASS
    every { tileClass.modifiers } returns emptySet<Modifier>()
    every { tileClass.containingFile } returns ksFile
    every { tileClass.packageName } returns pkgName
    every { tileClass.simpleName } returns simple

    val superRef = mockk<com.google.devtools.ksp.symbol.KSTypeReference>()
    val superType = mockk<com.google.devtools.ksp.symbol.KSType>()
    val superDecl = mockk<KSClassDeclaration>()
    val superName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.Tile" }
    every { superDecl.qualifiedName } returns superName
    every { superType.declaration } returns superDecl
    every { superRef.resolve() } returns superType
    every { tileClass.superTypes } returns sequenceOf(superRef)

    every { ksFile.declarations } returns sequenceOf(tileClass)
    every { ksFile.fileName } returns "Source.kt"
    every { ksFile.packageName } returns pkgName
    every { ksFile.filePath } returns "Source.kt"

    val resolver = mockk<Resolver> { every { getAllFiles() } answers { sequenceOf(ksFile) } }

    val processor = MosaicSymbolProcessor(codeGenerator)
    processor.process(resolver)
    processor.process(resolver)

    io.mockk.verify(exactly = 1) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `non tile classes are ignored`() {
    val codeGenerator = mockk<CodeGenerator>(relaxed = true)
    val ksFile = mockk<KSFile>()
    val nonTileClass = mockk<KSClassDeclaration>()
    every { nonTileClass.classKind } returns ClassKind.CLASS
    every { nonTileClass.modifiers } returns emptySet<Modifier>()

    val otherRef = mockk<com.google.devtools.ksp.symbol.KSTypeReference>()
    val otherType = mockk<com.google.devtools.ksp.symbol.KSType>()
    val otherDecl = mockk<KSClassDeclaration>()
    val otherName = mockk<KSName> { every { asString() } returns "com.example.Other" }
    every { otherDecl.qualifiedName } returns otherName
    every { otherType.declaration } returns otherDecl
    every { otherRef.resolve() } returns otherType
    every { otherDecl.superTypes } returns emptySequence()
    every { nonTileClass.superTypes } returns sequenceOf(otherRef)

    every { ksFile.declarations } returns sequenceOf(nonTileClass)
    every { ksFile.fileName } returns "Source.kt"
    every { ksFile.packageName } returns mockk { every { asString() } returns "com.example" }
    every { ksFile.filePath } returns "Source.kt"

    val resolver = mockk<Resolver> { every { getAllFiles() } answers { sequenceOf(ksFile) } }

    MosaicSymbolProcessor(codeGenerator).process(resolver)

    io.mockk.verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `abstract classes are ignored`() {
    val codeGenerator = mockk<CodeGenerator>(relaxed = true)
    val ksFile = mockk<KSFile>()
    val abstractClass = mockk<KSClassDeclaration>()
    every { abstractClass.classKind } returns ClassKind.CLASS
    every { abstractClass.modifiers } returns setOf(Modifier.ABSTRACT)
    every { abstractClass.superTypes } returns emptySequence()
    every { ksFile.declarations } returns sequenceOf(abstractClass)
    every { ksFile.fileName } returns "Source.kt"
    every { ksFile.packageName } returns mockk { every { asString() } returns "com.example" }
    every { ksFile.filePath } returns "Source.kt"
    val resolver = mockk<Resolver> { every { getAllFiles() } answers { sequenceOf(ksFile) } }
    MosaicSymbolProcessor(codeGenerator).process(resolver)
    io.mockk.verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `non class declarations are ignored`() {
    val codeGenerator = mockk<CodeGenerator>(relaxed = true)
    val ksFile = mockk<KSFile>()
    val interfaceDecl = mockk<KSClassDeclaration>()
    every { interfaceDecl.classKind } returns ClassKind.INTERFACE
    every { interfaceDecl.modifiers } returns emptySet<Modifier>()
    every { interfaceDecl.superTypes } returns emptySequence()
    every { ksFile.declarations } returns sequenceOf(interfaceDecl)
    every { ksFile.fileName } returns "Source.kt"
    every { ksFile.packageName } returns mockk { every { asString() } returns "com.example" }
    every { ksFile.filePath } returns "Source.kt"
    val resolver = mockk<Resolver> { every { getAllFiles() } answers { sequenceOf(ksFile) } }
    MosaicSymbolProcessor(codeGenerator).process(resolver)
    io.mockk.verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }
}
