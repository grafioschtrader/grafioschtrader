# Linux Installer — Remaining Work

**Code baseline:** backend 0.38.1, highest versioned Flyway script
`V0_38_1__fuw_connector_disposal_cost_estimate_and_hold_daily_total.sql`.

**Scope:** the bootstrap installer for the classic (non-Docker) installation of Grafioschtrader on Debian and Ubuntu,
`util/installer/`. Its behaviour is defined by the sources in `util/installer/src/`, the generated bundle
`util/installer/gt-install.sh` and [`util/installer/README.md`](../util/installer/README.md); the acceptance record
lives in [`util/installer/test/README.md`](../util/installer/test/README.md). This document fixes the remaining work
packages and the order in which they are done (§1–§5). Each package ends with its acceptance; a package starts only
after the one before it is committed, except where §6 allows otherwise.

Supported platforms stay as the installer reports them: primary are Debian 12, Debian 13, Ubuntu 24.04 and Ubuntu
26.04 (including Raspberry Pi OS and Armbian on these bases) on amd64 and arm64; legacy are Debian 11 and Ubuntu 22.04
on amd64 and arm64, and armhf on any of them, which stays on Node.js 22 and becomes `block` after 2027-04-30.

## 1. CI on the Ubuntu 26 runner

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
locale does not count as a fresh installation.

**Fresh installations still owed:**

| Release and architecture | Required evidence |
|---|---|
| Ubuntu 24.04 arm64, Ubuntu 26.04 arm64 | backend built locally, frontend served, listeners on loopback, migrations complete, no `uca1400` column, service up again after a reboot, rerun without a step |
| Debian 11 (legacy), Ubuntu 22.04 (legacy), one architecture each | the same, plus the legacy `WARN` with its reason |
| any primary release on a real host below 4000 MB RAM without zram and without swap | the swap file carries the first build |

**Read-only runs on the reference hosts** (`--check` and `--dry-run`): correct host class, complete component
report, no file, package or service changed.

| Host | System | Web |
|---|---|---|
| 192.168.100.83 | Debian 13 arm64 | nginx with Home Assistant and phpMyAdmin |
| 192.168.100.84 | Debian 13 arm64 | nginx |
| 192.168.100.74 | Armbian on Ubuntu 26.04 arm64 | Apache (AJP) |
| 192.168.100.82 | Debian 12 arm64, MariaDB through a socket unit on `*:3306` | Apache (AJP) |
| 192.168.100.80 | Debian 13 arm64, Docker installation | host nginx in front of Docker |

**Scenarios on real hosts** (so far covered by container or fixture tests only):

| Scenario | Required evidence |
|---|---|
| DuckDNS with the installer's updater and `DNS_FAMILY` `ipv4` and `both`; an address change under any family | records equal the host's addresses; certificate issued; the timer run follows the address change |
| DuckDNS with an updater already in a crontab | found by the inventory, `DUCKDNS_UPDATER` defaults to `no`, no second updater, no token asked |
| own domain with correct records, with a wrong A record, without a `www` record | certificate issued; mismatch reported with both values and the certbot command; certificate and vhost without `www` |
| `TLS_SOURCE=existing` | the referenced certificate is served; no copy, no renewal hook |
| `TLS_SOURCE=proxy` with Caddy on the same host owning 80/443, and with a proxy on another machine | local vhost on the chosen port, `X-Forwarded-For` reaches the login lockout as the client address, registration link carries the public URL |
| existing MariaDB with socket access, with password-only access; MariaDB 10.5 | no credential question when privileged socket access succeeds; otherwise the current password once; no foreign root password or plugin changed; all migrations succeed on 10.5 |
| other Node.js and Java consumers, a shared document root, a PHP location on the same nginx vhost | consumers still on their runtime, alternatives unchanged, other sites answer as before |
| occupied 8080/9090, several vhosts | alternative ports used consistently, no listener off loopback, no traffic routed to another site |
| secrets with shell, SQL and properties special characters; cancellation during execution | literal values survive; no value in the installer's logs, errors or argv, nor in `/var/log/grafioschtrader.log` once issue #274 is resolved; temporary credentials removed on every exit path |
| administrator address different from the SMTP sender | `g.main.user.admin.mail` holds the chosen address; registration at exactly that address receives administrator roles |
| editing after hand-over | edit `application.properties` and add a key to `application-production.properties`, run `./gtupdate.sh` as `grafioschtrader` in a German SSH session; the application answers with the edit in effect, `merger.sh` kept the template key and the production file is unchanged |

