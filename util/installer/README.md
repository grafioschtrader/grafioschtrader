# Linux installer: bootstrap and individual stages

## Development and bundle

Edit `src/*.sh` and `src/py/*.py`, then regenerate `gt-install.sh`. The generated file is versioned and remains the
only download needed on an installation host. `GT_INSTALL_SOURCE_ONLY=1` suppresses the command-line entry point.
Do not edit it directly or run the source modules as standalone installation commands.

```bash
bash util/installer/build.sh          # regenerate, preserving mode; no rewrite when already identical
bash util/installer/build.sh --check  # read-only comparison, exits nonzero on drift
bash util/installer/test/check-shell.sh
```

The build requires Python 3.9 or later and uses only its standard library. It does not fetch dependencies or execute
installer functions. `build.py` fixes module order, checks the complete module inventory and expands two markers:
`# @python name.py` inside a quoted `PY` heredoc, and `@python-inline name.py@` inside a shell single-quoted `-c`
argument. Inline expansion escapes Python apostrophes for the shell and preserves stdin and command arguments.
Helpers must be UTF-8 without BOM, use LF with a final newline, and may not contain a standalone `PY` delimiter
when embedded in a heredoc. Unknown markers, unused helpers and invalid Python stop the build before publication.

| Source | Responsibility |
|---|---|
| `00-common.sh` | shared state, messages, probes, quoting and source/version requirements |
| `10-inventory.sh` | host, packages, runtimes, database, network, web and DNS inventory |
| `20-compat.sh` | compatibility, port recommendations, inventory orchestration and reports |
| `30-questions.sh` | defaults, validation, prompts, answer files and credential preparation |
| `40-plan.sh` | action plans, shared stage contracts and dry-run/preparation orchestration |
| `50-state.sh` | private files, atomic journal and secret storage |
| `60-core.sh` | user, clone, database, encrypted configuration, toolchains and build tools |
| `70-app.sh` | application build, privileged resources, first start and verification |
| `80-web-nginx.sh` | nginx inventory, LAN routing, verification and rollback |
| `81-web-sites.sh` | domain sites, Apache, TLS and shared-site verification |
| `90-mail.sh` | SMTP check and its recorded outcome |
| `95-result.sh` | recorded milestones, final hand-over and read-only completion recognition |
| `96-bootstrap.sh` | full-plan approval, pinned APT transactions and stage orchestration |
| `99-main.sh` | core execution entry point, cleanup, CLI and source-only guard |
| `py/*.py` | embedded Python programs, compiled and tested as source files |

All installer CI jobs depend on `bundle`, which rebuilds and requires a clean diff for `gt-install.sh`.
Commit source changes and their generated bundle together. The local shell check also rejects drift before testing;
it works with a read-only repository mount. Full-bundle ShellCheck retains cross-module checks that cannot be
meaningfully applied to an individual module.

## Invocation

Start without a mode for the full bootstrap. The inventory, preparation and individual stages remain available:

```bash
sudo bash util/installer/gt-install.sh
sudo bash util/installer/gt-install.sh --answers /root/gt-answers --yes
sudo bash util/installer/gt-install.sh --check
sudo bash util/installer/gt-install.sh --check --plain
sudo bash util/installer/gt-install.sh --dry-run --plain
sudo bash util/installer/gt-install.sh --prepare --plain
sudo bash util/installer/gt-install.sh --prepare --answers /root/gt-answers
sudo bash util/installer/gt-install.sh --install-core --plain
sudo bash util/installer/gt-install.sh --install-core --answers /root/gt-answers --yes
sudo bash util/installer/gt-install.sh --install-app
sudo bash util/installer/gt-install.sh --install-web
sudo bash util/installer/gt-install.sh --check-mail
sudo bash util/installer/gt-install.sh --check-mail --answers /root/gt-answers --yes
```

The script is self-contained and can be copied to a Debian/Ubuntu host. Save it as a file before running it;
execution through `curl | bash` is refused. `--help` works without root. Modes cannot be combined; `--answers` is
accepted without a mode, with `--prepare`, `--install-core` and `--check-mail`. The three `--install-*` modes make
installation changes;
`--check-mail` records verification and can send the previously selected test message. Each accepts `--yes`
to use the saved scope without another terminal confirmation. Whiptail dialogs remain pending.

The modeless controller presents core, package dependencies and versions, application build/start, web/TLS and
mail delivery in one plan. Type `install` to approve it; `--yes` supplies that confirmation for unattended runs.
A fresh unattended run also needs `--answers`; a resumed `--yes` run reuses its saved answers and original secrets.
Changed answers are refused. Missing root credentials still require input when database bootstrap is unfinished.
Once the core is complete, resumption goes through application verification and never repeats empty-schema setup.

The controller rechecks inventory after approval and pins every newly installed APT dependency to an approved
version. Changed transactions stop for a new plan. It checks foreign application targets and the effective shared
web configuration before core changes, then compares web/DNS/TLS configuration again after the build. Individual
stage preflights remain active. Existing schema-1 core journals can enter `scope=bootstrap` only after approval;
completed journals stay read-only. The result and exit-code contract below also applies to modeless installation.

For domain installations using local TLS, install `bind9-dnsutils` first if `dig` is missing, then run the modeless
installer again. The full plan blocks before any changes until DNS and the certificate-name set can be checked.
The separately selected core/web stages retain their explicitly confirmed DNS-prerequisite transaction.

Startup diagnostics recognize `Access denied for user`, `FlywayException` and `APPLICATION FAILED TO START`
without waiting for the 15-minute health timeout. A persisted log cursor and systemd invocation identity limit
the scan to the current attempt, including resumption and log rotation. Public output contains only a failure
category and `/var/log/grafioschtrader.log`, never raw application diagnostics. An older unjournaled running
attempt must be inspected and stopped before retrying if it is not already healthy. No database is dropped or repaired.

Planning and installation share a stage contract, including on resumption. Before saving immutable answers or
executing the core, it rejects `WEBSERVER=none` with `TLS_SOURCE=letsencrypt` and selected names belonging to
foreign vhosts, except a domain included with `VHOST_INCLUDE=yes` and `TLS_SOURCE=existing` (see below).
The proxy-port default starts at
8081 and excludes the selected backend ports. Ports 80/443 and duplicate connector/proxy ports are rejected with
the offending answer named. Existing journals with unsupported selections also stop; original secrets and
ownership records must be retained. Changing saved installation answers remains pending work.

## Domain, TLS and Apache

`--install-web` consumes the immutable choices saved by the core. Select `WEBSERVER`, `DOMAIN`, the TLS source
and applicable fields before `--install-core`; this command is not a conversion tool for an existing LAN-only
installation. It supports nginx and Apache, keeps the separate HTTP LAN site, and returns **10** after verification.
Required web ports must be free or owned by the selected server. No firewall/router changes are made.
The confirmation lists the web packages and Apache modules using the same selections as execution. Certbot uses
webroot validation and needs no nginx/Apache Certbot plugin. Apache enables `proxy`, `proxy_ajp`, `proxy_http`,
`proxy_wstunnel`, `headers`, `alias`, `dir`, `mime`, `authz_core`, `deflate` and `filter`; local TLS adds `ssl`.
`a2enmod` also enables those modules' dependencies. The renewal hook resolves `systemctl` through PATH.

