# Mosaic OpenTelemetry

Trace Mosaic executions through your application's existing OpenTelemetry
instance. This optional runtime module uses the OpenTelemetry API and its official
[Kotlin coroutine context integration](https://github.com/open-telemetry/opentelemetry-java/tree/v1.66.0/extensions/kotlin).

## Installation and setup

This module is available from source. Publish matching runtime modules to your
local Maven repository from the repository root:

```bash
./gradlew :mosaic-core:publishToMavenLocal :mosaic-opentelemetry:publishToMavenLocal \
  -Pmosaic.version=0.6.0-SNAPSHOT
```

Then add the locally published version to your Kotlin/JVM application:

```kotlin
repositories {
  mavenLocal()
  mavenCentral()
}

dependencies {
  implementation("org.buildmosaic:mosaic-opentelemetry:0.6.0-SNAPSHOT")
}
```

The module includes `mosaic-core`, OpenTelemetry API, and the Kotlin coroutine
extension. It requires Java 17 or later; see the
[core installation requirements](../mosaic-core/README.md#requirements-and-installation)
for Kotlin consumer compatibility. The [Mosaic BOM](../mosaic-bom/README.md) aligns
Mosaic runtime versions when published with the same source version.

Provide the `OpenTelemetry` instance your application already uses, and reuse
the instrumentation across request-scoped Mosaics:

```kotlin
import org.buildmosaic.opentelemetry.OpenTelemetryMosaicInstrumentation
import org.buildmosaic.opentelemetry.create

// openTelemetry and requestCanvas are supplied by your application.
val instrumentation = OpenTelemetryMosaicInstrumentation(openTelemetry)
val mosaic = requestCanvas.create(instrumentation)
```

The optional second `create` argument selects the Tile coroutine dispatcher;
it defaults to `Dispatchers.Default`. Ordinary `Canvas.create()` from
`org.buildmosaic.core.injection` continues to create an uninstrumented Mosaic.
See the [core guide](../mosaic-core/README.md) for Canvas and Tile usage.

Your application owns the SDK/provider, sampler, resource, processors, exporters,
and vendor/backend configuration. This module installs none of them, changes no
global configuration, and has no SDK or exporter dependency. If your application
uses `GlobalOpenTelemetry`, pass `GlobalOpenTelemetry.get()` explicitly.

## Spans and links

Each actual SingleTile block or MultiTile batch gets one `INTERNAL` span. Cache
hits, in-flight joins, individual keys, and compose calls do not create spans.
The instrumentation scope is `org.buildmosaic.mosaic-opentelemetry`.

The captured caller's current OpenTelemetry context is the parent. Nested Tile
work and ordinary HTTP, database, or RPC instrumentation inside Tile work see
the Mosaic span as current, including across suspension and dispatcher changes.
Propagate your server/request context into the coroutine that calls Mosaic using
your framework's OpenTelemetry integration or `Context.asContextElement()`.
Applications that call the Kotlin context extension directly should also declare
`io.opentelemetry:opentelemetry-extension-kotlin` for compile-time access and align
it with their application's OpenTelemetry dependencies.

A runtime Tile name becomes the span name. Delegated properties capture their
first bound name (`val products by multiTile { ... }`); aliases preserve it.
Unnamed Tiles use `Mosaic single` or `Mosaic multi`. Names never include keys,
object hashes, lambda classes, or source locations. The adapter does not repeat
the name in a `mosaic.tile.name` attribute.

| Attribute | Emitted value |
| --- | --- |
| `mosaic.tile.kind` | `single` or `multi` |
| `mosaic.multitile.batch_size` | Core's exact count of distinct newly fetched keys; MultiTile only |
| `error.type` | Core's safe JVM exception class name; failure only |
| `mosaic.execution.cancelled` | `true` for cancelled executions |

A failure sets status `ERROR` with no description or exception event.
Cancellation keeps status `UNSET` and does not emit `error.type`.

When a Mosaic execution consumes an existing producer reference, its span links
to that producer with `mosaic.link.type = dependency`. This covers in-flight and
completed SingleTile reuse and cached MultiTile values, including values from
several earlier batches. Repeated references to the same producer span collapse.
External consumers without a current Mosaic execution do not own dependency links.

The first captured contributor to a MultiTile batch supplies its ordinary parent.
Other distinct, valid contributor span contexts become links at span creation,
with `mosaic.link.type = contributor`. Cached keys add no contributors and do not
increase batch size. Scheduler-created separate batches keep separate spans,
parents, contributor links, and batch sizes.

If a caller completes before a retained producer reference resolves, core removes
its execution context immediately and the adapter saves the actual completion
time. Only telemetry finalization waits for publication or abandonment. A
published producer adds its dependency link; abandonment adds none. The span ends
with the saved timestamp. Application results do not wait for telemetry.

## Privacy and limits

Installing this adapter does not automatically export application data. It never
receives or emits MultiTile keys, Tile results, Canvas values, application
exceptions, messages, stacks, causes, request IDs, user IDs, or arbitrary objects.
Applications can deliberately add attributes/events with ordinary OpenTelemetry
APIs while a Tile span is current; the application controls that data.

Each limit defaults to 64 and accepts nonnegative values, including zero:

```kotlin
val instrumentation = OpenTelemetryMosaicInstrumentation(
  openTelemetry,
  maxDependencyLinks = 64,
  maxContributorLinks = 64,
  maxPendingDependencies = 64,
)
```

| Limit | Scope |
| --- | --- |
| `maxDependencyLinks` | Dependency links per execution |
| `maxContributorLinks` | Contributor links per batch, excluding its parent |
| `maxPendingDependencies` | Active unresolved producer subscriptions per execution |

Links deduplicate by trace ID and span ID. Only the first contributor retains a
full caller context; other retained contributors and cache provenance hold span
identities. Resolved subscriptions release their state. When dependency link
capacity is exhausted, remaining subscriptions close and no longer delay
finalization. Truncated references are not retained for later deduplication.

The adapter emits `true` on `mosaic.links.dependency.truncated`,
`mosaic.links.contributor.truncated`, or `mosaic.dependencies.pending.truncated`
when the corresponding bound discards work. These flags have no application
payload. Your SDK's own attribute/link limits may reduce exported metadata further.

## Sampling and adapter failures

Sampling and exporting remain under your provider's control. With no adapter,
Mosaic uses its ordinary runtime path. A configured no-op or unsampled adapter
still propagates the current span context for downstream parentage; non-recording
executions skip dependency-link collections and subscriptions. Contributor links
are bounded and supplied at span creation so application samplers can use them.
Recording spans collect the metadata and links described above.
Unsampled SDK spans retain their own producer identities. A no-op span that borrows
its parent's identity propagates that context without publishing the parent as a
Mosaic producer link target.

Adapter/provider failures are isolated from Tile results, caches, and batching.
An optional `onCallbackFailure` handler receives adapter-side failures only:

```kotlin
val instrumentation = OpenTelemetryMosaicInstrumentation(
  openTelemetry,
  onCallbackFailure = { failure -> applicationTelemetryDiagnostics(failure) },
)
```

The handler defaults to doing nothing, must return promptly, and its failures
are also contained. Application Tile failures never reach this diagnostic hook.
