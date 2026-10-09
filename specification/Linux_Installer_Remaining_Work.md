# Linux Installer — Remaining Work

**Code baseline:** backend 0.38.1, highest versioned Flyway script
`V0_38_1__fuw_connector_disposal_cost_estimate_and_hold_daily_total.sql`.

**Scope:** the bootstrap installer for the classic (non-Docker) installation of Grafioschtrader on Debian and Ubuntu,
`util/installer/`. Its behaviour is defined by the sources in `util/installer/src/`, the generated bundle
`util/installer/gt-install.sh` and [`util/installer/README.md`](../util/installer/README.md); the acceptance record
lives in [`util/installer/test/README.md`](../util/installer/test/README.md), the administrator documentation in the
wiki page *Installation with the Linux installer*, linked from the gt-user-manual page *Installation and Update*. This
document fixes the remaining work packages and the order in which they are done (§1–§3). Each package ends with its
acceptance; a package starts only after the one before it is committed, except where §4 allows otherwise.

Supported platforms stay as the installer reports them: primary are Debian 12, Debian 13, Ubuntu 24.04 and Ubuntu
26.04 (including Raspberry Pi OS and Armbian on these bases) on amd64 and arm64; legacy is Ubuntu 22.04 on amd64 and
arm64, and armhf on any of them, which stays on Node.js 22 and becomes `block` after 2027-04-30. Debian 11 is
refused: its LTS ended on 2026-08-31 and `security.debian.org` no longer serves its packages.

## 1. CI on the Ubuntu 26 runner

The CI runs only on pushed commits, and hosts receive the installer and the helpers in `util/shellscripts/` only through
`master`: the installer downloaded from GitHub pins the current `master` commit and builds with the `gtupfrontend.sh`,
`gtupbackend.sh` and `checkversion.sh` of that commit. Publication therefore goes in this order: `master` of the
application repository, then the wiki, then the gt-user-manual, because the wiki page describes the dialogs and password
generation of the published installer.

The `ubuntu-latest` label moves to Ubuntu 26 from 2026-10-19. After that date run `.github/workflows/installer.yml`
by `workflow_dispatch` and confirm every job green on the new image, including the `core` job's
`dialogs-container.sh` step. Pin `ubuntu-24.04` in a job only when a suite cannot run on 26 and record the reason in
`util/installer/test/README.md`.

**Acceptance:** two consecutive green `installer.yml` runs on Ubuntu 26 without a Node.js deprecation warning of an
action.

## 2. Acceptance matrix

Full installations run only on disposable systems; production hosts contribute read-only evidence. QEMU/VM runs
require an explicit request. Every passed acceptance is recorded in `util/installer/test/README.md` with host,
release, kernel, toolchain versions, application commit and installer SHA-256.

Every fresh installation is started the way an administrator starts it: over SSH, with `sudo` from the
administrator's own home directory and the session's `LANG`, at least once in German. In QEMU this is
`GT_VM_MODE=dialogs` (`util/installer/test/vm-dialogs.py`); a run started through `systemd-run` in `/` without a
locale does not count as a fresh installation. Once issue #274 is resolved, `dialog-check` in
`util/installer/test/vm-bootstrap.sh` turns its note about a credential in `/var/log/grafioschtrader.log` into a
failure.

**Fresh installations still owed:**

| Release and architecture | Required evidence |
|---|---|
| Ubuntu 24.04 arm64 | backend built locally, frontend served, listeners on loopback, migrations complete, no `uca1400` column, service up again after a reboot, rerun without a step |

**Scenarios on real hosts** (so far covered by container or fixture tests only):

| Scenario | Required evidence |
|---|---|
| DuckDNS with the installer's updater and `DNS_FAMILY` `ipv4` and `both`; an address change under any family | records equal the host's addresses; certificate issued; the timer run follows the address change |
| DuckDNS with an updater already in a crontab | found by the inventory, `DUCKDNS_UPDATER` defaults to `no`, no second updater, no token asked |
| own domain with correct records, with a wrong A record, without a `www` record | certificate issued; mismatch reported with both values and the certbot command; certificate and vhost without `www` |
| `TLS_SOURCE=existing` | the referenced certificate is served; no copy, no renewal hook |
| `TLS_SOURCE=proxy` with Caddy on the same host owning 80/443, and with a proxy on another machine | local vhost on the chosen port, `X-Forwarded-For` reaches the login lockout as the client address, registration link carries the public URL |
| existing MariaDB with socket access, with password-only access | no credential question when privileged socket access succeeds; otherwise the current password once; no foreign root password or plugin changed |
| other Node.js and Java consumers, a shared document root, a PHP location on the same nginx vhost | consumers still on their runtime, alternatives unchanged, other sites answer as before |
| occupied 8080/9090, several vhosts | alternative ports used consistently, no listener off loopback, no traffic routed to another site |
| secrets with shell, SQL and properties special characters; cancellation during execution | literal values survive; no value in the installer's logs, errors or argv, nor in `/var/log/grafioschtrader.log` once issue #274 is resolved; temporary credentials removed on every exit path |
| administrator address different from the SMTP sender | `g.main.user.admin.mail` holds the chosen address; registration at exactly that address receives administrator roles |
| installation done with `SMTP_CONFIGURE=no`, then `--check-mail --answers FILE` with the SMTP lines; the same with a wrong SMTP host | the mail check passes and the result reaches `status=complete` without the skipped-mail warning; database, Jasypt and JWT secrets and the installation ID unchanged |
| editing after hand-over | edit `application.properties` and add a key to `application-production.properties`, run `./gtupdate.sh` as `grafioschtrader` in a German SSH session; the application answers with the edit in effect, `merger.sh` kept the template key and the production file is unchanged |

## 3. Stage 2 — Debian package

Starts only after §2 has installed a disposable machine of every primary release and architecture.

`grafioschtrader-installer`, `Architecture: all`:

- installs `gt-install.sh` as `/usr/sbin/gt-install` and nothing else;
- `Depends:` only packages available on every supported release: `bash`, `curl`, `git`, `sudo`, `logrotate`,
  `ca-certificates`;
- no `postinst` that installs Grafioschtrader; it prints "run `sudo gt-install`". Upgrading the package never reruns
  the installation, and removing it leaves the installed application, database and configuration in place;
- distributed through a signed APT repository on GitHub Pages, built by a workflow on the published release next to
  `.github/workflows/docker.yml`. Enrolling the repository on a host and maintaining its signing key are part of this
  stage.

**Acceptance:** the package installs and upgrades on Debian 12 and Ubuntu 24.04, `sudo gt-install --check` runs, and
removing the package leaves an installed Grafioschtrader untouched.

## 4. Order

| Order | Package | Depends on |
|---|---|---|
| 1 | §1 CI on the Ubuntu 26 runner | `master` published; 2026-10-19 |
| 2 | §2 Acceptance matrix | rows may start whenever hosts are available; real hosts need the published `master` |
| 3 | §3 Debian package | §2 complete for every primary combination |

## 5. Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Bash script first, thin `.deb` second; no application package | the jar contains the user's configuration and is rebuilt by `gtupdate.sh` outside any package manager; Java 25 cannot be expressed as a dependency on Debian 12 |
