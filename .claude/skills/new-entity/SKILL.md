---
name: new-entity
description: What a new JPA entity or table owes the rest of Grafioschtrader beyond persisting — its ExportDefinition for "export my data" and for account deletion, and its limit key so a user cannot grow the table without bound. Use whenever an entity class or a table is added, when an existing one gains a REST write path, and when a limit has to be registered, seeded or raised. Triggers on "new entity", "new table", "add an entity", "ExportDefinition", "export my data", "delete account", "EntityLimit", "limit key", "daily limit", "MAX limit". Not for a Flyway migration that only alters an existing table without adding an entity.
---

The full skill lives at `.agents/skills/new-entity/SKILL.md`, so that Claude Code and OpenAI Codex read
the same file. **Read it now with the Read tool and follow it in full** before doing anything else — this
stub contains no instructions of its own.
