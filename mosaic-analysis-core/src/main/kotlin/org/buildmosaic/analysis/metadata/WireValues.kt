package org.buildmosaic.analysis.metadata

import org.buildmosaic.analysis.ArgumentExpression
import org.buildmosaic.analysis.BooleanExpression
import org.buildmosaic.analysis.CallArguments
import org.buildmosaic.analysis.ContractParameter
import org.buildmosaic.analysis.DispatchReceiver
import org.buildmosaic.analysis.Guard

internal fun BooleanExpression.toWire(): WireBoolean =
  when (this) {
    is BooleanExpression.Constant -> WireBoolean.Constant(value)
    is BooleanExpression.ParameterValue -> WireBoolean.Parameter(parameter.toWire())
    is BooleanExpression.Opaque -> WireBoolean.Opaque(reason, site.toWire())
  }

internal fun WireBoolean.toModel(): BooleanExpression =
  when (this) {
    is WireBoolean.Constant -> BooleanExpression.Constant(value)
    is WireBoolean.Parameter -> BooleanExpression.ParameterValue(parameter.toModel())
    is WireBoolean.Opaque -> BooleanExpression.Opaque(reason, site.toModel())
  }

internal fun Guard.toWire(): WireGuard =
  when (this) {
    is Guard.Constant -> WireGuard.Constant(value)
    is Guard.BooleanParameter -> WireGuard.Parameter(parameter.toWire(), expected)
    is Guard.Opaque -> WireGuard.Opaque(reason, site.toWire())
  }

internal fun WireGuard.toModel(): Guard =
  when (this) {
    is WireGuard.Constant -> Guard.Constant(value)
    is WireGuard.Parameter -> Guard.BooleanParameter(parameter.toModel(), expected)
    is WireGuard.Opaque -> Guard.Opaque(reason, site.toModel())
  }

internal fun ArgumentExpression.toWire(): WireArgument =
  when (this) {
    is ArgumentExpression.Canvas -> WireArgument.Canvas(expression.toWire())
    is ArgumentExpression.BooleanValue -> WireArgument.BooleanValue(expression.toWire())
  }

internal fun WireArgument.toModel(): ArgumentExpression =
  when (this) {
    is WireArgument.Canvas -> ArgumentExpression.Canvas(expression.toModel())
    is WireArgument.BooleanValue -> ArgumentExpression.BooleanValue(expression.toModel())
  }

internal fun CallArguments.toWire() =
  WireArguments(
    values.map { (parameter, value) -> WireActual(parameter.toWire(), value.toWire()) },
  )

internal fun WireArguments.toModel(): CallArguments {
  val actuals = linkedMapOf<ContractParameter, ArgumentExpression>()
  values.forEach {
    val parameter = it.parameter.toModel()
    require(!actuals.containsKey(parameter)) { "Duplicate argument parameter $parameter" }
    actuals[parameter] = it.value.toModel()
  }
  return CallArguments(actuals)
}

internal fun DispatchReceiver.toWire(): WireReceiver =
  when (this) {
    DispatchReceiver.None -> WireReceiver.None
    DispatchReceiver.Forwarded -> WireReceiver.Forwarded
    is DispatchReceiver.Concrete -> WireReceiver.Concrete(type)
    is DispatchReceiver.Unknown -> WireReceiver.Unknown(reason)
  }

internal fun WireReceiver.toModel(): DispatchReceiver =
  when (this) {
    WireReceiver.None -> DispatchReceiver.None
    WireReceiver.Forwarded -> DispatchReceiver.Forwarded
    is WireReceiver.Concrete -> DispatchReceiver.Concrete(type)
    is WireReceiver.Unknown -> DispatchReceiver.Unknown(reason)
  }
