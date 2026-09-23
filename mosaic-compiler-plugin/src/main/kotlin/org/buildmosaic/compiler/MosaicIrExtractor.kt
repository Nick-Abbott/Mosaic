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
import org.buildmosaic.analysis.DispatchReceiver
import org.buildmosaic.analysis.Effect
import org.buildmosaic.analysis.Fact
import org.buildmosaic.analysis.LookupKind
import org.buildmosaic.analysis.ModuleContract
import org.buildmosaic.analysis.MultiTileExecution
import org.buildmosaic.analysis.OverrideSlot
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
import org.jetbrains.kotlin.ir.declarations.IrAnonymousInitializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
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
import org.jetbrains.kotlin.ir.expressions.IrErrorExpression
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrExpressionBody
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetField
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSpreadElement
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.expressions.IrWhen
import org.jetbrains.kotlin.ir.expressions.IrWhileLoop
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeProjection
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.util.isFakeOverride
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
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
                  val baseId = symbolId(base)
                  val implementationId = symbolId(function)
                  val slots =
                    base.parameters.filter { it.kind == IrParameterKind.Regular }.zip(
                      function.parameters.filter { it.kind == IrParameterKind.Regular },
                    ).mapIndexedNotNull { position, (baseParameter, implementationParameter) ->
                      if (isCanvasType(baseParameter.type) && isCanvasType(implementationParameter.type)) {
                        OverrideSlot(
                          ContractParameter(baseId, baseParameter.name.asString(), ParameterKind.CANVAS),
                          ContractParameter(
                            implementationId,
                            implementationParameter.name.asString(),
                            ParameterKind.CANVAS,
                          ),
                          position,
                        )
                      } else {
                        null
                      }
                    }
                  overrides += ResolvedOverride(receiverType, baseId, implementationId, slots)
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
              tiles += TileContract(propertyId(declaration), effects, site, multi = initializer.symbol.owner.name.asString() != "singleTile")
              declaration.getter?.let { locators[propertyId(declaration)] = binaryLocator(it, file) }
            }
            declaration.getter?.let { getter ->
              val stableTileGetter = initializer is IrCall && isTileFactory(initializer) && getter.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR
              val id = symbolId(getter)
              val site = location(file, getter, id)
              if (isCanvasType(getter.returnType)) {
                val result =
                  if (getter.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR) {
                    CanvasExpression.Unknown("Stored Canvas property provenance is unavailable", site)
                  } else {
                    returnedExpression(getter)?.let { canvasExpression(it, file, id, mutableMapOf()) }
                      ?: CanvasExpression.Unknown("Canvas getter body is unavailable", site)
                  }
                canvases += CanvasContract(id, canvasParameters(getter, id), result, site)
              } else {
                val getterEffects =
                  when {
                    stableTileGetter -> emptyList()
                    getter.origin == IrDeclarationOrigin.DEFAULT_PROPERTY_ACCESSOR ->
                      initializer?.let { extractEffects(it, file, id, mutableMapOf()) }.orEmpty()
                    getter.body != null -> extractEffects(getter.body!!, file, id, mutableMapOf())
                    else -> listOf(Effect.Unknown("$id:body", "Getter body is unavailable", site))
                  }
                callables += CallableContract(id, canvasParameters(getter, id), getterEffects, location(file, getter, id))
              }
              locators[id] = binaryLocator(getter, file)
            }
          }
          is IrConstructor -> {
            val id = symbolId(declaration)
            val site = location(file, declaration, id)
            val clazz = declaration.parent as? IrClass
            val hasCapabilityInitialization =
              declaration.body?.let(::containsCapability) == true ||
                clazz?.declarations?.filterIsInstance<IrAnonymousInitializer>()?.any(::containsCapability) == true ||
                clazz?.declarations?.filterIsInstance<IrProperty>()?.any {
                  it.backingField?.initializer?.expression?.let(::containsCapability) == true
                } == true
            val effects =
              if (hasCapabilityInitialization) {
                listOf(Effect.Unknown("$id:initialization", "Constructor initialization may have Mosaic effects", site))
              } else {
                emptyList()
              }
            callables += CallableContract(id, canvasParameters(declaration, id), effects, site)
            locators[id] = binaryLocator(declaration, file)
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
              val precedingEffects =
                (declaration.body as? IrBlockBody)?.statements?.dropLast(1)?.flatMap {
                  extractEffects(it, file, id, aliases)
                }.orEmpty()
              val returnedCanvas =
                returned?.let { canvasExpression(it, file, id, aliases) }
                  ?: CanvasExpression.Unknown("Canvas body is unavailable", site)
              val result =
                if (precedingEffects.isEmpty()) {
                  returnedCanvas
                } else {
                  CanvasExpression.WithEffects(
                    precedingEffects,
                    returnedCanvas,
                  )
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

    fun canvasValue(expression: IrExpression): CanvasExpression =
      CanvasExpression.Alias(
        "$owner:expression:${expression.startOffset}",
        canvasExpression(expression, file, owner, aliases),
      )

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
        is IrVararg -> element.elements.forEach(::scan)
        is IrSpreadElement -> scan(element.expression)
        is IrGetField -> {
          val property = element.symbol.owner.correspondingPropertySymbol?.owner
          val getter = property?.getter
          when {
            getter != null && symbolId(getter) != owner ->
              effects += Effect.Call("$owner:field:${element.startOffset}", symbolId(getter), site = location(file, element, owner))
            property != null -> {
              val initializer = property.backingField?.initializer?.expression
              if (initializer == null) {
                effects += Effect.Unknown("$owner:field:${element.startOffset}", "Stored field initialization is unavailable", location(file, element, owner))
              } else {
                effects += extractEffects(initializer, file, owner, aliases.toMutableMap())
              }
            }
            isCapabilityType(element.type) ->
              effects += Effect.Unknown("$owner:field:${element.startOffset}", "Capability field provenance is unavailable", location(file, element, owner))
          }
        }
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
          if (!isTileFactory(element)) effects += callActualEffects(element, file, owner, aliases)
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
                  execution = multiTileExecution(element),
                  site = site,
                )
            }
            target == "org.buildmosaic.core.injection.canvas" || target == "org.buildmosaic.core.injection.Canvas.withLayer" ->
              effects +=
                Effect.ConstructCanvas(
                  "$owner:construct:${element.startOffset}",
                  canvasValue(element),
                  site,
                )
            target == "org.buildmosaic.core.injection.create" ->
              effects +=
                Effect.ConstructCanvas(
                  "$owner:create:${element.startOffset}",
                  element.receiver()?.let { canvasValue(it) }
                    ?: CanvasExpression.Unknown("Missing Mosaic.create Canvas", site),
                  site,
                )
            isTileFactory(element) -> Unit
            element.symbol.owner.correspondingPropertySymbol != null -> {
              val getter = element.symbol.owner
              if (isCanvasType(getter.returnType) || isUserPropertyGetter(getter) || isCapabilityCall(element)) {
                val boundary = unavailableCallableReason(element)
                when {
                  boundary != null -> effects += Effect.Unknown("$owner:getter:${element.startOffset}", boundary, site)
                  isCanvasType(getter.returnType) ->
                    effects += Effect.ConstructCanvas("$owner:getter:${element.startOffset}", canvasValue(element), site)
                  else ->
                    effects += Effect.Call("$owner:getter:${element.startOffset}", symbolId(getter), callArguments(element, file, owner, aliases), site, dispatchReceiver(element, aliases), getter.modality != Modality.FINAL)
                }
              }
            }
            isCapabilityCall(element) || isUserCallable(target) -> {
              val boundary = unavailableCallableReason(element)
              if (boundary != null) {
                effects += Effect.Unknown("$owner:call:${element.startOffset}", boundary, site)
              } else {
                effects += Effect.Call("$owner:call:${element.startOffset}", symbolId(element.symbol.owner), callArguments(element, file, owner, aliases), site, dispatchReceiver(element, aliases), element.symbol.owner.modality != Modality.FINAL)
              }
            }
            else -> Unit
          }
        }
        is IrConstructorCall -> {
          element.arguments.filterNotNull().sortedBy { it.startOffset }.forEach(::scan)
          effects += usedDefaultEffects(element, file, owner)
          val constructor = element.symbol.owner
          val clazz = constructor.parent as? IrClass
          if (isUserConstructor(clazz)) {
            val targetId = symbolId(constructor)
            effects += Effect.Call("$owner:constructor:${element.startOffset}", targetId, site = location(file, element, owner))
          } else if (constructor.parameters.any { isCapabilityType(it.type) }) {
            effects += Effect.Unknown("$owner:constructor:${element.startOffset}", "Constructor capability effects are not summarized", location(file, element, owner))
          }
        }
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
            val parent =
              expression.argument("parent")?.let {
                CanvasExpression.Alias("$owner:expression:${it.startOffset}", canvasExpression(it, file, owner, aliases))
              } ?: CanvasExpression.Empty
            layer(expression, expression.argument("build"), parent, file, owner, aliases)
          }
          "org.buildmosaic.core.injection.Canvas.withLayer" -> {
            val parent =
              expression.receiver()?.let {
                CanvasExpression.Alias("$owner:expression:${it.startOffset}", canvasExpression(it, file, owner, aliases))
              }
                ?: CanvasExpression.Unknown("Missing layer parent", site)
            layer(expression, expression.argument("build"), parent, file, owner, aliases)
          }
          else -> {
            val boundary = unavailableCallableReason(expression)
            if (boundary != null) {
              CanvasExpression.WithEffects(
                callActualEffects(expression, file, owner, aliases),
                CanvasExpression.Unknown(boundary, site),
              )
            } else if (expression.symbol.owner.parameters.any {
                it.kind == IrParameterKind.Regular && !isCanvasType(it.type)
              }
            ) {
              CanvasExpression.WithEffects(
                callActualEffects(expression, file, owner, aliases),
                CanvasExpression.Unknown("Non-Canvas Canvas-helper arguments are unsupported", site),
              )
            } else {
              CanvasExpression.RuntimeCall(
                symbolId(expression.symbol.owner),
                callArguments(expression, file, owner, aliases),
                site,
                callerEffects = callActualEffects(expression, file, owner, aliases),
              )
            }
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
      } else if (containsCapability(statement)) {
        unknown += UnknownRegistration("Unsupported Canvas registration", location(file, statement, owner))
      }
    }
    return CanvasExpression.Layer("$owner:${call.startOffset}", parent, bindings, unknown, site)
  }

  private fun callActualEffects(
    call: IrCall,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): List<Effect> {
    val actuals = call.arguments.filterNotNull().sortedBy { it.startOffset }
    val effects =
      actuals.filter { lambdaBody(it) == null }.flatMap { actual ->
        if (isCanvasType(actual.type)) {
          listOf(
            Effect.ConstructCanvas(
              "$owner:actual:${actual.startOffset}",
              CanvasExpression.Alias(
                "$owner:expression:${actual.startOffset}",
                canvasExpression(actual, file, owner, aliases),
              ),
              location(file, actual, owner),
            ),
          )
        } else {
          extractEffects(actual, file, owner, aliases.toMutableMap())
        }
      }
    val target = resolvedName(call)
    val knownCallback =
      target in
        setOf(
          "org.buildmosaic.core.injection.canvas",
          "org.buildmosaic.core.injection.Canvas.withLayer",
          "org.buildmosaic.core.injection.CanvasBuilder.single",
        ) || isTileFactory(call)
    val withDefaults = effects + usedDefaultEffects(call, file, owner)
    return if (!knownCallback && actuals.any { lambdaBody(it)?.let(::containsCapability) == true }) {
      withDefaults + Effect.Unknown("$owner:callback:${call.startOffset}", "Unsupported capability-bearing callback", location(file, call, owner))
    } else {
      withDefaults
    }
  }

  private fun usedDefaultEffects(
    call: IrFunctionAccessExpression,
    file: IrFile,
    owner: String,
  ): List<Effect> {
    val target = call.symbol.owner.fqNameWhenAvailable?.asString().orEmpty()
    val eligible =
      when (call) {
        is IrCall -> isUserCallable(target) || isCapabilityCall(call)
        is IrConstructorCall -> isUserConstructor(call.symbol.owner.parent as? IrClass)
        else -> false
      }
    if (!eligible) return emptyList()
    if (target.startsWith("org.buildmosaic.core.")) return emptyList()
    return call.symbol.owner.parameters.filter {
      it.kind == IrParameterKind.Regular && call.arguments[it] == null
    }.mapNotNull { parameter ->
      val default = parameter.defaultValue
      val unavailable = default == null || (default as? IrExpressionBody)?.expression is IrErrorExpression
      if (!unavailable && default?.let(::containsCapability) != true) return@mapNotNull null
      Effect.Unknown(
        "$owner:default:${call.startOffset}:${parameter.name.asString()}",
        if (unavailable) {
          "Default expression is unavailable for ${symbolId(call.symbol.owner)}.${parameter.name.asString()}"
        } else {
          "Capability-bearing default expression is unsupported for ${symbolId(
            call.symbol.owner,
          )}.${parameter.name.asString()}"
        },
        location(file, call, owner),
      )
    }
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
    val type = call.typeArguments.firstOrNull() ?: return Fact.Unknown("Reified Canvas key type unavailable", site)
    val classId = runtimeKeyClass(type) ?: return Fact.Unknown("Runtime Canvas key class unavailable", site)
    val qualifier = call.argument("qualifier")
    val literal =
      when (qualifier) {
        null -> null
        is IrConst -> qualifier.value as? String
        else -> return Fact.Unknown("Dynamic qualifier is unsupported", site)
      }
    if (literal != null) limitations += "Qualifier literal at ${site.path}:${site.line} in $owner has no reliable external const origin in pre-inline IR"
    return Fact.Known(CanvasKeyIdentity(classId, literal), site)
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
          expression.receiver()?.let {
            CanvasExpression.Alias("$owner:expression:${it.startOffset}", canvasExpression(it, file, owner, aliases))
          }
            ?: CanvasExpression.Unknown("Missing Mosaic.create Canvas", location(file, expression, owner))
        } else {
          CanvasExpression.Unknown("Unsupported Mosaic receiver", location(file, expression, owner))
        }
      is IrGetValue ->
        aliases[expression.symbol]?.let { mosaicCanvas(it, file, owner, aliases) }
          ?: (expression.symbol.owner as? IrValueParameter)?.takeIf {
            it.kind == IrParameterKind.ExtensionReceiver || it.kind == IrParameterKind.DispatchReceiver
          }?.let { CanvasExpression.Current }
          ?: CanvasExpression.Unknown("Unresolved Mosaic alias", location(file, expression, owner))
      null ->
        CanvasExpression.Unknown(
          "Mosaic receiver is unavailable",
          SourceLocation(owner, file.fileEntry.name, 1, 1),
        )
      else -> CanvasExpression.Unknown("Unsupported Mosaic provenance", location(file, expression, owner))
    }

  private fun lookupCanvas(
    call: IrCall,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CanvasExpression =
    if (resolvedName(call).startsWith("org.buildmosaic.core.injection.Canvas.") ||
      resolvedName(call) in setOf("org.buildmosaic.core.injection.source", "org.buildmosaic.core.injection.sourceOr")
    ) {
      call.receiver()?.let {
        CanvasExpression.Alias("$owner:expression:${it.startOffset}", canvasExpression(it, file, owner, aliases))
      }
        ?: CanvasExpression.Unknown("Canvas lookup receiver is unavailable", location(file, call, owner))
    } else {
      mosaicCanvas(call.receiver(), file, owner, aliases)
    }

  private fun callArguments(
    call: IrCall,
    file: IrFile,
    owner: String,
    aliases: MutableMap<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): CallArguments {
    val targetId = symbolId(call.symbol.owner)
    val values = linkedMapOf<ContractParameter, ArgumentExpression>()
    call.symbol.owner.parameters.filter {
      it.kind == IrParameterKind.Regular && call.arguments[it] != null
    }.sortedBy { call.arguments[it]!!.startOffset }.forEach {
        parameter ->
      if (isCanvasType(parameter.type)) {
        call.arguments[parameter]?.let { actual ->
          values[ContractParameter(targetId, parameter.name.asString(), ParameterKind.CANVAS)] =
            ArgumentExpression.Canvas(CanvasExpression.Alias("$owner:expression:${actual.startOffset}", canvasExpression(actual, file, owner, aliases)))
        }
      }
    }
    return CallArguments(values)
  }

  private fun dispatchReceiver(
    call: IrCall,
    aliases: Map<org.jetbrains.kotlin.ir.symbols.IrValueSymbol, IrExpression>,
  ): DispatchReceiver {
    val parameter =
      call.symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.DispatchReceiver }
        ?: return DispatchReceiver.None

    fun resolve(expression: IrExpression?): DispatchReceiver =
      when (expression) {
        is IrConstructorCall -> {
          val type = expression.symbol.owner.parent as? IrClass
          if (type?.modality == Modality.FINAL) {
            type.fqNameWhenAvailable?.asString()?.let(DispatchReceiver::Concrete)
              ?: DispatchReceiver.Unknown("Concrete receiver type is unavailable")
          } else {
            DispatchReceiver.Unknown("Receiver is not final")
          }
        }
        is IrGetValue ->
          aliases[expression.symbol]?.let(::resolve)
            ?: if ((expression.symbol.owner as? IrValueParameter)?.kind == IrParameterKind.DispatchReceiver) {
              DispatchReceiver.Forwarded
            } else {
              DispatchReceiver.Unknown("Dispatch receiver parameter or mutable value")
            }
        else -> DispatchReceiver.Unknown("Unsupported dispatch receiver provenance")
      }
    return resolve(call.arguments[parameter])
  }

  private fun multiTileExecution(call: IrCall): MultiTileExecution {
    val tileParameter = call.symbol.owner.parameters.firstOrNull { it.name.asString() == "tile" }
    if (tileParameter?.type?.classFqName?.asString() != "org.buildmosaic.core.MultiTile") return MultiTileExecution.NOT_APPLICABLE
    if (call.argument("key") != null) return MultiTileExecution.KNOWN_NON_EMPTY
    val keys = call.argument("keys") ?: return MultiTileExecution.UNKNOWN
    if (keys is IrCall) {
      val name = resolvedName(keys)
      if (name in setOf("kotlin.collections.emptyList", "kotlin.collections.emptySet")) return MultiTileExecution.KNOWN_EMPTY
      if (name in setOf("kotlin.collections.listOf", "kotlin.collections.setOf", "kotlin.collections.mutableListOf", "kotlin.collections.mutableSetOf", "kotlin.collections.arrayListOf")) {
        val items = keys.arguments.filterIsInstance<IrVararg>().flatMap { it.elements }
        val direct = keys.arguments.filterNotNull().filterNot { it is IrVararg }
        if (items.isEmpty() && direct.isEmpty()) return MultiTileExecution.KNOWN_EMPTY
        if (direct.isNotEmpty()) return MultiTileExecution.KNOWN_NON_EMPTY
        if (items.none { it is IrSpreadElement }) return MultiTileExecution.KNOWN_NON_EMPTY
      }
    }
    return MultiTileExecution.UNKNOWN
  }

  private fun normalizeKeyClass(classId: String): String =
    when (classId) {
      "kotlin.collections.MutableList" -> "kotlin.collections.List"
      "kotlin.collections.MutableSet" -> "kotlin.collections.Set"
      "kotlin.collections.MutableMap" -> "kotlin.collections.Map"
      else -> classId
    }

  private fun runtimeKeyClass(type: IrType): String? {
    val classId = type.classFqName?.asString() ?: return null
    if (classId != "kotlin.Array") return normalizeKeyClass(classId)
    val element = (type as? IrSimpleType)?.arguments?.singleOrNull() as? IrTypeProjection ?: return null
    return arrayComponentDescriptor(element.type)?.let { "[$it" }
  }

  private fun arrayComponentDescriptor(type: IrType): String? {
    val classId = type.classFqName?.asString() ?: return null
    if (classId == "kotlin.Array") return runtimeKeyClass(type)
    val owner =
      when (classId) {
        "kotlin.Boolean" -> "java/lang/Boolean"
        "kotlin.Byte" -> "java/lang/Byte"
        "kotlin.Char" -> "java/lang/Character"
        "kotlin.Short" -> "java/lang/Short"
        "kotlin.Int" -> "java/lang/Integer"
        "kotlin.Long" -> "java/lang/Long"
        "kotlin.Float" -> "java/lang/Float"
        "kotlin.Double" -> "java/lang/Double"
        "kotlin.String" -> "java/lang/String"
        "kotlin.Any" -> "java/lang/Object"
        "kotlin.collections.Collection", "kotlin.collections.MutableCollection" -> "java/util/Collection"
        "kotlin.collections.List", "kotlin.collections.MutableList" -> "java/util/List"
        "kotlin.collections.Set", "kotlin.collections.MutableSet" -> "java/util/Set"
        "kotlin.collections.Map", "kotlin.collections.MutableMap" -> "java/util/Map"
        "kotlin.BooleanArray" -> return "[Z"
        "kotlin.ByteArray" -> return "[B"
        "kotlin.CharArray" -> return "[C"
        "kotlin.ShortArray" -> return "[S"
        "kotlin.IntArray" -> return "[I"
        "kotlin.LongArray" -> return "[J"
        "kotlin.FloatArray" -> return "[F"
        "kotlin.DoubleArray" -> return "[D"
        else -> return null
      }
    return "L$owner;"
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

  private fun isUserConstructor(clazz: IrClass?): Boolean {
    val name = clazz?.fqNameWhenAvailable?.asString().orEmpty()
    return name.isNotBlank() &&
      !name.startsWith("kotlin.") && !name.startsWith("kotlinx.") &&
      !name.startsWith("java.") && !name.startsWith("org.buildmosaic.core.")
  }

  private fun isUnsupportedMosaicExtension(call: IrCall): Boolean =
    call.symbol.owner.parameters.any {
      it.kind == IrParameterKind.ExtensionReceiver && it.type.classFqName?.asString() == "org.buildmosaic.core.Mosaic"
    }

  private fun unavailableCallableReason(call: IrCall): String? =
    when {
      isUnsupportedMosaicExtension(call) ->
        "Mosaic extension receiver transfer is unsupported for ${symbolId(call.symbol.owner)}"
      call.symbol.owner.isInline ->
        "External inline capability helper ${symbolId(call.symbol.owner)} has no pre-inline body"
      else -> null
    }

  private fun isUserPropertyGetter(getter: IrSimpleFunction): Boolean {
    val target = getter.fqNameWhenAvailable?.asString().orEmpty()
    return target.isNotBlank() &&
      !target.startsWith("kotlin.") &&
      !target.startsWith("kotlinx.") &&
      !target.startsWith("java.") &&
      !target.startsWith("org.buildmosaic.core.")
  }

  private fun containsCapability(element: IrElement): Boolean {
    var found = false
    element.acceptVoid(
      object : IrVisitorVoid() {
        override fun visitElement(element: IrElement) {
          element.acceptChildrenVoid(this)
        }

        override fun visitCall(expression: IrCall) {
          val userGetter =
            expression.symbol.owner.correspondingPropertySymbol != null &&
              isUserPropertyGetter(expression.symbol.owner)
          if (isCapabilityCall(expression) || isUserCallable(resolvedName(expression)) || userGetter) found = true
          super.visitCall(expression)
        }

        override fun visitGetField(expression: IrGetField) {
          if (isCapabilityType(expression.type)) found = true
          super.visitGetField(expression)
        }

        override fun visitConstructorCall(expression: IrConstructorCall) {
          if (expression.symbol.owner.parameters.any { isCapabilityType(it.type) } ||
            isUserConstructor(expression.symbol.owner.parent as? IrClass)
          ) {
            found = true
          }
          super.visitConstructorCall(expression)
        }
      },
    )
    return found
  }

  private fun isSource(target: String): Boolean =
    target in
      setOf(
        "org.buildmosaic.core.source", "org.buildmosaic.core.sourceOr",
        "org.buildmosaic.core.injection.source", "org.buildmosaic.core.injection.sourceOr",
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
        it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.ExtensionReceiver
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
      function.parameters.filter {
        it.kind == IrParameterKind.Regular || it.kind == IrParameterKind.ExtensionReceiver
      }.joinToString(
        "",
      ) { jvmType(it.type) }
    val suspendCall = (function as? IrSimpleFunction)?.isSuspend == true
    val continuation = if (suspendCall) "Lkotlin/coroutines/Continuation;" else ""
    val returnType =
      if (function is IrConstructor) {
        "V"
      } else if (suspendCall) {
        "Ljava/lang/Object;"
      } else {
        jvmType(function.returnType)
      }
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
}
