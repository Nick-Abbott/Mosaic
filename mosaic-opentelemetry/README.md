# Mosaic OpenTelemetry

Trace Mosaic executions through your application's existing OpenTelemetry
instance. This optional runtime module uses the OpenTelemetry API and its official
[Kotlin coroutine context integration](https://github.com/open-telemetry/opentelemetry-java/tree/v1.66.0/extensions/kotlin).

## Quick start

Configure tracing once on your application Canvas:

```kotlin
import org.buildmosaic.core.injection.canvas
import org.buildmosaic.core.injection.create
import org.buildmosaic.opentelemetry.tracing

val canvas = canvas {
  tracing()
}
```

`tracing()` discovers the OpenTelemetry instance registered by your Java agent
or global setup. Register it before building the Canvas. If no usable global
tracing provider is registered, Canvas setup fails with instructions to supply one;
discovery does not initialize or change the global provider.

Applications and framework integrations that own an OpenTelemetry instance can
supply it explicitly:

```kotlin
val applicationCanvas = canvas {
  tracing { openTelemetry }
}
```

The block runs once during Canvas setup and bypasses global discovery. Tracing is
inherited through Canvas layers, so request handling uses ordinary `create()`:

```kotlin
// RequestDataKey and requestData are supplied by your application.
val requestCanvas = applicationCanvas.withLayer {
  single(RequestDataKey) { requestData }
}
val mosaic = requestCanvas.create()
```

Configure tracing once in a Canvas hierarchy. A second installation on the same
Canvas or a descendant fails during setup. Canvases without `tracing()` use
Mosaic's ordinary runtime. See the [core guide](../mosaic-core/README.md) for Canvas
and Tile usage.

Your application owns the SDK/provider, sampler, resource, processors, exporters,
vendor/backend configuration, and shutdown. This module installs none of them,
changes no global configuration, and has no SDK or exporter dependency.

## Installation

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

Canvas configures tracing once; each request's Mosaic executes the graph, and each
actual Tile execution has its own span. Code inside a traced Tile can enrich that
execution's span using the standard OpenTelemetry API:

```kotlin
import io.opentelemetry.api.trace.Span
import org.buildmosaic.core.singleTile

val UserTile by singleTile {
  val user = service.loadUser()
  Span.current().setAttribute("app.user.segment", user.segment)
  user
}
```

Here `service` is an application service. `Span.current()` refers to this Tile
execution's span. Nested Tiles get their own current spans; suspension and dispatcher
changes preserve the correct span, and returning from nested work restores the caller's
span. A span is execution-scoped and does not belong in Canvas DI.

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

When a Tile reuses work from an existing execution, its span links to that
execution with `mosaic.link.type = dependency`. This covers in-flight and
completed SingleTile reuse and cached MultiTile values, including values from
several earlier batches. Repeated references to the same producer span collapse.
External consumers without a current Mosaic execution do not own dependency links.

The first captured contributor to a MultiTile batch supplies its ordinary parent.
Other distinct, valid contributor span contexts become links at span creation,
with `mosaic.link.type = contributor`. Cached keys add no contributors and do not
increase batch size. Scheduler-created separate batches keep separate spans,
parents, contributor links, and batch sizes.

If a caller finishes before reused work has a span, Mosaic removes its execution
context immediately and saves the actual completion time. Only telemetry
finalization waits for that work to start or be abandoned. Its span becomes the
dependency link target when available; abandonment adds no link. The caller's
span ends with the saved timestamp. Application results do not wait for telemetry.

## Privacy and limits

Enabling tracing does not automatically export application data. The integration never
receives or emits Tile inputs, MultiTile keys, Tile results, Canvas values, application
exceptions, messages, stacks, causes, request IDs, user IDs, or arbitrary objects.
Adding attributes or events through `Span.current()` is an explicit application
decision, subject to the application's privacy and telemetry policy.

State is bounded to 64 dependency links per execution, 64 contributor links per
batch (excluding its parent), and 64 unresolved dependencies per execution.

Links deduplicate by trace ID and span ID. Resolved dependencies release their
retained state. When dependency link capacity is exhausted, remaining unresolved
dependencies no longer delay telemetry finalization. Discarded dependencies are
not retained for later deduplication.

The adapter emits `true` on `mosaic.links.dependency.truncated`,
`mosaic.links.contributor.truncated`, or `mosaic.dependencies.pending.truncated`
when the corresponding bound discards work. These flags have no application
payload. Your SDK's own attribute/link limits may reduce exported metadata further.

## Sampling and adapter failures

Sampling and exporting remain under your provider's control. Without `tracing()`,
Mosaic uses its ordinary runtime path. A no-op or unsampled provider still
propagates the current span context for downstream parentage; non-recording
executions skip dependency-link collections and subscriptions. Contributor links
are bounded and supplied at span creation so application samplers can use them.
Recording spans collect the metadata and links described above.
Unsampled SDK spans retain their own span identities. A no-op span that borrows
its parent's identity propagates that context without representing the parent as
a Mosaic execution or dependency link target.

Provider failures during execution are isolated from Tile results, caches, and
batching. Application Tile failures expose only the safe error type described
above.
