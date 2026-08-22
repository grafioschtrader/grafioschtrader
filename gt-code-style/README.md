# Code style

Formatting is applied by tools, never by hand. Two commands cover the whole repository:

```bash
cd backend  && mvn spotless:apply     # Java
cd frontend && npm run format         # TypeScript, HTML, SCSS, JSON, Markdown
```

Both have a non-writing counterpart — `mvn spotless:check` and `npm run format:check` — which report
what is not conformant and change nothing. Neither is bound to the build: `mvn clean install` never
rewrites a source file and never fails because of formatting, and there is no commit hook.

## Backend — Eclipse profile, executed by Spotless

`backend/eclipse/gt-java-formatting.xml` (profile name `gt`) is the single definition: 2-space indent,
braces at end of line, wrapped at 120 characters. It is **build input**, not just an IDE convenience —
editing it changes what `mvn spotless:apply` produces, so treat a change to it as a change to every
Java file in the repository.

The plugin is configured once in `backend/pom.xml` (`pluginManagement`) and activated by a three-line
entry in each of the five module poms.

**Spring Tools / Eclipse setup**

- Preferences → Java → Code Style → Formatter → *Import…* → `gt-code-style/backend/eclipse/gt-java-formatting.xml`,
  then select the profile `gt`.
- Preferences → Java → Code Style → Organize Imports — the order must match what Spotless applies:
  static imports first, then `java`, `javax`, `org`, `com`, then everything else.
- Preferences → Java → Editor → Save Actions — enable *Format source code*, *Organize imports* and
  *Remove trailing whitespace*.

`.settings/`, `.project` and `.classpath` stay gitignored. The XML above is the shared contract; the
workspace metadata is not.

**If the IDE and the command line disagree**, Spotless is running a different Eclipse JDT than the IDE.
Pin it: `<gt.jdt.version>` in `backend/pom.xml` selects the JDT release Spotless uses. Verify by
formatting one module in Spring Tools and then running `mvn spotless:check` — it must report no changes.

> A comment line consisting only of slashes (`////…`) placed between enum constants crashes the JDT
> formatter from 4.34 onward (it is fine in 4.33). Use `// ------` for such separators.

## Frontend — Prettier, and IDEA delegates to it

`frontend/.prettierrc.json` is the single definition: 2-space indent, single quotes, 120 columns, no
trailing commas, LF. There is deliberately **no IntelliJ code-style XML any more** — a second,
hand-maintained definition is exactly what allowed the sources to drift apart in the first place.

**IntelliJ IDEA setup**

- Settings → Languages & Frameworks → JavaScript → Prettier → *Automatic Prettier configuration*.
- Tick **On 'Reformat Code' action** and **On save**.

<kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd> and `npm run format` then do the same thing. `frontend/.idea/`
stays gitignored; nothing in it needs to be shared.

Prettier does not reformat Angular templates written inline in `@Component({ template: … })` — it only
touches standalone `.html` files.

## Everything else

`/.editorconfig` is the floor for editors and coding agents that read neither of the above: UTF-8,
2-space indent, LF, final newline, no trailing whitespace, 120 columns. `/.gitattributes` stores every
text file as LF, with `*.bat`, `*.cmd` and `*.ps1` kept CRLF because cmd.exe and PowerShell need them.
