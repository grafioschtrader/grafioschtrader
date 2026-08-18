---
name: specification
description: Write, revise and retire the design specifications in specification/. Use when creating a concept document, when another agent or person has raised concerns/review notes against an existing specification that should be incorporated, when a staged implementation reaches the next stage, and when a specification has been fully implemented and must be removed. Triggers on "specification", "concept document", "fold in the concerns", "incorporate the review", "another agent says", "specification/". Not for Playwright e2e specs — those belong to the e2e-test skill.
---

# Design specifications in `specification/`

## Rule 0 — a specification always reads as a first draft

Whatever its edit history, the document must read as if one person wrote it in a single pass with the
codebase open. A reader must never be able to tell that it was reviewed, criticised, patched or partly
implemented.

Never appears in a specification:

- header status lines about revisions, reviews, or whose concerns were incorporated;
- any mention of a reviewer, another agent, a concern id (`C4`), or a companion review file;
- rebuttal grammar — "this is not enough", "contrary to", "was mis-diagnosed", "not merely",
  "the two figures do not disagree", "this does not break", "as noted above";
- changelog sections, or "corrected" / "updated" / "revised" annotations on individual rules;
- completion markers on delivered stages ("Stage 2 done ✔").

The replacement is always the same shape: **state the rule, name the code it lands on, give the formula.**
A finding that arrived as criticism becomes an ordinary requirement sentence, indistinguishable from one
that was there from the start.

One header line is not review framing and belongs in every specification:

```markdown
**Code baseline:** backend 0.36.7, highest Flyway script `V0_36_7__Frankfurter_and_entity_limit.sql`.
```

It tells the next reader how far the document's statements about existing code can be trusted.

## What a specification is, and where it lives

| Directory | Content |
|---|---|
| `specification/` | finalized concept specifications — the plan for work not yet done |
| `doc/` | working material, requirement drafts, diagrams, vendor documentation |

A specification is a **plan**. It quotes current behaviour only to describe what a change lands on, and it
is never the authority on that behaviour. It is written to be turned into source code, so prefer exact
identifiers, formulas, table and column names, and file paths over prose.

## Source code is the only source of truth

Never answer a question about how GT behaves today by reading a specification. Versions, Flyway numbers,
method names, named queries and file layouts drift; a document written two releases ago is a plan, not a
description. Re-verify against the tree whenever you rely on such a statement — at minimum at the start of
every implementation stage.

## Folding in concerns raised by someone else

Reviews arrive as a separate file next to the specification. Work them in like this, in order:

1. **Read the target specification in full first, then the concerns.** The other order frames every later
   judgement by someone else's conclusions.
2. **Verify every claim against the tree before believing it.** Reviews of this codebase land the
   conclusion but miss the mechanism often enough that unverified adoption writes a false statement into
   the specification. Check each one yourself and note `file:line` for the verdict.
3. **Classify each concern:**
   - *valid* → fold in;
   - *right conclusion, wrong mechanism* → fold in the conclusion, describe the real mechanism;
   - *wrong* → do not adopt. If the check surfaced a genuine adjacent problem, specify that one instead.
4. **Add what the review missed.** You are already in those files; findings of your own carry the same
   weight and go in the same way.
5. **Ask the user about decisions that are genuinely theirs** — a real design choice with trade-offs, not
   a detail resolvable from the code. Decide the rest yourself and record the outcome in the document's
   decisions table.
6. **Edit the specification in place**, in its own voice. Then make a **separate pass** for Rule 0: the
   scrub is easy to overlook while thinking about content.
7. **Delete the review file in the same change.** Its content now lives in the specification, and a
   `*_concern_*.md` lying beside it is the loudest possible signal that the document was patched.
8. **Report to the user what you accepted, corrected and rejected**, with the evidence. That belongs in
   the conversation — never in the document.

## Staged delivery — when a specification is updated

Only when the feature ships in several stages. A single-shot implementation never updates its
specification; it deletes it (below).

Between stages:

- refresh the **Code baseline** header;
- re-verify the statements about current behaviour that the completed stage itself invalidated;
- **remove** the stages that are done, including their acceptance criteria and any rules that only
  governed them.

What remains reads as a first draft of the work that is left.

## Retirement — delete the specification when it is implemented

When the last stage ships, **delete the file**. Do not archive it, do not move it to `doc/`, do not keep it
as documentation. The code is the truth and a stale specification is a liability.

Before deleting, move anything that must outlive it:

| Content | Destination |
|---|---|
| User-visible behaviour, limitations, how to record something | **gt-user-manual** — use the `update-user-manual` skill |
| A decision a future reader would otherwise re-litigate | commit message, or a GitHub issue via `create-github-issue` |
| A durable rule for agents working on the code | `CLAUDE.md`, `backend/CLAUDE.md` or `frontend/CLAUDE.md` |
| A rule about how to build or test the thing | the relevant skill in `.agents/skills/` |

The manual pass happens **before** the deletion. A specification's documented limitations are usually the
only place they exist; deleting the file first loses them. The gt-user-manual is the artefact we keep as
accurate as we can — the specification is not.

## Verification before reporting a specification task as done

- [ ] Every `§n.n` cross-reference resolves to a heading that exists in the document
- [ ] Every file, class, method, column and named query mentioned exists in the tree — spot-check them
- [ ] No stale version, Flyway number or path (the fastest regression: a migration number that moved on)
- [ ] Rule 0 scrub done as its own pass — grep for `review`, `concern`, `corrected`, `not merely`
- [ ] The review file is deleted, and `specification/` contains only concept documents
- [ ] Statements about current behaviour were verified against the tree in **this** session, not inherited
      from the document
- [ ] For a retirement: gt-user-manual updated first, then the specification deleted
