---
title: 'Choose a batching strategy'
description: 'Use bulk, per-key, or chunked fetching without changing consumers.'
---

All three keyed factories return a MultiTile. Callers use the same [composition API](/concepts/multitile/), regardless of how your backend fetches.

| Factory                             | Fetch block receives   | Choose it when                        |
| ----------------------------------- | ---------------------- | ------------------------------------- |
| `multiTile<K, V>`                   | `Set<K>`               | Your service accepts a bulk request   |
| `perKeyTile<K, V>`                  | One `K` per invocation | Your service fetches individual keys  |
| `chunkedMultiTile<K, V>(batchSize)` | `List<K>` per chunk    | Your bulk service limits request size |

## Use a bulk service

```kotlin
val ProductsByIdTile by multiTile<String, Product> { ids ->
  ProductService.getProducts(ids.toList())
}
```

The fetch block returns a map. Every requested key must have a non-null value; missing or null results fail that key with `NoSuchElementException`.

## Fetch individual keys

```kotlin
val ProductsByIdTile by perKeyTile<String, Product> { id ->
  ProductService.getProducts(listOf(id)).getValue(id)
}
```

Distinct keys fetch concurrently. The MultiTile still provides equal-key reuse within a Mosaic. Use a dedicated single-key service operation when available; the excerpt adapts the shared example's bulk service for demonstration.

## Limit batch size

```kotlin
val ProductsByIdTile by chunkedMultiTile<String, Product>(50) { ids ->
  ProductService.getProducts(ids)
}
```

Chunks run concurrently. A batch size limits keys **per request**, not request rate or concurrent requests. Apply backend concurrency limits in the service boundary if needed.

These snippets use `org.buildmosaic.core.*` and the shared order example's product model/service. Each is an alternative declaration, not three declarations to put in one scope.

## Separate reuse from batch shape

All strategies reuse the same MultiTile instance and equal keys within one Mosaic. Pending uncached keys may combine before scheduled execution begins, without an intentional wait. A started batch is fixed. Later callers may form another batch, and batches can execute concurrently.

Choose a strategy based on your backend API. Do not make response logic depend on how many batches happen to form. The [performance page](/reference/performance/) includes observed overlapping-key behavior, with its scheduling limitations.
