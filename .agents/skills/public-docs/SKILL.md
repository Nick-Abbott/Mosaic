---
name: public-docs
description: Write or edit Mosaic's root README, landing and overview docs, feature and performance summaries, installation and quick-start copy, and comparisons for developers evaluating adoption. Use technical-docs for learning, tasks, reference, and concepts.
---

# Public documentation

Treat the README as a product page for developers, not a project diary or internal
wiki. Help readers understand what Mosaic is, why to use it, how it works, what
using it looks like, its costs and tradeoffs, and where to learn more. Factual
marketing is welcome; every claim must hold up against repository evidence.

Route by reader purpose: this skill owns adoption, positioning, overview, first
impressions, and public proof. Use [technical-docs](../technical-docs/SKILL.md) for
learning, completing tasks, lookup, or understanding concepts and internals.
For changes spanning both audiences, apply each skill to the relevant document
or section; the root README normally summarizes and links to deeper material.

## Decide before drafting

1. Read applicable AGENTS.md instructions and the entire target document.
   Inspect heading order, narrative flow, tone, emoji/icons, example style,
   terminology, and depth. For a new page, read its parent and closest peers.
2. Read nearby public docs and follow relevant links to see what is already
   explained elsewhere. Check the supporting code, tests, build configuration,
   or results before making claims.
3. Identify the intended reader and the decision or task this addition supports.
   Decide whether it belongs in the root README at all, an existing deeper page,
   or maintainer material. Choose the location before writing prose.
4. Read the headings and content immediately before and after the proposed
   insertion. Does the sequence still tell a coherent story? Avoid interrupting
   related sections; never append a section just because a task produced facts.
5. Make the smallest useful edit. Restructure more broadly only when the existing
   information architecture is itself the problem, not because one section needs
   work. Do not replace an established README with a generic template.
6. Reread the entire document after editing. Check flow, repetition, links,
   factual support, and whether the diff stays within the requested scope.

## Placement and Mosaic's house style

Use progressive disclosure: the root README offers decision-making summaries
and the strongest examples, then links to a dedicated public guide, which can
link to detailed technical or reference material.

Preserve Mosaic's response-first feature narrative and visual style. The
[root README](../../../README.md) uses emoji and bold section headings with
Kotlin examples; match those conventions when adding a peer section. Follow
each target's own style rather than importing universal heading rules, emoji
bans, mandatory documentation maps, or another project's formatting rules.

Keep installation and first-use copy focused on getting started. Delegate API
detail to module guides and optional analysis setup to the
[Gradle plugin guide](../../../mosaic-gradle-plugin/README.md). Keep the runtime
and optional analysis tooling distinct. Summarize performance for adoption and
link to [application results and methodology](../../../performance/README.md);
[runtime microbenchmarks](../../../docs/performance.md) answer a different question.

Prefer concrete claims, short paragraphs, useful examples, clear hierarchy, and
Mosaic's existing terms (Canvas, Mosaic, Tile, MultiTile). Lead with user value.
Avoid generic AI prose, unsupported adjectives, repeated explanations, and raw
benchmark, test, or tool-output dumps.

## Evidence and audience boundaries

Keep measured fact, interpretation, and marketing summary distinguishable;
the summary must preserve the fact's scope and limitations. For example,
“Mosaic added 48–101 µs of CPU/request in the aggregate benchmark” identifies a
cost and workload; “blazing-fast enterprise-grade performance” supplies neither.
Verify figures against current evidence rather than reusing this example blindly.
Prefer absolute values when percentages magnify a tiny baseline. Keep units,
baseline, workload, and relevant uncertainty clear; CPU cost is not HTTP latency.

Normally omit worktree details, CI/debugging history, failed experiments,
rejected tools, benchmark-generator archaeology, local paths, workstation setup,
internal review process, temporary implementation constraints, and task/PR
chronology. Keep these in PR history, issues, developer/maintenance docs, or
generated local reports unless a library user genuinely needs them. Include
current user-relevant limitations and tradeoffs without narrating how they arose.
