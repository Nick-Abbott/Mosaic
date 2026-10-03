---
name: zero-tech-debt
description: After a substantial Mosaic feature or refactor is behaviorally correct, or when reviewing it before finalization, reshape artifacts of its implementation history into a coherent end state. Keep cleanup within the changed area and assess published compatibility deliberately.
---

# Zero tech debt

Ask: **If these requirements had existed before this work started, is this the
implementation we would have designed?**

Apply this pass after a non-trivial change reaches behavioral correctness, or
while reviewing it before finalization. Its scope is the requested change and
the collaborators that must change for that area to be coherent. It is not a
repository audit, a requirement for routine edits, or a promise of perfection.

## Review from the intended end state

1. State the intended end state in one or two sentences: supported behavior,
   durable interface, and owners of state and lifecycle. Include real
   compatibility obligations in that end state.
2. Compare the current change with that model. Trace affected callers and
   collaborators; identify rules with multiple owners or paths that exist only
   because of an intermediate implementation.
3. Identify artifacts of patch history: transitional wrappers, parallel behavior
   implementations, history-driven mode flags, duplicated ownership, obsolete
   compatibility scaffolding/fallbacks, types left from an earlier architecture,
   historical names, tests coupled to removed internals, experimental
   abstractions, and comments/docstrings narrating the work's history. A flag or
   wrapper serving a current requirement is not debt merely because it exists.
4. Remove or reshape those artifacts when it materially improves coherence.
   Optimize for the code that should exist, not merely the smallest diff from
   the old shape. Consolidate shared rules under one authoritative owner;
   delete dead paths instead of beautifying them. Prefer names and comments
   describing current intent, invariants, and responsibilities. Preserve useful
   rationale for constraints; place migration history in release material.
5. Verify behavior through the resulting durable interface and ownership model,
   including guarantees affected by deletions. Replace obsolete structural tests
   with behavioral coverage before removing them; retain unique regressions.
   Follow [AGENTS.md](../../../AGENTS.md) for the cheapest decisive test layer and
   required checks. Where affected, verify cancellation/concurrency, Canvas
   lifetime/configuration, instrumentation isolation, compiler/runtime
   correspondence, and consumer compatibility.
6. Stop when the requested change has a coherent final shape, intentional
   compatibility decisions, and verified behavior. Another possible abstraction
   or unrelated imperfection is not a reason to expand the work.

## Distinguish internal cleanup from published compatibility

- **Internal implementation compatibility:** search actual callers in the changed
  area and its integrations, including tests and tooling. Remove obsolete modes,
  wrappers, and fallback paths aggressively when no genuine requirement remains.
- **Published/public compatibility:** Mosaic is a library with external consumers.
  No in-repository or GitHub callers does not mean a public API is unused. Assess
  Kotlin source compatibility, JVM binary compatibility, SPI stability, and
  supported migration from the published contract, compatibility tests, and the
  [public API matrix](../../../mosaic-compiler-plugin/src/test/PUBLIC_API_MATRIX.md).
  The analysis matrix describes semantic coverage; it does not by itself prove
  JVM binary compatibility. Consider external consumer compilation and binary
  coverage appropriate to the affected contract.

Do not preserve public compatibility automatically. If a deliberate breaking
change is the right end state within the requested scope, surface its impact
explicitly and represent it in compatibility tests/matrix and release/migration
handling. Retain only scaffolding required by that supported migration; do not
silently keep architectural cruft or silently break consumers.

## Keep the pass bounded

For Mosaic, a coherent result favors one execution implementation and explicit
ownership of request state, caching, batching, completion, and cancellation.
Optional instrumentation can collaborate narrowly without owning parallel runtime
machinery. Dependency/configuration and test abstractions should have current
responsibilities rather than exist for hypothetical future implementations.

Do not invent a generic framework for one feature or redesign untouched helpers,
examples, scripts, or subsystems. Expand the diff only as needed to make the
changed area coherent. If the pass exposes a consequential interface or ownership
decision, use [codebase-design](../codebase-design/SKILL.md) for that decision;
do not turn finalization into an open-ended architecture exercise.

Conceptual source: jnsahaj's
[zero-tech-debt](https://github.com/jnsahaj/skills/tree/main/skills/zero-tech-debt),
adapted for Mosaic's framework ownership and published compatibility obligations.
