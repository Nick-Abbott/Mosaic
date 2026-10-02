package publicapi
import org.buildmosaic.core.*
import org.buildmosaic.core.injection.*

val NeedsMetricsTile = singleTile { source<Metrics>("named"); "ok" }
val NeedsManyTile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }
val NeedsPerKeyTile = perKeyTile<String, String> { source<Metrics>("named"); "ok" }
val NeedsChunkTile = chunkedMultiTile<String, String>(2) { source<Metrics>("named"); emptyMap() }
val DirectTile = Tile { source<Metrics>("named"); "ok" }
val DirectMultiTile = MultiTile<String, String> { source<Metrics>("named"); emptyMap() }
val CanvasPropertyTile = singleTile { val c = canvas; c.source<Metrics>(); "ok" }
val DirectCanvasPropertyTile = singleTile { canvas.source<Metrics>(); "ok" }
val CurrentMosaicTile = singleTile { source<Metrics>(); "ok" }
val BothLookupTile = singleTile { source<Metrics>(); canvas.source<Metrics>() }

suspend fun kclassMissing() { canvas {}.source(Metrics::class, "named") }
suspend fun kclassAliasMissing() { val type = Metrics::class; canvas {}.source(type, "named") }
suspend fun keyMissing() { canvas {}.source(CanvasKey(Metrics::class, "named")) }
suspend fun aliasMissing() { val k = CanvasKey(Metrics::class, "named"); val alias = k; canvas {}.source(alias) }
suspend fun crossFileMissing() { canvas {}.source(SharedKey) }
suspend fun optionalKclass() { canvas {}.sourceOr(Metrics::class, "named") }
suspend fun optionalKey() { canvas {}.sourceOr(CanvasKey(Metrics::class, "named")) }
suspend fun optionalReified() { canvas {}.sourceOr<Metrics>() }
suspend fun qualifiedReified() { canvas { single<Metrics>("named") { Metrics() } }.source(Metrics::class, "named") }
suspend fun qualifiedExplicit() { canvas { single(CanvasKey(Metrics::class, "named")) { Metrics() } }.source(SharedKey) }
suspend fun keyReuse() { canvas { single(SharedKey) { Metrics() } }.source(SharedKey) }
suspend fun emptyDistinct() { canvas { single<Metrics>() { Metrics() } }.source(EmptyKey) }
suspend fun emptyMatched() { canvas { single<Metrics>("") { Metrics() } }.source(EmptyKey) }
suspend fun nullMatched() { canvas { single<Metrics> { Metrics() } }.source(NullKey) }
suspend fun nullDistinct() { canvas { single<Metrics>("") { Metrics() } }.source(NullKey) }
suspend fun mosaicKey() { val m = canvas {}.create(); m.source(SharedKey) }
suspend fun mosaicOptionalKey() { val m = canvas {}.create(); m.sourceOr(SharedKey) }
suspend fun mosaicPropertyMissing() { canvas {}.create().compose(CanvasPropertyTile) }
suspend fun mosaicPropertySupplied() { canvas { single<Metrics> { Metrics() } }.create().compose(CanvasPropertyTile) }
suspend fun currentMosaicMissing() { canvas {}.create().compose(CurrentMosaicTile) }
suspend fun currentMosaicSupplied() { canvas { single<Metrics> { Metrics() } }.create().compose(CurrentMosaicTile) }
suspend fun bothLookups() { canvas { single<Metrics> { Metrics() } }.create().compose(BothLookupTile) }
suspend fun directPropertyMissing() { canvas {}.create().compose(DirectCanvasPropertyTile) }
suspend fun paintExplicit() { canvas { single(SharedKey) { Metrics() }; single<String> { paint(SharedKey); "ok" } } }
suspend fun paintReified() { canvas { single<Metrics>("named") { Metrics() }; single<String> { paint<Metrics>("named"); "ok" } } }
suspend fun parentNamed() { canvas(parent = canvas { single<Metrics>("named") { Metrics() } }) { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
suspend fun parentPositional() { canvas(canvas { single(SharedKey) { Metrics() } }) { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
suspend fun parentWorkFirst() { canvas(parent = canvas { single<String> { paint(SharedKey); "x" } }) { single(SharedKey) { Metrics() } } }
suspend fun layerFallback() { val p = canvas { single(SharedKey) { Metrics() } }; val alias = p; alias.withLayer { single<String> { paint(SharedKey); "ok" } }.source(SharedKey) }
suspend fun composeSync() { canvas {}.create().compose(NeedsMetricsTile) }
suspend fun composeAsync() { canvas {}.create().composeAsync(NeedsMetricsTile) }
suspend fun manySingle() { canvas {}.create().compose(NeedsManyTile, "a") }
suspend fun manySingleAsync() { canvas {}.create().composeAsync(NeedsManyTile, "a") }
suspend fun manyNonempty() { canvas {}.create().compose(NeedsManyTile, listOf("a")) }
suspend fun manyNonemptyAsync() { canvas {}.create().composeAsync(NeedsManyTile, listOf("a")) }
suspend fun manyEmpty() { canvas {}.create().compose(NeedsManyTile, emptyList()) }
suspend fun manyEmptyAsync() { canvas {}.create().composeAsync(NeedsManyTile, emptyList()) }
suspend fun perKeySingle() { canvas {}.create().compose(NeedsPerKeyTile, "a") }
suspend fun chunkSingle() { canvas {}.create().compose(NeedsChunkTile, "a") }
suspend fun directTile() { canvas {}.create().compose(DirectTile) }
suspend fun directMultiTile() { canvas {}.create().compose(DirectMultiTile, "a") }
suspend fun localSingle() { val tile = singleTile { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile) }
suspend fun directFresh() { canvas {}.create().compose(singleTile { source<Metrics>("named"); "ok" }) }
suspend fun localDirectConstructor() { val tile = Tile { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile) }
suspend fun localMulti() { val tile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, "a") }
suspend fun localMultiEmpty() { val tile = multiTile<String, String> { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
suspend fun localTileAlias() { val tile = singleTile { source<Metrics>("named"); "ok" }; val alias = tile; canvas {}.create().compose(alias) }
suspend fun localPerKey() { val tile = perKeyTile<String, String> { source<Metrics>("named"); "ok" }; canvas {}.create().compose(tile, "a") }
suspend fun localChunked() { val tile = chunkedMultiTile<String, String>(2) { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, "a") }
suspend fun localChunkedCreationWork() { val tile = chunkedMultiTile<String, String>(canvas {}.source<Int>()) { source<Metrics>("named"); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
suspend fun localScalarCapture() { val qualifier = "named"; val tile = singleTile { source<Metrics>(qualifier); "ok" }; canvas {}.create().compose(tile) }
suspend fun localKnownKeyCapture() { val key = CanvasKey(Metrics::class, "named"); val tile = singleTile { source(key); "ok" }; canvas {}.create().compose(tile) }
suspend fun localKeyAliasCapture() { val key = CanvasKey(Metrics::class, "named"); val alias = key; val tile = singleTile { source(alias); "ok" }; canvas {}.create().compose(tile) }
suspend fun localTypeQualifierCapture() { val type = Metrics::class; val qualifier = "named"; val tile = singleTile { canvas.source(type, qualifier); "ok" }; canvas {}.create().compose(tile) }
suspend fun localExportedKeyTile() { val tile = singleTile { source(SharedKey); "ok" }; canvas {}.create().compose(tile) }
suspend fun localCanvasCapture() { val captured = canvas {}; val tile = singleTile { captured.source<Metrics>(); "ok" }; canvas {}.create().compose(tile) }
suspend fun localCapturedMultiEmpty() { val captured = canvas {}; val tile = multiTile<String, String> { captured.source<Metrics>(); emptyMap() }; canvas {}.create().compose(tile, emptyList()) }
suspend fun twoLocalTilesAsync() { val first = singleTile { source<Metrics>("named"); "a" }; val second = singleTile { source<Metrics>("named"); "b" }; val m = canvas {}.create(); m.composeAsync(first); m.composeAsync(second) }
suspend fun dynamicType(type: kotlin.reflect.KClass<Metrics>) { canvas {}.source(type) }
suspend fun dynamicKey(key: CanvasKey<Metrics>) { canvas {}.source(key) }
suspend fun dynamicOptionalKey(key: CanvasKey<Metrics>) { canvas {}.sourceOr(key) }
suspend fun dynamicQualifier(q: String) { canvas {}.create().source<Metrics>(q) }
suspend fun dynamicRegistration(q: String) { canvas { single<Metrics>(q) { Metrics() } }.source<Metrics>() }
suspend fun dynamicRegistrationKey(key: CanvasKey<Metrics>) { canvas { single(key) { Metrics() } } }
suspend fun dynamicPaintKey(key: CanvasKey<Metrics>) { canvas { single<String> { paint(key); "ok" } } }
suspend fun dynamicKeyCapture(key: CanvasKey<Metrics>) { val tile = singleTile { source(key); "ok" }; canvas {}.create().compose(tile) }
suspend fun dynamicConstructedKeyCapture(qualifier: String) { val key = CanvasKey(Metrics::class, qualifier); val tile = singleTile { source(key); "ok" }; canvas {}.create().compose(tile) }