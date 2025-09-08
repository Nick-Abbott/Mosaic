package org.buildmosaic.build.ksp

import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ksp.writeTo

class MosaicSymbolProcessor(
  private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {
  private var generated = false
  private val codeGenerator = env.codeGenerator

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (generated) return emptyList()

    val tile = resolver.getClassDeclarationByName("org.buildmosaic.core.Tile") ?: return emptyList()

    // Find tiles defined in this module by scanning for classes that extend Tile
    val localTiles = resolver.getAllFiles().toList()
      .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
      .filter { it.classKind == ClassKind.CLASS || it.classKind == ClassKind.OBJECT }
      .filter { it.isPublic() }
      .filter { it.isSubclassOf(tile) }
      .distinct()
      .map { ClassName(it.packageName.asString(), it.simpleName.asString()) }

    // Always generate the function, even if no local tiles are found
    val registryClass = ClassName("org.buildmosaic.core", "MosaicRegistry")
    val registerFun = FunSpec.builder("registerGeneratedTiles").receiver(registryClass)

    // Register local tiles found in this module
    localTiles.forEach { className ->
      registerFun.addStatement(
        "register(%T::class) { mosaic -> %T(mosaic) }",
        className,
        className,
      )
    }

    // Call the library registry function to register tiles from dependencies
    val fqcn = "org.buildmosaic.core.generated.LibraryTileRegistry"
    val ksName = resolver.getKSNameFromString(fqcn)
    val decl = resolver.getClassDeclarationByName(ksName)

    if (decl != null) {
      registerFun.addStatement(
        "%T.registerLibraryTiles(this)",
        ClassName("org.buildmosaic.core.generated", "LibraryTileRegistry")
      )
    }

    val fileSpec = FileSpec.builder(
      "org.buildmosaic.core.generated",
      "GeneratedMosaicRegistry",
    ).addFunction(registerFun.build()).build()

    fileSpec.writeTo(codeGenerator, Dependencies(true))

    generated = true
    return emptyList()
  }
}

class MosaicSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return MosaicSymbolProcessor(environment)
  }
}

private fun KSClassDeclaration.isSubclassOf(other: KSClassDeclaration): Boolean {
  if (this == other) return false

  val base = other.asStarProjectedType().makeNotNullable()
  val chain = sequenceOf(this.asStarProjectedType()) + getAllSuperTypes()
  return chain
    .map { it.starProjection().makeNotNullable() }
    .any { base.isAssignableFrom(it) }
}
