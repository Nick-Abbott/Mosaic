/*
 * Copyright 2025 Nicholas Abbott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.abbott.mosaic.metadata

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

@Suppress("LargeClass")
class TileMetadataSymbolProcessorTest {
  @Test
  fun `process writes tile classes to metadata file`() {
    val output = ByteArrayOutputStream()
    val codeGenerator =
      mockk<CodeGenerator> {
        every { createNewFile(any(), any(), any(), any()) } returns output
      }

    val ksFile = mockk<KSFile>()
    val ksName = mockk<KSName> { every { asString() } returns "com.example.MyTile" }
    val tileClass = mockk<KSClassDeclaration>()
    every { tileClass.classKind } returns ClassKind.CLASS
    every { tileClass.modifiers } returns emptySet<Modifier>()
    every { tileClass.containingFile } returns ksFile
    every { tileClass.qualifiedName } returns ksName

    val superRef = mockk<KSTypeReference>()
    val superType = mockk<KSType>()
    val superDecl = mockk<KSClassDeclaration>()
    val superName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.Tile" }
    every { superDecl.qualifiedName } returns superName
    every { superType.declaration } returns superDecl
    every { superRef.resolve() } returns superType
    every { tileClass.superTypes } returns sequenceOf(superRef)

    every { ksFile.declarations } returns sequenceOf(tileClass)
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    assertEquals("com.example.MyTile\n", output.toString())
  }

  @Test
  fun `processor runs only once`() {
    val output = ByteArrayOutputStream()
    val codeGenerator =
      mockk<CodeGenerator> {
        every { createNewFile(any(), any(), any(), any()) } returns output
      }

    val ksFile = mockk<KSFile>()
    val ksName = mockk<KSName> { every { asString() } returns "com.example.MyTile" }
    val tileClass = mockk<KSClassDeclaration>()
    every { tileClass.classKind } returns ClassKind.CLASS
    every { tileClass.modifiers } returns emptySet<Modifier>()
    every { tileClass.containingFile } returns ksFile
    every { tileClass.qualifiedName } returns ksName

    val superRef = mockk<KSTypeReference>()
    val superType = mockk<KSType>()
    val superDecl = mockk<KSClassDeclaration>()
    val superName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.Tile" }
    every { superDecl.qualifiedName } returns superName
    every { superType.declaration } returns superDecl
    every { superRef.resolve() } returns superType
    every { tileClass.superTypes } returns sequenceOf(superRef)

    every { ksFile.declarations } returns sequenceOf(tileClass)
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)
    processor.process(resolver)

    verify(exactly = 1) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `process without tiles does not create file`() {
    val codeGenerator = mockk<CodeGenerator>(relaxed = true)
    val resolver = mockk<Resolver> { every { getAllFiles() } returns emptySequence() }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `non tile classes are ignored`() {
    val codeGenerator = mockk<CodeGenerator>(relaxed = true)
    val ksFile = mockk<KSFile>()
    val nonTileClass = mockk<KSClassDeclaration>()
    every { nonTileClass.classKind } returns ClassKind.CLASS
    every { nonTileClass.modifiers } returns emptySet<Modifier>()

    val otherRef = mockk<KSTypeReference>()
    val otherType = mockk<KSType>()
    val otherDecl = mockk<KSClassDeclaration>()
    val otherName = mockk<KSName> { every { asString() } returns "com.example.Other" }
    every { otherDecl.qualifiedName } returns otherName
    every { otherType.declaration } returns otherDecl
    every { otherRef.resolve() } returns otherType
    every { otherDecl.superTypes } returns emptySequence()
    every { nonTileClass.superTypes } returns sequenceOf(otherRef)

    every { ksFile.declarations } returns sequenceOf(nonTileClass)
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
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
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
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
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    verify(exactly = 0) { codeGenerator.createNewFile(any(), any(), any(), any()) }
  }

  @Test
  fun `tile detected through indirect inheritance`() {
    val output = ByteArrayOutputStream()
    val codeGenerator =
      mockk<CodeGenerator> {
        every { createNewFile(any(), any(), any(), any()) } returns output
      }

    val ksFile = mockk<KSFile>()
    val ksName = mockk<KSName> { every { asString() } returns "com.example.IndirectTile" }
    val tileClass = mockk<KSClassDeclaration>()
    every { tileClass.classKind } returns ClassKind.CLASS
    every { tileClass.modifiers } returns emptySet<Modifier>()
    every { tileClass.containingFile } returns ksFile
    every { tileClass.qualifiedName } returns ksName

    val intermediateRef = mockk<KSTypeReference>()
    val intermediateType = mockk<KSType>()
    val intermediateDecl = mockk<KSClassDeclaration>()
    val intermediateName = mockk<KSName> { every { asString() } returns "com.example.Intermediate" }
    every { intermediateDecl.qualifiedName } returns intermediateName
    every { intermediateType.declaration } returns intermediateDecl
    every { intermediateRef.resolve() } returns intermediateType
    every { tileClass.superTypes } returns sequenceOf(intermediateRef)

    val tileRef = mockk<KSTypeReference>()
    val tileType = mockk<KSType>()
    val tileDecl = mockk<KSClassDeclaration>()
    val tileName = mockk<KSName> { every { asString() } returns "com.abbott.mosaic.Tile" }
    every { tileDecl.qualifiedName } returns tileName
    every { tileType.declaration } returns tileDecl
    every { tileRef.resolve() } returns tileType
    every { intermediateDecl.superTypes } returns sequenceOf(tileRef)

    every { ksFile.declarations } returns sequenceOf(tileClass)
    val resolver = mockk<Resolver> { every { getAllFiles() } returns sequenceOf(ksFile) }

    val processor = TileMetadataSymbolProcessor(codeGenerator)
    processor.process(resolver)

    assertEquals("com.example.IndirectTile\n", output.toString())
  }
}
