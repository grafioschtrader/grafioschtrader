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
```

## Rules

- Keep issues small and focused — one problem, one solution
- If a topic is too large, split it into multiple issues
- Match the style of previous issues in the repository
- Title should be short and descriptive