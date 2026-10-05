---
name: diagnosing-bugs
description: Diagnose reported Mosaic failures, intermittent bugs or performance regressions with a decisive reproduction and ranked hypotheses. Use for debugging, not routine verification.
---

# Diagnosing bugs

Read [AGENTS.md](../../../AGENTS.md) for test ownership and required submission
checks, then the affected module's current guide and contract tests. Select the
cheapest layer that decisively detects the failure:

- Evaluator/model/codec semantics: `mosaic-analysis-core` tests.
- IR extraction fidelity: `mosaic-compiler-plugin` tests/direct K2 fixtures;
  extend a relevant fixture before adding another compiler invocation.
- Gradle wiring, incremental/clean equivalence, packaging and publication:
  `mosaic-gradle-plugin` TestKit tests with disposable projects/local repositories.
- Execution, Canvas, cancellation or tracing: the appropriate runtime module's tests.
- Performance: the relevant [JMH or application harness](../../../docs/performance.md).

Focused internal invariant tests are legitimate, including extraction, concurrency,
cancellation and compiler/runtime correspondence. A public-interface-only rule
must not displace them. Preserve conservative unknowns and the
[public API matrix](../../../mosaic-compiler-plugin/src/test/PUBLIC_API_MATRIX.md);
an unsupported analysis path is not automatically a bug. Reproduction does not
authorize publishing artifacts or changing external services. Exclude credentials
and private consumer data from fixtures, logs and shared evidence.

## Diagnosis loop

1. Define the reported symptom and expected behavior, then build a runnable check
   that can fail on that exact symptom. Read enough code to construct it; prefer an
   existing test, fixture, command or small disposable harness. A compiler fixture,
   TestKit build or concurrency harness can be the cheapest decisive loop even
   when it takes minutes.
   Tight means minimal setup and a decisive signal, not a seconds-only deadline.
   Narrow the target and reuse safe setup without skipping what detects the bug.
   Record the command and observed failure, distinguishing environment/setup errors
   and nearby defects from the reported bug. If reproduction remains unavailable,
   report attempts and missing evidence/access; label theories as unverified and
   request what is needed instead of claiming a cause or applying a speculative fix.
2. Reproduce repeatedly and minimize inputs, state and steps without losing the
   failure. Stop shrinking when further reduction would lose the relevant boundary
   or cost more than it clarifies. For intermittent failures, use bounded repetition
   or stress and record attempts/failures, seed and scheduling conditions. Control
   interleavings with existing clocks, schedulers, latches or completion signals;
   do not substitute arbitrary sleeps for causal synchronization.
3. Generate several ranked, falsifiable hypotheses before choosing a fix. State
   each prediction and the observation that would rule it out. Share the shortlist
   without blocking on acknowledgement. Differentially compare known-good and bad
   revisions, inputs or configurations; use bisection when a reliable discriminator
   and known endpoints exist, in an isolated checkout with disposable state.
4. Probe only to distinguish those predictions, changing one variable at a time.
   Prefer targeted debugger inspection or bounded diagnostics. Tag temporary probes
   and logs with a unique marker such as `DEBUG-<task>` so they can be found and
   removed. For slowness, establish a comparable baseline and measure/profile before
   changing code; compare the same workload and relevant runtime conditions afterward.
5. Choose a correction for the demonstrated cause within the requested scope.
   Add or extend regression coverage at the existing owning seam only when it
   protects a distinct failure or invariant not already caught.
   When retaining coverage, demonstrate failure for the intended reason before the
   fix when practical. Apply the fix and verify success. Report why coverage suffices
   or what seam/evidence is missing; neither finding authorizes an architecture audit.
6. Re-run the original, unminimized reproduction and the applicable repository checks.
   For intermittent bugs, report post-fix repetitions and remaining uncertainty;
   one passing attempt proves little. Remove tagged probes and throwaway harnesses,
   search for the marker, and inspect the final diff. Report the cause, actual
   verification and limitations in the PR/task response, not a new permanent report.

Conceptual source: Matt Pocock's [diagnosing-bugs](https://github.com/mattpocock/skills/tree/v1.3.1/skills/engineering/diagnosing-bugs)
(v1.3.1); rewritten for Mosaic's repository guidance.
