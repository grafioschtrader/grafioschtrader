---
name: release
description: Cut and ship a Grafioschtrader release — version bump via nv.bat, the hand-maintained version strings, the pre-release audit for stray files, build verification, commit/tag/push to both remotes, the GitHub Release with its house-style notes, the GHCR Docker image workflow, and the update of the Docker host and the classic hosts. Use whenever a new version is prepared, tagged, published or deployed. Triggers on "release", "cut a release", "new version", "bump the version", "nv.bat", "gh release", "release notes", "Docker images", "GHCR", "update the Docker host", "update.sh", "deploy", "gtupdate".
---

# Releasing Grafioschtrader

A release is **a commit on `master` + a tag `V<version>` + a *published* GitHub Release**. There is no release
script. The published release is the step that ships: it is the only trigger of the Docker image workflow.

Work through the steps in order. Stop and ask the user whenever a step needs something only they have
(production database access, a password, a decision about what goes into the release).

## Site-specific data

Remotes other than `origin`, host names, SSH users and install paths of the machines that run GT are **not** in
this public file. They live in `.agents/skills/release/site.local.md` next to this file, which is gitignored.
Read it now if it exists. If it does not, ask the user for the second git remote (step 4) and skip steps 7 and 8
unless the user supplies the hosts.

**Never write a password into any file** — not into `site.local.md`, not into a script, not into a commit. Ask
the user for it when a step needs it and pass it on the command line only.

## Version numbers

`major.minor.patch[.revision]` (`README.md`). A changed **fourth** digit (`0.36.7` → `0.36.7.1`) means the release
contains no Flyway migration; any other change means the schema is migrated on startup, and the release notes must
say so. Tags are uppercase: `V0.37.1`.

The version the Docker workflow publishes is read from `backend/pom.xml`, **not** from the tag.

## Step 1 — Pre-release audit: what would ride along?

Stray files have been committed into a release three times (Playwright artefacts under a root `target/`,
`frontend/debug.log`, Git-LFS hook stubs in a literal `dev/null/` directory, agent scratch files under `tmp/`).
The pre-release commit is never automatically the release commit. Check:

```bash
git status --porcelain                                   # untracked + modified
git diff --name-only <prevtag>..HEAD | cut -d/ -f1 | sort -u   # top-level dirs touched since last release
git ls-files "dev/null*" tmp target nul error.txt        # tracked strays - git status is silent about these
```

A top-level directory that is not `backend`, `frontend`, `docker`, `util`, `scripts`, `specification`, `doc`,
`gt-code-style`, `.agents`, `.claude`, `.github` or a root file you recognise is almost certainly a stray. Never
`git add -A` without having looked. `*.bat` files (including `backend/nv.bat`) are gitignored by design because
they hold credentials; `backend/*/pom.xml.versionsBackup` is ignored as well.

## Step 2 — Bump the version surface

1. **`backend/nv.bat <version>`** — run by the user, or by you only if it exists in the working tree (it is
   gitignored and needs the production database plus a local MariaDB client). It regenerates:
   - `grafioschtrader-server/src/main/resources/db/migration/gt_ddl.sql` (never hand-edit this file)
   - `grafioschtrader-server/src/test/resources/db/migration/test/V1__schema.sql` and `V2__testdata.sql`
   - the fixtures under `grafioschtrader-server/src/test/resources/testdata/generated/`
   - `grafiosch-test-integration/migration-baseline/V0_10_0__init.sql`
   - all six `pom.xml` versions (`mvn versions:set` at its tail)

   `V2__testdata.sql` is a **Git LFS** object (~160 MB). Never hand-patch it; fix the generator and add a follow-up
   migration instead.
2. **By hand**: `"version"` in `frontend/package.json` **and** both `"version"` fields at the top of
   `frontend/package-lock.json`. The lockfile is tracked; the web image, `angular.yml` and `gtupfrontend.sh`
   install with `npm ci` and break on a mismatch.
3. **Example versions under `docker/`**: `grep -rn "<previous version>" docker/`. Update the *example* strings
   (`docker/.env.example` `GT_VERSION` comment, `docker/update.sh` usage lines, the example commands in
   `docker/README.md`). **Judge each hit** — a sentence such as "installations of 0.37.1 or older" is
   historical and must keep its number.

The version-related files of a release commit are therefore: six poms, `gt_ddl.sql`, `V1__schema.sql`,
`V2__testdata.sql`, `testdata/generated/*`, `migration-baseline/V0_10_0__init.sql`, `frontend/package.json`,
`frontend/package-lock.json`, and the `docker/` example strings. Everything else in the commit is feature work.

## Step 3 — Verify the build before tagging

A green Maven build says nothing about Angular, and a green local Angular build says nothing about the web image.

```bash
cd backend && mvn clean install -DskipTests      # must end with zero [WARNING] compiler lines
cd frontend && npm run buildprod                 # builds only the "frontend" project
cd frontend && npx ng build grafiosch-host       # the second project; it has no production configuration
```

Then compare `"scripts"` in `frontend/package.json` against the previous tag
(`git diff <prevtag> -- frontend/package.json`). `docker/web.Dockerfile` copies only the two manifests and
`frontend/scripts/` before `npm ci`, and copies the YAML schemas from the backend module explicitly. Any new npm
hook (`postinstall`, `pre*`) that touches files outside those needs a matching `COPY` in `web.Dockerfile`, and
`util/shellscripts/gtupfrontend.sh` needs the same treatment because it calls `ng build` directly and skips npm
pre-hooks. V0.37.1's first image build failed exactly this way.

