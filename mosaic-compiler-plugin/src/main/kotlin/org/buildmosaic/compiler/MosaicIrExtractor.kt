@file:Suppress(
  "CyclomaticComplexMethod",
  "LongMethod",
  "TooManyFunctions",
  "NestedBlockDepth",
  "LongParameterList",
  "LargeClass",
  "MaxLineLength",
  "ktlint:standard:max-line-length",
  "DEPRECATION_ERROR",
)

package org.buildmosaic.compiler

import org.buildmosaic.analysis.ArgumentExpression
import org.buildmosaic.analysis.Binding
import org.buildmosaic.analysis.CallArguments
import org.buildmosaic.analysis.CallableContract
import org.buildmosaic.analysis.CanvasContract
import org.buildmosaic.analysis.CanvasExpression
import org.buildmosaic.analysis.CanvasKeyIdentity
import org.buildmosaic.analysis.ContractParameter
import org.buildmosaic.analysis.DiscoveryKind
import org.buildmosaic.analysis.Effect
import org.buildmosaic.analysis.Fact
import org.buildmosaic.analysis.LookupKind
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.ParameterKind
import org.buildmosaic.analysis.ResolvedOverride
import org.buildmosaic.analysis.SourceLocation
import org.buildmosaic.analysis.SummaryCodec
import org.buildmosaic.analysis.TileContract
import org.buildmosaic.analysis.TileReference
import org.buildmosaic.analysis.UnknownRegistration
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrBlock
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import java.io.File

