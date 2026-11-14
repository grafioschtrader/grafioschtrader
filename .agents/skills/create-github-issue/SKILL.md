---
name: create-github-issue
description: Create a GitHub issue for grafioschtrader following the Problem/Solution pattern. Use when the user wants to create, file, or open a GitHub issue. Triggers on phrases like "create an issue", "open an issue", "file an issue", "new issue", "GitHub issue".
---

# Create GitHub Issue for GrafioschTrader

## Workflow

1. **Read existing issues** to understand the current style and conventions:
```
   gh issue list --repo grafioschtrader/grafioschtrader --limit 10
```
   Review 2-3 recent issues to match their tone, length, and formatting.

2. **Draft the issue** using the Problem/Solution structure. Keep it concise — a few sentences per section is ideal.

3. **Create the issue** via `gh` CLI:
```
   gh issue create --repo grafioschtrader/grafioschtrader --title "..." --body "..."
```

## Issue Structure

Every issue follows this pattern:
```markdown
## Problem
[Brief description of what is wrong or missing]

## Solution
[Brief description of what should be done to fix or improve it]

## User Manual
- [Page title](https://grafioschtrader.github.io/gt-user-manual/en/<section>/<page>/#<anchor>)
```

The **User Manual** section links the page of the gt-user-manual that documents (or will document) the feature,
as in issue #299. Add it whenever the issue touches user-visible behaviour.

- Find the page under `../gt-user-manual/content/` (`_index.en.md` of the folder), pick the heading the feature
  belongs to and use its slug as the anchor.
- URL form: `https://grafioschtrader.github.io/gt-user-manual/en/<path below content/>/#<anchor>` — English
  variant, no `/de/`, no `/en/` duplication inside the path.
- No manual page exists yet: still name the intended page, and create it via the `update-user-manual` skill when
  the feature is implemented.
- Purely internal issues (build, refactoring, test infrastructure) omit the section.
- Link manual pages only; never reference GitHub issues from the manual (see gt-user-manual rules).

## Rules

- Keep issues small and focused — one problem, one solution
- If a topic is too large, split it into multiple issues
- Match the style of previous issues in the repository
- Title should be short and descriptive