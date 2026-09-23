package org.buildmosaic.analysis.metadata

import org.buildmosaic.analysis.CanvasKeyIdentity
import org.buildmosaic.analysis.CaptureOrigin
import org.buildmosaic.analysis.EvidenceKind
import org.buildmosaic.analysis.Fact

internal fun Fact<CanvasKeyIdentity>.toWire(): WireFact =
  when (this) {
    is Fact.Known -> WireFact.Known(value.toWire(), provenance?.toWire(), evidence.name, capturedOrigin?.toWire())
    is Fact.Unknown -> WireFact.Unknown(reason, site.toWire())
    is Fact.ExportedKey -> WireFact.ExportedKey(declaration, site.toWire())
  }

internal fun WireFact.toModel(): Fact<CanvasKeyIdentity> =
  when (this) {
    is WireFact.Known ->
      Fact.Known(
        value.toModel(),
        provenance?.toModel(),
        enumValue<EvidenceKind>(evidence),
        capturedOrigin?.toModel(),
      )
    is WireFact.Unknown -> Fact.Unknown(reason, site.toModel())
    is WireFact.ExportedKey -> Fact.ExportedKey(declaration, site.toModel())
  }

internal fun CaptureOrigin.toWire(): WireOrigin =
  when (this) {
    is CaptureOrigin.Inline -> WireOrigin.Inline(declaration, artifact, contractHash)
    is CaptureOrigin.Constant -> WireOrigin.Constant(declaration, artifact, literal)
  }

internal fun WireOrigin.toModel(): CaptureOrigin =
  when (this) {
    is WireOrigin.Inline -> CaptureOrigin.Inline(declaration, artifact, contractHash)
    is WireOrigin.Constant -> CaptureOrigin.Constant(declaration, artifact, literal)
  }
