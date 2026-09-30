package org.buildmosaic.opentelemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes

internal val tileKind = AttributeKey.stringKey("mosaic.tile.kind")
internal val batchSizeKey = AttributeKey.longKey("mosaic.multitile.batch_size")
internal val errorTypeKey = AttributeKey.stringKey("error.type")
internal val cancelledKey = AttributeKey.booleanKey("mosaic.execution.cancelled")
internal val contributorsTruncatedKey = AttributeKey.booleanKey("mosaic.links.contributor.truncated")
internal val dependenciesTruncatedKey = AttributeKey.booleanKey("mosaic.links.dependency.truncated")
internal val pendingTruncatedKey = AttributeKey.booleanKey("mosaic.dependencies.pending.truncated")
internal val dependencyLink = Attributes.of(AttributeKey.stringKey("mosaic.link.type"), "dependency")
internal val contributorLink = Attributes.of(AttributeKey.stringKey("mosaic.link.type"), "contributor")
