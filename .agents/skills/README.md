# Agent skills

Reusable procedures for the AI coding agents used on this project. The `SKILL.md` format is a
cross-vendor standard, but every agent reads it from its own directory — there is no path both read.
Hence one canonical copy plus a stub:

| Path | Read by | Content |
|---|---|---|
| `.agents/skills/<name>/SKILL.md` | OpenAI Codex CLI (repo scope) | **the real skill** |
| `.claude/skills/<name>/SKILL.md` | Claude Code (project scope) | a stub that points here |

**Edit the file in `.agents/skills/` — never the stub.** The only thing duplicated is the
`description:` line, which Claude needs in order to decide when to trigger the skill; keep the two
copies identical when you change it. Do not "clean up" the duplication by deleting a stub: Claude Code
does not read `.agents/skills/`, so the skill would simply disappear for it.

Both files must be **UTF-8 without BOM**, like the message bundles (root `CLAUDE.md` → "Message
Properties Encoding"). Windows editors defaulting to cp1252 have corrupted these files before.

Adding a third agent means adding a stub in whatever directory that agent reads. The canonical file
does not move.