Local domain TLS requires `dig`. If it is missing, `--dry-run` and `--prepare` report the missing prerequisite
and plan `bind9-dnsutils` without installing it. `--install-core` and `--install-web` offer a separate package
transaction, confirmed with `install-dns-tools` (or the existing `--yes` option). Stale APT metadata, an unavailable
package lock, package removals and upgrades block this transaction. After installation the installer refreshes
the inventory and repeats DNS checks before continuing with core or web changes. A DNS failure leaves the
prerequisite package installed; a retry reuses it. LAN-only and upstream-proxy setups do not require `dig`.

For an enabled IPv6 family, published AAAA records must form a nonempty subset of the suitable global host
addresses on the default interface; unused host addresses need no DNS record. Every published address must
belong to that set. Empty or unknown results fail the match, and disabled address families must have no records.
IPv4 records must match the detected public IPv4 address. These checks also determine whether `www` is included.
DNS matching does not establish public reachability; router and firewall access still need separate verification.

- `TLS_SOURCE=existing`: validates the full certificate chain, names, expiry, matching key and private key
  permissions; references `TLS_CERT` and `TLS_KEY` without copying them. DNS mismatches are warnings. The owner
  must renew the certificate and reload the selected web server afterward.
- `TLS_SOURCE=letsencrypt`: requires matching public DNS for the selected address families. Adds `www` only
  when it matches and installs Certbot. `LETSENCRYPT_CERT_NAME` proposes an existing matching Certbot lineage;
  accepting that name and the installation plan authorizes reuse. Its certificate/key must cover all selected
  names and pass the usual trust, expiry and permission checks. The root-owned renewal configuration and existing
  schedule are preserved. An additional installer-owned deploy hook reloads the selected server only when that
  lineage renews. A scoped `renew --cert-name ... --dry-run` verifies the existing renewal configuration.
  Choosing `-` explicitly requests a new `gt-install-<installation ID>` certificate via `certonly --webroot`, with
  an owned HTTP site, `certbot.timer` and a reload hook. This path needs public TCP 80 for every requested name
  now and at renewal; other issuance methods use an existing certificate. Older journals without this answer
  propose reuse during the web confirmation; an already started installer issuance retains its recorded lineage.
  The selected lineage and renewal configuration hash are recorded; changes block resumption. The installer writes
  its own HTTPS site and redirect; Certbot never rewrites foreign or managed vhosts. Unrelated lineages are retained.
  See [Certbot's webroot documentation](https://eff-certbot.readthedocs.io/en/stable/using.html#webroot).
- `TLS_SOURCE=proxy`: serves the domain over HTTP on `TLS_PROXY_LISTEN`, preserving `X-Forwarded-Proto`.
  Choose a free port distinct from 80, 443 and both backend ports. The upstream proxy must preserve `Host` and
  overwrite client-supplied forwarding headers with the actual client address/protocol. Optional `TLS_PROXY_FROM`
  restricts this site to that address and
  loopback. The separate LAN site still needs port 80: a different local server already owning port 80 requires
  manual integration. HTTPS is checked through the public proxy; a failed external probe is explicitly recorded
  as `step.tls=unverified`, never as verified TLS.

Apache uses AJP for `/api` and `/m2m`, and the separate loopback HTTP connector for WebSockets. It installs
`sites-available/grafioschtrader.conf`, plus `grafioschtrader-domain.conf` for a domain and a temporary
`grafioschtrader-http.conf` for ACME bootstrap. nginx uses the same names without `.conf`. Both serve the SPA,
preserve the `/socket/websocket` to `/socket` mapping, allow 50 MB requests and compress relevant content.
Apache's effective vhost/include dumps are checked before publication. A sole, package-checksum-matching
Apache default site with an implicit IP name is disabled with its original link journaled; named foreign sites
are preserved. Address-specific/wildcard/ambiguous foreign vhosts, Apache foreign ports other than 80/443 and
name collisions require manual integration.

TLS verification checks trusted HTTPS with SNI for every selected name, the served certificate fingerprint,
the real backend identity, exact frontend index/JavaScript and nested routes. Failed activation restores the
previous own links and reloads; a failed ACME request retains HTTP. Apache module enablement is recorded for
rollback. Original shared-site statuses survive resumption. Repeat `--install-web` after resolving the cause;
configuration drift or missing original secrets blocks it. Certbot diagnostics are root-only in
`/var/lib/gt-install/tls.log`. A local DuckDNS updater is installed by the core (see below); an existing router
or other client may manage DuckDNS with `DUCKDNS_UPDATER=no`.

### Client addresses and login lockout

LAN, HTTP and TLS edge sites overwrite `X-Forwarded-For` with the connection's client address. In `proxy` mode,
the local web server appends its peer to the upstream chain. The backend honours that chain only when its direct
peer is explicitly trusted, walking from right to left to the first untrusted address. AJP carries the client's
address as the remote address, so direct untrusted clients' forwarding headers are ignored there too.

`g.security.login.trusted-proxies` is a comma-separated list of literal IPv4/IPv6 addresses, defaulting to
`127.0.0.1,::1`. Empty disables forwarding-header trust. Private networks and host names are not implicitly trusted.
The installer writes this property to `application-production.properties`, adds `TLS_PROXY_FROM` for proxy mode,
and sets `server.forward-headers-strategy=none` so the container preserves the original peer for this decision.
Do not add a forwarding filter or RemoteIpValve that rewrites that peer before login processing.

The upstream edge proxy must replace client-supplied headers. Source restrictions alone do not establish correct
client identity. Without `TLS_PROXY_FROM`, the plan warns that client attribution behind the upstream proxy is
not reliable: an untrusted upstream address becomes the counter key. For manually configured installations,
apply the same edge-header and backend-property settings. Installer and backend changes must be present in the
same pinned application revision before distributing the installer as usable.

## Mail verification

After `--install-app`, run `--check-mail`, confirm with `check-mail`, or use `--check-mail --yes`.
It invokes `grafiosch.installer.InstallerMailConfiguration` through the deployed JAR's Boot launcher, as the
application user in the service working directory. Spring resolves the actual properties, production overrides
and Jasypt-encrypted mail password. No application context, additional server, database or public endpoint starts.
An older JAR without this helper blocks verification; the helper and installer must ship together.
Missing/decryption-failing properties and server, user, transport or administrator values that differ from the
confirmed answers block before SMTP. The journal's SMTP password is not used as a fallback.

The Python SMTP client uses those resolved values and verifies connectivity, the selected STARTTLS/implicit TLS
transport, certificate trust/name and authentication. Authenticated plaintext SMTP is refused. An explicitly
selected unauthenticated relay is supported. Credentials pass through private standard input, never command
arguments or diagnostic output; PLAIN/LOGIN authentication preserves UTF-8 passwords and special characters.

With `SMTP_TEST=yes`, it submits one message with a `Date` header from `SMTP_USER` to `ADMIN_EMAIL`. With `SMTP_TEST=no`, it checks
the connection/login without sending. Acceptance by the SMTP server does not prove inbox delivery. Successful
acceptance is journaled; repeated completed checks send no duplicate. If the process is killed between SMTP
acceptance and durable recording, a retry can resubmit the same Message-ID and the command says so.
Old journal-only milestones require another configuration and SMTP check; an already accepted message is not resent.
This verifies resolved application settings with the SMTP probe, not the application's registration-mail workflow.
`SMTP_CONFIGURE=no` records `skipped` and leaves registration incomplete. Every normal mail-stage exit,
including a previously verified or skipped check, produces the hand-over report below. SMTP transport failures
record a failed mail milestone and return **10**; incompatible application configuration blocks with **2**.

## Final hand-over

Run `--check-mail` after the application and web stages. It combines their recorded evidence into `backend`,
`web`, `tls` and `mail` milestones. A certificate only issued or selected for reuse is still pending until the
web stage verifies HTTPS. LAN installations skip TLS; an external proxy that cannot be reached from the host
records `unverified` and asks for an external check. This report records installation acceptance, not ongoing health.

| Overall result | Condition | Exit |
|---|---|---|
| `complete` | backend, web and mail `ok`; TLS `ok`, `unverified` or `skipped`; complete build/start evidence | 0 |
| `incomplete` | backend `ok`, with pending web/mail/TLS or skipped/failed mail or failed TLS | 10 |
| `failed` | backend/web failed or result publication failed | 1 |
| `blocked` | invalid state, configuration or ownership prevents proceeding | 2 |

The report includes the browser URL, administrator registration address, update/configuration instructions,
planned and built commits, the SHA-256 of the installer bundle used for hand-over, and recorded warnings.
`mail_delivery` distinguishes SMTP acceptance (`accepted`, not proof of inbox delivery), a successful check
without sending (`not-requested`), skipped mail, pending verification and uncertain delivery after interruption.
An incomplete report lists the remaining actions. Saved answers remain immutable; changing a skipped SMTP
selection requires the future re-planning support and cannot be achieved just by repeating the check.

The one exception is the SMTP password, because a wrong one shows up only in this check. Until the mail milestone
is verified, `--check-mail --answers FILE` accepts a corrected `SMTP_PASSWORD`; every other answer and secret in
`FILE` must match the installation. After confirmation (`change-mail-password` or `--yes`) the installer journals
the change, stores the new secret, replaces only `spring.mail.password` in `application.properties` (the cron slots
stay), rebuilds the backend of the installed commit, because the properties are packaged into the JAR, restarts
Grafioschtrader and repeats the mail check. An interrupted change resumes with the next `--check-mail`.

The same public fields are atomically written as literal `key=value` lines to `/var/lib/gt-install/result`
(root-owned, mode 600). Do not source this file as shell code. Technical keys and milestone values are stable;
explanations use the selected English or German language. Credentials and private diagnostic logs are excluded.
Warnings raised during journaled stages are retained across retries. Older journals can only report warnings
still available when they resume.

The result is published before `status=complete` and the UTC `completed_at` are committed to the journal.
An interruption between these writes leaves the journal resumable; saved SMTP acceptance prevents a duplicate
message. Incomplete results retain `status=running`. Schema 1 and `scope=core` remain compatible with earlier
single-stage journals; `step.core=complete` alone never means the application installation is complete.

After completion, `--check`, `--dry-run`, `--prepare`, all `--install-*` modes and `--check-mail` validate the
journal and display the recorded result without reading secrets, checking deployment hashes, contacting SMTP
or changing files. This remains true after `gtupdate.sh` changes the deployed JAR. Update as `grafioschtrader`
from `/home/grafioschtrader` with `./gtupdate.sh`. Template keys in `application.properties` are merged;
additional keys belong in `application-production.properties`, which is preserved unchanged. Keep the generated
launchers and assigned cron slot. Restoring files never reverses database migrations.

## nginx and LAN access

After `--install-app`, run `--install-web` and confirm with `install-web`, or use `--install-web --yes`.
For this LAN-only path, the saved answers select `WEBSERVER=nginx` and an empty `DOMAIN`. The original journal, secrets,
configuration and built artifacts are checked again; no passwords are requested and no application rebuild
or database bootstrap runs. A missing nginx is installed from configured APT sources after a simulated
transaction excludes removals and upgrades. Existing nginx must be running with a valid distribution configuration.

The stage serves the `LAN_ADDRESS` answer, shows the LAN URL and installs
`/etc/nginx/sites-available/grafioschtrader` with a matching link in `sites-enabled`. `LAN_ADDRESS` defaults to the
source address of the default route; the question lists every global IPv4 address of the host, so a machine whose
default route leaves over WLAN while the intranet is on Ethernet selects the Ethernet address. An address that
belongs to no interface of the host blocks the plan and every web stage. Journals written before the question
existed keep the default-route address. The address is recorded;
an address change blocks resumption until the configuration is reviewed. Use a stable LAN address or DHCP
reservation. Port 80 must be free or owned by nginx. The firewall and router are not changed; clients need
an existing permitted network path to HTTP port 80. This stage configures HTTP, with no certificate.

The own server block serves `/grafioschtrader/`, redirects its root URL there, and handles nested Angular routes.
It proxies `/api`, `/m2m`, `/ws` and `/socket/websocket` (rewritten to `/socket`) to the selected loopback backend
port. Forwarded headers, WebSocket upgrade headers, a 50 MB request limit and compression are scoped to that
block. Existing document-root landing pages and nginx default sites are preserved. The proxy directives follow
the [nginx proxy documentation](https://nginx.org/en/docs/http/ngx_http_proxy_module.html) and
[WebSocket guidance](https://nginx.org/en/docs/http/websocket.html).

Foreign files are never edited. Includes are expanded for host/listener checks and existing sites are probed
with their actual Host header/SNI before and after reload. A pre-existing LAN-address vhost, wildcard/regex
names, dynamic/recursive includes, address-specific port-80 listeners, unsupported listen options or a shared
port 80 without an explicit `default_server` require manual integration or a future stage. These conservative
checks prevent uncertain routing from being silently adopted. Existing HTTPS sites are compared without
validating their certificate chain; only the installer's own TLS is validated as described above.

`nginx -t` must pass without warnings. Verification requires the actual GT database/profile through nginx,
the exact built frontend index, its base href, a referenced JavaScript file and MIME type, and the nested-route
fallback. Original shared-site HTTP statuses are saved privately before activation and reused after interruption.
On failed validation, reload or verification, only the installer's enablement link is removed and nginx reloads
the previous configuration. The own server file and journal remain for diagnosis and resumption; foreign
sites and the application/database are preserved. Repeat `--install-web` after resolving the cause. Inspect
`nginx -t`, `journalctl -u nginx` and `/var/log/nginx/error.log` for nginx diagnostics.

Success enables nginx at boot, records `step.web=complete`, prints the LAN URL and returns **10**.
The installation remains incomplete until `--check-mail` performs mail verification and final hand-over. A repeated successful
stage rechecks the running site and enablement without rewriting/reloading it. Foreign-vhost snippets and
firewall changes remain separate work.

## First build and application service

After a successful core run, use `--install-app`. It shows the scope and requires the literal confirmation
`install-app`; `--install-app --yes` accepts that scope unattended using the original journal and secrets.
No new answers file or MariaDB root password is needed. The core must include the Node/build-tools stage, and
the pinned source must contain the helpers' `GT_INSTALL_BUILD_ONLY` contract. Older source revisions without
that contract are blocked; the installer never silently moves an existing checkout to a newer commit.

The host needs `systemd`, `sudo`/`visudo`, `logrotate`, `wget`, `curl`, `python3`, `mariadb-client`, `iproute2`
and the core prerequisites. Missing tools are named before changes; this stage does not install additional
packages. It performs these actions:

- Copies the update/helper scripts from the pinned commit into the service user's home. The generated
  `gtvar.sh`, encryption-key launcher and encrypted configuration are preserved.
- Creates the GT frontend directory under `DOCROOT` and a mode-640 application log. A shared document root
  keeps its ownership and other content. Existing foreign targets are refused.
- Applies the selected system time zone, then assigns the cron slot once through `gtcronrandom.sh`. The
  protected saved properties and publication hashes allow an interrupted hand-off to resume with the same slot.
- Publishes root-owned sudoers, systemd and logrotate files after validation with the real Linux tools.
  Sudo permits only `systemctl start/stop grafioschtrader.service`. The unit uses the core-selected Java through
  the launcher, follows MariaDB in boot ordering, uses `UMask=0077`, and has no automatic restart loop.
  Logrotation is weekly, keeps eight compressed rotations and uses `copytruncate`.
- Runs the frontend and backend helpers sequentially as `grafioschtrader`, using the selected toolchains.
  Both run with `GT_INSTALL_BUILD_ONLY=1`, so neither a build failure nor a successful component starts the
  service. The first build uses the approved commit; later updates use `~/gtupdate.sh` to fetch master.
  Low-memory hosts retain the frontend helper's release-download behavior; that bundle can differ from the
  pinned backend revision. Build output is kept in root-only `/var/lib/gt-install/app-build.log`.
- Requires exactly one nonempty executable JAR and a nonempty frontend index, records their hashes, then
  journals the first start before invoking systemd. Until this boundary the database must still be empty.
  After it, resumption checks Flyway history without repeating database bootstrap or rotating credentials.
- Waits up to 15 minutes for `/api/gtinfo`, checks the database/profile, the service process's exact loopback
  listeners, successful migrations and absence of `uca1400` columns, then enables startup at boot.

Repeat `--install-app` after resolving a failure. Failed migrations, configuration/artifact drift, foreign unit
overrides and missing original secrets block recovery; no schema is automatically dropped or repaired.
`--install-core` refuses to rerun once the application stage begins. Diagnostics remain in the build log and
`/var/log/grafioschtrader.log`. Success returns **10**, retains `status=running` and records `step.app=complete`:
web serving, TLS and mail verification still require the following stages. Do not run `gtupdate.sh` between
installer stages, because it changes the pinned checkout and deployment hashes.

The automated tests cover failure/resumption rules and real configuration validators in disposable Ubuntu
containers. The QEMU acceptance test also verifies a full application build, systemd startup and an actual
guest reboot on Ubuntu 24.04 amd64. Other distribution/architecture combinations and low-memory release
downloads remain separate acceptance work; see [the test instructions](test/README.md).

## Installation core

`--install-core` supports fresh Debian/Ubuntu hosts with systemd and the inventory prerequisites (`git`, `curl`,
`openssl`, `runuser`, `useradd`, `passwd`, `flock`, `sha256sum`). It reuses suitable **JDK 25+ and Maven 3.8+**
installations, installs suitable packages from the host's configured APT sources, or otherwise installs verified
vendor archives. Package installation requires current APT metadata and `fuser` from `psmisc`; Java/Maven APT
installation also requires `update-alternatives`. Vendor archives require `python3`, `tar` and `gzip`;
Node/build-tool preparation requires `python3`, `tar` and `xz` (`xz-utils`). Missing prerequisites are reported
before execution; install them and repeat the command.
It displays a plan for this scope and asks for the literal confirmation `install-core`. With an answers file,
`--yes` explicitly authorizes this scope without a terminal. Inventory, package transactions and existing DB
credentials are checked again before mutation; changed findings require another invocation and a fresh plan.

The core performs these steps:

1. Acquire `/var/lib/gt-install/lock`; write the root-only journal and application secrets atomically.
2. Install missing base packages (`git`, `curl`, `wget`, `ca-certificates`, `gnupg`, `sudo`, `openssl`, `logrotate`,
   `whiptail`, `tzdata`) from the confirmed plan, refusing removals and upgrades. Verify their installed status;
   resume an interrupted transaction by installing only what remains missing. Already completed base packages
   must still be present. Create the swap file when selected (see below). Resolve Java/Maven, install the
   confirmed APT versions or vendor archives when needed,
   preserve existing alternatives and verify
   `java`, `javac` and Maven with the selected JDK. Create the disabled-login-password `grafioschtrader` user,
   prepare Node/npm and the isolated build tools, then clone the planned source commit into
   `/home/grafioschtrader/build/grafioschtrader`, running Git as that user.
3. Install distribution MariaDB packages when absent, without package removals/upgrades. On this new server only,
   set the requested root password while preserving `unix_socket`, remove anonymous accounts and the default
   `test` schema. Verify the password under the fresh service user's OS identity, reject a wrong password and
   verify passwordless Linux-root socket administration independently. Debian's package also enables
   `mariadb.socket`, which listens on port 3306 of every address and bypasses `bind-address`; the installer
   disables it on this new server, restarts MariaDB and stops unless port 3306 listens on loopback only.
4. Create an empty `grafioschtrader` schema with `utf8mb4` / `utf8mb4_general_ci` and its account when absent.
   Existing empty schemas require consent and matching charset/collation. Existing accounts require their current
   password and existing schema privileges; their passwords and grants are not changed. Verify TCP login to
   `127.0.0.1:3306`. Apply the buffer-pool drop-in and restart MariaDB only when selected and needed.
5. Write encrypted application properties, the production override, `gtvar.sh` and a protected Bash service
   launcher with the selected Java/heap and `JASYPT_ENCRYPTOR_PASSWORD`. The launcher is not started.

Success returns **10**, meaning **core ready, full installation unfinished**. No backend build, application service,
web server, firewall, TLS, DuckDNS update or mail test is performed. The full dry-run still describes later steps;
the core's own confirmation plan lists only its actual changes. Existing classic/Docker installations and foreign
installation pieces are refused. Java/Maven vendor archives and Node archives are installed automatically as
described below.

### Including Grafioschtrader into an existing HTTPS site

When an existing nginx or Apache site of this host already serves the domain, `TLS_SOURCE` defaults to
`existing` and `VHOST_INCLUDE` is asked (default `no`). With `VHOST_INCLUDE=yes` and `TLS_SOURCE=existing`
the installer creates no own domain site and requests no certificate: the existing site keeps terminating TLS
and renewing its certificate, and Grafioschtrader's routes are included into it. The LAN site stays an own site;
a foreign site serving the LAN address still blocks.

The target is resolved from the configuration itself: for nginx by parsing `sites-enabled` and `conf.d` with
their plain includes, for Apache from `apache2ctl -t -D DUMP_VHOSTS` at the web stage (read-only modes never run
`apache2ctl`). Exactly one HTTPS block must serve the domain; port-80 blocks of the same domain, usually
redirects, stay untouched. The plan blocks on a second HTTPS block, a dynamic include, an nginx block whose
opening line does not end with `{`, an Apache `RewriteRule` in the virtual host, and any existing location,
`ProxyPass` or `Alias` on `/api`, `/m2m`, `/socket`, `/ws` or `/grafioschtrader`. `TLS_CERT`/`TLS_KEY` must be
that block's own certificate and key.

| Server | Snippet | Include inserted after the opening line of the block |
|---|---|---|
| nginx | `/etc/nginx/snippets/grafioschtrader.conf` | `include /etc/nginx/snippets/grafioschtrader.conf;` |
| Apache | `/etc/apache2/conf-available/grafioschtrader.conf` (not enabled globally) | `Include /etc/apache2/conf-available/grafioschtrader.conf` |

The nginx snippet declares every route with `^~`, so regex locations of the site, such as `\.php$` or a caching
rule for `\.(js|css)$`, cannot capture Grafioschtrader's paths. The Apache snippet uses `Alias` for the frontend
and one `<Location>` per backend route, so proxy and forwarding headers apply to those paths only and the site's
own requests are unchanged. Both forward the client address as `X-Forwarded-For` and `https` as the protocol.

Before the edit the file is copied to `<file>.gt-install.<timestamp>`, and the journal records target, backup
and the digest of the edited file. A configuration test, a reload, the verification of Grafioschtrader through
`https://<domain>` and the comparison of every existing site's responses before and after decide; any failure
copies the backup back and reloads. A completed include is only verified again; an edit of the target file by
someone else afterwards stops the stage instead of being overwritten. Restoring web files never rolls back
database migrations.

Without an unambiguous, conflict-free target the installer edits no foreign file. It publishes the snippet,
reports `web=pending` (result `incomplete`) and names the snippet in the result. After the administrator includes
it into the right block and reloads, the next run of the installer verifies Grafioschtrader through
`https://<domain>` and completes; it never tries the automatic insertion for that installation again.

### Another web server (`WEBSERVER=none`)

With `WEBSERVER=none` another web server, reverse proxy or tunnel serves Grafioschtrader; it requires
`TLS_SOURCE=existing` or `proxy` for a domain, because nothing local would answer Let's Encrypt's HTTP-01
request. The installer installs no web server and writes `/root/gt-install-webserver.conf` (600): nginx locations
and Apache directives for the own server block, with the backend reached over HTTP on `BACKEND_PORT` and the
request scheme forwarded. Nothing in that file is active; an edited copy is not overwritten. The web stage then
verifies `http://<lan>/grafioschtrader/` and, for a domain, `https://<domain>/grafioschtrader/` (behind an upstream
proxy an unverifiable public check is recorded as `tls=unverified`, as for own sites). While they do not answer, the
result is `incomplete` with `web=pending`; a later run of the installer verifies again and completes. Selected ufw
rules are applied in both cases.

### DuckDNS updater

For a `<name>.duckdns.org` domain `DUCKDNS_UPDATER` is asked; it defaults to `no` when the inventory found an
updater in a crontab, `/etc/cron.d`, a systemd unit, `/etc/ddclient.conf` or a DuckDNS container, and to `yes`
otherwise. A router-side client is invisible to the host, so the question is always asked. With `yes` the plan
blocks while a foreign updater exists, and `DUCKDNS_TOKEN` is collected: a UUID, checked at the prompt, in an
answers file and in the secret store.

The core installs the updater right after creating the `grafioschtrader` user, so the records converge during
the build:

| File | Content |
|---|---|
| `~/duckdns/duck.sh` (700) | reads the token, determines the stable global IPv6 address of the default-route interface on every run, calls `https://www.duckdns.org/update` and logs `OK`/`KO` to `~/duckdns/duck.log` (kept at most 2000 lines) |
| `~/duckdns/token` (600) | the token only |
| `/etc/systemd/system/grafioschtrader-duckdns.service`, `.timer` | runs the script as `grafioschtrader` every five minutes at a minute/second offset derived from the host name, and one minute after boot |

Every request goes over IPv4, because `www.duckdns.org` has no IPv6 address, and always with an empty `ip=`.
`DNS_FAMILY=ipv4` and `both` let DuckDNS take the requesting address for the A record; `ipv6` and `both` send
`ipv6=<address>`, and an `ipv6` request that carries the address gains no A record. With `ipv6`, an A record left
by an earlier updater is not removed: remove it on duckdns.org, otherwise the DNS check reports the mismatch. A
systemd timer is used instead of cron because systemd is a precondition of the installer, while minimal cloud images
ship no cron.

The token never appears in a process argument, the log or a message. The script writes the token-bearing URL into
a private `mktemp` curl configuration (umask 077) beside itself, passes it with `-K` and removes it on exit; curl's
own diagnostics are discarded and only its exit status is logged. Before the first update the plan accepts DNS
records that do not match yet with a warning, and the certificate names are `<name>.duckdns.org` and
`www.<name>.duckdns.org`, because DuckDNS answers every name below the subdomain with the same records. The core
starts the service once: `KO` stops the installation before any certificate with a hint on the token and the
subdomain; otherwise it waits up to three minutes, in 10-second steps, until the domain resolves to the host's
selected addresses, then enables the timer. A completed step only re-publishes unchanged files and keeps the timer
enabled; the journal recognizes the installer's own units, so a later plan does not count them as a foreign
updater.

### ufw rules

`FIREWALL_ALLOW` is asked only while ufw is active and defaults to `yes`; without the rules the selected routes
would stay unreachable. It allows exactly the selected web routes:

| Route | Rule |
|---|---|
| LAN site, and HTTP-01 / redirect for a domain | `ufw allow 80/tcp` |
| `letsencrypt` or `existing` TLS | `ufw allow 443/tcp` |
| `proxy` TLS with `TLS_PROXY_FROM` | `ufw allow from <address> to any port <TLS_PROXY_LISTEN> proto tcp` |
| `proxy` TLS without a source address | `ufw allow <TLS_PROXY_LISTEN>/tcp` |

SSH is never opened or changed: a global `22/tcp` rule could widen access that the administrator restricted, and the
installer does not need it. Both web stages add the rules right after starting the web server, before any site or
certificate, so Let's Encrypt's HTTP-01 request passes an active ufw. A rule that `ufw show added` already lists is
journaled as `preexisting` and never claimed; a rule the installer adds is journaled as intent before `ufw allow`
and as owned afterwards. A completed stage only verifies that every rule is still listed; a rule the installer
added and an administrator removed later stops the stage instead of being re-added silently.

### Swap file

A host with less than 4000 MB RAM and no active swap is asked `SWAP` (default `yes`) when its root filesystem
can hold a swap file: ext2/3/4 and xfs get `dd` plus `mkswap`, btrfs gets `btrfs filesystem mkswapfile`
(btrfs-progs 6.1 and later). Any other root filesystem, or btrfs without `mkswapfile`, is reported with a note
and not asked. The plan reserves 2 GiB on the root filesystem.

The core creates the swap right after the base packages, so the backend build already has it. It writes the
2 GiB file under a private name beside `/swapfile`, formats it, sets mode 600, publishes it by rename and
activates it. It copies `/etc/fstab` to `/etc/fstab.gt-install.<timestamp>` and appends
`/swapfile none swap sw 0 0` through a copy beside the file. The journal records the swap file, the backup and
the fstab digest. A resumed run discards an interrupted swap file and recognizes its own fstab line; a completed
run only verifies the file and the single fstab entry, and activates the swap again when needed. A foreign
`/swapfile` or an existing `/swapfile` line in `/etc/fstab` is never adopted and stops the plan.

### Java and Maven installation choices

Availability is determined from `apt-cache policy openjdk-25-jdk-headless` and `apt-cache policy maven`, not from
a distribution/version table. Refresh missing or old package lists with `sudo apt-get update` and rerun the
installer. Read-only modes never refresh them. An APT candidate is used when it offers JDK 25 or Maven >= 3.8;
exact candidate versions are shown before confirmation, rechecked and recorded for recovery. Transactions needing
package removals or upgrades remain blocked. Existing suitable installations are reused without an APT
transaction. Debian 12, for example, has no JDK 25 in APT but Maven 3.8: Java comes from the vendor archive below,
Maven from APT. On armhf the APT JDK is never used: Debian builds it as the interpreter-only Zero VM, under which
Maven and the backend run unusably slowly (Maven Central even drops the first TLS handshake). Only JDKs with a
HotSpot JIT (`lib/server` or `lib/client`) count as suitable, so armhf always gets Liberica.

Before an APT installation, the core journals existing `update-alternatives` selections. It restores changed selections
after success or failure, including an interrupted run on resumption. A changed automatic selection becomes
manual to retain its original target; a previously absent group may use its new package default. APT may pull
an additional distribution JRE for Maven. GT still builds and runs with its explicitly selected JDK, regardless
of the system default. `gtvar.sh` includes both selected toolchain paths; Maven probes use this JDK even before
the application user exists.

If APT has no suitable candidate, the plan resolves a vendor archive before confirmation and shows its exact
version, file name and checksum:

| Component | Source | Checksum | Destination |
|---|---|---|---|
| Java on amd64/arm64 | Eclipse Temurin, from `api.adoptium.net` (latest release of the required major) | SHA-256 from the Adoptium API | `/opt/jdk-<version>-temurin` |
| Java on armhf | BellSoft Liberica, from `api.bell-sw.com`; Temurin ships no 32-bit Arm JDK | SHA-1 from the BellSoft API, the only checksum BellSoft publishes | `/opt/jdk-<version>-liberica` |
| Maven | Highest stable Maven 3 release listed on `downloads.apache.org` | SHA-512 from `downloads.apache.org` | `/opt/apache-maven-<version>` |

The `+` of a JDK version becomes `_` in the destination, for example `/opt/jdk-25.0.4.1_1-temurin`, so the path
stays literal in `gtvar.sh`, the launcher and the systemd unit. Vendor metadata is literal data: the planner
accepts an entry only when its file name and download link equal the ones derived from version and
architecture, and it never downloads from a link taken from the response. JDK archives come from the vendors'
GitHub releases; Maven comes from `dlcdn.apache.org`, falling back to `archive.apache.org` once the CDN no longer
lists the confirmed version.

The journal records source, version, file name and checksum. Resumption downloads exactly this archive, never a
newer release, and needs no vendor metadata. The archive is downloaded into a private stage beside the
destination (`/opt/.gt-jdk-<run>`, `/opt/.gt-maven_archive-<run>`), so it never fills a small tmpfs `/tmp`, and
compared with the recorded checksum. Its members are checked to stay below the expected top-level directory. It
is then unpacked into the same stage, the download is removed, and the stage is published by rename with a
recorded tree digest. The disk plan adds 600 MiB on `/opt` for a JDK archive and 30 MiB for Maven. A stage left by an interrupted download or extraction is
discarded and rebuilt; a published destination is accepted only with its recorded digest. An existing
destination the journal does not own blocks the plan.

Archives never register alternatives, never change `/etc/profile.d`, the global `PATH` or an existing
`/opt/maven` link, and leave every other Java selection untouched. The selected paths reach the build and service
through `gtvar.sh`, the launcher and the unit, exactly like APT toolchains. The core selects an archive by its
own destination, not by discovery order. Later updates of an archive toolchain are manual: install the new
release into a new directory and adapt `gtvar.sh` and the launcher. When the plan cannot resolve the vendor
metadata, for example without access to the vendor APIs, it blocks and prints the manual alternative:

| Component | Manual alternative and discovery location |
|---|---|
| Java | A current Java 25 JDK archive, for example Liberica: `amd64` -> `linux-amd64`, `arm64` -> `linux-aarch64`, `armhf` -> `linux-arm32-vfp-hflt`. Verify the vendor checksum and unpack into a **new** `/usr/local/jdk-25` directory. Check `/usr/local/jdk-25/bin/java --version` and `/usr/local/jdk-25/bin/javac --version`. |
| Maven | A current stable Maven 3 **binary** `tar.gz` and its SHA-512 from Apache. Verify the checksum, then unpack into a **new** `/opt/apache-maven-<version>` directory. The installer also detects `/opt/maven/bin/mvn` if that symlink already exists. |

Follow the project guides for [Java](https://github.com/grafioschtrader/grafioschtrader/wiki/Install-Java) and
[Maven](https://github.com/grafioschtrader/grafioschtrader/wiki/Installing-the-Latest-Release-of-Apache-Maven).
Choose current downloads from [BellSoft](https://bell-sw.com/pages/downloads/) or
[Apache Maven](https://maven.apache.org/download.cgi); the old example Maven version in the wiki is not pinned
into this installer. Never extract over an existing installation. Rerun `--check` after manual installation to
verify discovery, then the installation.

### Node.js, npm and frontend build tools

The core reads the Node version range and Angular CLI major from the planned source revision's
`checkversion.sh`. A compatible Node with an adjacent npm executable under `/usr`, `/usr/local` or `/opt` can
be reused. Private home-directory/nvm installations are not reused for the service user.

Otherwise the core resolves an official Node archive before confirmation: the current Node 24 line for amd64
(`x64`) and arm64, or Node 22 for armhf (`armv7l`, subject to the platform support cutoff). It must satisfy the
source requirements. The plan records its exact version, filename and SHA-256 from Node's HTTPS
`SHASUMS256.txt`. Execution downloads that pinned archive, verifies its hash and validates member/link paths
before extracting into an owned staging directory and publishing `/opt/nodejs-gt`.

Angular CLI and `semver` are installed into **`/opt/gt-build-tools`**, a GT-specific global npm prefix. Their exact
stable top-level versions and SHA-512 integrity values are resolved from the official npm registry before
confirmation. Their tarballs are verified before npm installs them; npm verifies dependency integrity and
enforces engine requirements. Lifecycle scripts, analytics and npm update notifications are disabled. Installation
uses private npm configuration/cache files; system npm configuration and global packages are not modified.
The completed prefix is root-owned, readable by the service user and not writable by other users.

The selected Node path, prefix and analytics setting are exported in `gtvar.sh`, so `npm list -g`, `ng`, `semver`
and the existing updater version check use the GT tools. Execution verifies Node/npm, both package versions,
`semver` and Angular CLI as the service user. This prepares the tools; it does not run `npm ci` or build the app.

An unsuitable system Node remains installed and selected for its other consumers. `NODE_REPLACE=yes` is blocked
in this core scope. Existing foreign `/opt/nodejs-gt` or `/opt/gt-build-tools` destinations are never adopted or
overwritten. The journal pins the original versions, checksums and hashes of completed trees, including permissions
and symlink targets. Interrupted staging/publication can resume; changed or missing owned trees block recovery.
Completed tools are verified without reinstalling or resolving newer releases. Archive/prefix updates remain a
separate maintenance operation; they do not receive APT updates.

An older core journal can acquire this additional scope after a new plan and confirmation. Its original secrets
remain in use. After verifying the existing configuration, only the managed `gtvar.sh` is extended for Node/npm;
existing encrypted properties and the service encryption key are retained.

### State, secrets and resumption

`/var/lib/gt-install` and `/root/.gt-install` have mode 700. The journal `/var/lib/gt-install/state` and the secret
store `/root/.gt-install/secrets` have mode 600 and are root-owned. They are literal data files, never sourced.
Publication flushes the temporary file before atomic rename and the destination filesystem afterward, so a
successful journal/secret write does not rely on delayed operating-system buffers.
The journal records `schema=1`, `scope=core` or `scope=bootstrap`, `status=running`, a unique installation ID, pinned source revision,
validated answers, selected toolchain paths, resource intent/ownership and hashes of managed files. The secret
store is bound to the same installation ID. It contains DB/Jasypt/JWT and applicable SMTP/DuckDNS values;
**the MariaDB root password is never persisted**.

While APT toolchains are pending, their eventual paths can be `pending`. Their pinned package versions and
original alternatives are journaled before installation. Only a successful executable check records final paths
and completes that step; older core journals with already selected toolchains remain readable.

Repeat the same `--install-core` command after a failure. The core reuses the original secrets and verifies the
recorded resources. It checks existing database authentication again, resumes an interrupted account creation,
and can start a stopped MariaDB server that it installed. Already generated configuration is verified without
rewriting it. Root setup is reverified using the original root password; an already configured root password is
never rotated to make a retry succeed.

Missing/invalid secret storage, mismatched installation IDs, changed published configuration, missing owned
resources, changed toolchain paths or supplied answers that differ from the journal stop recovery. Restore the
original values/resources before continuing; this stage does not support changing installation answers in place.
A partial source checkout can be completed at the recorded commit. There is no automatic uninstall or database
rollback. The core keeps `status=running` even on success, so inventory continues to identify an unfinished install.

### Configuration and encryption

Template keys are replaced in place; comments, unrelated settings and cron values remain intact. Missing,
duplicated or developer-controlled (`!`) target keys block generation. Backend listeners use loopback; Apache's
additional HTTP port is written to the production override. SMTP auth/STARTTLS/SSL flags match the chosen mode,
including `starttls.required` in the override. Skipped mail clears template credentials. Admin and sender addresses
remain independent, and `jasypt.encryptor.password` keeps `${JASYPT_ENCRYPTOR_PASSWORD:}`.

Encryption runs as `grafioschtrader` using the source revision's Jasypt Maven plugin. Its `encrypt`/`decrypt` file
goals process a mode-600 temporary file in a private directory outside the clone. Unique delimiters preserve
parentheses and literal `${...}` sequences; each result must decrypt to the exact original input before publishing.
The encryption key is supplied only in the child environment. Plugin stdout/stderr are captured privately because
they can contain plaintext. The `encrypt-value` goal is unsuitable here: Maven parameter interpolation can alter
literal credentials, and `JASYPT_PLUGIN_VALUE` alone does not populate that parameter.

Properties and `gtvar.sh` are mode 600; `grafioschtrader.sh` is mode 700. All belong to the service user.
The Bash launcher uses shell-safe quoting and `exec` with an absolute Java path. Private temporary files/directories
are removed on success, failure and handled cancellation. Supplied secrets never appear in progress reports or
external command arguments.

## Dry-run

`--dry-run` first inventories the host, then asks applicable questions in a terminal and prints the proposed
actions. The current renderer uses plain prompts with or without `--plain`; German locales select German prompts.
Enter accepts a default, `!quit` or end-of-input cancels with exit code `130`. Questions read from `/dev/tty`, so
redirecting the report to a file does not consume answers from stdin. A fresh host needs an attached terminal.

The shared question model covers domain and DNS families, TLS provider, web server, backend ports, administrator,
mail, database reuse, Java heap, document root, time zone, swap, shared Node replacement and firewall changes.
An installed web server selected by the inventory is retained. Domain names must be ASCII (use punycode for IDNs);
paths must be absolute and normalized, with letters, digits, dots, underscores, hyphens and slashes only.
Booleans use `yes`/`no` in both languages. Defaults and accepted choices appear in each prompt.

No password or token is requested, generated or included in the dry-run report. It lists applicable secret names
and purposes. An existing database account remains blocked until its credentials can be verified; inaccessible
database metadata stays blocked too.

The plan lists packages and repositories, files, database objects, users, service operations, chosen listeners,
firewall rules, disk requirements and deferred verification. It simulates available package transactions with
`apt-get --simulate`, checks DNS with `dig` when available, and validates supplied certificates with
OpenSSL. Nothing invokes a package update, ACME issuance/renewal, DNS update or SMTP test.

Blockers are explicit: stale/unavailable package metadata, provisional source requirements, occupied ports,
unverified database access, unsafe document roots, ambiguous web configuration, DNS mismatches for Let's Encrypt,
invalid certificates, package removals and upgrades of existing packages. Transactions requiring a new vendor
repository remain blocked until their candidates can be resolved. Isolated archives name their destination and
required version line; exact artifact versions and checksums must be resolved before execution. A plan with zero
blockers is an informational proposal, never authorization to execute it or evidence of a successful installation.

Existing classic, completed and Docker installations receive a no-bootstrap plan and updater guidance without
questions. Foreign partial installations, unfinished installer state and unknown/invalid state block planning.

## Credential preparation

`--prepare` collects configuration and credentials, performs read-only checks of existing MariaDB accounts and
prints a plan. It is a rehearsal for installation: **all collected/generated secrets are discarded on exit**.
It does not persist a deployable configuration or send SMTP/DuckDNS requests. Root authentication, when required,
precedes the other questions so authenticated inventory determines whether the GT database/account already exists.
An inactive database is never started by an authentication probe. Failed authentication stops preparation.

| Key | When required | Purpose |
|---|---|---|
| `DB_ROOT_PASSWORD` | New MariaDB: twice. Existing MariaDB: current password once, only when privileged socket inventory fails. | Future new-server root password, or read-only inventory authentication; existing root accounts are never changed. |
| `DB_PASSWORD` | New GT account: twice. Existing account: current password once. | `spring.datasource.password`; existing account must authenticate over TCP to `127.0.0.1:3306` as exactly `grafioschtrader@localhost`. |
| `JASYPT_PASSWORD` | Always, twice. | The future service's `JASYPT_ENCRYPTOR_PASSWORD`, used to encrypt application secrets. |
| `ADMIN_EMAIL` | Always, ordinary email input. | `g.main.user.admin.mail`; registration with this address becomes administrator. |
| `SMTP_PASSWORD` | `SMTP_CONFIGURE=yes` and `SMTP_AUTH=yes`, once. | Current mail-account password or app password, for `spring.mail.password`. |
| `DUCKDNS_TOKEN` | DuckDNS domain with `DUCKDNS_UPDATER=yes`, once. | Existing DuckDNS token; unnecessary when a router or another client updates DNS. |

New passwords require matching confirmation; empty input retries and never generates a replacement. Existing
credentials are entered once. Input is hidden and preserves spaces, quotes, backslashes, dollar signs and equals
signs literally. NUL and control characters are rejected. Ctrl-C or end-of-input cancels; while a password is
entered the terminal runs with `-echo -isig`, so Ctrl-C arrives as an input byte and cancels without depending on
signal timing. Terminal echo and signal keys are restored on normal completion and handled cancellation. Secret values never enter the printable answer map, reports or
external command arguments. Temporary MariaDB option files are mode 600 inside private scratch space and removed
after use and on exit. Shell tracing is disabled before secret handling.

`SMTP_CONFIGURE` defaults to `yes`; only an explicit `no` skips mail and leaves registration incomplete.
Configured mail requires `SMTP_HOST`, `SMTP_PORT` (default 587), `SMTP_AUTH` (default `yes`), `SMTP_USER` and
`SMTP_SECURITY`. `SMTP_USER` must be an email address even with `SMTP_AUTH=no`: the backend also uses it as the
Sender/From address. Separate login and sender values are currently unsupported. Security choices are `starttls`,
`tls`, `none`; defaults are `starttls` for 587 and `tls` for 465. Other ports require an explicit choice.
Authenticated SMTP with `none` blocks the plan. `SMTP_TEST` records an installation choice; preparation sends no mail.

A fresh 48-character alphanumeric `JWT_SECRET` is generated internally with OpenSSL, independently of the
passwords, once per preparation. Its value is never displayed. The browser login password is chosen later during
registration; no Linux login password or data-provider API key is collected.

### Answers file

For unattended preparation or core installation, supply a root-owned regular file with mode **600**, never a symlink. The file contains
one `KEY=value` per line using keys in `gt_question_model`. Blank lines and lines starting with `#` are ignored.
Everything after the first `=` is literal, including leading/trailing spaces and quotes; do not shell-quote values.
Unknown/duplicate keys, malformed lines, control characters and NUL are rejected without echoing values.
Defaults apply to omitted configuration fields; required fields without defaults and every applicable secret must
be supplied. A new password has one field, without a second confirmation field. Inactive fields are rejected;
`JWT_SECRET` is internal and is not an accepted input key. The parser never sources or evaluates the file.

For a fresh LAN host, mandatory fields without defaults include `ADMIN_EMAIL`, `DB_ROOT_PASSWORD`, `DB_PASSWORD`,
`JASYPT_PASSWORD`, and SMTP host/sender/password unless `SMTP_CONFIGURE=no`. Supply `TIMEZONE` if inventory cannot
detect it. Domain/TLS choices introduce further required fields shown by `--dry-run`.
The caller's file is never modified, copied into installer state or removed. Protect and remove it yourself when
no longer needed. `--check` and `--dry-run` reject `--answers` before reading any file.

## Report

The report contains the host class, source revision and requirements, toolchains, packages and candidate versions,
RAM and disks, listeners, database metadata, other runtime consumers, web configurations, certificates, existing
DuckDNS clients, and network reachability. Compatibility rows propose `reuse`, `install`, `isolate`, or `block`.
They are recommendations for a future bootstrap, not commands executed by this increment.

Existing installations are classified as `completed`, `unfinished`, `classic`, `foreign-partial`, or `docker`.
The installer's state takes priority. Invalid state or unavailable Docker inventory is reported conservatively;
neither becomes a fresh host. Existing classic installations continue to use `gtupdate.sh`, Docker installations
`docker/update.sh`. A non-empty GT database blocks bootstrap even when it belongs to a healthy existing installation.

`UNKNOWN` means a probe failed, was unavailable, or could not safely be performed. In particular, the checker does
not connect to an inactive socket-activated MariaDB server, and failed root socket authentication does not mean
the database or account is absent. Inventory itself requests no database password. Sources are pinned to the commit resolved
from `master`, with `git ls-remote` or, on a minimal image without git, GitHub's ref advertisement through `curl`; failed downloads fall back to explicitly provisional built-in version floors. The downloaded
`checkversion.sh` is parsed as data and never executed.

Web configuration is read statically, including enabled files and resolvable includes. Literal vhost names and
ports are probed locally. Dynamic includes and directives inherited through an included vhost fragment remain
`UNKNOWN`; the report does not claim to validate an effective configuration or choose where to insert a snippet.
`--check` stops at this inventory; `--dry-run` adds the questions and answer-dependent checks described above.
Network results describe reachability from this host, not public inbound reachability.

Exit codes:

| Code | Meaning |
|---|---|
| `0` | Check/plan/preparation without blockers, successful hand-over, or previously completed installation; review warnings |
| `10` | Core/application/web stage finished, or hand-over reports an incomplete installation |
| `2` | Check/plan contains a bootstrap blocker, a core step failed, or invocation/root/lock/terminal requirements were not satisfied |
| `1` | Required execution environment unavailable, failed hand-over milestone, or result/journal publication failed |
| `130` | Question input ended or the user cancelled with `!quit` / Ctrl-C |

The checker writes no installation state, persistent log, package metadata, or configuration. Its scratch
directory and any npm cache are temporary and removed on exit. An existing installer lock is opened read-only;
the checker never creates the lock or state directory. It neither changes Java alternatives nor invokes a web
configuration test, starts services, or installs npm packages. Reports omit process arguments, cron contents,
database password hashes and signed release download query parameters.

## Development and verification

The source-only guard `GT_INSTALL_SOURCE_ONLY=1` exposes the script's functions to tests without running the CLI.
`gt_inventory` collects facts; `gt_compatibility` computes recommendations; `gt_report` renders them.
`gt_question_model`, `gt_question_applies`, `gt_default` and `gt_validate_answer` define the questions;
`gt_questions` renders them. `gt_plan` revalidates answers and creates display records, and `gt_plan_report` renders
those records. `gt_prepare_root` authenticates missing inventory before the questions; `gt_prepare_secrets` collects
application secrets and verifies existing GT credentials. `SECRET` and `FILE_ANSWERS` must never be printed.
`gt_install_core` owns confirmation and revalidation; `gt_core_execute` sequences the stateful base-package,
toolchain, user, build-tool, source,
database and configuration steps. `gt_state_load` and `gt_secrets_load` validate recovery data before reuse.
Plan rows must never become shell commands. The planner never collects secret answers.
Keep collectors independent of execution and parse configuration as data, never with `source` or `eval`.

Run the [test suite](test/README.md) as a regular Linux user. It includes the existing updater regressions and
fixture-based checker and core cases. `test/smoke-check.sh`, `test/smoke-plan.sh` and `test/smoke-prepare.sh` are additional **disposable-container-only**
checks for the real root CLI and scratch cleanup; they also run on a read-only root filesystem with `/tmp` as tmpfs.
The planner smoke check uses `script` to drive a pseudo-terminal on a fresh container.
The test README also describes a dedicated container suite that installs real MariaDB and verifies core recovery
and Jasypt encryption. It uses a direct MariaDB start adapter; systemd and full application acceptance remain separate.
The toolchain container starts with Java 17 and installs real APT Java 25/Maven, including interruption recovery,
preservation of system alternatives and Maven execution as the application user with Java 25.

Tests model supported release/architecture combinations and representative host configurations. Runs on the
actual reference hosts listed in the specification remain acceptance work; fixture coverage is not a claim that
those machines have been inspected.
