---
name: codebase-design
description: Design or materially restructure a Mosaic module, interface, or ownership model, especially foundational or public behavior. Work on the change at hand; do not perform a repository architecture audit or redesign routine helpers.
---

# Codebase design

Perfect the shape of heavily leveraged things. Keep everything else clean and
coherent. Apply this skill to the requested design and its affected collaborators;
do not scan the repository for unrelated deepening opportunities.

## Spend design effort where it pays back

A module can be a function, class, package, runtime subsystem, or compiler
subsystem. A deep module gives callers substantial behavior through an interface
that is easy to understand. Depth is leverage at the interface, not implementation
size or a ratio of lines of code. The interface includes invariants, ordering,
failure modes, lifecycle, configuration, and concurrency guarantees as well as
types and methods. Hiding necessary complexity should reduce what callers must
know, without concealing behavior they need to use the module correctly.

In Mosaic, depth has particular leverage in:

- Tile / MultiTile public behavior and request-scoped execution;
- batching, caching, deduplication, cancellation, and concurrency ownership;
- Canvas dependency, configuration, and resource ownership;
- instrumentation/tracing seams and their failure isolation;
- compiler/runtime semantic correspondence;
- testing abstractions, the public DSL, and extension/SPIs.

Not every helper, fixture, benchmark script, Gradle helper, example, or internal
utility needs architectural redesign. A small direct implementation can be the
right final shape. Use these concepts when they clarify a decision; keep Mosaic's
normal vocabulary, including public API, SPI, and Kotlin `interface`.

## Shape the work in front of you

1. State the behavior and constraints the changed module must support. Read its
   actual callers, affected collaborators, and relevant contract tests. Identify
   who owns state, lifecycle transitions, cancellation, and failure handling.
2. Prefer one authoritative owner for each behavioral rule. Improve locality:
   a change to that rule should concentrate in its owner and verification, rather
   than require synchronized edits across callers or parallel implementations.
   Optional instrumentation should collaborate through a narrow protocol rather
   than duplicate scheduling, caching, or application completion ownership.
3. Apply the deletion test to a proposed or existing abstraction. If removing it
   spreads necessary complexity into many callers, it is probably earning its
   keep. If removal eliminates complexity entirely, it may be accidental
   indirection. Judge the resulting caller burden, not the number of files.
4. Put seams where behavior actually needs to vary. An adapter supplies a concrete
   implementation at a seam. One adapter suggests a hypothetical seam; two
   independently justified adapters suggest a real one. Do not invent a second
   implementation to justify an abstraction. A real test substitution can justify
   an internal seam without making it an extension point for library consumers.
5. For consequential public interfaces or ownership models, design it twice:
   sketch at least two substantially different shapes before committing. Compare
   typical caller code, hidden complexity, ownership, invariants, actual
   variability, and compatibility costs. Choose the simplest shape that meets the
   requirements; a sketch and comparison usually suffice without implementing
   alternatives or adding a generic framework.

Internal seams can improve locality and testing. They do not automatically belong
in the public API or SPI. Expose an extension point only for a supported consumer
need, with deliberate ownership and stability obligations; hypothetical future
implementations are insufficient. Canvas and test support should not require
speculative DI/configuration abstractions or a parallel execution engine.

For public changes, assess Kotlin source compatibility, JVM binary compatibility,
and SPI stability against the published contract and compatibility coverage.
Absence of repository callers does not establish safety. Represent intentional
breaks in compatibility/release handling rather than silently retaining cruft.
When runtime semantics change, check affected compiler correspondence and the
[public API matrix](../../../mosaic-compiler-plugin/src/test/PUBLIC_API_MATRIX.md),
preserving deliberate analysis scope and conservative unknown boundaries.

## Verify the durable surface and stop

Use the interface as the durable test surface: verify observable results and
ownership guarantees instead of mirroring internal class structure. When a
cluster genuinely becomes a deeper module, move its behavioral coverage to the
stronger interface and remove superseded shallow-module tests once that coverage
exists. Retain focused internal tests when they decisively prove an invariant
that broader tests cannot, including concurrency and IR extraction fidelity;
deepening is not grounds for deleting unique regression coverage.

End the design pass when ownership, interface, invariants, and expected variability
are coherent and the relevant behavior can be verified. Another imaginable
abstraction is not a reason to continue. Do not add speculative seams, generic
frameworks for one feature, unrelated cleanup, or codebase-wide refactors. For a
completed substantial implementation, [zero-tech-debt](../zero-tech-debt/SKILL.md)
provides a focused check for artifacts of the implementation history.

Conceptual source: Matt Pocock's
[codebase-design](https://github.com/mattpocock/skills/tree/main/skills/engineering/codebase-design),
including its deepening and design-it-twice companions; adapted for Mosaic.
