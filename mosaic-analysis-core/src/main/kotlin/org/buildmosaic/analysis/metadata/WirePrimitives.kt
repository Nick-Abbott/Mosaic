package org.buildmosaic.analysis.metadata

import org.buildmosaic.analysis.CanvasKeyIdentity
import org.buildmosaic.analysis.ContractParameter
import org.buildmosaic.analysis.ParameterKind
import org.buildmosaic.analysis.SourceLocation

internal fun SourceLocation.toWire() = WireSite(owner, path, line, column)

internal fun WireSite.toModel() = SourceLocation(owner, path, line, column)

internal fun CanvasKeyIdentity.toWire() = WireKey(classId, qualifier)

internal fun WireKey.toModel() = CanvasKeyIdentity(classId, qualifier)

internal fun ContractParameter.toWire() = WireParameter(owner, name, kind.name)

internal fun WireParameter.toModel() = ContractParameter(owner, name, enumValue<ParameterKind>(kind))

internal inline fun <reified T : Enum<T>> enumValue(value: String): T =
  enumValues<T>().firstOrNull {
    it.name == value
  } ?: throw IllegalArgumentException("Unknown metadata enum kind $value")
