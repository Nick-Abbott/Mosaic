---
name: technical-docs
description: Write or edit Mosaic guides, tutorials, how-tos, reference, and concept or architecture explanations under docs/ and in module documentation. Use public-docs for adoption, positioning, project overviews, and public result summaries.
---

# Technical documentation

Start with the reader's intent and choose a home for the information before
drafting. This skill owns learning, performing a task, looking up facts, and
understanding concepts or internals, including module READMEs serving those goals.
Use [public-docs](../public-docs/SKILL.md) for adoption, positioning, first
impressions, and public proof, even when that content lives outside the root
README. Apply both skills to changes spanning both purposes, keeping each
document appropriate to its audience.

## Classify the content

Choose the primary purpose before writing; these are reader needs, not required
directory names or a reason to reorganize existing docs.

| Purpose | Reader intent | Writing approach |
| --- | --- | --- |
| Tutorial | Learn Mosaic by following a guided path. | Give one clear path with prerequisites, working examples, and visible outcomes. |
| How-to | Accomplish a known task. | Start with the goal, give direct steps, and show how to verify success. |
| Reference | Look up precise facts. | Be terse and complete for the stated scope: APIs, options, defaults, constraints, commands. |
| Explanation | Understand concepts, design, or tradeoffs. | Explain why and how ideas connect; keep rationale here rather than inside procedural steps. |

Avoid mixing these styles unnecessarily. Link to supporting reference or
explanation instead of turning a tutorial into an exhaustive manual. An existing
mixed guide can keep distinct sections; a small addition does not require a split.

## Find the narrowest useful home

1. Read applicable AGENTS.md instructions, the root README for orientation, the
   entire target document, and relevant neighboring module READMEs and docs.
   Notice their structure, naming, links, terminology, tone, and example style.
2. Identify the audience, what they already know, and what they need to learn,
   do, look up, or understand. Distinguish library users from maintainers.
3. Search existing documentation for the concept and related API names. Extend
   an existing section when it meets the need; avoid duplicate explanations.
4. Before creating a page or section, answer: “Why does this belong here rather
   than in README.md or another existing page?” Preserve current organization
   and naming; do not impose a new documentation taxonomy or map.
5. Check adjacent headings for a coherent sequence. Add cross-links where they
   materially improve discovery, keeping a brief summary at broader entry points.

Mosaic already places runtime usage in the
[core guide](../../../mosaic-core/README.md), testing in the
[test guide](../../../mosaic-test/README.md), and analysis setup and constraints in
the [Gradle plugin guide](../../../mosaic-gradle-plugin/README.md). Compiler and
analysis-core READMEs explain extraction and contract semantics. Use
[docs/performance.md](../../../docs/performance.md) for JMH operations and
[performance/README.md](../../../performance/README.md) for application benchmark
results and methodology. `docs/` also contains release maintenance instructions;
location alone does not make a page suitable for library users.

## Write and verify

Make the smallest edit that serves the chosen purpose. Match the target's style
and Mosaic terminology, use short paragraphs and useful examples, and link to
deeper material instead of repeating it. Keep maintainer/process details in
maintenance documentation and out of user guides unless needed for the task.

Verify code, API names, commands, versions, defaults, and constraints against
source, tests, examples, and build configuration. Examples should compile or
faithfully reflect the current API with necessary context made clear. Keep
runtime requirements distinct from optional analysis restrictions. Run changed
runnable examples when practical and report verification limits honestly.

Reread the complete edited page for reader intent, placement, flow, and duplicated
content. Resolve added or changed links and anchors, review the diff for unrelated
rewrites, and run `git diff --check`.
