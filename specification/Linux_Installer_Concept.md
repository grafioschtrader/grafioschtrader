# Linux Installer — Concept

**Code baseline:** backend 0.38.1, highest versioned Flyway script
`V0_38_1__fuw_connector_disposal_cost_estimate_and_hold_daily_total.sql`.

**Scope:** a bootstrap installer for the classic (non-Docker) installation of Grafioschtrader on Debian and Ubuntu.
It takes a machine from "nothing installed" to a running instance whose further life is `~/gtupdate.sh`, and stops
there. Updates remain the job of `util/shellscripts/gtupdate.sh`; the Docker installation (`docker/install.sh`) is
unaffected.

## 1. Goal

An installation that has reached the point where the user only runs `./gtupdate.sh` is easy to operate. The way
there is not: the wiki page *Installation on Debian 10 to 13 based Linux* and its sub-pages (Java, Maven on old
Debian releases, Node.js, MariaDB, Apache2 or nginx, Let's Encrypt, DuckDNS) require knowledge of every component
and take hours. The installer replaces that path with one guided run that

1. inventories the machine without changing anything,
2. decides per component whether it is reused, installed, isolated for Grafioschtrader, replaced, or blocks the
   installation,
3. asks all questions up front and shows the resulting plan,
4. executes the plan, ending with a pinned first build, a verified running instance and a report
   of what is still missing.

The result uses **the files and places the wiki describes and every existing installation uses**: user
`grafioschtrader`, clone in `~/build/grafioschtrader`, the scripts of `util/shellscripts/` in the home directory,
`gtvar.sh`, `grafioschtrader.sh`, `grafioschtrader.service`, `/etc/sudoers.d/grafioschtrader`. `gtupdate.sh`, the
wiki and forum answers apply to an installer-made host without distinction.

### 1.1 Fixed constraints

- **The backend is built on the target machine.** The user adapts `application.properties`, the file is packaged
  into the jar, and `gtupdate.sh` saves it before `git reset --hard origin/master` and merges it into the new
  template with `merger.sh`. The installer feeds its values into that mechanism (§7.3); it never ships a prebuilt
  jar.
- **The installer builds `master`**, exactly what `gtupdate.sh` builds. It records the commit it planned against
  and the commit that was built (§3.2).
- The frontend follows `gtupfrontend.sh`: built with `ng build` from 3700 MB RAM upward, downloaded as
  `latest.tar.gz` from the GitHub release `Latest` below that. Both are built with `--base-href /grafioschtrader/`
  (`buildprod` in `frontend/package.json`), so the base path is fixed to `grafioschtrader/` and is not a question.
- An existing installation is detected, reported and left untouched (§4.2). Its owner already has `gtupdate.sh`.

### 1.2 Supported platforms

| Tier | Distribution and release | Architectures | Tested on |
|---|---|---|---|
| primary | Debian 12, Debian 13, Ubuntu 24.04, Ubuntu 26.04 (incl. Raspberry Pi OS and Armbian on these bases) | amd64, arm64 | every release × architecture |
| legacy | Debian 11, Ubuntu 22.04 | amd64, arm64 | one combination each |
| legacy | any of the above | armhf | one combination (Ubuntu 22.04) |

Armbian reports `ID=ubuntu` or `ID=debian` with the base release; Raspberry Pi OS reports `ID=debian`. The
installer decides on `ID`, `ID_LIKE` and `VERSION_ID` alone.

Legacy targets run with a `WARN` that names the reason:

- **Debian 11:** regular security support ended 2024-08, Debian LTS ended 2026-08-31; only paid Extended LTS
  remains.
- **Ubuntu 22.04:** standard support ends 2027-04.
- **armhf:** Node.js 24 ships no armv7l build, so armhf stays on Node.js 22, maintained until 2027-04-30
  (§5.3). From that date on the installer reports armhf as `block`, because `checkversion.sh` will then reject the
  only available Node.js. armhf hosts never build the frontend; none has 3700 MB RAM.

## 2. Delivery

| Stage | Content |
|---|---|
| 1 | Whiptail, execution prerequisites, installation, resumption and verification in `util/installer/gt-install.sh` |
| 2 | `.deb` package `grafioschtrader-installer`, a thin package around stage 1 (§11) |

The installer relies on the update scripts returning a failed build status and on the backend's combined
UTC/collation connection initialization. The prerequisite checks in `util/installer/test/` verify this contract.

Packaging the application itself is unsuitable for this lifecycle. The jar contains the user's configuration and is
rebuilt by `gtupdate.sh` outside any package manager, the Java 25 dependency cannot be expressed against the official
repositories of Debian 11 and 12, and a package would have to reconcile its file ownership with an updater that
resets the clone. Installing the application is therefore the script's work; the package only distributes the
script.

Configuration management tools (Ansible roles, preseeding) are not used. The audience is one person installing one
host who follows the wiki today; one readable script with `--check` and `--dry-run` is the form that replaces that
page.

### 2.1 Source form

Extend the ordered Shell modules in `util/installer/src/` with the remaining resource executors and dialog interface.
Python helpers live in `util/installer/src/py/`; web and service templates remain heredocs. Run
`bash util/installer/build.sh` to assemble and inline these sources into the versioned, self-contained
`util/installer/gt-install.sh`. Users download that single file. Keep `GT_INSTALL_SOURCE_ONLY=1` and the separation
between `gt_inventory`, `gt_compatibility`, `gt_questions`, `gt_plan` and their report functions. The source layout,
build command, read-only contract and available modes are documented in
[`util/installer/README.md`](../util/installer/README.md).

Every installer CI job depends on the bundle check: rebuilding followed by
`git diff --exit-code -- util/installer/gt-install.sh` must succeed. Local checks compare the bundle without
writing it. Run ShellCheck for the modules and the complete bundle, compile the Python helpers, and run the Bats
suite against the bundle. Source changes and their generated bundle belong in the same commit.

Extend the existing `.github/workflows/installer.yml` checks and Bats tests under `util/installer/test/` with the
Whiptail navigation, the execution integration of §6.2, the destination quoting helpers of §7.4 and
execution/resumption scenarios. Reuse the existing question and planning fixtures for those front ends.
Keep shell sources and the generated bundle within the project line-length convention; audit the existing long
lines separately. Verify all container suites when changing the GitHub Actions runner image, and migrate
`actions/checkout` away from its deprecated Node.js runtime as part of CI maintenance.

### 2.2 User interface and language

The installer runs in a terminal, locally or over SSH; most target hosts are headless or reached by SSH even when a
desktop is installed. A graphical dialog (zenity, yad) would need a desktop session on the host itself, and a
temporary web wizard would send passwords across the LAN unencrypted, need a one-time token so nobody else on the
network can take over the installation, and need a free port — far more code than a Bash installer should carry.
The interface is therefore a terminal one, in three interchangeable front ends:

| Front end | Used when | Look |
|---|---|---|
| `whiptail` | a terminal is attached, `whiptail` exists, `TERM` is not `dumb`, and the terminal has at least 80 × 24 cells | full-screen dialogs as in `raspi-config`: input boxes, password boxes without echo, menus and radio lists, yes/no boxes, a progress gauge |
| plain prompts | `--plain`, or one of the `whiptail` conditions fails | one line per question, as `mariadb-secure-installation` asks |
| answers file | `--answers <file>` (§6.2) | no interaction |

`whiptail` (package `whiptail`, in `main` on Ubuntu, a standard package on Debian) is part of the default
installation of every distribution release of §1.2 and of Raspberry Pi OS and Armbian; debconf and `needrestart` use
it for their own dialogs. An installing run adds it to the base packages of §7.2 in case a minimal image lacks
it; the read-only modes install nothing and fall back to plain prompts. `dialog` offers multi-field forms but is
rarely preinstalled, so it is not used.

**One question model.** Whiptail uses `gt_question_model`, `gt_question_applies`, `gt_default`,
`gt_validate_answer` and `gt_valid_secret`, as preparation does. Keep credentials in the separate `SECRET` map
without permitting values in `gt_plan_report`. In `whiptail` the user can go back to the previous question
(*Cancel* returns one question, *Esc* asks whether to abort); a rejected value reopens the same dialog with the
validator's message. New passwords are entered twice without echo; credentials of existing accounts and tokens
are entered once without echo. Empty input never selects an implicit generated password (§6.1).

**Screens.** The `whiptail` front end shows, in order: the inventory and compatibility report of §5 as a scrollable
text box; for a domain, the checklist of §5.7 as a message box with *Continue* and *Stop here*; the questions; the
summary and action plan of §6.1 as a scrollable text box with *Install* and *Cancel*. During execution a gauge shows
the current step of §7.2 and the last line of its log; the long first build drives the gauge from the build
log, so a Raspberry Pi run of many minutes visibly progresses. The final report of §7.5 is a scrollable text box and is
also printed to the terminal after `whiptail` exits, so it stays in the scrollback; explicitly generated passwords
may be shown once on `/dev/tty` only (§7.4).

**Language.** Prompts, help texts and the final report are German when `LANG` (or `LC_ALL`, `LC_MESSAGES`) starts
with `de`, English otherwise. Extend the bilingual question definitions and message helpers for execution and
dialog messages. Button labels of `whiptail` are passed with `--yes-button`,
`--no-button`, `--ok-button` and `--cancel-button` in the same language. The log is always English.


## 3. Invocation and modes

```bash
curl -fsSLO https://raw.githubusercontent.com/grafioschtrader/grafioschtrader/master/util/installer/gt-install.sh
sudo bash gt-install.sh            # interactive installation
sudo bash gt-install.sh --check    # inventory and compatibility report only
sudo bash gt-install.sh --plain    # line prompts instead of whiptail dialogs
```

`curl … | bash` is refused: the questions read from the terminal, and a piped script has no usable stdin.

| Mode | Writes | Purpose |
|---|---|---|
| `--check` | nothing | inventory (§4) and compatibility report (§5); safe on any host, including production hosts with Grafioschtrader |
| `--dry-run` | nothing | additionally asks the questions (§6) and prints the action plan (§7) |
| *(none)* | yes | installation, or resumption of an unfinished one |
| `--answers <file>` | yes | unattended run (§6.2) |
| `--plain` | — | combinable with the modes above: plain prompts instead of `whiptail` (§2.2) |

`--check` and `--dry-run` create no log, no state file and no secret; they do not run `apt-get update`, install
packages, start or stop services, or execute `checkversion.sh` (which installs `semver` through npm). Their only
scratch space is a `mktemp -d` directory removed on exit. The report goes to standard output; the caller decides
whether to keep it. Package metadata older than 24 hours is reported as such, because candidates derived from it are
uncertain.

Installing runs create `/var/lib/gt-install/lock` and hold it through `flock`. Read-only modes retain the existing
checker's behavior: open an existing lock without creating it or the state directory. All inventory modes require
root; the help command remains available without it.

### 3.1 Version requirements

Use the requirements returned by `gt_source_revision` and `gt_parse_requirements`; their source remains
`util/shellscripts/checkversion.sh`. Question defaults and execution checks must use the same parsed values as
the compatibility report, never a separately maintained version table.

### 3.2 Source revision

Planning uses the commit and requirements returned by the existing source-revision probe. An installing run stops
when `source.requirements` is `fallback`: provisional built-in floors are sufficient for an informational report,
but cannot authorize execution.

The full-bootstrap report includes the planned and built commit from the pinned first build. Later
`gtupdate.sh` runs follow master; verify their changed `checkversion.sh` requirements before building and stop
with the difference when the selected toolchain no longer satisfies them. A source-pin transition during an
unfinished installation requires a new plan (§7.2).

## 4. Inventory integration

Reuse `gt_inventory` for the question and execution phases. The source code defines the collected facts and the
read-only report; the following requirements govern how the remaining installer consumes them.

### 4.1 System

Recollect facts before execution and on resumption. Derive defaults from the inventory, keeping unavailable values
as `unknown`. Answer-dependent probes add the selected paths, ports, DNS names, certificates and package sources;
they must retain the checker's scratch-only write policy until the user accepts the plan.

### 4.2 Classification of the host

Apply the existing `host.class` result to installing runs:

| Class | Installing run |
|---|---|
| `completed` | prints recorded completion date and built commit, then "update with `./gtupdate.sh` as user grafioschtrader"; exits 0 without changes |
| `unfinished` | validates the state and resumes (§8.2) |
| `classic` | "update with `./gtupdate.sh`"; exits without changes |
| `foreign-partial` | lists existing and missing pieces; exits without adopting or removing them |
| `docker` | "update with `docker/update.sh`"; exits without changes |
| `invalid-state` or `unknown` | blocks execution and names the information that must be resolved |
| `fresh` | continues with questions and planning |

The class never substitutes for the database checks of §5.4. An existing database without installation files
still requires those checks.

### 4.3 Components

Use the collected component versions, package candidates, listeners and configuration evidence in §5.
Before writing web configuration, resolve any `UNKNOWN` include context and establish an unambiguous effective
vhost for each selected name. Static evidence alone must not authorize editing a foreign vhost. When database
metadata is unavailable, obtain the user's root authentication choice in §6 and repeat the inventory; do not
interpret missing access as an absent schema or account.

### 4.4 Other consumers of shared components

Keep other runtime consumers, schemas and enabled vhosts in the action plan. Confirm consumers before replacing a
shared runtime, and record each affected vhost's HTTP status using its own host name and port for the comparison
after changes (§7.2). Unresolved wrapper commands or dynamic configuration prevent an automatic replacement.

## 5. Phase 2 — compatibility and plan

Every component receives an **action** and, independently, any number of **notes**:

| Action | Meaning |
|---|---|
| `reuse` | present and suitable; used as is |
| `install` | missing; installed host-wide |
| `isolate` | a host-wide version exists but does not suit Grafioschtrader and is used by others; a Grafioschtrader-only copy is installed beside it and put on the path through `gtvar.sh` |
| `replace` | the host-wide version is replaced; only with explicit consent in §6 |
| `block` | the installation cannot proceed; the run ends before the first change |

| Note | Meaning |
|---|---|
| `WARN` | true and worth knowing — maintenance status, a consumer that is affected, a setting that is not ideal; repeated in the final report |
| `UNKNOWN` | the inventory could not determine it |

"Version at least X" only says that a component meets Grafioschtrader's floor. Whether a newer runtime or database
release is tested is a note, not a pass.

Before execution the planned package transaction is simulated with `apt-get -s install …`. Every `Remv` line and
every upgrade of a package that §4.4 lists as used by others is shown in the plan; a removal is a `block` unless the
user confirms it explicitly.

Execution must resolve the provisional resources that `gt_plan_toolchains` emits for a shared Node replacement
(§5.3): the NodeSource signing fingerprint and the package candidates of its repository. Keep that transaction
blocked until a fresh simulation covers the selected versions and their dependencies. Add
explicit confirmation of each proposed removal or shared-package upgrade; a general installation confirmation must not
silently override the dry-run blockers. Database access and secret-dependent verification must be resolved before
any application database change. Recollect the inventory and compare the complete plan before executing it.

### 5.1 Java 25

The APT, vendor-archive and manual paths of `gt_core_toolchain_plan` and `gt_core_toolchains` are documented in
`util/installer/README.md`. Archive resolution needs `python3`; bootstrap it on minimal hosts together with the
prerequisites of §5.3.

Verify on real hosts (§9) that the Temurin archive on arm64 and the Liberica archive on armhf serve the
application service through a real systemd startup and reboot, and that a later `gtupdate.sh` build uses the
archive JDK on every architecture, while a different shared system Java remains selected.

### 5.2 Maven

Verify the Apache Maven archive on Debian 11 within the legacy acceptance of §9, including a later `gtupdate.sh`
build with the selected `JAVA_HOME`.

### 5.3 Node.js and Angular CLI

Exercise the selected Node and CLI in real frontend builds and release downloads.
The Node reuse/archive selection, pinned metadata, private npm prefix, checksum verification and recovery
contracts are documented in `util/installer/README.md`. Bootstrap the additional `python3` and `xz-utils`
prerequisites before metadata resolution on minimal hosts.

Verify `npm ci`, `ng build` and the low-memory release-download branch with the selected Node and CLI, including
arm64/armhf hosts and the support cutoff in §1.2. The generated environment must reach sequential and parallel
updater invocations without enabling analytics or changing other applications' Node/npm installations.

Shared Node replacement is an additional full-bootstrap choice. Before enabling `NODE_REPLACE=yes`, resolve
the NodeSource package version, signing fingerprint and complete APT transaction, list affected consumers and
obtain explicit consent. Record repository/keyring ownership and verify consumers afterward. Isolation remains
the default and the fallback when replacement cannot be authorized.

### 5.4 MariaDB

| Finding | Action |
|---|---|
| no database server | `install` `mariadb-server`, `mariadb-client` |
| MariaDB ≥ 10.3 | `reuse` |
| MariaDB < 10.3 | `block` |
| Oracle MySQL server | `block` — the migrations use MariaDB-specific SQL, and the packages exclude each other |
| server listens on a non-loopback address | `reuse` + `WARN` with the address; not changed, another application may depend on it |
| MariaDB ≥ 11.5 and the planned `application.properties` (§3.2) lacks the combined UTC/`character_set_collations` connection-init statement | `block` — that revision would build a schema with mixed collations |
| database `grafioschtrader` absent | `install` |
| database `grafioschtrader` exists without tables | `reuse` after a question in §6 |
| database `grafioschtrader` exists with tables (a Grafioschtrader schema with `flyway_schema_history`, an unfinished migration, or an unrelated schema) | `block` |
| user `grafioschtrader@localhost` absent | `install` |
| user `grafioschtrader@localhost` exists | `reuse` only when §6 supplies its password and a login with it succeeds (§7.2); the account is never altered, its password never rotated, and it is never dropped |

A non-empty database is not adopted. The application runs Flyway with `baseline-on-migrate: true` and
`baseline-version: 0.10.0` (`application.yaml`), so starting against an unknown schema would baseline it and migrate
on top. Moving an existing installation to a new host is a separate procedure after a fresh installation: stop the
service, load the dump into the empty database, start the service, and let the migrations bring it to the built
version. The report names this procedure; it never touches the schema itself.

The database user is fixed to **`grafioschtrader@localhost`**, because the migrations create triggers and procedures
with `DEFINER=grafioschtrader@localhost` (e.g. `V0_10_0__init.sql`). Making it configurable would need its own
migration design. The database is created with `CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci`.

Use `gt_core_database` for empty-schema bootstrap and its ownership journal for MariaDB resources. The
root-authentication and existing-account contracts are documented in `util/installer/README.md`; full bootstrap
must preserve them when installing toolchains or resuming later steps. Full bootstrap must use the application stage's post-start verifier once the owned schema is no longer empty.

MariaDB 11.5 and later map `utf8mb3`/`utf8mb4` to the `uca1400` collations. The migrations create tables with and
without an explicit `COLLATE`, so the server mapping has to be overridden on every connection the application and
Flyway open. The backend's `spring.datasource.hikari.connection-init-sql` combines this mapping with UTC
initialization. The installer changes no server-wide collation setting, because the
database server may be shared. After the first start it verifies the result (§7.2).

`innodb_buffer_pool_size` is offered from the RAM table of `docker/install.sh` (< 3000 MB → 384M, < 6000 MB → 1G,
otherwise 2G) only when no file under `/etc/mysql` sets it, written to
`/etc/mysql/mariadb.conf.d/60-grafioschtrader.cnf`, and applied by a restart that the plan lists together with the
other databases on that server. Both need consent.

### 5.5 Web server

Integrate `gt_install_web` into full bootstrap using the saved selection and the ownership, routing and
verification contract documented in [`util/installer/README.md`](../util/installer/README.md).

**Shared vhosts.** When an existing nginx or Apache vhost owns the selected name, publish the GT routes as
`/etc/nginx/snippets/grafioschtrader.conf` or `/etc/apache2/conf-available/grafioschtrader.conf`. Check the effective
vhost for existing `/api`, `/m2m`, `/socket`, `/ws` and `/grafioschtrader` routes. With no conflict and an unambiguous
insertion point, offer a consented include after a backup `<file>.gt-install.<timestamp>`. Otherwise leave the
snippet for manual inclusion and report web integration as pending. Restore the backup if configuration testing,
route verification or comparison with the original shared-site responses fails.

**Co-resident proxies.** Extend `gt_extended_preflight` and the site renderers for a different local process
owning port 80. With `TLS_SOURCE=proxy`, place the domain and LAN access on the approved `TLS_PROXY_LISTEN`
without starting a conflicting distribution default site. Show the LAN URL including its port. Keep all foreign
listeners and websites intact. When no integration is possible, write the proposed configuration to
`/root/gt-install-webserver.conf` and report the installation as incomplete (§7.5).

Allow proxy port 80 when it is free and domain/LAN routing is unambiguous; otherwise propose the next free port
from 8081 upward, excluding both backend connectors. When the LAN site moves off port 80, `gt_firewall_rules` must
allow its port instead. A file-based root landing page may be created only inside an installer-owned document root.

### 5.6 Memory, swap and disk

The memory warnings, heap defaults and space requirements are planned by `gt_compatibility` and
`gt_memory_defaults`; the swap file contract (`gt_core_swap`) is documented in `util/installer/README.md`.

Verify the low-memory path on a real arm64 or armhf host below 4000 MB RAM without swap (§9), where the swap file
and the frontend download carry the first build on the slowest hardware. Below 3700 MB `gtupfrontend.sh` downloads
`latest.tar.gz`, which is rebuilt on every frontend push to `master` and can be newer than the built backend; this
limitation belongs in the gt-user-manual (§10).

### 5.7 DNS and public reachability

The domain answer of §6.1 selects one of three DNS modes. The installer cannot tell a static domain from one kept
current by another dynamic DNS service, and does not need to: in both cases someone else maintains the records.

| Mode | Selected by | Updater | Records maintained by | TLS |
|---|---|---|---|---|
| LAN only | `DOMAIN` empty | — | — | none, plain HTTP in the LAN (§5.8) |
| DuckDNS | `DOMAIN` ends in `.duckdns.org` | installed by the installer, unless one already runs | the installer's updater, or the existing one | required, source per §5.8 |
| own domain | any other `DOMAIN` (static, or another dynamic DNS service such as dynv6, No-IP, or the router's own client) | — | the user at the registrar, or the existing client or router | required, source per §5.8 |

**What only the user can do.** No script can create a DuckDNS account — there is no API for accounts or subdomains
— and no script can configure the router. Right after the domain answer, before the remaining questions, the installer
prints the checklist for the selected mode, filled with the values it determined:

| Mode | Checklist |
|---|---|
| DuckDNS | account on duckdns.org, subdomain created, token copied; router forwards TCP 80 and 443 (IPv4) to `<lan-ipv4>`; router firewall admits TCP 80 and 443 (IPv6) to this host |
| own domain | record `A <domain> → <public-ipv4>` and/or `AAAA <domain> → <public-ipv6>` at the registrar, or the existing dynamic DNS client pointing there; the same router settings as for DuckDNS |
| LAN only | nothing |

The user can stop here, complete the list and start again; nothing has been changed yet.

**Public addresses.** The installer determines which address families can carry the domain:

- *IPv6:* the global addresses of the interface that holds the IPv6 default route (`ip -6 route show default`),
  excluding `fd00::/8`, `fe80::/10`, temporary (privacy) and deprecated addresses — `ip -6 addr show dev <if> scope
  global -temporary -deprecated`. Privacy addresses change daily and cannot carry a record.
- *IPv4:* the address the internet sees, from an echo service (`curl -4 -s https://ifconfig.co`), and the LAN address
  from `ip -4 route get 1.1.1.1`. A private LAN address means the router translates, so the port forwarding of the
  checklist is required.

Whether IPv4 is reachable from the internet at all cannot be proven from the host: behind DS-Lite or carrier-grade
NAT the echo service answers with a shared address that no port forwarding reaches. `DNS_FAMILY` (§6.1) therefore
asks, with `both` as the default when both families exist and the explanation that `ipv6` is the right answer on a
DS-Lite or CGNAT line. Let's Encrypt validates over IPv6 when an AAAA record exists, so with `both` the router must
admit port 80 on both families; a record for a family that is not reachable is worse than no record.

**DuckDNS updater.** Detection, the `DUCKDNS_UPDATER` default, the token handling and the updater installed by
`gt_core_duckdns` (script, protected token file and systemd timer) are documented in `util/installer/README.md`.
Verify with a real DuckDNS subdomain (§9): after the update the records equal the host's addresses for every
`DNS_FAMILY`, with `ipv6` no A record exists, the certificate is issued for both names, and the timer follows a
changed IPv6 prefix within five minutes.

### 5.8 TLS

Extend the TLS paths in `gt_domain_plan`, `gt_tls_issue`, `gt_tls_verify` and `gt_install_extended_web`.
Their three TLS sources, HTTP-01 webroot ownership, certificate validation and renewal behavior are documented in
[`util/installer/README.md`](../util/installer/README.md). Domain and LAN access remain separate, including when
integration uses a consented foreign vhost (§5.5).

**Certificate defaults and reuse.** Extend inventory-driven defaults to find certificates referenced by enabled
vhosts and managed by acme.sh, dehydrated or lego. Propose `existing` with those paths. A Certbot lineage covering
the requested names may be reused only with explicit consent and ownership checks; preserve its renewal client,
authenticator and hooks. Never adopt or replace unrelated certificates. Prefer `proxy` when Caddy, Traefik,
a container or a tunnel already terminates TLS.

If consented Certbot reuse fails its renewal dry run, name the saved authenticator and webroot as possible
causes without changing them. Existing `standalone` authentication or another webroot may require the owner
to adjust their renewal setup.

**External acceptance.** Combine locally verified TLS and `step.tls=unverified` proxy outcomes with the report
rules in §7.5. A local probe cannot prove public reachability through NAT or a router. Report the exact public URL
and remaining external check. Verify that the real application retains the original client address for login
lockout and uses the public browser URL for registration confirmation links.

**Later changes.** Switching a completed LAN-only installation to a domain or between TLS sources is a manual
operation. Carry the per-source renewal/reload responsibilities and transition instructions into gt-user-manual
before retiring this specification.

## 6. Phase 3 — questions

### 6.1 Questions

Extend the shared question model and the execution boundary of `gt_install_core` to the full bootstrap scope.
The input, credential persistence and confirmation contracts are documented in
[`util/installer/README.md`](../util/installer/README.md). Full installation consumes the same journal and secrets;
upgrading a core-only run must preserve its installation ID, original credentials and resource ownership.

Whiptail must provide password boxes and matching confirmation for new passwords. When navigation changes an
earlier answer, discard configuration and secrets whose conditions no longer hold and recompute dependent
defaults and checks. Preserve the planner's document-root safeguards: modifying a foreign vhost requires its
effective configuration, routes, document root and exact include location to be known.

An explicit password-generation action may be offered for new DB/root/Jasypt passwords. Empty input never selects
it, and existing credentials cannot be generated. Use the `gen_secret` alphabet in `docker/install.sh`
(`openssl rand -base64`, removing `/`, `+`, `=`), with 24 characters for generated passwords. The internally
generated JWT remains 48 characters. Show a generated password once on the terminal for the user to record (§7.4).

The questions end with a summary and action plan listing every package, repository, file, user, database object,
service and firewall rule to be changed and every foreign file to be backed up. The user confirms before execution.
Re-inventory immediately before the first mutation; a changed port, package or other planned resource stops the
run and requires a new plan. Obtain ACME terms consent before issuing a certificate. Offer `ADMIN_EMAIL` as the
Let's Encrypt email default when already available.

### 6.2 Answers file

Extend the existing answers-file front end to the full installation scope and additional planned actions.
Retain the preparation/core parser's literal-data and conditional-validation rules. Scope-wide confirmation
must not silently authorize package removals, shared-runtime replacement or changes to previously selected
credentials. New choices introduced during full bootstrap must be included in the confirmed plan.

## 7. Phase 4 — execution

### 7.1 Conventions

- `DEBIAN_FRONTEND=noninteractive`. A held dpkg lock is waited for up to 10 minutes with the holder named, then
  reported as an error; the run never starts a package transaction it cannot finish.
- Everything inside the home directory — `git`, Maven, the encryption of §7.3, `gtupdate.sh` — runs as
  `sudo -H -u grafioschtrader bash -c '. ~/gtvar.sh; …'`. Running it as root would leave root-owned files the next
  `gtupdate.sh` cannot replace.
- Third-party APT repositories get a keyring under `/etc/apt/keyrings` and a `signed-by` entry; tarballs are verified
  against the vendor's published checksum before unpacking.
- `set -x` is never used; secrets never reach the log (§7.4).

### 7.2 Steps

Each step verifies its target state before acting and records the outcome in the state file (§8.1).
Use the application-stage functions in `gt-install.sh` for pinned builds, privileged service configuration,
cron publication and the first-start boundary; their execution and resumption contracts are documented in
`util/installer/README.md`.

1. **Additional prerequisites.** Install the `python3` and `xz-utils` prerequisites of §5.1 and §5.3 and resolve
   the NodeSource transaction of §5.3 before approval. Support installing missing DNS tooling before resolving the final
   certificate-name set; present any resulting scope changes for confirmation before continuing.
2. **Web server:** add consented foreign-vhost snippets,
   co-resident proxy support and selected firewall changes (§5.5), with rollback of foreign-file backups.
3. **DNS and TLS:** install and verify the selected local DuckDNS updater (§5.7), then invoke the TLS stage.
   Add certificate discovery and consented reuse (§5.8). Connect failure/retry instructions and private Certbot
   diagnostics to the terminal/Whiptail interface; retain working HTTP after certificate failures.

4. **Application interface:** connect its private build log and
   verification progress to the terminal/Whiptail interface (§2.2). Provide an explicit, re-planned transition
   for an older core revision whose helpers lack the build-only contract; never silently change its source pin.
5. **Migration recovery:** a wrong-collation schema may be recreated only after separate explicit confirmation and only when the
   installer created it. Never treat a restored JAR or web configuration as a database rollback.

### 7.3 Configuration contract

Verify later `gtupdate.sh` runs against the configuration produced by the application stage. The generated properties, Jasypt
file-goal encryption, exact decryption check and service-launcher contract are documented in
`util/installer/README.md`. Keep encryption/decryption diagnostics outside the installation log.

Verify the update boundary: `merger.sh` retains user lines only for keys present in the new template and drops
additional keys; `application-production.properties` is restored unchanged. Exercise template keys, production
overrides and the assigned cron slot across a real update without replacing the generated launchers.

The backend milestone must demonstrate that the service decrypts and uses the configured database credentials;
the mail milestone demonstrates the selected SMTP configuration. Additional choices and toolchain changes must
regenerate only the affected owned configuration after confirmation, preserving all original application secrets.

### 7.4 Quoting and secrecy

Reuse the core's shell, SQL and properties serialization helpers when extending generated configuration.
Add destination-specific quoting for curl configuration and URL components, and accept validated host names
only for web server configuration. Jasypt encryption does not make shell interpolation safe; the helpers apply regardless.

Whiptail password boxes must preserve the preparation helpers' hidden-input contract. Preserve
literal characters through shell, SQL, curl configuration, URL encoding and properties
serialization; each format needs its own escaping. SQL quoting must match the session's SQL mode. Do not put a
password in `-p...`, `--password=...`, `--user user:password`, `-D...`, or an interpolated DuckDNS request URL in a
process argument. In particular, DuckDNS's token-bearing URL is supplied through protected curl configuration;
neither it nor the response/error diagnostics may echo the token.

Persist application secrets only in `/root/.gt-install/secrets`, the service launcher (`JASYPT_PASSWORD`, mode
700), the DuckDNS updater's protected configuration (`DUCKDNS_TOKEN`, mode 700/600 as appropriate), and as `ENC(...)`
in application properties. Temporary credential files are mode 600 in a private scratch directory, removed on
success, error and cancellation. The MariaDB root password is never written to persistent installer files,
reports, backups or state; request it again on resumption whenever password authentication is needed. The caller's
answers file is an input they own, not an installer backup (§6.2).

Plans and confirmation summaries display only secret names and statuses (*supplied*, *reuse*, *generate*), never
their values. Supplied passwords/tokens are not repeated in the final report. Explicitly requested generated
passwords may be displayed once to `/dev/tty` for the user to record, never to logged stdout; a generated root
password must be recorded before its value is discarded. The internal JWT key need not be displayed. Shell
tracing, command/debug output and exception messages must not bypass this rule. Any Jasypt child-process
environment containing credentials is restricted to that process and not retained for later commands.

### 7.5 Result integration

Extend `gt_handover` and the completion guard in `util/installer/src/95-result.sh` for new resources.
The result format, milestone meanings, SMTP delivery distinctions, publication ordering and completed-rerun
behavior are documented in `util/installer/README.md`. Extend the existing result with pending foreign-vhost
inclusion and `WEBSERVER=none`; preserve incomplete outcomes until those routes are verified.

Persist warnings from each newly added bootstrap stage. Show the same result in the Whiptail interface and
retain `/var/lib/gt-install/result` for unattended runs. A full-bootstrap journal may be marked complete only
after its own additional resources and the existing application, web, TLS and mail evidence have been verified.

## 8. State, resumption and failure handling

### 8.1 State file

Extend the core journal in `/var/lib/gt-install/state` and its strict parser with the remaining bootstrap
resources: packages, repositories/keyrings, web files, backups and final verification
milestones. Preserve the application-stage resource hashes and first-start journal. Retain atomic replacement, private permissions, the
installation ID and the separate protected secret store. Preserve schema-1 single-stage journals, including
completed journals, the approved full-plan receipt, the bundle digest and completion timestamp. Extend the
strict completion validator whenever new resources are added; completion requires their verification evidence.

### 8.2 Resumption

Extend verification and recovery to every added resource and execution step. Reuse the core's original
credentials and ownership records. Keep missing-secret recovery explicit; never replace credentials to bypass
a failed step. Changed answers must identify and invalidate only their dependent steps in a new confirmed plan.

Retain the application's first-start boundary when adding recovery paths: populated schemas go through the
post-start verifier. Web and TLS interruptions must preserve verified application targets and shared resources.
Never automatically drop data as a recovery action.

### 8.3 Failures

There is no global rollback; a partly installed toolchain is harmless and reused by the next run. Every failing step
names the log, the step and how to resume. Every foreign configuration file is backed up before it is modified, and
a failed configuration test restores it. `block` findings end the run before the first change. Rolling back a web
server file or a jar never rolls back database migrations; the report says so wherever it offers a restore.

## 9. Verification of stage 1

Full installations run only on disposable systems; the production hosts contribute read-only evidence. A successful
existing installation does not prove that a clean bootstrap works. QEMU/VM acceptance runs require an explicit request.

| Scenario | Required evidence |
|---|---|
| fresh installation per primary release of §1.2 on arm64, and one per legacy row (Debian 11, Ubuntu 22.04, armhf) | backend built locally, frontend served, listeners on loopback as in §5.5, migrations complete, no `uca1400` column, service up again after a reboot |
| `--check` and `--dry-run` on the reference hosts: Debian 13 with nginx and Home Assistant, Debian 13 with nginx, Armbian on Ubuntu 26.04 with Apache, Debian 11 with Apache, Ubuntu 22.04 armhf with Apache, Debian 12 with Apache and a socket-activated MariaDB, and the Debian 13 Docker host | correct class (§4.2) on each, complete component report, no file, package or service changed |
| interruption during toolchain, application, web and TLS changes | resumption recognizes its own resources, reuses the secrets, re-verifies, creates nothing twice |
| rerun after success | "completed" result with update instructions, no change |
| injected Maven, `npm ci`, download, extraction and frontend build failures | non-zero result in both build branches, no invalid deployment, diagnostics in the log |
| a changed template key and an added key in `application-production.properties` | both survive the pinned first build and a later `gtupdate.sh` |
| empty database on MariaDB 10.5 and 11.8 | time zone and collation initialization effective, all migrations succeed |
| existing database user, non-empty database, unrelated schema | correct classification, password verified by login, nothing adopted or rotated |
| existing MariaDB with socket access, with password-only access, and interrupted root setup on an installer-created server | no credential question when privileged socket access succeeds on an existing server; otherwise current password requested; no foreign root password/plugin changes; resumed root setup re-verifies the original password |
| administrator address different from SMTP sender | chosen address written to `g.main.user.admin.mail`; registration at exactly that address receives administrator roles; SMTP sender stays independent |
| secret special characters and cancellation during execution | literal values survive shell/SQL/curl/properties serialization; no value in logs, errors or argv; temporary credentials removed on every exit path |
| other Node.js and Java consumers, a shared document root, a PHP location on the same nginx vhost | consumers still on their runtime, alternatives unchanged, other sites answer as before |
| occupied 8080/9090, several vhosts | alternative ports used consistently, no listener off loopback, no traffic routed to another site |
| SMTP skipped, snippet not included, certbot failing | `incomplete` with the remaining actions, working steps kept |
| DuckDNS with the installer's updater, `DNS_FAMILY` `ipv4`, `ipv6` and `both` | records equal the host's addresses; with `ipv6` no A record exists; certificate issued; the timer run follows an address change |
| DuckDNS with an updater already in a crontab | found by the inventory, `DUCKDNS_UPDATER` defaults to `no`, no second updater, no token asked |
| own domain with correct records, with a wrong A record, without a `www` record | certificate issued; mismatch reported with both values and the certbot command; certificate and vhost without `www` |
| `proxy` with Caddy on the same host owning 80/443, and with a proxy on another machine | local vhost on the chosen port, `X-Forwarded-For` reaches the login lockout as the client address, registration link carries the public URL |
| `whiptail` on Debian 13 and Ubuntu 26.04 over SSH; `--plain`; a terminal smaller than 80 × 24; `LANG=de_CH.UTF-8` and `LANG=en_US.UTF-8` | dialogs and buttons in the right language, *Cancel* returns one question, a rejected value reopens its dialog, the small terminal falls back to plain prompts, both front ends produce the same plan for the same answers |
| hand-over and real application mail | run `--check-mail` against the deployed Grafioschtrader JAR, including Boot launcher and production-profile resolution; then edit `application.properties`, run `./gtupdate.sh` as `grafioschtrader`, application answers again with the edit in effect |

## 10. Follow-up outside the code

- The wiki page *Installation on Debian 10 to 13 based Linux* names the installer as the primary way; the manual
  steps stay as reference for unsupported systems. The nginx page's sentence that points nginx at 8080 contradicts its
  own `proxy_pass` blocks and the connector settings (9090); it is changed to 9090.
- The installation pages of the gt-user-manual get the same note (`update-user-manual` skill), including the armhf end
  date of §1.2, the frontend-version limitation of §5.6, the configuration contract of §7.3, the DNS checklists of §5.7, the three TLS sources and the later switch from LAN-only to a domain (§5.8) and the procedure for moving an existing database to a new host (§5.4).

## 11. Stage 2 — Debian package

`grafioschtrader-installer`, `Architecture: all`:

- installs `gt-install.sh` as `/usr/sbin/gt-install` and nothing else;
- `Depends:` only packages available on every release of §1.2: `bash`, `curl`, `git`, `sudo`, `logrotate`,
  `ca-certificates`;
- no `postinst` that installs Grafioschtrader; it prints "run `sudo gt-install`". Upgrading the package never reruns
  the installation, and removing it leaves the installed application, database and configuration in place;
- distributed through a signed APT repository on GitHub Pages, built by a workflow on the published release next to
  `.github/workflows/docker.yml`. Enrolling the repository on a host and maintaining its signing key are part of this
  stage's cost.

Stage 2 starts only after stage 1 has installed a disposable machine of every primary combination.

## 12. Decisions

| # | Decision | Reason |
|---|---|---|
| 1 | Bootstrap only; updates stay with `gtupdate.sh` | the update path works; the gap is the first installation |
| 2 | Backend built on the target, from `master` | user configuration is packaged into the jar and merged by `merger.sh`; `gtupdate.sh` follows `master` |
| 3 | Bash script first, thin `.deb` second; no application package, no Ansible | Java 25 not resolvable as a dependency on Debian 11/12; one host, one readable script |
| 4 | Modular Shell/Python sources, one versioned self-contained bundle, and a required rebuild comparison before all installer CI jobs | downloaded alone from `master`; deterministic assembly and a byte-for-byte CI check keep source and bundle together |
| 5 | Existing installations are classified and left alone; the installer's own state decides first | they already have `gtupdate.sh`; resumption must still work |
| 6 | Version requirements parsed from `checkversion.sh`, never executed | one source for installer and updater; the script installs `semver` |
| 7 | Shared runtimes are reused or isolated, replaced only by explicit choice; Java alternatives restored | other programs on shared hosts |
| 8 | Database user fixed to `grafioschtrader@localhost`; existing accounts verified, never altered; non-empty databases never adopted | `DEFINER` in the migrations; `baseline-on-migrate` |
| 9 | No server-wide collation change; the backend's init SQL carries it | shared database servers |
| 10 | All backend listeners on loopback; `server.*` keys in `application-production.properties` | the proxy is local; `merger.sh` drops keys outside the template |
| 11 | nginx uses the HTTP connector, Apache2 AJP; with both installed the listening one wins | matches the existing installations and the template default |
| 12 | Own vhost, or an included snippet only with consent and when routes do not collide | hosts share the web server with other applications |
| 13 | Base path fixed to `grafioschtrader/` | the downloaded frontend is built for it |
| 14 | SMTP optional; without it the result is `incomplete` | the installation works, registration does not |
| 16 | Milestones and exit codes instead of one success flag | unattended runs and honest reports |
| 17 | Prompts German or English after `LANG` | the audience arrives through the German and English wiki and videos |
| 19 | Three DNS modes; an updater only for DuckDNS and only when none runs yet; static domains and other dynamic DNS services get no updater | the installer maintains only what it owns; two updaters fight over one record |
| 20 | Address family asked, interface derived from the default route | DS-Lite and CGNAT make IPv4 unreachable in ways the host cannot detect; a hard-coded interface belongs to one installation |
| 21 | Mode checklist printed before the remaining questions | account, token, records and router settings are the user's work and must be done before certbot can succeed |
| 22 | A domain always means TLS; LAN-only means plain HTTP without any certificate | public bearer-token sessions need HTTPS; a self-signed certificate helps nobody in the LAN |
| 23 | Three TLS sources: certbot by the installer, an existing certificate, a TLS-terminating proxy | users already run other ACME clients, commercial certificates, reverse proxies and tunnels |
| 24 | certbot only with HTTP-01; DNS-01 and wildcards through `existing` | DNS provider APIs are out of the installer's reach |
| 25 | Existing certificates are referenced, not copied; no renewal hook installed | the user's renewal tool keeps owning the files |
| 26 | Terminal interface with `whiptail`, plain prompts as fallback; no desktop dialog, no web wizard | headless hosts reached by SSH; `whiptail` is preinstalled on Debian and Ubuntu; a web wizard would expose secrets in the LAN |
| 27 | Every question defined once, rendered by interchangeable front ends | identical validation in dialog, prompt and answers file; testable without a terminal |
| 28 | Explicit credential input for installation; manual entry by default for new DB/root/Jasypt passwords, generation only by explicit choice; JWT generated internally | each required credential is consciously supplied or selected, external credentials cannot be invented, and dry-run needs no secret values |
| 29 | Root password set only on an installer-created MariaDB server, while retaining socket administration | meets the new-server password requirement without changing shared-server authentication or disabling local administrative access |
| 30 | SMTP authentication, transport security and sender are explicit inputs | the application uses `spring.mail.username` for login and sender; empty values cannot silently select a different authentication mode |