**Windows trap**: a running `npm start` holds `node_modules/@esbuild/win32-x64/esbuild.exe`; `npm ci` deletes
`node_modules` first and then fails with EPERM, leaving a broken tree. Stop the dev server first, or validate the
lockfile in a scratch directory holding only `package.json` + `package-lock.json`.

Run the backend tests before committing only if the user asks for it (see `AGENTS.md`); never start the E2E
roundtrip on your own.

## Step 4 — Commit, push, tag

- Commit message: `V<version> - <short headline of the main features>` (see `git log --oneline` for precedent).
- Push `master` to **`origin`** (GitHub) **and** to the second remote named in `site.local.md` — the deployed
  classic hosts pull from that one, not from GitHub.
- **Never use `push.bat`**: it runs `git push --force`, which a fast-forward release never needs and which
  endangers the other remote.
- Tag `V<version>` on the release commit and push the tag to **both** remotes.
- The second remote serves Git LFS over SSH; if an LFS push fails there, do not push with `--no-verify` (the
  clones would receive pointer files). Report it to the user.

## Step 5 — Publish the GitHub Release

`gh release create V<version> --title "V<version>" --notes-file <file>`. Take the previous release as the style
template: `gh release view <prevtag> --json body -q .body`. House style:

1. `See [Milestone V<version>](<milestone url>) for details.`
2. `## Upgrade notes for version <version>` — every migration by name, what it changes, and anything that alters
   behaviour for existing data or configuration defaults. State "This release updates the database" or that it
   does not. Mention when backend and frontend must be on the same version.
3. `## What's new in <version>` — one bullet per issue: `- **Feature name (#NNN)**: what the user gets.`
4. Optional further sections (`## Also in this release`, a thematic section) and a closing paragraph about what
   was deliberately left out.

Write for users, not developers; no class names unless the user must run them (e.g. an audit tool).

**`gh` on this project's Windows machine intermittently fails with HTTP 401** despite a valid login. Retry, and
verify the outcome (`gh release view V<version>`) instead of inferring it from the exit code of a chain.

## Step 6 — Watch the Docker image workflow

`.github/workflows/docker.yml` runs on `release: published` only (plus manual `workflow_dispatch`). It builds
`ghcr.io/grafioschtrader/grafioschtrader-backend` and `-web` for `linux/amd64` + `linux/arm64` and pushes
`:<version>` and `:latest`. Both packages are public.

```bash
gh run list --workflow=docker.yml --limit 3
gh run watch <run-id> --exit-status --interval 20     # about 6 minutes
```

Expected and harmless in its log: the failing `Ensure the package is publicly pullable` step warnings and Node
deprecation notices. If the build fails: fix on `master`, then move the tag to the fix commit
(`git tag -f V<version> <fix>` + force-push **only the tag** to both remotes) and re-run the workflow with
`gh workflow run docker.yml`. The GitHub Release stays; V0.36.5 and V0.37.1 were both handled this way.

## Step 7 — Update the Docker host (optional, only when the user asks)

Host, SSH access and install path come from `site.local.md`. On the host, in the install directory's `docker/`:

1. **Refresh the deployment files first.** The host clone is shallow and parked on an old tag; without this an
   outdated `update.sh` runs:
   `git -C <clone> fetch --depth=50 origin master && git -C <clone> checkout origin/master -- docker`
2. `nohup ./update.sh <version> > update-<version>.log 2>&1 &` — the health poll can outlast a 10-minute tool
   timeout. Do not touch local untracked files such as `docker-compose.override.yml` or `.env`.
3. Poll the log for the closing block `Grafioschtrader is running.` (followed by `Images:` / `Schema:` /
   `Backup:` / `Log:`), not for "done". A normal run takes 1–4 minutes including the pre-update database dump.
   Issue the poll loop through a POSIX shell: PowerShell expands `$(...)` inside the ssh argument and breaks the
   remote loop. `pgrep -f 'update.sh'` matches the ssh command line itself; judge completion from the log.
4. Judge Flyway by `ERROR`/`Exception` lines and the `Successfully applied` line. `Can't DROP ...; check that it
   exists` (1091) and `DROP TEMPORARY TABLE` (1051) notices are harmless.
5. Verify: the published port answers `302 → /grafioschtrader/ → 200`; the redirect is normal.

`docker` is not installed on the Windows development machine — inspect images and manifests on the host.

## Step 8 — Update the classic hosts (optional, only when the user asks)

Classic installations update with `~/gtupdate.sh` (repo copy: `util/shellscripts/gtupdate.sh`) as the service
user, in a login shell (`bash -lc`, Maven is only on the login PATH). The script contains no host name — it runs
`git fetch origin` in the build clone. So "the update did nothing" is essentially always a **wrong `origin` URL in
that clone**, never a bug in the script: a stale remote whose DNS name still resolves fails its fetch silently.
Check `git -C ~/build/grafioschtrader remote get-url origin` on each host. Never customise `~/gtupdate.sh` on a
host — it replaces itself with the repo copy on the next run.

When copying a shell script from the Windows checkout to a host, pipe `git show HEAD:<path>` over ssh; the working
tree has CRLF line endings that break bash.

## Done means

- `gh release view V<version>` shows the published release with its notes.
- The Docker workflow run for that release is green; `:<version>` exists for both images.
- `master` and the tag are on both remotes (`git ls-remote <remote> V<version>`).
- Steps 7/8 only if requested, each verified by its own check above.
- Report to the user what was done, what was skipped, and any warning that needs their attention.
