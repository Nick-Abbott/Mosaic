package com.abbott.mosaic.build

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.KSTypeReference
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo

class MosaicSymbolProcessor(
  private val codeGenerator: CodeGenerator
) : SymbolProcessor {
  private var generated = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (generated) return emptyList()

    val tiles = resolver.getAllFiles()
      .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
      .filter { declaration ->
        declaration.classKind == ClassKind.CLASS &&
          !declaration.modifiers.contains(Modifier.ABSTRACT) &&
          declaration.extendsTile()
      }
      .toList()

    if (tiles.isEmpty()) {
      generated = true
      return emptyList()
    }

    val registryClass = ClassName("com.abbott.mosaic", "MosaicRegistry")
    val registerFun = FunSpec.builder("registerGeneratedTiles").receiver(registryClass)

    tiles.map { ClassName(it.packageName.asString(), it.simpleName.asString()) }
      .forEach { className ->
        registerFun.addStatement(
          "register(%T::class) { mosaic -> %T(mosaic) }",
          className,
          className,
        )
      }

    val fileSpec = FileSpec.builder(
      "com.abbott.mosaic.generated",
      "GeneratedMosaicRegistry",
    ).addFunction(registerFun.build()).build()

    @Suppress("SpreadOperator")
    val dependencies = Dependencies(false, *tiles.mapNotNull { it.containingFile }.toTypedArray())
    fileSpec.writeTo(codeGenerator, dependencies)

    generated = true
    return emptyList()
  }
}

class MosaicSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return MosaicSymbolProcessor(environment.codeGenerator)
  }
}

private fun KSClassDeclaration.extendsTile(): Boolean {
  fun KSTypeReference.extendsTileRef(): Boolean {
    val resolved = resolve()
    val qName = resolved.declaration.qualifiedName?.asString()
    return qName == "com.abbott.mosaic.Tile" ||
      (resolved.declaration as? KSClassDeclaration)?.extendsTile() == true
  }

  return superTypes.any { it.extendsTileRef() }
}
