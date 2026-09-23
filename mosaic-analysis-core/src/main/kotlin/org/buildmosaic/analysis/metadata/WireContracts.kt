package org.buildmosaic.analysis.metadata

import org.buildmosaic.analysis.Binding
import org.buildmosaic.analysis.CanvasExpression
import org.buildmosaic.analysis.TileReference
import org.buildmosaic.analysis.UnknownRegistration

internal fun CanvasExpression.toWire(): WireCanvas =
  when (this) {
    CanvasExpression.Empty -> WireCanvas.Empty
    CanvasExpression.Current -> WireCanvas.Current
    is CanvasExpression.ParameterValue -> WireCanvas.Parameter(parameter.toWire())
    is CanvasExpression.Layer ->
      WireCanvas.Layer(
        id,
        parent.toWire(),
        bindings.map {
          it.toWire()
        },
        unknownRegistrations.map { it.toWire() },
        site.toWire(),
      )
    is CanvasExpression.Choice -> WireCanvas.Choice(guard.toWire(), whenTrue.toWire(), whenFalse.toWire())
    is CanvasExpression.RuntimeCall ->
      WireCanvas.RuntimeCall(
        target,
        arguments.toWire(),
        site.toWire(),
        callerEffects.map {
          it.toWire()
        },
        receiver.toWire(),
        virtualDispatch,
      )
    is CanvasExpression.ValueReference -> WireCanvas.ValueReference(id, site.toWire())
    is CanvasExpression.WithEffects -> WireCanvas.WithEffects(effects.map { it.toWire() }, result.toWire())
    is CanvasExpression.Captured ->
      WireCanvas.Captured(
        owner,
        origin.toWire(),
        expression.toWire(),
        faithfullyCaptured,
        site.toWire(),
      )
    is CanvasExpression.Assumption -> WireCanvas.Assumption(id, site.toWire())
    is CanvasExpression.Alias -> WireCanvas.Alias(id, expression.toWire())
    is CanvasExpression.Unknown -> WireCanvas.Unknown(reason, site.toWire())
  }

internal fun WireCanvas.toModel(): CanvasExpression =
  when (this) {
    WireCanvas.Empty -> CanvasExpression.Empty
    WireCanvas.Current -> CanvasExpression.Current
    is WireCanvas.Parameter -> CanvasExpression.ParameterValue(parameter.toModel())
    is WireCanvas.Layer ->
      CanvasExpression.Layer(
        id,
        parent.toModel(),
        bindings.map {
          it.toModel()
        },
        unknownRegistrations.map { it.toModel() },
        site.toModel(),
      )
    is WireCanvas.Choice -> CanvasExpression.Choice(guard.toModel(), whenTrue.toModel(), whenFalse.toModel())
    is WireCanvas.RuntimeCall ->
      CanvasExpression.RuntimeCall(
        target,
        arguments.toModel(),
        site.toModel(),
        callerEffects.map {
          it.toModel()
        },
        receiver.toModel(),
        virtualDispatch,
      )
    is WireCanvas.ValueReference -> CanvasExpression.ValueReference(id, site.toModel())
    is WireCanvas.WithEffects -> CanvasExpression.WithEffects(effects.map { it.toModel() }, result.toModel())
    is WireCanvas.Captured ->
      CanvasExpression.Captured(
        owner,
        origin.toModel(),
        expression.toModel(),
        faithfullyCaptured,
        site.toModel(),
      )
    is WireCanvas.Assumption -> CanvasExpression.Assumption(id, site.toModel())
    is WireCanvas.Alias -> CanvasExpression.Alias(id, expression.toModel())
    is WireCanvas.Unknown -> CanvasExpression.Unknown(reason, site.toModel())
  }

internal fun Binding.toWire() = WireBinding(key.toWire(), constructorEffects.map { it.toWire() }, site.toWire())

internal fun WireBinding.toModel() = Binding(key.toModel(), constructorEffects.map { it.toModel() }, site.toModel())

internal fun UnknownRegistration.toWire() = WireRegistration(reason, site.toWire())

internal fun WireRegistration.toModel() = UnknownRegistration(reason, site.toModel())

internal fun TileReference.toWire(): WireTile =
  when (this) {
    is TileReference.Stable -> WireTile.Stable(contractId, receiverId)
    is TileReference.ExportedProperty -> WireTile.ExportedProperty(contractId, site.toWire())
    is TileReference.Alias -> WireTile.Alias(reference.toWire())
    is TileReference.Fresh -> WireTile.Fresh(templateId, allocationId, invocationId)
    is TileReference.Unknown -> WireTile.Unknown(reason, site.toWire())
  }

internal fun WireTile.toModel(): TileReference =
  when (this) {
    is WireTile.Stable -> TileReference.Stable(contractId, receiverId)
    is WireTile.ExportedProperty -> TileReference.ExportedProperty(contractId, site.toModel())
    is WireTile.Alias -> TileReference.Alias(reference.toModel())
    is WireTile.Fresh -> TileReference.Fresh(templateId, allocationId, invocationId)
    is WireTile.Unknown -> TileReference.Unknown(reason, site.toModel())
  }
