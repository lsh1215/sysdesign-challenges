# Codex Project Context

This directory contains the Codex-facing migration of the previous Claude Code project context.

- `../AGENTS.md` is the primary project instruction file Codex should read first.
- `skills/` contains the migrated project skills from `.claude/skills/`.
- Claude-specific hooks, logs, and sub-agent definitions were not migrated because Codex does not execute Claude hook configuration.

When a task matches a skill area, read the relevant `skills/<name>/SKILL.md` before editing.
