# Linux Installer — Remaining Work

**Code baseline:** backend 0.38.2, highest versioned Flyway script
`V0_38_2__correlation_set_include_dividends_and_hold_portfolio_indexes.sql`.

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

**Scenarios on real hosts** (so far covered by container or fixture tests only); they need either an inbound IPv4
connection, a domain whose DNS records can be set, a second host for the proxy, or the fix of issue #274:

| Scenario | Required evidence |
|---|---|
| DuckDNS with `DNS_FAMILY` `ipv4` and `both`, only on a connection with its own public IPv4 and port forwarding (DS-Lite offers no inbound IPv4) | records equal the host's addresses; certificate issued |
| own domain with correct records, with a wrong A record, without a `www` record | certificate issued; mismatch reported with both values and the certbot command; certificate and vhost without `www` |
| `TLS_SOURCE=proxy` with the proxy on another machine | local vhost on the chosen port, `X-Forwarded-For` reaches the login lockout as the client address, registration link carries the public URL |
| secrets with shell, SQL and properties special characters, once issue #274 is resolved | no value in `/var/log/grafioschtrader.log` |

## 3. Debian package from the published repository

The package `grafioschtrader-installer`, its build and repository scripts in `util/installer/package/`, the
`package` job of `installer.yml` and the release workflow `.github/workflows/apt.yml` define the package; the APT
repository appears under `https://grafioschtrader.github.io/grafioschtrader/apt` with the first release published
after them, because `apt.yml` runs only on a published release or on a `V<version>` tag that contains it. The
repository secret `APT_SIGNING_KEY` holds the key whose public half is
`util/installer/package/grafioschtrader-archive-keyring.asc`.

With that release:

- confirm the `apt.yml` run green, including its container installation, and `apt/` on `gh-pages` beside the
  Javadoc; the next Javadoc deployment must leave `apt/` in place;
- enroll the published repository on `.86` (Debian 12) and `.82` (Ubuntu 24.04) exactly as `util/installer/README.md`
  describes, install the package, run `sudo gt-install --check`, remove and purge it, and record the run in
  `util/installer/test/README.md`; the upgrade is accepted with the release after that;
- describe enrollment and `sudo gt-install` in the wiki page *Installation with the Linux installer*, then in the
  gt-user-manual, in the publication order of §1.

**Acceptance:** the package installs and upgrades from the published repository on Debian 12 and Ubuntu 24.04,
`sudo gt-install --check` runs, and removing the package leaves an installed Grafioschtrader untouched.

## 4. Order

| Order | Package | Depends on |
|---|---|---|
| 1 | §1 CI on the Ubuntu 26 runner | `master` published; 2026-10-19 |
| 2 | §2 Acceptance matrix | rows may start whenever hosts are available; real hosts need the published `master` |
| 3 | §3 Debian package from the published repository | the next published release |

## 5. Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Bash script first, thin `.deb` second; no application package | the jar contains the user's configuration and is rebuilt by `gtupdate.sh` outside any package manager; Java 25 cannot be expressed as a dependency on Debian 12 |
