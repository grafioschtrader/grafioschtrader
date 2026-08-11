---
name: update-user-manual
description: Update the gt-user-manual project from grafioschtrader context. Use when the user wants to carry over context from grafioschtrader to create or update documentation in gt-user-manual. Triggers on phrases like "update the manual", "document this in the user manual", "add this to gt-user-manual", or when the user explicitly asks to work on gt-user-manual from a grafioschtrader session.
---

# Update GT User Manual

## Setup — Ensure Access to gt-user-manual

The gt-user-manual project must be accessible before making any changes. If it is not already added to the current session, run:
```
/add-dir ../gt-user-manual
```

**Adjust the path if your local setup differs.** The default assumes both projects are sibling directories:
```
parent/
+-- grafioschtrader/
+-- gt-user-manual/
```

If the directory is already available (check by listing its files), skip this step.

## Workflow

1. **Ensure gt-user-manual is accessible** via `/add-dir` as described above.

2. **Read the gt-user-manual CLAUDE.md first.** Before making any changes, always read and follow the instructions in the `CLAUDE.md` file located in the gt-user-manual project root. These instructions define the documentation structure, style, and conventions. Treat them as authoritative for all manual content.

3. **Carry over context.** Preserve the technical context from the current grafioschtrader session. The documentation should accurately reflect the feature, behavior, or concept being discussed.

4. **Use Mermaid diagrams for clarity.** When a concept, workflow, or relationship between components would be easier to understand visually, include a Mermaid diagram. Prefer diagrams for:
   - Workflows and process flows (e.g., how a transaction is processed)
   - Entity relationships (e.g., how domain objects relate to each other)
   - State transitions (e.g., lifecycle of an order)
   - Architecture overviews (e.g., frontend/backend interaction)
   - Decision trees (e.g., validation logic)

   Do **not** add diagrams purely for decoration — only when they genuinely aid user understanding.

5. **Write for end users.** The gt-user-manual targets users of GrafioschTrader, not developers. Explain *what* and *why* rather than implementation details, unless the CLAUDE.md in gt-user-manual specifies otherwise.

## Mermaid Diagram Guidelines

- Use `graph TD` or `graph LR` for workflows and hierarchies
- Use `sequenceDiagram` for interactions between components
- Use `classDiagram` for entity relationships when appropriate
- Use `stateDiagram-v2` for state transitions
- Keep diagrams focused — split complex diagrams into smaller ones
- Label edges clearly so the diagram is self-explanatory
- Use consistent naming aligned with GrafioschTrader terminology

## Example Usage

When the user says something like:
> "Document the watchlist feature in the user manual"

Then:
1. Run `/add-dir ../gt-user-manual` if not already accessible
2. Read `CLAUDE.md` in gt-user-manual
3. Understand the watchlist feature from the grafioschtrader context
4. Write or update the relevant section in gt-user-manual
5. Include a Mermaid diagram if the feature involves a workflow, relationships, or states that benefit from visualization