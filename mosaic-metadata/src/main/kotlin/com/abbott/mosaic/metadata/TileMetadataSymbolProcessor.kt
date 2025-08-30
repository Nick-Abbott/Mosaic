package com.abbott.mosaic.metadata

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier

class TileMetadataSymbolProcessor(
  private val codeGenerator: CodeGenerator,
) : SymbolProcessor {
  private var processed = false

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (processed) return emptyList()

    val tiles =
      resolver.getAllFiles()
        .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
        .filter { declaration ->
          declaration.classKind == ClassKind.CLASS &&
            !declaration.modifiers.contains(Modifier.ABSTRACT) &&
            declaration.extendsTile()
        }
        .toList()

    if (tiles.isEmpty()) {
      processed = true
      return emptyList()
    }

    @Suppress("SpreadOperator")
    val dependencies =
      Dependencies(aggregating = true, *tiles.mapNotNull { it.containingFile }.toTypedArray())
    val out =
      codeGenerator.createNewFile(
        dependencies,
        "com.abbott.mosaic.generated",
        "tiles",
        "mosaic-metadata",
      )

    out.bufferedWriter().use { writer ->
      tiles.forEach { tile ->
        writer.appendLine(tile.qualifiedName!!.asString())
      }
    }

    processed = true
    return emptyList()
  }
}

class TileMetadataSymbolProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
    return TileMetadataSymbolProcessor(environment.codeGenerator)
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