/** Read-only K2 IR collector for the deliberately small prototype DSL subset. */
@OptIn(UnsafeDuringIrConstructionAPI::class)
class MosaicIrExtractor(
  private val output: String,
  private val moduleId: String,
) : IrGenerationExtension {
  private val limitations = mutableListOf<String>()

  override fun generate(
    moduleFragment: org.jetbrains.kotlin.ir.declarations.IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    val canvases = mutableListOf<CanvasContract>()
    val tiles = mutableListOf<TileContract>()
    val callables = mutableListOf<CallableContract>()
    val overrides = mutableListOf<ResolvedOverride>()
    val locators = linkedMapOf<String, String>()
    moduleFragment.files.sortedBy { it.fileEntry.name }.forEach { file ->
      fun visit(declaration: IrDeclaration) {
        when (declaration) {
          is IrClass -> {
            val receiverType = declaration.fqNameWhenAvailable?.asString()
            if (declaration.modality == Modality.FINAL && receiverType != null) {
              declaration.declarations.filterIsInstance<IrSimpleFunction>().filterNot { it.isFakeOverride }.forEach {
                  function ->
                function.overriddenSymbols.map { it.owner }.filter { base ->
                  base.modality == Modality.ABSTRACT &&
                    base.isSuspend && function.isSuspend &&
                    base.parameters.any { it.kind == IrParameterKind.Regular && isCanvasType(it.type) }
                }.forEach {
                    base ->
                  overrides += ResolvedOverride(receiverType, symbolId(base), symbolId(function))
                }
              }
            }
            declaration.declarations.forEach(::visit)
          }
          is IrProperty -> {
            val initializer = declaration.backingField?.initializer?.expression
            if (!declaration.isVar && initializer is IrCall && isTileFactory(initializer)) {
              val site = location(file, declaration, propertyId(declaration))
              val lambda =
                initializer.argument(if (initializer.symbol.owner.name.asString() == "chunkedMultiTile") "fetch" else "block")
                  ?: initializer.argument("fetch")
              val effects =
                lambdaBody(lambda)?.let { extractEffects(it, file, propertyId(declaration), mutableMapOf()) }
                  ?: listOf(Effect.Unknown("${propertyId(declaration)}:body", "Tile block is unavailable", site))
              tiles += TileContract(propertyId(declaration), effects, site)
              declaration.getter?.let { locators[propertyId(declaration)] = binaryLocator(it, file) }
            }
          }
          is IrSimpleFunction -> {
            if (declaration.isFakeOverride || declaration.correspondingPropertySymbol != null) return
            val id = symbolId(declaration)
            locators[id] = binaryLocator(declaration, file)
            val site = location(file, declaration, id)
            val aliases = mutableMapOf<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>()
            val params = canvasParameters(declaration, id)
            val returned = returnedExpression(declaration)
            if (isCanvasType(declaration.returnType)) {
              val precedingCapability =
                (declaration.body as? IrBlockBody)?.statements?.dropLast(1)?.any(::containsCapability) == true
              val result =
                if (precedingCapability) {
                  CanvasExpression.Unknown("Unsupported capability effect before Canvas return", site)
                } else {
                  returned?.let { canvasExpression(it, file, id, aliases) }
                    ?: CanvasExpression.Unknown("Canvas body is unavailable", site)
                }
              canvases += CanvasContract(id, params, result, site)
            } else if (declaration.body != null || params.isNotEmpty() || declaration.modality == Modality.ABSTRACT) {
              val effects =
                declaration.body?.let { extractEffects(it, file, id, aliases) }
                  ?: listOf(Effect.Unknown("$id:body", "Callable body is unavailable", site))
              callables += CallableContract(id, params, effects, site)
            }
          }
        }
      }
      file.declarations.forEach(::visit)
    }
    val module = ModuleContract(moduleId, canvases, tiles, callables, overrides)
    File(output).apply {
      parentFile.mkdirs()
      writeBytes(SummaryCodec.encode(module, limitations = limitations, binaryLocators = locators))
    }
  }

  private fun extractEffects(
    body: IrElement,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): List<Effect> {
    val effects = mutableListOf<Effect>()

    fun scan(element: IrElement) {
      when (element) {
        is IrBlockBody -> element.statements.forEach(::scan)
        is IrBlock -> element.statements.forEach(::scan)
        is IrVariable -> {
          if (!element.isVar) element.initializer?.let { aliases[element.symbol] = it }
          // Construction is eager even when this value is never composed later.
          if (isCanvasType(element.type)) {
            element.initializer?.let { expression ->
              effects +=
                Effect.ConstructCanvas(
                  "$owner:construct:${element.startOffset}",
                  CanvasExpression.Alias("$owner:val:${element.startOffset}", canvasExpression(expression, file, owner, aliases)),
                  location(file, element, owner),
                )
            }
          } else {
            element.initializer?.let(::scan)
          }
        }
        is IrReturn -> scan(element.value)
        is IrTypeOperatorCall -> scan(element.argument)
        is IrWhen, is IrWhileLoop -> {
          if (containsCapability(
              element,
            )
          ) {
            effects += Effect.Unknown("$owner:control:${element.startOffset}", "Unsupported control flow with Mosaic capabilities", location(file, element, owner))
          }
        }
        is IrCall -> {
          val target = resolvedName(element)
          val site = location(file, element, owner)
          when {
            isSource(target) -> {
              val kind = if (target.endsWith("sourceOr")) LookupKind.OPTIONAL else LookupKind.REQUIRED
              effects += Effect.Lookup("$owner:lookup:${element.startOffset}", lookupCanvas(element, file, owner, aliases), key(element, file, owner), kind, site)
            }
            target == "org.buildmosaic.core.injection.CanvasFactory.paint" ->
              effects += Effect.Lookup("$owner:paint:${element.startOffset}", CanvasExpression.Current, key(element, file, owner), LookupKind.PAINT, site)
            target == "org.buildmosaic.core.Mosaic.compose" || target == "org.buildmosaic.core.Mosaic.composeAsync" -> {
              val tile =
                element.argument("tile")?.let { tileReference(it, file, owner, aliases) }
                  ?: TileReference.Unknown("Missing Tile argument", site)
              effects +=
                Effect.Compose(
                  "$owner:compose:${element.startOffset}",
                  mosaicCanvas(element.receiver(), file, owner, aliases),
                  tile,
                  if (target.endsWith("composeAsync")) DiscoveryKind.COMPOSE_ASYNC else DiscoveryKind.COMPOSE,
                  site = site,
                )
            }
            target == "org.buildmosaic.core.injection.canvas" || target == "org.buildmosaic.core.injection.Canvas.withLayer" ->
              effects +=
                Effect.ConstructCanvas(
                  "$owner:construct:${element.startOffset}",
                  canvasExpression(element, file, owner, aliases),
                  site,
                )
            target == "org.buildmosaic.core.injection.create" ->
              effects +=
                Effect.ConstructCanvas(
                  "$owner:create:${element.startOffset}",
                  element.receiver()?.let { canvasExpression(it, file, owner, aliases) }
                    ?: CanvasExpression.Unknown("Missing Mosaic.create Canvas", site),
                  site,
                )
            isTileFactory(element) -> Unit
            element.symbol.owner.correspondingPropertySymbol != null && isCapabilityType(element.type) -> Unit
            isCapabilityCall(element) || isUserCallable(target) -> {
              if (element.symbol.owner.isInline) {
                effects += Effect.Unknown("$owner:inline:${element.startOffset}", "External inline capability helper ${symbolId(element.symbol.owner)} has no pre-inline body", site)
              } else {
                effects += Effect.Call("$owner:call:${element.startOffset}", symbolId(element.symbol.owner), callArguments(element, file, owner, aliases), site, knownReceiver(element))
              }
            }
            else -> {
              element.receiver()?.let(::scan)
              element.regularArguments().filterNot { it is IrFunctionExpression }.forEach(::scan)
              if (element.regularArguments().any { it is IrFunctionExpression && containsCapability(it) }) {
                effects += Effect.Unknown("$owner:callback:${element.startOffset}", "Unsupported capability-bearing callback", site)
              }
            }
          }
        }
        is IrConstructorCall -> element.arguments.filterNotNull().forEach(::scan)
        else -> {
          if (containsCapability(element)) {
            effects +=
              Effect.Unknown(
                "$owner:unsupported:${element.startOffset}",
                "Unsupported capability-bearing IR ${element::class.simpleName}",
                location(file, element, owner),
              )
          }
        }
      }
    }
    scan(body)
    return effects
  }

  private fun canvasExpression(
    expression: IrExpression,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CanvasExpression =
    when (expression) {
      is IrGetValue -> {
        aliases[expression.symbol]?.let {
          CanvasExpression.Alias("$owner:val:${expression.symbol.owner.startOffset}", canvasExpression(it, file, owner, aliases))
        }
          ?: (expression.symbol.owner as? IrValueParameter)?.let {
            CanvasExpression.ParameterValue(ContractParameter(owner, it.name.asString(), ParameterKind.CANVAS))
          }
          ?: CanvasExpression.Unknown("Mutable or unresolved Canvas value", location(file, expression, owner))
      }
      is IrTypeOperatorCall -> canvasExpression(expression.argument, file, owner, aliases)
      is IrCall -> {
        val target = resolvedName(expression)
        val site = location(file, expression, owner)
        when (target) {
          "org.buildmosaic.core.injection.canvas" -> {
            val parent = expression.argument("parent")?.let { canvasExpression(it, file, owner, aliases) } ?: CanvasExpression.Empty
            layer(expression, expression.argument("build"), parent, file, owner, aliases)
          }
          "org.buildmosaic.core.injection.Canvas.withLayer" -> {
            val parent =
              expression.receiver()?.let { canvasExpression(it, file, owner, aliases) }
                ?: CanvasExpression.Unknown("Missing layer parent", site)
            layer(expression, expression.argument("build"), parent, file, owner, aliases)
          }
          else ->
            if (expression.symbol.owner.isInline) {
              CanvasExpression.Unknown(
                "External inline Canvas helper ${symbolId(expression.symbol.owner)} has no pre-inline body",
                site,
              )
            } else if (expression.symbol.owner.parameters.any {
                it.kind == IrParameterKind.Regular && !isCanvasType(it.type)
              }
            ) {
              CanvasExpression.Unknown("Non-Canvas Canvas-helper arguments are unsupported", site)
            } else {
              CanvasExpression.RuntimeCall(
                symbolId(expression.symbol.owner),
                callArguments(expression, file, owner, aliases),
                site,
              )
            }
        }
      }
      else ->
        CanvasExpression.Unknown(
          "Unsupported Canvas expression ${expression::class.simpleName}",
          location(file, expression, owner),
        )
    }

  private fun layer(
    call: IrCall,
    build: IrExpression?,
    parent: CanvasExpression,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CanvasExpression {
    val site = location(file, call, owner)
    val body = lambdaBody(build)
    if (body == null) {
      return CanvasExpression.Layer(
        "$owner:${call.startOffset}",
        parent,
        unknownRegistrations = listOf(UnknownRegistration("Canvas builder body unavailable", site)),
        site = site,
      )
    }
    val bindings = mutableListOf<Binding>()
    val unknown = mutableListOf<UnknownRegistration>()
    if (body !is IrBlockBody) {
      return CanvasExpression.Layer(
        "$owner:${call.startOffset}",
        parent,
        unknownRegistrations = listOf(UnknownRegistration("Unsupported Canvas builder body", site)),
        site = site,
      )
    }
    val statements = body.statements
    statements.forEach { statement ->
      if (statement is IrCall && resolvedName(statement) == "org.buildmosaic.core.injection.CanvasBuilder.single") {
        val bindingSite = location(file, statement, owner)
        val ctorBody = lambdaBody(statement.argument("ctor"))
        val effects =
          ctorBody?.let { extractEffects(it, file, owner, aliases.toMutableMap()) }
            ?: listOf(Effect.Unknown("$owner:constructor:${statement.startOffset}", "Binding constructor body unavailable", bindingSite))
        bindings += Binding(key(statement, file, owner), effects, bindingSite)
      } else {
        unknown += UnknownRegistration("Unsupported Canvas registration", location(file, statement, owner))
      }
    }
    return CanvasExpression.Layer("$owner:${call.startOffset}", parent, bindings, unknown, site)
  }

  private fun key(
    call: IrCall,
    file: IrFile,
    owner: String,
  ): Fact<CanvasKeyIdentity> {
    val site = location(file, call, owner)
    if (call.symbol.owner.parameters.any {
        it.kind == IrParameterKind.Regular && it.name.asString() in setOf("key", "type")
      }
    ) {
      return Fact.Unknown("Explicit CanvasKey or KClass argument is outside the supported key subset", site)
    }
    val type =
      call.typeArguments.firstOrNull()?.classFqName?.asString()
        ?: return Fact.Unknown("Reified Canvas key type unavailable", site)
    val qualifier = call.argument("qualifier")
    val literal =
      when (qualifier) {
        null -> null
        is IrConst -> qualifier.value as? String
        else -> return Fact.Unknown("Dynamic qualifier is unsupported", site)
      }
    if (literal != null) limitations += "Qualifier literal at ${site.path}:${site.line} in $owner has no reliable external const origin in pre-inline IR"
    return Fact.Known(CanvasKeyIdentity(type, literal), site)
  }

  private fun tileReference(
    expression: IrExpression,
    file: IrFile,
    owner: String,
    aliases: Map<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): TileReference =
    when (expression) {
      is IrGetValue ->
        aliases[expression.symbol]?.let { tileReference(it, file, owner, aliases) }
          ?: TileReference.Unknown("Tile value is not a stable alias", location(file, expression, owner))
      is IrCall ->
        expression.symbol.owner.correspondingPropertySymbol?.owner?.let { TileReference.Stable(propertyId(it)) }
          ?: TileReference.Unknown("Tile getter is not a stable property", location(file, expression, owner))
      is IrGetField ->
        expression.symbol.owner.correspondingPropertySymbol?.owner?.let { TileReference.Stable(propertyId(it)) }
          ?: TileReference.Unknown("Tile field is not a stable property", location(file, expression, owner))
      else -> TileReference.Unknown("Unsupported Tile expression", location(file, expression, owner))
    }

  private fun mosaicCanvas(
    expression: IrExpression?,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CanvasExpression =
    when (expression) {
      is IrCall ->
        if (resolvedName(expression) == "org.buildmosaic.core.injection.create") {
          expression.receiver()?.let { canvasExpression(it, file, owner, aliases) }
            ?: CanvasExpression.Unknown("Missing Mosaic.create Canvas", location(file, expression, owner))
        } else {
          CanvasExpression.Unknown("Unsupported Mosaic receiver", location(file, expression, owner))
        }
      is IrGetValue ->
        if (expression.symbol.owner.name.asString().startsWith("\$this\$")) {
          CanvasExpression.Current
        } else {
          aliases[expression.symbol]?.let { mosaicCanvas(it, file, owner, aliases) }
            ?: CanvasExpression.Unknown("Unresolved Mosaic alias", location(file, expression, owner))
        }
      null -> CanvasExpression.Current
      else -> CanvasExpression.Unknown("Unsupported Mosaic provenance", location(file, expression, owner))
    }

  private fun lookupCanvas(
    call: IrCall,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CanvasExpression =
    if (resolvedName(call).startsWith("org.buildmosaic.core.injection.Canvas.")) {
      call.receiver()?.let { canvasExpression(it, file, owner, aliases) } ?: CanvasExpression.Current
    } else {
      CanvasExpression.Current
    }

  private fun callArguments(
    call: IrCall,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CallArguments {
    val targetId = symbolId(call.symbol.owner)
    val values = linkedMapOf<ContractParameter, ArgumentExpression>()
    call.symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }.forEach { parameter ->
      if (isCanvasType(parameter.type)) {
        call.arguments[parameter]?.let { actual ->
          values[ContractParameter(targetId, parameter.name.asString(), ParameterKind.CANVAS)] =
            ArgumentExpression.Canvas(canvasExpression(actual, file, owner, aliases))
        }
      }
    }
    return CallArguments(values)
  }

  private fun knownReceiver(call: IrCall): String? {
    if (call.symbol.owner.modality != Modality.FINAL) return null
    val receiver = call.receiver() ?: return null
    if (receiver is IrConstructorCall) {
      val type = receiver.symbol.owner.parent as? IrClass
      if (type?.modality == Modality.FINAL) return type.fqNameWhenAvailable?.asString()
    }
    return null
  }

  private fun returnedExpression(function: IrFunction): IrExpression? =
    when (val body = function.body) {
      is IrBlockBody -> body.statements.filterIsInstance<IrReturn>().lastOrNull()?.value
      is org.jetbrains.kotlin.ir.expressions.IrExpressionBody -> body.expression
      else -> null
    }

  private fun lambdaBody(expression: IrExpression?): IrElement? =
    when (expression) {
      is IrFunctionExpression -> expression.function.body
      is IrBlock -> expression.statements.filterIsInstance<IrFunctionExpression>().lastOrNull()?.function?.body
      else -> null
    }

  private fun canvasParameters(
    function: IrFunction,
    id: String,
  ): List<ContractParameter> =
    function.parameters.filter {
      it.kind == IrParameterKind.Regular && isCanvasType(it.type)
    }.map { ContractParameter(id, it.name.asString(), ParameterKind.CANVAS) }

  private fun isCanvasType(type: IrType): Boolean =
    type.classFqName?.asString() in setOf("org.buildmosaic.core.injection.Canvas", "org.buildmosaic.core.injection.MosaicCanvas")

  private fun isCapabilityType(type: IrType): Boolean =
    type.classFqName?.asString() in
      setOf(
        "org.buildmosaic.core.injection.Canvas", "org.buildmosaic.core.injection.MosaicCanvas",
        "org.buildmosaic.core.injection.CanvasBuilder", "org.buildmosaic.core.injection.CanvasFactory",
        "org.buildmosaic.core.Mosaic", "org.buildmosaic.core.Tile", "org.buildmosaic.core.MultiTile",
      )

  private fun isCapabilityCall(call: IrCall): Boolean =
    isCapabilityType(call.symbol.owner.returnType) ||
      call.symbol.owner.parameters.any { isCapabilityType(it.type) }

  private fun isUserCallable(target: String): Boolean =
    target.isNotBlank() &&
      !target.substringAfterLast('.').startsWith("<get-") &&
      !target.startsWith("kotlin.") &&
      !target.startsWith("kotlinx.") &&
      !target.startsWith("java.") &&
      !target.startsWith("org.buildmosaic.core.")

  private fun containsCapability(element: IrElement): Boolean {
    var found = false
    element.acceptChildrenVoid(
      object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
          element.acceptChildrenVoid(this)
        }

        override fun visitCall(expression: IrCall) {
          if (isCapabilityCall(expression) || isUserCallable(resolvedName(expression))) found = true
          super.visitCall(expression)
        }

        override fun visitGetField(expression: IrGetField) {
          if (isCapabilityType(expression.type)) found = true
          super.visitGetField(expression)
        }
      },
    )
    return found
  }

  private fun isSource(target: String): Boolean =
    target in
      setOf(
        "org.buildmosaic.core.source", "org.buildmosaic.core.sourceOr",
        "org.buildmosaic.core.injection.Canvas.source", "org.buildmosaic.core.injection.Canvas.sourceOr",
      )

  private fun isTileFactory(call: IrCall): Boolean =
    resolvedName(call) in
      setOf(
        "org.buildmosaic.core.singleTile", "org.buildmosaic.core.multiTile",
        "org.buildmosaic.core.perKeyTile", "org.buildmosaic.core.chunkedMultiTile",
      )

  private fun resolvedName(call: IrCall): String = call.symbol.owner.fqNameWhenAvailable?.asString().orEmpty()

  private fun propertyId(property: IrProperty): String =
    property.fqNameWhenAvailable?.asString() ?: property.name.asString()

  private fun symbolId(function: IrFunction): String {
    val declaration = if (function is IrSimpleFunction && function.isFakeOverride) function.overriddenSymbols.firstOrNull()?.owner ?: function else function
    val fq = declaration.fqNameWhenAvailable?.asString() ?: declaration.name.asString()
    val params =
      declaration.parameters.filter {
        it.kind == IrParameterKind.Regular
      }.joinToString(",") { it.type.classFqName?.asString() ?: it.type.toString() }
    return "$fq($params)"
  }

  private fun binaryLocator(
    function: IrFunction,
    file: IrFile,
  ): String {
    val classOwner = (function.parent as? IrClass)?.fqNameWhenAvailable?.asString()?.replace('.', '/')
    val packagePath = file.packageFqName.asString().replace('.', '/')
    val facade = File(file.fileEntry.name).nameWithoutExtension + "Kt"
    val jvmOwner = classOwner ?: listOf(packagePath, facade).filter { it.isNotBlank() }.joinToString("/")
    val parameters =
      function.parameters.filter { it.kind == IrParameterKind.Regular }.joinToString(
        "",
      ) { jvmType(it.type) }
    val suspendCall = (function as? IrSimpleFunction)?.isSuspend == true
    val continuation = if (suspendCall) "Lkotlin/coroutines/Continuation;" else ""
    val returnType = if (suspendCall) "Ljava/lang/Object;" else jvmType(function.returnType)
    val kotlinName = function.name.asString()
    val jvmName =
      if (kotlinName.startsWith("<get-") && kotlinName.endsWith('>')) {
        "get" + kotlinName.removePrefix("<get-").removeSuffix(">").replaceFirstChar { it.uppercase() }
      } else {
        kotlinName
      }
    return "$jvmOwner#$jvmName($parameters$continuation)$returnType"
  }

  private fun jvmType(type: IrType): String =
    when (val name = type.classFqName?.asString()) {
      "kotlin.Unit" -> "V"
      "kotlin.Boolean" -> "Z"
      "kotlin.Byte" -> "B"
      "kotlin.Char" -> "C"
      "kotlin.Short" -> "S"
      "kotlin.Int" -> "I"
      "kotlin.Long" -> "J"
      "kotlin.Float" -> "F"
      "kotlin.Double" -> "D"
      "kotlin.String" -> "Ljava/lang/String;"
      "kotlin.Any" -> "Ljava/lang/Object;"
      "kotlin.collections.List", "kotlin.collections.MutableList" -> "Ljava/util/List;"
      "kotlin.collections.Set", "kotlin.collections.MutableSet" -> "Ljava/util/Set;"
      "kotlin.collections.Map", "kotlin.collections.MutableMap" -> "Ljava/util/Map;"
      null -> "Ljava/lang/Object;"
      else -> "L${name.replace('.', '/')};"
    }

  private fun location(
    file: IrFile,
    element: IrElement,
    owner: String,
  ): SourceLocation {
    val offset = element.startOffset.coerceAtLeast(0)
    return SourceLocation(
      owner,
      File(file.fileEntry.name).name,
      file.fileEntry.getLineNumber(offset) + 1,
      file.fileEntry.getColumnNumber(offset) + 1,
    )
  }

  private fun IrCall.argument(name: String): IrExpression? =
    symbol.owner.parameters.firstOrNull {
      it.kind == IrParameterKind.Regular && it.name.asString() == name
    }?.let { arguments[it] }

  private fun IrCall.receiver(): IrExpression? =
    symbol.owner.parameters.firstOrNull {
      it.kind == IrParameterKind.DispatchReceiver || it.kind == IrParameterKind.ExtensionReceiver
    }?.let { arguments[it] }

  private fun IrCall.regularArguments(): List<IrExpression> =
    symbol.owner.parameters.filter { it.kind == IrParameterKind.Regular }.mapNotNull { arguments[it] }
}
