---
name: writing-for-agents
description: Create or materially edit Mosaic AGENTS.md, .agents/skills/** or other guidance primarily for coding agents. Not a general documentation-writing skill.
---

# Writing for agents

Use for creating or materially editing `AGENTS.md`, `.agents/skills/**`, or other
text primarily instructing a coding agent. A document does not qualify merely
because an agent reads it. Read the whole target, applicable
[AGENTS.md](../../../AGENTS.md) and the relevant neighboring skills/docs first.

- Identify the recurring decision this guidance changes and its authoritative home.
  Keep `AGENTS.md` primarily short navigation and shared guardrails. Put conditional
  workflows in focused skills and topic detail behind relevant document pointers.
- Write context pointers as “When X, read/use Y for Z.” The condition controls
  whether the agent follows the link; fix a weak trigger before inlining its target.
  Skill descriptions need the same precision. Routine edits should not trigger an
  audit, broad cleanup or a workflow intended only for explicit requests.
- Balance always-loaded context against the human effort of finding the right tool.
  Keep common steps together; disclose branch-specific references only when needed.
  Split unrelated responsibilities into focused skills only when each needs its own
  trigger, not simply to create more files.
- Give each instruction one owner. Link to existing rules and cheaply discoverable
  facts in build files, scripts, CLI help or repository structure. Write the missing
  judgment, constraint or rationale rather than caching those facts in prose.
- Specify observable completion criteria and authorization boundaries. Separate a
  reusable rule from one task's chronology. One past agent mistake alone does not
  justify another standing rule: prefer an existing pointer or deterministic check
  for a mechanical problem, and prose for genuine judgment/context.

Route human-facing adoption and technical documentation to
[public-docs](../public-docs/SKILL.md) and [technical-docs](../technical-docs/SKILL.md).
Keep agent instructions distinct from public API/compatibility contracts, module
guides and Gradle configuration; link to the owner instead of maintaining a second
version matrix or build-command catalog. Task history belongs in the PR; release
history belongs in release material.

Finish by rereading the guidance with its neighboring skills: remove duplication,
stale instructions and sentences that do not change behavior; check narrow triggers,
completion criteria and local links. Validate skill frontmatter and invocation
metadata with available tooling. For explicit-only skills, set
`policy.allow_implicit_invocation: false` in `agents/openai.yaml` and state the
explicit trigger in the skill; leave ordinary model-invoked skills discoverable.

Conceptual source: Matt Pocock's [writing-for-agents](https://github.com/mattpocock/skills/tree/v1.3.1/skills/productivity/writing-for-agents)
(v1.3.1); rewritten for Mosaic's repository guidance.
