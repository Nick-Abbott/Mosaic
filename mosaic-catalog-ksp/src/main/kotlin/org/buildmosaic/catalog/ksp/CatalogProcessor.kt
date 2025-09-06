package org.buildmosaic.catalog.ksp

import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference

class CatalogProcessorProvider : SymbolProcessorProvider {
  override fun create(environment: SymbolProcessorEnvironment) = CatalogProcessor(environment)
}

class CatalogProcessor(
  private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {
  private var generated = false
  private val codeGen = env.codeGenerator

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (generated) return emptyList()

    val tiles =
      resolver.getAllFiles().toList()
        .flatMap { file -> file.declarations.filterIsInstance<KSClassDeclaration>() }
        .filter { it.classKind == ClassKind.CLASS || it.classKind == ClassKind.OBJECT }
        .filter { it.isPublic() }
        .filter { it.extendsTile() }
        .map { it.qualifiedName!!.asString() }
        .distinct().sorted().toList()

    if (tiles.isEmpty()) {
      generated = true
      return emptyList()
    }

    codeGen.createNewFileByPath(Dependencies(true), "META-INF/mosaic-catalog", "list").use { out ->
      out.writer().use { w -> tiles.forEach { w.appendLine(it) } }
    }

    generated = true
    return emptyList()
  }
}

private fun KSClassDeclaration.extendsTile(): Boolean {
  fun KSTypeReference.extendsTileRef(): Boolean {
    val resolved = resolve()
    val qName = resolved.declaration.qualifiedName?.asString()
    return qName == "org.buildmosaic.core.Tile" ||
      (resolved.declaration as? KSClassDeclaration)?.extendsTile() == true
  }

  return superTypes.any { it.extendsTileRef() }
}