## 3. Documentation

**Wiki.** The page *Installation on Debian 10 to 13 based Linux* names the installer as the primary way; the manual
steps stay as reference for unsupported systems. The nginx sub-page points nginx at 8080, which contradicts its own
`proxy_pass` blocks and the connector settings; change it to 9090.

**gt-user-manual** (installation pages, `update-user-manual` skill), German and English:

- download and invocation (`--check`, `--dry-run`, modeless installation, `--answers` with `--yes`, `--plain`),
  resumption by running the installer again without a mode, and `--check-mail` for a wrong SMTP password;
- the dialogs: when they appear (terminal of at least 80 × 24, `whiptail` present, otherwise single-line prompts),
  their order, *Back* and *Esc*, Tab to reach the buttons of a scrollable text, and the transcript printed after the
  last dialog;
- generating new passwords (*Generate a password*, or `!generate` in the single-line prompts), that a generated
  password is shown only once, and that existing credentials and tokens cannot be generated;
- armhf stays on Node.js 22 and is refused from 2027-04-30;
- below 3700 MB RAM the frontend is downloaded as `latest.tar.gz` from the GitHub release `Latest`, which is rebuilt
  on every frontend push to `master` and can be newer than the built backend;
- the configuration contract: user settings belong in `application.properties`, which `gtupdate.sh` saves and
  `merger.sh` merges into the new template, keeping only keys present in the template; `server.*` and other local
  overrides belong in `application-production.properties`, which updates leave unchanged;
- the DNS checklists per mode (DuckDNS with the installer's updater, DuckDNS updated elsewhere, own domain), the
  address-family question and the router rules for incoming 80/443;
- the three TLS sources and who renews and reloads: certbot by the installer (renewal through certbot's timer and
  the installer's reload hook), an existing certificate (renewal and reload stay with the user's tool), a
  TLS-terminating proxy (the proxy owns the certificate);
- switching a completed LAN-only installation to a domain, or between TLS sources, is a manual procedure; describe
  its steps per web server;
- moving an existing installation to a new host: fresh installation, stop the service, load the dump into the empty
  database, start the service and let the migrations bring it to the built version;
- limitations: the SMTP login is also the sender address; nginx configurations with wildcard or regex names,
  dynamic includes, address-specific port-80 listeners or a shared port 80 without `default_server` are integrated
  manually, as the installer's report describes; a new password that is literally `!generate` cannot be typed in
  the single-line prompts.

**Acceptance:** both language versions build, and every limitation above is findable on the installation pages.

## 4. Changing the SMTP selection later

Saved answers are immutable; a changed answer is refused. The one exception today is the SMTP password through
`--check-mail`. Extend this to the whole SMTP selection: an installation completed with `SMTP_CONFIGURE=no`, or with
wrong SMTP settings, can set or change `SMTP_HOST`, `SMTP_PORT`, `SMTP_AUTH`, `SMTP_USER`, `SMTP_SECURITY`,
`SMTP_PASSWORD` and `SMTP_TEST` after a confirmed plan.

- Only the owned configuration affected by mail is regenerated; the application is rebuilt and restarted as for the
  SMTP password.
- The original database, Jasypt and JWT secrets are preserved; the installation ID and resource ownership stay.
- All other saved answers stay immutable; domain and TLS changes remain the manual procedure of §3.
- The result changes from `incomplete` to `complete` once the mail milestone passes.

**Acceptance:** Bats cases for the accepted SMTP change and for a refused change of any other answer; on a real host,
an installation done without SMTP gains mail and reaches `status=complete`.

## 5. Stage 2 — Debian package

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

## 6. Order

| Order | Package | Depends on |
|---|---|---|
| 1 | §1 CI on the Ubuntu 26 runner | 2026-10-19 |
| 2 | §2 Acceptance matrix | — ; rows may start whenever hosts are available |
| 3 | §3 Documentation | — |
| 4 | §4 Changing the SMTP selection later | — |
| 5 | §5 Debian package | §2 complete for every primary combination |

## 7. Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Only the SMTP selection becomes changeable; domain and TLS changes stay manual | mail is the only incomplete milestone a user can fix without touching web or certificates |
| 2 | Bash script first, thin `.deb` second; no application package | the jar contains the user's configuration and is rebuilt by `gtupdate.sh` outside any package manager; Java 25 cannot be expressed as a dependency on Debian 11/12 |
