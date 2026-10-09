# Installer checks

From the repository root on Linux, with Bash, Python 3.9+, Bats, ShellCheck and OpenSSL installed:

```bash
bash util/installer/test/check-shell.sh
```

After editing `util/installer/src/`, run `bash util/installer/build.sh` first. The check script verifies bundle
identity without writing to the checkout, checks syntax and ShellCheck for modules and the bundle, runs the eleven
`test_build.py` tests and then the existing Bats suite. Python compilation writes bytecode only into temporary
test directories. Build tests cover deterministic assembly, drift rejection, missing/unexpected sources, invalid
Python, heredoc boundaries, literal inline quoting, the downloaded file's independence from its source tree and the
120-character limit of the shell sources.

The eleven bundle tests also run with Windows Python and Git Bash first on `PATH`:
`python -m unittest discover -s util/installer/test -p test_build.py`. Fixtures use explicit LF endings;
shell subprocesses use the resolved Bash path and the same Python interpreter as the test runner.

The workflow's `bundle` job runs an actual rebuild and `git diff --exit-code -- util/installer/gt-install.sh`.
Every other installer job depends on it. A stale generated file therefore prevents installation acceptance from
running against code that differs from the reviewed sources.

Run as a regular user to include the shared-document-root permission scenarios. Tests create temporary homes,
clones and document roots and replace build, download and service commands with stubs. Checker tests use a
temporary filesystem and allow-listed probe fixtures to verify classification, compatibility, privacy and
read-only behavior. Planner tests cover question defaults/conditions/validation, protected installations,
package simulations, shared services, DNS/IPv6, web conflicts and certificate validation with real OpenSSL fixtures.
They never operate on an installed application. The GitHub Actions workflow runs the same command.

`dns.bats` covers the separately confirmed `bind9-dnsutils` prerequisite, changed APT plans, stale metadata,
locks, declined confirmation, failed installation, inventory refresh and repeated DNS checks. Core and web
boundary tests reject failed DNS checks before continuing installation. Planner fixtures cover IPv6 subsets,
foreign AAAA records, missing/unknown addresses and optional `www`. Package installation and DNS responses are
stubbed: these regressions do not establish real APT installation or public DNS/router reachability.

Stage-contract regressions cover unsupported immutable selections in both new and resumed core plans, foreign
vhost integration and proxy defaults that exclude both backend ports. Renderer and actual nginx/Apache container
checks inject forged `X-Forwarded-For` headers. Backend lockout tests are database-free JUnit tests in
`grafiosch-test-integration/.../service/LoginAttemptServiceIpAddressTest.java`; they cover direct/AJP clients,
loopback and upstream proxies, IPv6 normalization, malformed chains and disabled proxy trust. Docker cases
verify independent client counters, successful-login reset, proxy chains and untrusted neighboring containers.

`login-proxy-host.sh` derives a disposable Compose project from `docker/docker-compose.yml`, using its network
membership, fixed Caddy address and trust environment variable. It starts the real `docker/Caddyfile` and the
built `LoginAttemptServiceIpAddress` behind a small HTTP adapter. Boot resolves the actual environment variable;
no Spring application context, database or authentication endpoint starts. Two client containers verify separate
counters, forged-header rejection, successful-login reset and direct access from an untrusted peer. No host ports
are published, the repository is read-only, and the test removes its containers and networks on exit.

After the Maven package command and Java/SMTP image build below, run:

```bash
bash util/installer/test/login-proxy-host.sh
```

The test uses `172.30.232.0/24` with Caddy at `172.30.232.2`; override `GT_TEST_PROXY_NETWORK_PREFIX`
if that subnet is already in use. Dynamic peers start before Caddy to check its address reservation.
CI runs this test in `mail-application`.

Credential tests cover literal/hidden input, mandatory secrets, confirmation, root and application authentication,
strict answer-file parsing, private temporary files and redacted reports. Install `mariadb-client` to include the
real MariaDB option-parser round trip. Run the root-owned input-file cases and terminal checks separately:

```bash
sudo bats util/installer/test/credentials.bats util/installer/test/core.bats
python3 util/installer/test/secret-terminal.py
```

