---
title: 'Use your HTTP framework'
description: 'Run the shared order response behind Spring Boot, Ktor, or Micronaut.'
---

Mosaic composes application logic; your HTTP framework owns routing, serialization, status codes, and server lifecycle. The repository has runnable Spring Boot, Ktor, and Micronaut examples using the same order Tile library.

## Run an example

Clone [BuildMosaic/Mosaic](https://github.com/BuildMosaic/Mosaic), use JDK 21, and run one application from the repository root:

```bash
./gradlew -p examples :spring-example:bootRun
./gradlew -p examples :ktor-example:run
./gradlew -p examples :micronaut-example:run
```

Choose one command; each example serves on port 8080. Request an order:

```bash
curl http://localhost:8080/orders/order-1
curl http://localhost:8080/orders/order-1/total
```

The services are in-memory example implementations. Inspect the [models and services](https://github.com/BuildMosaic/Mosaic/tree/main/examples/tile-library/src/main/kotlin/org/buildmosaic/library) for sample data and response shape.

## Keep the request boundary small

Inside a suspending handler:

```kotlin
val page = applicationCanvas.withLayer {
  single(OrderKey) { orderId }
}.withMosaic { compose(OrderPageTile) }
```

The example's declarations and `org.buildmosaic.core.injection.withMosaic` supply these names. The Canvas binds the request input; the fresh Mosaic scopes Tile reuse to that request. Your handler returns or serializes `page`.

## Pick the example for your framework

| Example                                                                                 | Where to look                                         |
| --------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| [Spring Boot](https://github.com/BuildMosaic/Mosaic/tree/main/examples/spring-example)  | Canvas configuration bean and order controllers       |
| [Ktor](https://github.com/BuildMosaic/Mosaic/tree/main/examples/ktor-example)           | Coroutine routes and StatusPages error mapping        |
| [Micronaut](https://github.com/BuildMosaic/Mosaic/tree/main/examples/micronaut-example) | Canvas factory, dependency injection, and controllers |

The Micronaut example bridges controller methods using `runBlocking`; use your framework's supported suspending boundary when designing a production handler. The examples show integration, rather than installing a published Mosaic adapter. `mosaic-integrations` exists in the project organization; no official adapter is claimed as available.

## Own application resources

If the application Canvas constructs `AutoCloseable` services, close it during framework shutdown. If a request child owns local resources, scope it explicitly around composition. See [resource ownership](/guides/resources/).

Use [OpenTelemetry tracing](/guides/tracing/) with your framework's existing request context propagation. Test the response composition separately with [mosaic-test](/guides/testing/); keep framework-specific HTTP assertions in your application tests.
