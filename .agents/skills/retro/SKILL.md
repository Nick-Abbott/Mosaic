---
name: retro
description: Only on explicit user request, review a coding session for improvements to the Mosaic agent environment. Report recommendations without edits.
---

# Agent environment retrospective

Run only when the user explicitly requests a retrospective of a coding session or
task. Review the agent environment, not the product implementation. Do not invoke
this as routine finalization, a codebase cleanup or an architecture review.

1. Identify the specified session/task; default to the current session when none
   is named. Read available primary evidence (conversation, tool outcomes, diffs,
   logs and verification results) within that scope. State evidence gaps rather
   than inventing a session history or searching unrelated private logs.
2. Inspect the relevant guidance and existing scripts/build/CI checks before
   suggesting additions. Look for difficult navigation, repeated information
   gathering, missing log access, costly verification/tool loops, missing or unwired
   deterministic checks, duplicated/stale/ineffective guidance, excess context and
   skills with unclear triggers or responsibilities.
3. Separate one-off agent mistakes from repeatable environment problems. For each
   plausible improvement, cite the session event and the mechanism that made it
   costly or likely to recur. An ignored clear instruction is not by itself proof
   that another instruction would help. Check whether an existing mechanism already
   solves the problem but is hard to discover or is not running.
4. Prefer deterministic automation for mechanical mistakes. Recommend written
   guidance only when judgment or otherwise unavailable context is required; use
   [writing-for-agents](../writing-for-agents/SKILL.md) to assess that recommendation.
   Recommend only access actually needed, with privacy and authorization boundaries.

Check for duplication across [AGENTS.md](../../../AGENTS.md), architecture and
documentation skills, Gradle/build configuration, module guides and public
API/compatibility material. Prefer a pointer to the owning source over another
command list, version table or interpretation of supported behavior.

Present meaningful candidates in plain Markdown/chat, ranked by expected payoff
and severity. For each give the session evidence, recurring mechanism versus
one-off uncertainty, proposed environment change, likely benefit and effort/risk.
Name the best next improvement, or say none is supported. Stop after recommendations.
Do not edit files, install tools, change access, create issues or implement checks
as part of the retro; the user can separately authorize a candidate.

Conceptual source: Matt Pocock's [retro](https://github.com/mattpocock/skills/tree/v1.3.1/skills/engineering/retro)
(v1.3.1); rewritten for Mosaic's repository guidance.