The terminal test uses fake secrets and a real pseudo-terminal to verify mismatch retry, trace suppression,
Ctrl-C/SIGTERM handling and echo restoration. It never contacts a database or modifies an installation.
`test_secret_terminal.py` deterministically covers delayed child exit after PTY closure, the shared deadline
and preservation of exit status. It runs in the shell check; the PTY integration test remains a separate CI step.
CI acceptance of the terminal wait change requires two consecutive green `installer.yml` runs. Both attempts of
[run 37632497540](https://github.com/grafioschtrader/grafioschtrader/actions/runs/37632497540) passed for
`ee5fb9f4aceceda16de9e0b683f812532934e6a7`. Local tests do not replace those workflow runs.

When Ctrl-C still raised a terminal SIGINT, the Ctrl-C case occasionally waited for one more keystroke: 33 of
400 runs with 16 parallel tests on WSL Ubuntu 24.04. With `-isig` Ctrl-C is an input byte; 2 of 2560 such runs
still exceeded the 15-second deadline under that load. Run the test repeatedly under parallel load before
changing the reader. Background jobs of a non-interactive shell ignore SIGINT, and a shell started with an
ignored signal cannot trap it; restore `SIG_DFL` before starting such a stress run.

`bootstrap.bats` checks modeless selection, approval before journal publication, scope transition, package
dependency/version pinning, changed-plan rejection, populated-schema resumption without root credentials,
stage ordering and exact mail/result exit codes. Full-plan DNS prerequisites must be available before any changes.
The startup tests in `app.bats` cover historical-log exclusion, failure redaction, split tokens, rotation,
copytruncate/regrowth, systemd invocation recovery and immediate termination before polling or boot enablement.
These fixture tests do not replace full-installation and reboot acceptance on explicitly requested disposable VMs.

`result.bats` covers schema-1 compatibility, private result publication, completed-journal validation, all
read-only completed entrypoints, pending/failed/skipped milestones, proxy TLS, no-send SMTP, warning persistence,
and interruption between result publication and journal completion. It checks that the accepted message is not
sent again and that later update artifact drift does not reopen a completed installation. `domain-mail.bats`
and the SMTP container also exercise the hand-over through the actual mail-stage controller.

`smoke-prepare.sh` runs the actual `--prepare --answers` CLI with a temporary root-owned fixture file. Run it only
in a fresh disposable container with OpenSSL installed. It verifies the secret-status report and scratch cleanup;
the expected missing-host-prerequisite exit code is 2. Like the other smoke scripts it supports a read-only root
filesystem with writable `/tmp`. It never installs the proposed plan.

`smoke-check.sh` exercises the real root CLI inside a disposable container, including cleanup of its scratch
directory. A minimal container without systemd should complete the report with exit code 2. This smoke check is
separate from Bats and is not intended to run against the developer's machine.

`smoke-plan.sh` drives the real `--dry-run --plain` command through a pseudo-terminal using `script` from
util-linux. Run it only in a fresh disposable container: its canned answers select a LAN installation with
nginx and no mail. It must finish with a plan, accept exit code 2 for missing prerequisites, and leave no scratch
directory. To enforce the read-only contract, run both smoke scripts with a read-only root and `/tmp` as tmpfs:

```bash
docker run --rm --read-only --tmpfs /tmp -v "$PWD:/repo:ro" ubuntu:24.04 \
  bash /repo/util/installer/test/smoke-plan.sh
```

## Front ends

`dialogs.bats` runs the whiptail front end against `whiptail-stub.sh`, a scripted whiptail that answers dialogs by
their title, accepts the proposal of every other dialog and logs each call's arguments. It covers the front-end
selection (terminal below 80 × 24, `--plain`, `--yes`, `TERM=dumb`, missing whiptail, modes without questions),
German and English texts and buttons, Back stepping to the previous question and the abort question on the first
one, a rejected value reopening its dialog with the expected form, discarded answers and secrets after a changed
answer together with a recomputed default, the DNS checklist's *Stop here*, password confirmation, Back from the
first credential to the last question, explicit generation in both front ends (never for existing credentials,
never by empty input, never in the log or plan) and the identical plan for the same answers in both front ends.
The stub never answers a password box by itself, and the tests assert that no credential reaches its log.

`smoke-dialogs.py` drives the real whiptail through a pseudo-terminal: a German `--dry-run` and an English
`--prepare` that generates the three new passwords. It checks the dialog sequence, buttons and key help, the printed
transcript, that each generated password was on the screen exactly once and never in the transcript, and that no
scratch directory remains. `dialogs-container.sh` prepares a fresh container for it (whiptail, Python, the German
locale, `ss` and `openssl`); `installer.yml` runs it in the `core` job:

```bash
docker run --rm -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/util/installer/test/dialogs-container.sh
```

Neither replaces the acceptance over SSH on real Debian 13 and Ubuntu 26.04 hosts in both languages.

## Installation core

`base-packages.bats` verifies that execution installs the planned missing packages, rejects removals/upgrades,
checks installation success and resumes interrupted transactions. Completed-package drift and unavailable
inventory block execution. Planner and Apache tests compare the package/module selections used by both paths.

`base-packages-container.sh` runs real APT in a fresh disposable Ubuntu container. It first installs the documented
inventory prerequisites as fixture setup (including any dependency updates needed by an older image), then checks
the installer's remaining base-package transaction and repeats it without another APT installation. Installer
transactions continue to reject all upgrades. Journal persistence is covered separately; this test keeps journal
values in memory. CI runs this acceptance before the database/encryption container test:

```bash
docker run --rm -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/util/installer/test/base-packages-container.sh
```

`core.bats` uses temporary fixtures to check atomic journals and secret storage, ownership and permissions,
interruption recovery, literal property/SQL rendering, publication drift, locking and the execution boundary.
Changed inventory must stop execution before state or secrets are written. These tests do not install packages
or contact a database.

`core-container.sh` **installs MariaDB and creates Linux/database users and configuration**. Run it only in a
fresh, dedicated Docker container. It refuses a non-container, non-root invocation or an existing GT core state.
The image supplies JDK 25 and Maven; the test installs the distribution's MariaDB packages itself. No host port
or database volume is needed, and the repository is mounted read-only:

```bash
docker build -t gt-installer-core - < util/installer/test/core.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-core bash util/installer/test/core-suite.sh
docker image rm gt-installer-core
```

`core-suite.sh` runs the ordinary-user shell suite, root-owned-file cases, terminal checks and the actual core
integration. For just the integration, replace its name with `core-container.sh`. It verifies new root-password
authentication under the service user's OS identity, rejection of a wrong password, retained root socket access,
TCP application login, interrupted account creation, reuse of original secrets, real Jasypt encryption/decryption,
configuration drift and missing-secret refusal. Test credentials contain quotes, backslashes, whitespace and
literal `${...}` expressions; they are disposable fixtures.

The container has no systemd: a test adapter starts `mariadbd` directly. A local source fixture uses the actual
application properties and the repository's Jasypt plugin version in a minimal Maven POM. Thus this checks real
database and encryption operations, but does not verify systemd integration, the complete application build or a
running backend. The GitHub Actions core job runs this integration in its own fresh container.

## Include into an existing HTTPS site

`vhost.bats` parses a Certbot-style nginx site (HTTP redirect block plus HTTPS block with Certbot's include,
a PHP and a caching regex location) and an Apache virtual host dump. It covers the target selection, the
refusal of overlapping locations, a second HTTPS block, dynamic includes, an unusual opening line, an Apache
`RewriteRule` and overlapping `ProxyPass`, the `^~` and `<Location>` scoping of the snippets, the certificate
match, insertion, journal, verification, rollback with resumption and a changed target. `vhost-container.sh`
repeats it with real nginx and Apache in a disposable container from `web.Dockerfile`: an existing HTTPS site
with its own test certificate, PHP and static-file rules; a forced verification failure must restore the file
byte for byte, then the include must serve the frontend and API through the domain while the site's own paths
answer as before.

```bash
docker run --rm -v "$PWD:/repo:ro" gt-installer-domain-test bash util/installer/test/vhost-container.sh nginx
docker run --rm -v "$PWD:/repo:ro" gt-installer-domain-test bash util/installer/test/vhost-container.sh apache2
```

A second argument selects the manual paths. `manual` adds a second HTTPS block for the domain: the installer must
leave the site byte for byte unchanged and stay pending, then verify after the administrator's include. `none`
selects `WEBSERVER=none`: the stage stays pending until the server takes the nginx or Apache part of
`/root/gt-install-webserver.conf` literally, then verifies it. `web-manual.bats` covers the same paths with
fixtures, including the result action, a hand-edited proposal and the full-plan web review.

`lan-address.bats` uses a fixture host with the default route on one address and a second, intranet-only one: the
`LAN_ADDRESS` default and prompt, the chosen address in the web stages, vhost matching and domain checklist, the
blocker for an address of no interface, and the default-route fallback for journals without the answer.

`mail-change.bats` covers the changed SMTP selection before mail verification: a corrected password, an
installation without mail that gains it from defaults and reaches `status=complete` without its skipped-mail
warning, a relay without authentication that drops the password. The journal intent precedes the secret, only the
mail keys change, the service stops before the backend build and starts after it. Another changed answer or secret,
an invalid selection, a verified milestone and an unchanged selection change nothing; an interrupted change resumes
without the file, or asks for it again when the password was not yet stored. `core.bats` loads the secrets file on
either side of that password write while the change is open.

`mariadb-socket.bats` covers a newly installed MariaDB whose package enabled `mariadb.socket`: the socket is
disabled once, the server restarted, a listener beyond loopback stops the core, and a shared server is not touched.

## DuckDNS updater

`duckdns.bats` runs the generated `duck.sh` against `curl` and `ip` fixtures: the token appears only in the private
curl configuration, never in arguments or the log, and the configuration is removed; `ipv4`, `ipv6` and `both`
send the expected parameters over the expected transport; `KO`, curl failures, a missing IPv6 address and an
invalid token file fail distinctly. Core cases cover the single update, the DNS wait with its 3-minute limit, the
timer, a rejected token without disclosure and the completed rerun. Plan cases cover foreign updaters and
containers versus the installer's own units, lowercase subdomains, and the pending-update DNS warning with fixed
certificate names. `credentials.bats` checks the UUID format at the prompt. A real DuckDNS account is not used by
any automated test.

## ufw rules

`firewall.bats` covers the rule set per web route (never SSH), the question default and condition, plan rows,
the stage-contract removal, preexisting and owned rules, resumption of an interrupted rule, removed rules,
failing `ufw` calls and the order before any site or certificate. `firewall-container.sh` runs the real `ufw`
in a disposable Ubuntu container whose rules stay in its own network namespace:

```bash
docker run --rm --cap-add NET_ADMIN -v "$PWD:/repo:ro" ubuntu:24.04 bash /repo/util/installer/test/firewall-container.sh
```

## Java and Maven

`toolchains.bats` covers candidate selection, exact version pinning, manual fallback guidance for amd64/arm64/armhf,
Maven's selected `JAVA_HOME`, interrupted journals, alternative restoration and blocked removals/upgrades. Vendor
archive cases use metadata fixtures for Temurin (amd64/arm64), Liberica (armhf, newest of several releases) and
the Apache Maven listing. They cover forged download links and malformed checksums, offline resumption with the
journaled archive, foreign destinations, architecture changes, strict journal validation, checksum mismatches,
replacement of a partial stage, the archive-host fallback, an unexpected top-level directory, tampered published
trees and selection of the archive's own destination without APT or alternatives.

`toolchains-container.sh` **installs Java 25 and Maven and creates the application user**. Use only a fresh,
dedicated container from `toolchains.Dockerfile`. It starts with a shared Java 17, injects a failure after the
real APT transaction, reloads the journal, resumes, and verifies that the system still selects Java 17 while
Maven runs on Java 25 as `grafioschtrader`. It also checks that completed toolchains never trigger another package
transaction. The GitHub Actions toolchains job runs this integration separately:

```bash
docker build -t gt-installer-toolchains - < util/installer/test/toolchains.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-toolchains bash util/installer/test/toolchains-container.sh
docker image rm gt-installer-toolchains
```

This covers real APT packages on Ubuntu 24.04 amd64.

`toolchains-archive-container.sh` **downloads and installs real vendor archives and creates the application user**.
Use only a fresh, dedicated container from `toolchains-archive.Dockerfile`: Debian 12 with a shared Java 17 and no
JDK 25 in APT. It resolves the current Temurin archive, lets the real download and checksum pass, interrupts the
extraction, reloads the journal and resumes. It then verifies the published JDK, the unchanged Java 17
selections, the absence of an alternatives entry for the archive and Maven on Java 25 as `grafioschtrader`.
Completed toolchains must neither download nor invoke APT again. `GT_TEST_MAVEN=apt` uses Debian 12's APT Maven;
`GT_TEST_MAVEN=archive` treats it as too old, as Ubuntu 22.04's Maven 3.6 is, and installs the Apache Maven archive.
Each run downloads the JDK twice (about 140 MB each):

```bash
docker build -t gt-installer-toolchains-archive - < util/installer/test/toolchains-archive.Dockerfile
docker run --rm -e GT_TEST_MAVEN=apt -v "$PWD:/repo:ro" gt-installer-toolchains-archive \
  bash util/installer/test/toolchains-archive-container.sh
docker run --rm -e GT_TEST_MAVEN=archive -v "$PWD:/repo:ro" gt-installer-toolchains-archive \
  bash util/installer/test/toolchains-archive-container.sh
docker image rm gt-installer-toolchains-archive
```

These containers run on amd64. The Temurin arm64 and Liberica armhf archives have fixture coverage here; the
real-hardware acceptances below installed both.

## Node and npm build tools

`node.bats` covers architecture mapping, pinned recovery metadata, invalid checksums/version ranges, archive
traversal and symlink rejection, owned staging, interrupted publication and content/permission drift.

`node-container.sh` **creates a service user and installs Node and npm build tools**. Run it only in a fresh,
dedicated container. It puts an unsuitable shared Node fixture on PATH, installs a real official Node archive
and real Angular CLI/semver packages in isolated destinations, interrupts publication, reloads the journal and
continues with the original pins. It verifies the existing `checkversion.sh` as the service user through the
generated `gtvar.sh`, rejects modified tools and confirms that shared Node/npm files were preserved.

```bash
docker build -t gt-installer-node - < util/installer/test/core.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-node bash util/installer/test/node-container.sh
docker image rm gt-installer-node
```

The GitHub Actions node job runs this real integration on Ubuntu amd64. arm64/armhf archive mapping is covered
by fixtures; real installations on those architectures remain separate acceptance work. The QEMU test below
also builds the actual frontend on Ubuntu amd64.

## Application stage

`app.bats` checks the first-start database boundary, migration failures, build/start ordering, privileged-file
publication, recovery, loopback listeners and health responses. `update.bats` also proves that
`GT_INSTALL_BUILD_ONLY=1` prevents service commands on success and failure in both component helpers.

`app-container.sh` requires a fresh disposable container. It creates the real service user and writes actual
unit, sudoers, logrotate, log and cron files. It runs `systemd-analyze verify`, `visudo` and `logrotate --debug`,
checks exact sudo permissions, private artifact hashes and repeatable cron publication, then injects drift.
Only the systemd reload/time-zone adapter is substituted; systemd is not PID 1 in this test container.

```bash
docker build -t gt-installer-app - < util/installer/test/app.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-app bats util/installer/test/app.bats
docker run --rm -v "$PWD:/repo:ro" gt-installer-app bash util/installer/test/app-container.sh
docker image rm gt-installer-app
```

These container checks do not establish a full GT build, migration/startup under systemd or reboot recovery.
The QEMU test below covers those boundaries on Ubuntu amd64; ARM and the low-memory frontend download branch
with real dependencies remain separate acceptance work.

## nginx routing and rollback

`web.bats` checks effective include handling, name/listener conflicts, target ownership, managed-file drift,
APT transaction guards and CLI boundaries. `web-container.sh` starts actual nginx and a recording HTTP backend
on an alternate loopback port. It checks all proxy paths and forwarded/WebSocket headers, static JavaScript,
the Angular fallback, preservation of shared/default sites, repeated publication and interruption recovery.
Injected validation and changed-site responses must disable only the owned link and reload the baseline.

```bash
docker build -t gt-installer-web - < util/installer/test/web.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-web bats util/installer/test/web.bats
docker run --rm -v "$PWD:/repo:ro" gt-installer-web bash util/installer/test/web-container.sh
docker image rm gt-installer-web
```

No host ports or application databases are used. Only the absent systemd commands are adapted to real
nginx reloads; boot enablement and integration with the real application belong to the QEMU acceptance.

## Actual build and systemd reboot in QEMU

`vm-host.sh` runs acceptance in a disposable amd64 VM with its own kernel and systemd PID 1: Ubuntu 24.04 by
default, or `GT_VM_OS=debian-12`, `debian-13`, `ubuntu-26.04` or the legacy `ubuntu-22.04`; the additional releases run in bootstrap or dialogs mode. Debian 12 has no JDK 25 in APT, so its run exercises the vendor JDK
archive while Debian's APT Maven pulls in a shared Java 17; it is supported in bootstrap mode only, because the
stage driver installs Java from APT. A controller container keeps one guest disk and refuses another release;
use a new container per release, for example
`docker exec -e GT_VM_OS=debian-12 gt-installer-vm-debian12 bash /repo/util/installer/test/vm-host.sh`.
`GT_VM_MEMORY` sets the guest RAM in MiB (default 12288). Below 4000 MiB the bootstrap driver leaves `SWAP` and
`JAVA_HEAP` to the installer defaults and requires the swap file to be active and listed once in `/etc/fstab`,
after the installation and again after the reboot; below 3700 MiB it also requires the downloaded frontend
release instead of a local build. The controller requires `/dev/kvm`, at least 16 GB of available host RAM and
space for a 60 GB sparse guest disk. It assigns 12 GB and eight virtual CPUs to the guest. Docker needs only
the KVM device and a read-only repository mount; no privileged container, host database mount or published
network port is used. SSH forwards to loopback inside the controller container with a newly generated key.

The controller downloads the [official Ubuntu cloud image](https://cloud-images.ubuntu.com/noble/current/)
or the [Debian 12 genericcloud image](https://cloud.debian.org/images/cloud/bookworm/latest/), verifies it
against the vendor's SHA-256 or SHA-512 list and records its SHA-256 with the source revisions. The default `GT_VM_MODE=bootstrap` runs
`vm-bootstrap.sh`: it invokes the actual modeless CLI with a private answers file and `--yes`. The installer
resolves and pins the public source commit itself. The driver installs only probe prerequisites and a local SMTP
fixture; Java, Maven, MariaDB, Node, base packages, application builds and the web server belong to the installer.

The driver pauses the installer after its first-start intent, lets the real service finish migrations, then
terminates the installer with SIGTERM. A modeless `--yes` retry supplies no answers or database-root password.
It must preserve the installation ID and secrets, reach `scope=bootstrap` / `status=complete`, verify the actual
backend and LAN frontend and submit exactly one message to the guest-only SMTP sink. After reboot it verifies
a changed kernel boot ID, automatic database/backend/web startup and application health. A completed modeless
rerun must preserve journal, result, build log, artifacts and secrets and must not send another message.

`GT_VM_SMTP=later` (bootstrap mode only) installs with `SMTP_CONFIGURE=no` instead. The resumed run must then end
with exit 10, `status=running`, `mail=skipped`, the skipped-mail warning in the result and no message. The driver
then runs `--check-mail --answers FILE --yes` with the SMTP lines towards the guest's sink, which rebuilds the
backend with real Jasypt encryption; it must reach `status=complete` with exactly one accepted message, unchanged
installation ID and secrets, and no skipped-mail warning left in the result. Reboot and the completed rerun follow.

`GT_VM_MODE=stages` retains the individual-stage acceptance in `vm-guest.sh`. It snapshots Git HEAD and overlays the current
installer, its changed shell helpers and the application's connection-initialization properties. Other uncommitted
application changes and local ignored credentials are excluded. This isolated snapshot gets its own commit;
the working repository is never committed or reset by the test. The current installer is copied separately
and its SHA-256 recorded, allowing a verifier fix to resume against the same built application commit.

In stage mode, the guest invokes the real core execution functions with that local source pin and generated fixture passwords.
Java/Maven prerequisites are installed by the test driver; core CLI inventory, interactive prompts and vendor
fallback selection are covered by the separate suites. MariaDB, encryption, Node installation, both complete
application builds, systemd, sudoers and logrotation all run for real. No service-command adapter is used.
The application and nginx/LAN entry points must return 10. The web stage installs nginx through APT and verifies
the actual built frontend and application through the guest's LAN address. After `systemctl reboot`, the test
requires a new kernel boot ID, automatic database/backend/nginx startup, successful health/migration/listener
and LAN checks, unchanged secrets/configuration and repeated application/web stages without rebuilding or
rewriting the nginx site.

```bash
docker build -t gt-installer-vm - < util/installer/test/vm.Dockerfile
docker run -d --name gt-installer-vm --device /dev/kvm -v "$PWD:/repo:ro" gt-installer-vm sleep infinity
docker exec gt-installer-vm bash /repo/util/installer/test/vm-host.sh
mkdir -p tmp/installer-vm-results
docker cp gt-installer-vm:/work/results/. tmp/installer-vm-results/
docker rm -f gt-installer-vm
docker image rm gt-installer-vm
```

`GT_VM_MODE=dialogs` is the acceptance of the whiptail front end (Debian 12/13, Ubuntu 24.04/26.04, LAN with nginx).
The guest is prepared as in bootstrap mode, including the local SMTP sink, and gets the `de_CH.UTF-8` and
`en_US.UTF-8` locales of a tester's terminal. `vm-dialogs.py` then works on the controller side over `ssh -tt`, with
the terminal size of a pseudo-terminal and `LANG` exported before `sudo`, as in a user's SSH session: a dry-run in a
23-row terminal must fall back to the plain prompts and cancel with `!quit`; a dry-run in the language other than
`GT_VM_LANG` (default `de_CH.UTF-8`, or `en_US.UTF-8`) must show that language's dialogs, buttons and key help;
finally a modeless installation in `GT_VM_LANG` is driven only by dialogs — generated root and Jasypt passwords, a
typed and confirmed database password, SMTP towards the sink — until the printed result reports `status=complete`.
No key is sent while the gauge is shown. The guest's `dialog-check` then verifies the completed journal, one
accepted message, backend and web, and that no credential appears in `/var/lib/gt-install` or the application log;
the reboot check follows as in bootstrap mode. Each session's raw terminal output and its text are kept as
`results/dialogs-{small,other,install}.{raw,txt}`.

`GT_VM_DISK_DIR` places the cloud image and the guest disk on another mounted directory, for example a larger
Windows drive under WSL. Keys and state stay in `/work`, because `ssh` refuses keys on a `drvfs` mount without Unix
permissions:

```bash
mkdir -p /mnt/d/gt-installer-vm/dlg-debian13
docker run -d --name gt-installer-vm-dlg-debian13 --device /dev/kvm -v "$PWD:/repo:ro" \
  -v /mnt/d/gt-installer-vm/dlg-debian13:/disk gt-installer-vm sleep infinity
docker exec -e GT_VM_OS=debian-13 -e GT_VM_MODE=dialogs -e GT_VM_LANG=de_CH.UTF-8 -e GT_VM_MEMORY=8192 \
  -e GT_VM_DISK_DIR=/disk gt-installer-vm-dlg-debian13 bash /repo/util/installer/test/vm-host.sh
```

On WSL, keep a WSL process alive until the guest has powered off, including while diagnosing a failed command.
Otherwise WSL shutdown can abruptly power off QEMU. The controller preserves its disk and private keys until
the named container is removed. Retry the host script to resume an interrupted core/application stage;
missing or corrupt original credentials deliberately block recovery. Application passwords and the fixture's
root-password input stay inside the disposable guest. Logs and `results/PASS` distinguish a completed run from
an interrupted one. Real ARM, low-memory frontend downloads and the remaining distribution matrix require
separate runs. Set `GT_VM_WEB=apache2` to select Apache. Domain/TLS acceptance currently requires
`GT_VM_MODE=stages` and `GT_VM_DOMAIN=yes`; it uses an isolated test CA. The default is modeless bootstrap with
nginx and LAN HTTP. Public mail delivery, interactive typing and a later update to master remain separate checks.

Modeless acceptance on 2026-10-07 passed on Ubuntu 24.04 amd64 with nginx and kernel `6.8.0-142-generic`.
The actual CLI installed the toolchains, database and web server, built the application, and resumed after
SIGTERM (exit 143) with a populated schema and first-start intent still pending. Resumption exited 0 with
`status=complete`, unchanged secrets/identity and exactly one accepted local SMTP message. Reboot changed
the kernel boot ID; all three services started automatically and the real LAN frontend/API passed verification.
A completed modeless rerun preserved journal, result, build log, artifacts and secrets without another message.
Application commit: `9d0a04ee0f3230f387a8e2d1a62a29e956db56e6`; installer SHA-256:
`3fa9e8dc385721bb40eed773cb20e708f7e62ceffb463ea931b455107d09a29b`; cloud image SHA-256:
`6a81c37564db9b1ee84e141922625e1d7c5b389b99bb3c572e0243607d5bb4d2`.

Modeless acceptance on 2026-10-07 also passed on Debian 12 amd64 with nginx and kernel `6.1.0-53-cloud-amd64`.
APT offered no JDK 25: the installer planned and installed the Temurin `25.0.4.1+1` archive
(`OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz`, SHA-256 `dbb69839…cf41e`) into
`/opt/jdk-25.0.4.1_1-temurin`, and Maven `3.8.7-1` from APT. Maven's APT dependencies installed a shared Java 17,
which remained the system selection; the archive JDK has no alternatives entry. Build, service, resumption after
SIGTERM, one accepted local SMTP message, reboot with automatic startup and a read-only completed rerun passed
as on Ubuntu. Application commit: `838ae5272a57c129472752821df4271dae0dd6ff`; installer SHA-256:
`2e1b5263b6ddd5d76eb558c2351143c3e576d2ce44f38980de843ed9561dc37b`; cloud image SHA-256:
`9ebb87ba8e3e0ab593e39cb89fed15dda05e5997b575285a7fae4ac4bc42ad20`.

Modeless acceptance on 2026-10-07 passed on Debian 13.7 amd64 with nginx and kernel `6.12.111+deb13-cloud-amd64`.
Debian 13 offers `openjdk-25-jdk-headless` `25.0.4.1+1-1~deb13u1` and Maven `3.9.9-1` in APT, which the installer
used. MariaDB `11.8.6` maps utf8mb4 to the `uca1400` collations by default; the backend's connection
initialization kept the schema on `utf8mb4_general_ci` through all migrations. `/tmp` is a tmpfs. Build,
resumption, one accepted mail, reboot and the completed rerun passed; the result carried no warning. Application
commit: `35c41a8af27cb581f632c5ad2cd31c73e7687e37`; installer SHA-256:
`9e1b7d3367c62fad0ac689ccc5d334687df454dde6d7e47d76e64a948d0c7931`; cloud image SHA-256:
`b2aca2bee42c7082fd6aba4a5bb1a89612087a405e99896920f3346f7b23b9d1`.

Modeless acceptance on 2026-10-07 passed on Ubuntu 26.04.1 LTS amd64 with nginx and kernel `7.0.0-34-generic`.
The installer used `openjdk-25-jdk-headless` `25.0.4.1+1-1~26.04.4` and Maven `3.9.12-1` from APT with MariaDB
`11.8.6`. Build, resumption, one accepted mail, reboot and the completed rerun passed; the result carried no
warning. Application commit and installer SHA-256 as for Debian 13; cloud image SHA-256:
`8800651811af9a85465ad1d552add729947bb16488dddb4a9b5305a3d97332b2`.

Dialog acceptance (`GT_VM_MODE=dialogs`, 8192 MiB) on 2026-10-09 passed on Debian 13 amd64, kernel
`6.12.111+deb13-cloud-amd64`, and Ubuntu 26.04 amd64, kernel `7.0.0-34-generic`, each over `ssh -tt` from a fresh
guest. On both a 23-row terminal fell back to the plain prompts and `!quit` cancelled with 130, and a dry-run in the
other language showed its buttons and key help. Debian 13 then installed in `de_CH.UTF-8`, Ubuntu 26.04 in
`en_US.UTF-8`, driven only by dialogs: generated MariaDB root and Jasypt passwords, a typed and confirmed database
password, SMTP towards the guest's sink. Both reached `status=complete` with one accepted message; neither
credential appeared under `/var/lib/gt-install`; reboot and the read-only completed rerun passed. Debian 13
installed Temurin `25.0.4.1+1` and Apache Maven `3.10.0` as isolated archives, Ubuntu 26.04
`openjdk-25-jdk-headless` `25.0.4.1+1-1~26.04.4` and Maven `3.9.12-1` from APT; both Node `24.21.0`,
`@angular/cli` `22.2.2`, MariaDB `11.8.6` and nginx (`1.26.3` / `1.28.3`). Application commit:
`3249e96afcb5caab3fbde93a7fc9b8913f18decf`; installer SHA-256 `b98ed3764dcf4addca7e5f13ee3b72b931c25f300d25adbab77f5e139b8c3257`
on Debian 13 and `0985e58095d1170341ba3d4a2537d6f256560a090c7693b361b0cff4f5661b75` on Ubuntu 26.04 (the
latter without the locale isolation of `gt_as_app`, which an English session does not need); cloud images as above.

Dialog acceptance (`GT_VM_MODE=dialogs`, 8192 MiB) on 2026-10-09 also passed on Debian 12 amd64, kernel
`6.1.0-53-cloud-amd64`, in `de_CH.UTF-8`: the 23-row fallback with `!quit`, the English dry-run, then an installation
driven only by dialogs to `status=complete` with one accepted message, no credential under `/var/lib/gt-install`,
reboot and the read-only completed rerun. APT offered no JDK 25, so the installer used the Temurin `25.0.4.1+1`
archive, with Maven `3.10.0`, Node `24.21.0`, `@angular/cli` `22.2.2`, MariaDB `10.11.18` and nginx `1.22.1`.
Application commit: `9b8962091ff0230d14548e31fa389a1a4051fd57`; installer SHA-256:
`4b3667ce5de0b8481261de739e3cfc72024ffec1e3d436eb8eb38af972563f94`; cloud image as for the modeless Debian 12 run.
The same evidence passed on Ubuntu 24.04 amd64, kernel `6.8.0-142-generic`, in `en_US.UTF-8` with the German dry-run,
using `openjdk-25-jdk-headless` `25.0.4.1+1-1~24.04.4` and Maven `3.8.7` from APT, Node `24.21.0`, `@angular/cli`
`22.2.2`, MariaDB `10.11.14` and nginx `1.24.0`; same application commit and installer SHA-256, cloud image as for
the modeless Ubuntu 24.04 run. On both, `dialog-check` noted the database password in the application log (issue
#274).

SMTP-later acceptance (`GT_VM_SMTP=later`, bootstrap mode, 8192 MiB) on 2026-10-09 passed on Debian 13 amd64,
kernel `6.12.111+deb13-cloud-amd64`, with nginx. The resumed installation ended with exit 10, `mail=skipped` and the
skipped-mail warning; `--check-mail --answers` with the SMTP lines then rebuilt the backend and exited 0 with
`status=complete`, `mail_delivery=accepted`, exactly one message at the sink, the same installation ID and secrets,
and a result without any warning. Reboot and the read-only completed rerun passed. Maven `3.9.9` from APT, Node
`24.21.0`. Application commit and installer SHA-256 as for the Debian 12 dialog run; cloud image SHA-256:
`b2aca2bee42c7082fd6aba4a5bb1a89612087a405e99896920f3346f7b23b9d1`.

Legacy dialog acceptance (`GT_VM_MODE=dialogs`, 8192 MiB) on 2026-10-09 passed on Ubuntu 22.04 amd64, kernel
`5.15.0-198-generic`, in `en_US.UTF-8` with the evidence of the dialog runs above; the result carried
`WARN: Legacy platform: Ubuntu 22.04 standard support ends 2027-04`. The installer used `openjdk-25-jdk-headless`
`25.0.4.1+1-1~22.04.4` from APT, the Apache Maven `3.10.0` archive (APT offers 3.6), Node `24.21.0`, `@angular/cli`
`22.2.2`, MariaDB `10.6.23` and nginx `1.18.0`. Application commit: `b0662727ce645de00e9bb1c8bdbf94c7d1304220`;
installer SHA-256 as for the Debian 12 dialog run; cloud image SHA-256:
`012d81fade7e8ff4428fc35dfeee711d66d1f47d5ea9c5c8ced96a85b959835c`. The same attempt on Debian 11 failed before the
installer started: after the end of its LTS on 2026-08-31, `security.debian.org` still indexes `bullseye-security`
but no longer serves its packages (404), so every APT installation that pulls a security update fails. Debian 11
is therefore refused as an unsupported release.

The first attempts of this acceptance found two faults that the unattended runs, started through `systemd-run` in
`/` without a locale, could not reach. `runuser` kept the administrator's working directory, a 0700 home on
Debian 13, where `npx` failed with `EACCES` and `checkversion.sh` rejected Node `24.21.0`; and the German locale
reached `gtupfrontend.sh`, whose `free -m` then printed `Speicher:` instead of `Mem:`. `gt_as_app` now runs in the
service user's home with `LC_ALL=C.UTF-8`, and both helpers call `LC_ALL=C free -m`. Independently of the installer,
the backend's Hibernate startup message `HHH10001005` writes the JDBC URL that MariaDB Connector/J reports,
including `password=`, to `/var/log/grafioschtrader.log` (mode 640); `dialog-check` records this as a note until
issue #274 is resolved.

Read-only acceptance on 2026-10-09 passed on two production reference hosts with the installer downloaded from
`master` at `525da03bec0a7123a3367d215ea46396c9b154bd` (SHA-256
`e57c60fd4614ecc5f8862947b2cc094fbd321f5af472f8073213f54648770c2d`). `.74` (ROCK 4C+, Armbian 26.11 on Ubuntu 26.04
arm64, kernel `6.18.55-current-rockchip64`, Apache with AJP) reported `host.class=classic` with the `./gtupdate.sh`
hint; `--check` exits 2 because the non-empty database blocks a bootstrap, `--dry-run` exits 0. `.80` (Raspberry
Pi 5, Debian 13 arm64, kernel `6.18.34+rpt-rpi-2712`, host nginx in front of Docker) first reported
`foreign-partial`, because a service user and an empty native database remained from an earlier classic
installation; since GT containers now take precedence it reports `host.class=docker` with the `docker/update.sh`
hint, and both modes exit 0. On both hosts the package list, unit files, `/var/lib/gt-install` and every file under
`/etc`, `/usr`, `/opt`, `/var/www` and `/home/grafioschtrader` were unchanged. The only service difference was
`systemd-timedated`, which the inventory's `timedatectl show` starts through D-Bus and which ends itself when idle.

Real-hardware acceptance on 2026-10-08 passed on a Radxa ROCK 5B (arm64, 16 GB, SD card) with Debian 12.15,
kernel `6.1.84-8-rk2410`, nginx `1.22.1`, Let's Encrypt and the installer's own DuckDNS updater. The host is
dual-homed: the intranet on Ethernet without a default route, the internet over WLAN; `LAN_ADDRESS` selected the
Ethernet address. The installer used the Temurin `25.0.4.1+1` aarch64 archive, Maven `3.8.7` from APT, the
isolated Node `24.21.0` with a local `ng build`, and MariaDB `10.11.18`. `bind9-dnsutils` was installed first, as
documented for a modeless domain installation. With `DNS_FAMILY=ipv6` the update set the AAAA record to the WLAN
address and left no A record; the certificate covers the domain and `www`. The run found and resumed past five
defects that VMs do not show: the DuckDNS request over IPv6 (DuckDNS has no IPv6 address), a DuckDNS unit missing
its first lines, write-once root files that a fixed installer could not replace, Debian's `mariadb.socket`
listening on 3306 of every address (disabled by hand on this host), and no way to correct a wrong SMTP password;
`--check-mail --answers` then rebuilt the backend with the corrected password and the test mail was accepted. Reboot
and the completed rerun passed without change; the result kept the planning-time DNS warning. Application commit:
`611e25fccc6e41a631405cf8ea5d9e2b1918c86f`; final installer SHA-256:
`eeb0bedacf064d4d6da38dc8fa1ae8805549a36d23fecd76ec3f5cbccff9c309`.

Real-hardware acceptance on armhf passed on 2026-10-08 on an Odroid XU4 (armv7l, 2 GB, zram swap of 994 MiB) with
Armbian 26.8 on Debian 13, kernel `6.6.151-current-odroidxu4`, Apache `2.4.68`, Let's Encrypt and the installer's
own DuckDNS updater, dual-homed like the ROCK 5B. The installer used the Liberica `25.0.4.1+1` arm32 archive,
Maven `3.9.9` from APT, Node 22 for the build tools, the downloaded frontend release (below 3700 MB RAM), MariaDB
`11.8.6`, `-Xms128m -Xmx896m` and a 384M buffer pool; the backend build took about 20 minutes. Three defects
surfaced: the minimal image has no git, so the source lookup and the core prerequisite check blocked before the
plan could install it; and Debian builds `openjdk-25` for armhf as the interpreter-only Zero VM, whose first TLS
handshake took about 30 seconds, so Maven Central dropped it and the Jasypt step failed. The journal of that run
was corrected by hand to the Liberica archive. Reboot and the completed rerun passed without change; MariaDB
listens on loopback only. Application commit: `611e25fccc6e41a631405cf8ea5d9e2b1918c86f`; final installer
SHA-256: `17845e53581e4c593d5c869c691b0ffb8abc20d10b0b7db99bb674c3fcfe1686`.

Low-memory acceptance on 2026-10-09 passed on the same Odroid XU4 (1988 MiB `MemTotal`), kernel
`6.6.151-current-odroidxu4`, after the earlier installation and its packages were removed and Armbian's zram swap
was disabled, so the host had no swap at all. The modeless installation with an answers file (`--answers FILE
--yes`, started as root from the administrator's home) planned and created the 2 GiB `/swapfile` with its
`/etc/fstab` line before the first build, chose the installer's heap default `-Xms128m -Xmx896m`, the Liberica
`25.0.4.1+1` arm32 archive, Maven `3.9.9` from APT, Node `22.23.3` for the build tools, the downloaded frontend
release, MariaDB `11.8.6` and Apache `2.4.68`, and reached `status=complete` with Let's Encrypt and an accepted test
message. The backend build took 4:51 min. A 20-second memory sampler recorded at most 1017 MiB used RAM and 23 MiB
used swap: below 3700 MiB the frontend is downloaded, so the swap file only backs the Maven and `javac` build.
After a reboot the swap file was active again from `/etc/fstab`, MariaDB, the backend and Apache started by
themselves and `/api/gtinfo` answered after 189 seconds of uptime. Two attempts before had stopped in the plan
without changing anything: once because the APT lists were older than 24 hours and the pinned helpers could not be
fetched during an internet outage, once because Armbian's ramlog had restored the removed
`/var/log/grafioschtrader.log` from `/var/log.hdd` at boot. Application commit:
`c63be7145b153d30ca7fb8cfea795e573d1a14a0`; installer SHA-256:
`e57c60fd4614ecc5f8862947b2cc094fbd321f5af472f8073213f54648770c2d`.

Real-hardware acceptance on Debian 13 arm64 passed on 2026-10-08 on a Radxa ROCK 4C+ (4 GB, zram swap, SD card)
with Armbian 26.11, kernel `6.18.54-current-rockchip64`, nginx `1.26.3`, Let's Encrypt and the installer's own
DuckDNS updater, dual-homed like the ROCK 5B. The image had neither git nor dig; the modeless plan installed both
with its approved base packages and checked DNS after the DuckDNS update. The installer used `openjdk-25` and
Maven `3.9.9` from APT, the isolated Node `24.21.0` with a local `ng build` (3852 MB `MemTotal`), MariaDB `11.8.6`,
a 1024M buffer pool and `-Xms128m -Xmx896m`; the build took about 20 minutes. A reboot during the first build
found one defect: with `armbian-ramlog` enabled, Armbian rewrites `/var/log/` to `/var/log.hdd/` in every
`/etc/logrotate.d` file at boot, so the resumed run took the installer's own logrotate file for a foreign one.
After that fix the run resumed by itself, rebuilt and completed without warnings in the result. Reboot and the
completed rerun passed without change; MariaDB listens on loopback only. Application commit:
`c7ae51427adbeef147b058806cf5a136ba6c1acc`; final installer SHA-256:
`a8607a5b009bbfdc3a085585e4ab0d11b3ef576729fa3e92478789ff14952676`.

Small-host acceptance on 2026-10-07 passed on the same Debian 12 image with `GT_VM_MEMORY=3072` (2983 MiB
`MemTotal`, no swap). The installer asked for and created the 2 GiB `/swapfile` before the first build, wrote
the single `/swapfile none swap sw 0 0` line after the backup `/etc/fstab.gt-install.<timestamp>`, proposed
`-Xms128m -Xmx896m` and downloaded the frontend release instead of running `ng build`. The swap was active
after the installation, after the SIGTERM resumption and, through `/etc/fstab`, after the reboot. The result
reported `status=complete` with the low-RAM frontend warning. Application commit:
`2a60e6a42bc7605c1ed420c4922ebb1fb654d364`; installer SHA-256:
`9a77106a4c557152921a5cda580fce79bff9d10317dddf52c0ed660937c6e7a9`.

Local acceptance on 2026-10-05 passed with kernel `6.8.0-142-generic`, Java `25.0.4.1`, Node `24.21.0` and
MariaDB `10.11.14` on Ubuntu 24.04 amd64. All six Maven reactor modules built successfully, the production
frontend was generated, and all 85 Flyway migrations succeeded with zero failed migrations and zero
`uca1400` columns. Both services started automatically after a changed kernel boot ID; the original secrets,
configuration and artifact hashes survived, and resumption did not rebuild. Source base:
`1fe9d42c41794c0f18ef21b059011e6ab792622f`; isolated application commit:
`774071b647c92e4edbac6ea41521c414639f46a2`; installer SHA-256:
`51247836b44cb159effac19e8b65b830a1276b2229b9675f194ffc021696971b`.
The verifier accepts Java's IPv4-mapped loopback representation while continuing to reject wildcard and
public listeners. Journal and secret publication flushes data before rename and filesystem metadata afterward.

The nginx/LAN acceptance on 2026-10-05 also passed on the same Ubuntu/toolchain combination: real nginx APT
installation, frontend and API through `10.0.2.15`, automatic startup after reboot and repeated web/application
stages with unchanged site, secrets and build artifacts. Isolated application commit:
`bbbd8ddcea4fca0c37ac4095bda826d8dd345851`; final installer SHA-256:
`41fef32d73226f1a9602d20f6237df3fb8fd967088b2cd31f7599b9ec240538c`.
The separate nginx container test verifies shared-site rollback and an actual WebSocket 101 handshake with
a data frame through the proxy. No production services or host web ports are used.

Apache/domain acceptance on 2026-10-05 passed on Ubuntu 24.04 amd64 with the same Java, Node and MariaDB
versions: real APT installation, AJP to the built backend, trusted HTTPS/SNI, retained LAN HTTP, automatic
startup after a changed boot ID, and repeat application/web stages without changing original secrets or builds.
The Apache stock site had an implicit IPv6 name and was recognized by its package checksum. Isolated application
commit: `fef51bed2d9f5b4b75ff167bb0ccd4fc1f1b8ebd`; installer SHA-256:
`556fcb1a806a825b8668d2dd02ca4db90f19f7b07a5ad472360bb26f4896fcc6`.

## Domain, ACME and SMTP containers

From the repository root on a Linux Docker host:

```bash
docker build -t gt-installer-domain-test - < util/installer/test/web.Dockerfile
for web in nginx apache2; do
  docker run --rm -v "$PWD:/repo:ro" gt-installer-domain-test bash util/installer/test/domain-container.sh "$web"
  docker run --rm -v "$PWD:/repo:ro" gt-installer-domain-test bash util/installer/test/domain-container.sh "$web" proxy
  bash util/installer/test/acme-host.sh "$web"
done
docker run --rm -v "$PWD:/repo:ro" gt-installer-domain-test bash util/installer/test/mail-container.sh
```

The domain fixture speaks real HTTP/WebSocket and AJP13. Tests verify SNI, trusted certificate/fingerprint,
HTTP redirect, SPA/index/JavaScript, TLS WebSocket handshake/data, shared sites, interrupted activation and
rollback to bootstrap HTTP while LAN stays available. Proxy tests verify protocol headers and source ACLs.
The SMTP fixture exercises STARTTLS, implicit TLS, UTF-8/special-character credentials, no-send, an explicit
unauthenticated relay, rejected recipients, wrong password, missing STARTTLS, wrong certificate name and
duplicate suppression, including the `Date` header. It substitutes application configuration resolution and never
contacts an external mail server. `certbot-reuse.bats` verifies reuse consent/defaults, certificate coverage,
renewal-file ownership/drift, scoped hooks and absence of duplicate issuance. The Pebble acceptance also reuses the
issued lineage, verifies its renewal again, and checks that its configuration and lineage list remain unchanged.

`InstallerMailConfigurationTest` in `grafiosch-test-integration` exercises real Spring configuration resolution
and Jasypt, including production overrides, wrong keys, malformed ciphertext and incompatible transport flags.
It starts no Spring application context, database or SMTP server. Build its executable test JAR and run the separate
Java/SMTP acceptance as follows (CI does both):

```bash
mvn -f backend/pom.xml -pl grafiosch-test-integration -am package -Dtest=InstallerMailConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false
docker build -t gt-installer-mail-application - < util/installer/test/mail-application.Dockerfile
docker run --rm -v "$PWD:/repo:ro" gt-installer-mail-application bash util/installer/test/mail-application-container.sh
```

The fixture replaces application settings in a disposable copy of that JAR. The real Boot launcher resolves its
mail settings and decrypts the password, then Python connects to the local SMTP fixture. A wrong journal SMTP
password cannot override the deployed value; a wrong Jasypt key or conflicting external production property
blocks before another message. This does not exercise the running application's registration-mail workflow.

`acme-host.sh` creates a private Docker network with [Pebble](https://github.com/letsencrypt/pebble), performs
real HTTP-01 validation on both names and an actual Certbot renewal dry run with the saved deploy hook. No host
ports or public ACME service are used. It removes its two containers and network on exit. Only the disposable
client trusts the generated issuing CA; only the test adapter bypasses verification of Pebble's own development
API certificate. Production code always verifies TLS. The container translates the exact systemd reload hook to
a real server reload because it has no systemd PID 1; the VM test checks actual systemd separately.

These cases run in `installer.yml`, alongside `domain-mail.bats` ownership and failure-boundary tests. Public
DNS/router reachability, the wider platform matrix and co-resident proxies owning port 80 need separate acceptance.

## Database acceptance

`DatabaseInitializationCheck.java` runs independently of Spring: it loads each application's actual
`application.properties`, initializes three physical Hikari connections, runs Flyway against the same pool and
checks the resulting schema. It exercises the complete `V*.sql` history, including when a newer `B` snapshot is
present in the working tree. No application background jobs or REST/E2E suites run.

Use fresh, disposable MariaDB 10.5 and 11.8 containers. Bind them only on loopback and give their data directories
temporary storage, for example:

```bash
docker run -d --rm --name gt-installer-db105 -p 127.0.0.1:13315:3306 \
  -e MARIADB_ROOT_PASSWORD=installer-test --tmpfs /var/lib/mysql mariadb:10.5
docker run -d --rm --name gt-installer-db118 -p 127.0.0.1:13318:3306 \
  -e MARIADB_ROOT_PASSWORD=installer-test --tmpfs /var/lib/mysql mariadb:11.8
```

After both servers report ready, obtain the application's runtime classpath with Maven (its reactor dependencies
must already be installed locally):

```bash
mvn -f backend/pom.xml -pl grafioschtrader-server dependency:build-classpath \
  -Dmdep.includeScope=runtime -Dmdep.outputFile=target/installer-test-classpath.txt
export GT_INSTALL_TEST_DB_PASSWORD=installer-test
installer_classpath=$(cat backend/grafioschtrader-server/target/installer-test-classpath.txt)
java -cp "$installer_classpath" util/installer/test/DatabaseInitializationCheck.java . \
  jdbc:mariadb://127.0.0.1:13315/ 10.5
java -cp "$installer_classpath" util/installer/test/DatabaseInitializationCheck.java . \
  jdbc:mariadb://127.0.0.1:13318/ 11.8
docker stop gt-installer-db105 gt-installer-db118
unset GT_INSTALL_TEST_DB_PASSWORD
```

The check refuses servers with either application schema already present, creates `grafioschtrader` and
`grafiosch`, and never drops databases. Repeat against new containers. MariaDB before 11.2 has no
`character_set_collations` variable; only the UTC assertion and resulting schema checks apply there.
