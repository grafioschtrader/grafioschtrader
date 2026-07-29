# Grafioschtrader with Docker

Run a complete Grafioschtrader instance — database, backend, web server with
automatic HTTPS — with a few commands. No Java, Node.js, Maven or web server
setup required. Works on x86-64 servers and on a Raspberry Pi 4/5 (64-bit OS,
ARM64) alike.

## Quickstart

Requirements: [Docker Engine](https://docs.docker.com/engine/install/) with the
Compose plugin (both included in Docker Desktop and in the standard
`get.docker.com` install).

```bash
# 1. Get the docker/ directory (no full clone needed)
git clone --depth 1 https://github.com/grafioschtrader/grafioschtrader.git
cd grafioschtrader/docker

# 2. Run the interactive installer
bash install.sh          # or: chmod +x install.sh && ./install.sh

# 3. Open the printed URL and register with your admin email address
```

The installer asks for:

| Question | Meaning |
|----------|---------|
| Domain (optional) | With a domain you get automatic HTTPS via Let's Encrypt. Without one, GT runs on plain HTTP in your local network. |
| DuckDNS (optional) | For home servers: keeps your `*.duckdns.org` subdomain pointed at your changing home IP. |
| Admin email | The user who registers with this address receives admin rights. |
| SMTP (recommended) | Registration sends a confirmation email — without SMTP new users cannot finish registering. |

Database passwords and the JWT secret are generated automatically and stored in
`.env` (readable only by your user). **Jasypt encryption is not needed** — the
Docker setup passes secrets as environment variables.

## The two operating modes

### Local network only (no domain)

`GT_SITE_ADDRESS=:80` (the default). Grafioschtrader is reachable at
`http://<host-ip>/grafioschtrader/`. No certificates, nothing exposed to the
internet.

### Internet-reachable with automatic HTTPS

Set a domain, e.g. `GT_SITE_ADDRESS=myhost.duckdns.org`. Caddy obtains and
renews a free Let's Encrypt certificate automatically. Requirements:

1. The domain resolves to your public IP.
2. **Ports 80 and 443 are forwarded** to the Docker host (router setting
   "port forwarding" / "NAT"). Port 80 is required for the certificate
   challenge; HTTP requests are then redirected to HTTPS.

### Home server with DuckDNS (e.g. Raspberry Pi)

1. Create a free account at [duckdns.org](https://www.duckdns.org) and add a
   subdomain (e.g. `myhost` → `myhost.duckdns.org`). Note your token.
2. Run `bash install.sh`, enter `myhost.duckdns.org` as domain and answer the
   DuckDNS questions — a small updater container then keeps the subdomain
   pointed at your home IP.
3. Forward ports 80 and 443 on your router to the Pi.

**Already have a DuckDNS domain that's kept updated (router / existing updater)?**
You don't need a second domain. Enter your existing domain (e.g.
`gt8p.duckdns.org`) at the Domain prompt, answer **No** to "Run a DuckDNS updater
here?" (a second updater would fight the first over the same record), and forward
ports 80 and 443 to this host. Caddy still obtains the Let's Encrypt certificate
for the reused domain.

Other dynamic DNS providers work too: set up their update client yourself
(often built into the router) and just use the domain in `install.sh`.

### Running alongside another web server (custom ports)

If ports 80/443 are already taken on the host (an existing nginx/Apache/Caddy,
Home Assistant, another reverse proxy, …), `install.sh` detects the conflict:

- **With a domain** it stops and asks you to free 80/443 first — automatic
  Let's Encrypt HTTPS only works on the standard ports.
- **Without a domain** (local HTTP) it offers to publish Grafioschtrader on
  different host ports instead (e.g. `8080`). You can also set these directly
  in `.env`:

  ```properties
  GT_SITE_ADDRESS=:80        # local HTTP inside the container
  GT_HTTP_PORT=8080          # published on the host -> http://<host>:8080/grafioschtrader/
  GT_HTTPS_PORT=8443         # just needs to be free; unused in local mode
  ```

To keep the other web server terminating TLS on 80/443 and have it reverse-proxy
to Grafioschtrader, point it at `http://127.0.0.1:${GT_HTTP_PORT}` and leave GT
in local-HTTP mode.

## Everyday operations

All commands from the `docker/` directory.

```bash
docker compose ps                  # status + health of all services
docker compose logs -f backend     # backend log (migrations, errors)
docker compose down                # stop (data is kept in Docker volumes)
docker compose up -d               # start / apply .env changes
```

### Update to a new release

```bash
./update.sh 0.36.3     # or: ./update.sh latest
```

`update.sh` does the whole update: it backs up the database, `.env` and
`config/`, migrates renamed configuration keys when a release needs it, fetches
the new images — pulling the published ones, or building them from source with
`--build` — restarts the stack and waits until the database migrations have
finished, then reports the resulting schema version. Run it from the `docker/`
directory; without arguments it updates to the version already set in `.env`.

A release may rename a setting, for example when a key moves from the
application prefix `gt.` to the library prefix `g.`. Since `config/` lives on
the host and is never touched by an image update, `update.sh` rewrites the
affected keys in `config/application-production.properties` itself. It only does
so once `docker-compose.yml` shows that the installation has been updated to the
release in question, and it stops before pulling anything if it finds both the
old and the new spelling of a key — decide which value should win, remove the
other, and run the update again. The `.bak` copies taken next to the database
dump hold the state from before the change.

If the images have to be built instead of pulled, update the source first —
`update.sh` builds what is checked out next to it and refuses to mislabel an
older release with a new version number:

```bash
git -C .. fetch --depth 1 origin master && git -C .. reset --hard FETCH_HEAD
./update.sh 0.36.3 --build
```

The same thing by hand:

```bash
# 1. Back up the database first — migrations cannot be undone
source .env
docker compose exec mariadb mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" \
  --single-transaction grafioschtrader | gzip > gt-backup-$(date +%F)-pre-update.sql.gz

# 2. Only when GT_VERSION pins a version: set the new one in .env
#    (with GT_VERSION=latest, skip this step)
sed -i 's/^GT_VERSION=.*/GT_VERSION=0.36.3/' .env

# 3. Fetch the new images and restart
docker compose pull
docker compose up -d

# 4. Watch the database migrations complete
docker compose logs -f backend
```

Database migrations run automatically on startup, so the backend can stay in
`starting` for a while after a release with many migrations. `GT_VERSION=latest`
(the installer's default) tracks the newest release; set `GT_VERSION=0.36.3` to
pin an exact version, which is worth doing if you want an update to be a
deliberate, reversible step.

To go back, set the previous `GT_VERSION` and run `docker compose up -d` — but
note that the *database* is not rolled back with it, so an actual downgrade also
means restoring the dump from step 1 (see [Backup and restore](#backup-and-restore)).

### Backup and restore

```bash
# Backup (uses DB_ROOT_PASSWORD from .env)
source .env
docker compose exec mariadb mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" \
  grafioschtrader | gzip > gt-backup-$(date +%F).sql.gz

# Restore into a fresh instance
gunzip -c gt-backup-YYYY-MM-DD.sql.gz | \
  docker compose exec -T mariadb mariadb -uroot -p"$DB_ROOT_PASSWORD" grafioschtrader
```

Also back up the `.env` file — it contains the passwords matching the database
volume.

### Changing settings

Two files, two purposes:

| File | Contains | Apply with |
|------|----------|-----------|
| `.env` | Infrastructure: database, domain/TLS, mail/SMTP, admin email, memory sizing | `docker compose up -d` |
| `config/application-production.properties` | Grafioschtrader behavior: scheduled task times, logging levels, upload limits, … | `docker compose restart backend` |

Relevant `.env` keys are documented in [`.env.example`](.env.example),
including memory-tuning values for 2/4/8 GB hosts (`JAVA_OPTS`,
`DB_INNODB_BUFFER_POOL_SIZE`).

[`config/application-production.properties`](config/application-production.properties) is
mounted into the backend container and can override any property of the
built-in
[`application.properties`](../backend/grafioschtrader-server/src/main/resources/application.properties) —
for example the end-of-day price update time:

```properties
# Run the EOD price update at 22:30 UTC instead of the default 05:54 UTC
gt.eod.cron.quotation=0 30 22 * * ?
```

All scheduled-task times are interpreted in **UTC**. The file lives on your
machine and survives image updates — the same role
`application-production.properties` plays in the classic installation.
Exception: settings that docker-compose supplies as environment variables
(database, mail, JWT, admin email) cannot be overridden here, because
environment variables always take precedence in Spring Boot; change those in
`.env`. Alternatively, any Spring property can also be set as an environment
variable in a `docker-compose.override.yml`
(e.g. `GT_EOD_CRON_QUOTATION=0 30 22 * * ?`).

## Building images locally (developers)

Instead of pulling the published images from GHCR:

```bash
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
```

This builds the backend (Maven, Java 25) and frontend (Angular) inside Docker —
the Angular build needs roughly 4 GB of free RAM.

To customize the Caddy configuration, bind-mount your own file over the baked-in
one:

```yaml
# docker-compose.override.yml
services:
  web:
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
```

## Troubleshooting

- **`docker compose ps` shows backend as `starting` for a long time** — normal
  on first boot: all database migrations run once. Follow with
  `docker compose logs -f backend`.
- **`unauthorized` / `denied` when pulling the images** — the images are pulled
  anonymously; no `docker login` is involved. Reproduce the raw registry error
  with a bare pull, because `install.sh` deliberately hides it behind
  `--ignore-pull-failures` (so one unavailable image doesn't abort the others):

  ```bash
  docker compose pull                                            # shows the real error
  docker pull ghcr.io/grafioschtrader/grafioschtrader-backend:latest
  ```

  Causes, in order of likelihood:
  1. **The GHCR packages are not public.** Freshly published packages are
     private by default, and then *every* tag fails, including older ones. Only
     the project owner can fix this, under
     *[Organization packages](https://github.com/orgs/grafioschtrader/packages) →
     package → Package settings → Change visibility → Public*. Check from any
     machine without Docker:
     ```bash
     curl -s "https://ghcr.io/token?service=ghcr.io&scope=repository:grafioschtrader/grafioschtrader-backend:pull"
     # public -> {"token":"..."} | private -> {"errors":[{"code":"UNAUTHORIZED",...}]}
     ```
  2. **A proxy or firewall intercepts `ghcr.io`** — corporate networks in
     particular. Configure the proxy for the Docker daemon, not just the shell.

  Until the pull works you can build the images from source instead, which is
  what `install.sh` offers when it detects missing images:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
  ```
  Note that these local images are tagged exactly like the published ones, so a
  later successful `docker compose pull` replaces them.
- **No HTTPS certificate** — check that the domain resolves to your public IP
  (`nslookup myhost.duckdns.org`) and that ports 80/443 are forwarded. Caddy
  logs the ACME errors: `docker compose logs web`. If your ISP blocks port 80,
  a DNS-01 challenge is possible but requires a custom Caddy build with the
  DuckDNS DNS plugin (not covered by the standard image).
- **Registration email never arrives** — check the SMTP values in `.env`
  (`MAIL_*`), then `docker compose up -d` and watch
  `docker compose logs -f backend` while registering. Note: port 465 uses
  SSL (`MAIL_SMTP_SSL=true`), port 587 uses STARTTLS (`MAIL_SMTP_STARTTLS=true`).
- **Registration link broken** — the confirmation link is derived from the
  browser's `Referer` header; strict privacy extensions that strip it can break
  registration.
- **Raspberry Pi SD-card wear** — limit container log size in
  `docker-compose.override.yml`:
  ```yaml
  services:
    backend:
      logging: { driver: local, options: { max-size: 20m, max-file: "3" } }
  ```
- **Scheduled jobs (price updates etc.) run at "odd" times** — all cron
  settings are interpreted in UTC by design; the containers deliberately run
  on UTC.
