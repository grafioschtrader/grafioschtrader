# Installer checks

From the repository root on Linux, with Bash, Python 3.9+, Bats, ShellCheck and OpenSSL installed:

```bash
bash util/installer/test/check-shell.sh
```

After editing `util/installer/src/`, run `bash util/installer/build.sh` first. The check script verifies bundle
identity without writing to the checkout, checks syntax and ShellCheck for modules and the bundle, runs the ten
`test_build.py` tests and then the existing Bats suite. Python compilation writes bytecode only into temporary
test directories. Build tests cover deterministic assembly, drift rejection, missing/unexpected sources, invalid
Python, heredoc boundaries, literal inline quoting and the downloaded file's independence from its source tree.

The ten bundle tests also run with Windows Python and Git Bash first on `PATH`:
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
CI acceptance of the terminal wait change requires two consecutive green `installer.yml` runs. Local tests
do not replace those workflow runs.

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

## Java and Maven

`toolchains.bats` covers candidate selection, exact version pinning, manual fallback guidance for amd64/arm64/armhf,
Maven's selected `JAVA_HOME`, interrupted journals, alternative restoration and blocked removals/upgrades.

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

This covers real APT packages on Ubuntu 24.04 amd64. Alternative archives and other distribution/architecture
combinations have fixture coverage and manual instructions; they are not claimed as real installation tests.

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

`vm-host.sh` and `vm-guest.sh` run the application acceptance in a disposable Ubuntu 24.04 amd64 VM with its
own kernel and systemd PID 1. The controller requires `/dev/kvm`, at least 16 GB of available host RAM and
space for a 60 GB sparse guest disk. It assigns 12 GB and eight virtual CPUs to the guest. Docker needs only
the KVM device and a read-only repository mount; no privileged container, host database mount or published
network port is used. SSH forwards to loopback inside the controller container with a newly generated key.

The controller downloads the [official Ubuntu cloud image](https://cloud-images.ubuntu.com/noble/current/),
verifies its SHA-256 and records it with the source revisions. It snapshots Git HEAD and overlays the current
installer, its changed shell helpers and the application's connection-initialization properties. Other uncommitted
application changes and local ignored credentials are excluded. This isolated snapshot gets its own commit;
the working repository is never committed or reset by the test. The current installer is copied separately
and its SHA-256 recorded, allowing a verifier fix to resume against the same built application commit.

The guest invokes the real core execution functions with that local source pin and generated fixture passwords.
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

On WSL, keep a WSL process alive until the guest has powered off, including while diagnosing a failed command.
Otherwise WSL shutdown can abruptly power off QEMU. The controller preserves its disk and private keys until
the named container is removed. Retry the host script to resume an interrupted core/application stage;
missing or corrupt original credentials deliberately block recovery. Application passwords and the fixture's
root-password input stay inside the disposable guest. Logs and `results/PASS` distinguish a completed run from
an interrupted one. Real ARM, low-memory frontend downloads and the remaining distribution matrix require
separate runs. Set `GT_VM_WEB=apache2` and `GT_VM_DOMAIN=yes` on the controller container to test Apache/AJP
and existing-certificate TLS with an isolated test CA. The default is nginx without a domain. Mail delivery
and a later update to master remain separate from this VM test.

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
