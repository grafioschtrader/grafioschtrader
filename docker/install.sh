#!/usr/bin/env bash
#
# Grafioschtrader Docker installer.
# Asks a few questions, generates secrets, writes .env and starts the stack.
# Safe to re-run: an existing .env can be kept or reconfigured.
#
set -euo pipefail
cd "$(dirname "$0")"

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
info()  { printf '  %s\n' "$*"; }
warn()  { printf '\033[33m  ! %s\033[0m\n' "$*"; }
fail()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------
# Preflight
# --------------------------------------------------------------------------
command -v docker >/dev/null 2>&1 \
  || fail "docker is not installed. See https://docs.docker.com/engine/install/"
docker compose version >/dev/null 2>&1 \
  || fail "the 'docker compose' plugin is missing. See https://docs.docker.com/compose/install/"

# Check before docker info: socket activation can hide a disabled boot service.
# Never change a local service when the CLI targets a remote engine, Docker
# Desktop or a rootless/user-owned socket. Without a name, 'docker context
# inspect' resolves the endpoint the CLI itself uses: DOCKER_HOST before
# DOCKER_CONTEXT before the saved context.
ensure_docker_autostart() {
  local endpoint state
  local -a admin=()
  endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}')" \
    || fail "cannot determine the current Docker endpoint."
  case "$endpoint" in
    unix:///var/run/docker.sock|unix:///run/docker.sock) ;;
    *) info "Docker uses a remote or separately managed endpoint; arrange its automatic startup on the engine host."
       return 0 ;;
  esac
  state="$(systemctl show docker.service --property=LoadState --value 2>/dev/null || true)"
  if [ "$state" = masked ]; then
    fail "docker.service is masked. Resolve this with the host administrator before installing."
  elif [ "$state" != loaded ]; then
    warn "No local systemd Docker service found. Ensure Docker starts automatically on this host."
    return 0
  fi
  state="$(systemctl is-enabled docker.service 2>/dev/null || true)"
  if [ "$state" = enabled ] && systemctl is-active --quiet docker.service; then
    return 0
  fi
  if [ "$(id -u)" != 0 ]; then
    command -v sudo >/dev/null 2>&1 \
      || fail "Docker needs boot startup enabled. Run 'sudo systemctl enable --now docker.service', then retry."
    admin=(sudo)
  fi
  info "Enabling Docker at boot and starting it now (administrator privileges may be required)."
  "${admin[@]}" systemctl enable --now docker.service \
    || fail "could not enable/start Docker. Check 'systemctl status docker.service' and retry."
  [ "$(systemctl is-enabled docker.service 2>/dev/null || true)" = enabled ] \
    && systemctl is-active --quiet docker.service \
    || fail "Docker must be running and persistently enabled before installation can continue."
}

ensure_docker_autostart
docker info >/dev/null 2>&1 \
  || fail "cannot talk to the Docker daemon. Is it running? (You may need to add your user to the 'docker' group.)"

# Value of KEY in .env without the quotes Compose dotenv syntax allows.
env_value() {
  local value
  value="$(grep -oP "^$1=\K.*" .env 2>/dev/null || true)"
  value="${value//\"/}"; printf '%s' "${value//\'/}"
}

# The DuckDNS address family only takes effect through a docker-compose.yml that
# hands it to duckdns-update.sh. An older file (e.g. brought back by an update to
# an older release) runs the image's own updater, which always sends this host's
# IPv4 address. Such a file must not start the updater.
check_duckdns_wiring() {
  local mode config
  case ",$(env_value COMPOSE_PROFILES)," in *,duckdns,*) ;; *) return 0 ;; esac
  mode="$(env_value DUCKDNS_UPDATE_IP)"
  # duckdns-update.sh refuses an empty or unknown mode by itself.
  case "$mode" in ipv4|ipv6|both) ;; *) return 0 ;; esac
  config="$(docker compose "$@" config duckdns 2>/dev/null)" \
    || fail "cannot read the DuckDNS service from docker-compose.yml. Check 'docker compose config duckdns'."
  grep -qE "^ +UPDATE_IP: \"?${mode}\"?$" <<< "$config" && grep -q '/etc/gt-duckdns-update.sh' <<< "$config" \
    || fail "docker-compose.yml does not hand DUCKDNS_UPDATE_IP=${mode} to duckdns-update.sh, so the
       DuckDNS updater would send this host's IPv4 address. Nothing was started. Restore the file of this
       checkout (git -C .. checkout -- docker/docker-compose.yml) or use the docker/ directory of a newer release."
}

# Compose waits for MariaDB/backend healthchecks and for the other services to
# run. A failed start or timeout must not reach either installation success message.
start_stack() {
  check_duckdns_wiring "$@"
  info "Starting Grafioschtrader and waiting for its healthchecks (up to 30 minutes)."
  info "The first start runs database migrations. Follow progress with: docker compose logs -f backend"
  docker compose "$@" up -d --wait --wait-timeout 1800 \
    || fail "Grafioschtrader did not become ready. Check 'docker compose ps -a' and 'docker compose logs backend mariadb web'."
}

gen_secret() {
  local len="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '/+=\n' | head -c "$len"
  else
    head -c 64 /dev/urandom | base64 | tr -d '/+=\n' | head -c "$len"
  fi
}

# Give this installation its own time slot for the daily download of prices and
# dividends, so that not every GT instance world-wide queries the free data
# providers in the same minute. It only fires while the schedule in
# config/application-production.properties is still untouched, so a time set by
# hand is never overwritten. The anchor is drawn in this host's local time; the
# written values are UTC, because the backend evaluates all cron expressions in
# UTC. Set GT_CRON_RANDOMIZE=off to skip it.
randomize_cron() {
  local script=../util/shellscripts/gtcronrandom.sh
  local config=config/application-production.properties
  [ -f "$config" ] || return 0
  if [ ! -f "$script" ]; then
    warn "gtcronrandom.sh was not found next to this installation — the daily price"
    warn "and dividend jobs keep the default times. Set gt.eod.cron.quotation and"
    warn "gt.dividend.update.data in $config by hand."
    return 0
  fi
  bash "$script" --file "$config" || warn "The cron randomization failed — the default times are kept."
}

# True if a TCP port already has a listener on this machine. Best-effort: when
# neither ss nor netstat is available the port is assumed free.
port_in_use() {
  if command -v ss >/dev/null 2>&1; then
    [ -n "$(ss -Htln "sport = :$1" 2>/dev/null)" ]
  elif command -v netstat >/dev/null 2>&1; then
    netstat -tln 2>/dev/null | grep -qE "[:.]$1[[:space:]]"
  else
    return 1
  fi
}

# IPv4 addresses of this host's own interfaces, without loopback, link-local
# (which some images assign with scope global) and the bridges Docker creates.
# A host on two networks (Ethernet intranet, WLAN uplink) lists each of them.
lan_candidates() {
  if command -v ip >/dev/null 2>&1; then
    ip -4 -o addr show scope global 2>/dev/null \
      | awk '$2 !~ /^(docker[0-9]*|br-.*|veth.*)$/ { sub(/\/.*/, "", $4); print $4 }'
  else
    hostname -I 2>/dev/null | tr ' ' '\n' | grep -E '^[0-9.]+$'
  fi | grep -v '^169\.254\.' || true
}

valid_ipv4() {
  local octet
  local -a octets
  [[ "$1" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || return 1
  IFS=. read -r -a octets <<< "$1"
  for octet in "${octets[@]}"; do (( 10#$octet <= 255 )) || return 1; done
  [[ "$1" != 127.* && "$1" != 169.254.* ]]
}

# With a domain, Caddy serves only that name over HTTPS and redirects every HTTP
# request to it — also one addressed to the LAN IP, which has no certificate. A
# second site http://<LAN address>/ keeps Grafioschtrader reachable inside the
# network, as with the classic installation. That site is plain HTTP.
choose_lan_address() {
  local candidates default answer
  candidates="$(lan_candidates | paste -sd' ' -)"
  default="${candidates%% *}"
  info "Inside the local network Grafioschtrader can also be served over plain HTTP"
  info "at http://<LAN address>/ (unencrypted; the domain keeps HTTPS)."
  [ -z "$candidates" ] || info "Addresses of this host: ${candidates}"
  while true; do
    read -r -p "LAN address for http://<address>/ [${default:--}], '-' for none: " answer
    answer="${answer:-${default:--}}"
    if [ "$answer" = - ]; then LAN_ADDRESS=""; return 0; fi
    if valid_ipv4 "$answer"; then break; fi
    warn "Enter an IPv4 address of this host outside 127.0.0.0/8 and 169.254.0.0/16, or '-'."
  done
  case " $candidates " in
    *" $answer "*) ;;
    *) warn "$answer is not assigned to an interface of this host; the LAN site answers only requests to it." ;;
  esac
  LAN_ADDRESS="$answer"
}

# Never infer inbound reachability from an outbound connection: DS-Lite hosts
# can reach IPv4 services while only accepting incoming connections over IPv6.
choose_duckdns_mode() {
  local choice
  info "How can this server be reached from the internet?"
  info "  1) IPv4 only — public IPv4 with router port forwarding (NAT)"
  info "  2) IPv6 only — e.g. DS-Lite / no incoming public IPv4"
  info "  3) Both IPv4 and IPv6 — both paths must reach this server"
  while true; do
    read -r -p "DuckDNS address updates [1/2/3, no default]: " choice
    case "$choice" in
      1) DUCKDNS_UPDATE_IP=ipv4; break ;;
      2) DUCKDNS_UPDATE_IP=ipv6; break ;;
      3) DUCKDNS_UPDATE_IP=both; break ;;
      *) warn "Choose 1, 2 or 3 explicitly." ;;
    esac
  done
  if [ "$DUCKDNS_UPDATE_IP" != ipv6 ]; then
    info "Forward TCP ports 80/443 from the router to this host's LAN IPv4 address."
  fi
  if [ "$DUCKDNS_UPDATE_IP" != ipv4 ]; then
    info "Allow incoming TCP 80/443 in the router's IPv6 firewall for THIS host."
    info "The AAAA record must point to this host, not the router's IPv6 address."
    info "The updater uses the host's outgoing IPv6 address; use a stable address"
    info "and make sure the firewall permits it, also after a prefix change."
  fi
  info "Remove obsolete A/AAAA records for unreachable address families in DuckDNS."
  info "Selecting one family does not delete the other family's existing record."
}

# Old installers enabled an IPv4-only updater without asking. On a rerun, ask
# for its mode without forcing the user to re-enter mail settings or secrets.
upgrade_duckdns_mode() {
  case ",$(env_value COMPOSE_PROFILES)," in *,duckdns,*) ;; *) return 0 ;; esac
  case "$(env_value DUCKDNS_UPDATE_IP)" in ipv4|ipv6|both) return 0 ;; esac
  warn "This configuration has no valid DuckDNS address-family selection."
  choose_duckdns_mode
  cp .env ".env.backup.$(date +%Y%m%d%H%M%S)"
  sed -i '/^DUCKDNS_UPDATE_IP=/d' .env
  printf '\nDUCKDNS_UPDATE_IP=%s\n' "$DUCKDNS_UPDATE_IP" >> .env
}

# --------------------------------------------------------------------------
# Existing .env
# --------------------------------------------------------------------------
# Non-fatal pull: with no internet (or images not yet published) locally
# present images are still usable.
pull_images() {
  # --ignore-pull-failures so one unavailable image (e.g. the GHCR images before
  # they are published) does not abort the pull of the others (mariadb etc.).
  docker compose pull --ignore-pull-failures \
    || warn "Some images could not be downloaded — continuing with what is available."
}

# Secrets carried over from an existing .env on reconfiguration. The database
# volume keeps the old credentials, so generating new ones would break the
# connection between backend and database.
OLD_DB_ROOT_PASSWORD=""; OLD_DB_PASSWORD=""; OLD_JWT_SECRET=""
if [ -f .env ]; then
  bold "An existing configuration (.env) was found."
  read -r -p "Keep it and just (re)start Grafioschtrader? [Y/n] " keep
  if [ "${keep:-Y}" != "n" ] && [ "${keep:-Y}" != "N" ]; then
    upgrade_duckdns_mode
    randomize_cron
    pull_images
    start_stack
    bold "Grafioschtrader is running with the existing configuration."
    exit 0
  fi
  OLD_DB_ROOT_PASSWORD="$(grep -oP '^DB_ROOT_PASSWORD=\K.*' .env || true)"
  OLD_DB_PASSWORD="$(grep -oP '^DB_PASSWORD=\K.*' .env || true)"
  OLD_JWT_SECRET="$(grep -oP '^JWT_SECRET=\K.*' .env || true)"
  cp .env ".env.backup.$(date +%Y%m%d%H%M%S)"
  info "The old .env was backed up; database passwords are kept."
fi

bold ""
bold "=== Grafioschtrader setup ==="
echo

# --------------------------------------------------------------------------
# Domain / TLS
# --------------------------------------------------------------------------
bold "1) Web address"
info "With a domain, Grafioschtrader gets a free Let's Encrypt HTTPS certificate"
info "automatically. Without one it runs on plain HTTP in your local network."
read -r -p "Domain (e.g. myhost.duckdns.org) — leave empty for local HTTP only: " DOMAIN
DOMAIN="${DOMAIN## }"; DOMAIN="${DOMAIN%% }"

SITE_ADDRESS=":80"
LAN_ADDRESS=""
if [ -n "$DOMAIN" ]; then
  warn "Ports 80 and 443 of this machine must be reachable from the internet"
  warn "(IPv4: NAT port forwarding; IPv6: firewall allowance for this host),"
  warn "otherwise the Let's Encrypt certificate cannot be issued."
  choose_lan_address
  SITE_ADDRESS="${DOMAIN}${LAN_ADDRESS:+, http://${LAN_ADDRESS}}"
fi

# --------------------------------------------------------------------------
# DuckDNS
# --------------------------------------------------------------------------
COMPOSE_PROFILES=""
DUCKDNS_SUBDOMAIN=""
DUCKDNS_TOKEN=""
DUCKDNS_UPDATE_IP=""
use_duckdns="n"
if [ -n "$DOMAIN" ]; then
  case "$DOMAIN" in
    *.duckdns.org)
      info "If your router or another service already keeps ${DOMAIN} pointed at"
      info "your home IP (DDNS client, an existing updater, etc.), answer No —"
      info "you reuse that domain as-is and no second updater is started."
      read -r -p "Run a DuckDNS updater here so ${DOMAIN} points to this host? [Y/n] " a
      [ "${a:-Y}" != "n" ] && [ "${a:-Y}" != "N" ] && use_duckdns="y" ;;
    *) read -r -p "Do you use DuckDNS dynamic DNS for this domain? [y/N] " a
       [ "${a:-n}" = "y" ] || [ "${a:-n}" = "Y" ] && use_duckdns="y" ;;
  esac
fi
if [ "$use_duckdns" = "y" ]; then
  choose_duckdns_mode
  default_sub="${DOMAIN%%.duckdns.org}"
  read -r -p "DuckDNS subdomain [${default_sub}]: " DUCKDNS_SUBDOMAIN
  DUCKDNS_SUBDOMAIN="${DUCKDNS_SUBDOMAIN:-$default_sub}"
  while [ -z "$DUCKDNS_TOKEN" ]; do
    read -r -p "DuckDNS token (from https://www.duckdns.org): " DUCKDNS_TOKEN
  done
  COMPOSE_PROFILES="duckdns"
fi

# --------------------------------------------------------------------------
# Host ports
# --------------------------------------------------------------------------
# The web container publishes these ports on THIS machine. Defaults are 80/443;
# they are changed automatically when something else (another web server, a
# reverse proxy, Home Assistant, ...) already occupies them.
GT_HTTP_PORT=80
GT_HTTPS_PORT=443
if [ -n "$DOMAIN" ]; then
  if port_in_use 80 || port_in_use 443; then
    echo
    warn "Ports 80 and/or 443 are already in use on this machine."
    warn "Automatic HTTPS for a public domain requires Grafioschtrader to own"
    warn "both 80 and 443 (Let's Encrypt validates on those ports), so the other"
    warn "service must be stopped first. Alternatively re-run without a domain to"
    warn "run Grafioschtrader on different ports (local HTTP, no certificate)."
    read -r -p "Continue and let Grafioschtrader claim 80/443 anyway? [y/N] " c
    { [ "${c:-N}" = "y" ] || [ "${c:-N}" = "Y" ]; } \
      || fail "Aborted. Free ports 80/443, or re-run without a domain for alternative ports."
  fi
else
  if port_in_use "$GT_HTTP_PORT"; then
    echo
    warn "Port ${GT_HTTP_PORT} is already in use on this machine."
    read -r -p "Host HTTP port to publish Grafioschtrader on [8080]: " GT_HTTP_PORT
    GT_HTTP_PORT="${GT_HTTP_PORT:-8080}"
  fi
  # In local mode Caddy serves HTTP only, but compose still needs a free host
  # port to map container 443 onto — pick an alternative if 443 is taken.
  if port_in_use "$GT_HTTPS_PORT"; then
    read -r -p "Host HTTPS port (unused in local mode, must just be free) [8443]: " GT_HTTPS_PORT
    GT_HTTPS_PORT="${GT_HTTPS_PORT:-8443}"
  fi
fi

# --------------------------------------------------------------------------
# Admin email
# --------------------------------------------------------------------------
echo
bold "2) Administrator"
info "The user who registers with this email address receives admin rights."
ADMIN_EMAIL=""
while [ -z "$ADMIN_EMAIL" ]; do
  read -r -p "Admin email address: " ADMIN_EMAIL
done

# --------------------------------------------------------------------------
# SMTP
# --------------------------------------------------------------------------
echo
bold "3) Outgoing mail (SMTP)"
info "Registration sends a confirmation email. Without SMTP, new users cannot"
info "complete their registration."
MAIL_HOST=""; MAIL_PORT="465"; MAIL_USER=""; MAIL_PASSWORD=""
MAIL_SMTP_SSL="true"; MAIL_SMTP_STARTTLS="false"
read -r -p "Configure SMTP now? [Y/n] " smtp
if [ "${smtp:-Y}" != "n" ] && [ "${smtp:-Y}" != "N" ]; then
  read -r -p "SMTP host (e.g. smtp.gmail.com): " MAIL_HOST
  read -r -p "SMTP port [465]: " MAIL_PORT
  MAIL_PORT="${MAIL_PORT:-465}"
  read -r -p "SMTP username (usually the sender email): " MAIL_USER
  read -r -s -p "SMTP password: " MAIL_PASSWORD; echo
  if [ "$MAIL_PORT" = "587" ]; then
    MAIL_SMTP_SSL="false"; MAIL_SMTP_STARTTLS="true"
  fi
else
  warn "Skipping SMTP — user registration emails will NOT work until you set"
  warn "the MAIL_* values in .env and run 'docker compose up -d' again."
fi

# --------------------------------------------------------------------------
# Users
# --------------------------------------------------------------------------
echo
bold "4) Users"
read -r -p "Maximum number of users [20]: " GT_ALLOWED_USERS
GT_ALLOWED_USERS="${GT_ALLOWED_USERS:-20}"

# --------------------------------------------------------------------------
# Secrets + sizing
# --------------------------------------------------------------------------
DB_ROOT_PASSWORD="${OLD_DB_ROOT_PASSWORD:-$(gen_secret 24)}"
DB_PASSWORD="${OLD_DB_PASSWORD:-$(gen_secret 24)}"
JWT_SECRET="${OLD_JWT_SECRET:-$(gen_secret 48)}"

mem_mb=4096
if [ -r /proc/meminfo ]; then
  mem_mb=$(awk '/MemTotal/ {print int($2/1024)}' /proc/meminfo)
fi
if [ "$mem_mb" -lt 3000 ]; then
  JAVA_OPTS="-Xms128m -Xmx896m";  DB_POOL="384M"
elif [ "$mem_mb" -lt 6000 ]; then
  JAVA_OPTS="-Xms256m -Xmx1792m"; DB_POOL="1G"
else
  JAVA_OPTS="-Xms512m -Xmx2048m"; DB_POOL="2G"
fi
info "Detected ~${mem_mb} MB RAM -> Java heap '${JAVA_OPTS}', DB buffer pool ${DB_POOL}"

# --------------------------------------------------------------------------
# Write .env
# --------------------------------------------------------------------------
# Disabling a Compose profile does not stop its already-running containers.
if [ "$use_duckdns" != y ] && [ -f .env ]; then
  docker compose --profile duckdns stop duckdns
fi
umask 077
cat > .env <<EOF
# Generated by install.sh on $(date -u +%Y-%m-%dT%H:%M:%SZ)
# You can edit this file and apply changes with: docker compose up -d

DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}

ADMIN_EMAIL=${ADMIN_EMAIL}
GT_ALLOWED_USERS=${GT_ALLOWED_USERS}

GT_SITE_ADDRESS="${SITE_ADDRESS}"
GT_HTTP_PORT=${GT_HTTP_PORT}
GT_HTTPS_PORT=${GT_HTTPS_PORT}

${COMPOSE_PROFILES:+COMPOSE_PROFILES=${COMPOSE_PROFILES}}
DUCKDNS_SUBDOMAIN=${DUCKDNS_SUBDOMAIN}
DUCKDNS_TOKEN=${DUCKDNS_TOKEN}
DUCKDNS_UPDATE_IP=${DUCKDNS_UPDATE_IP}

MAIL_HOST=${MAIL_HOST}
MAIL_PORT=${MAIL_PORT}
MAIL_USER=${MAIL_USER}
MAIL_PASSWORD=${MAIL_PASSWORD}
MAIL_SMTP_AUTH=true
MAIL_SMTP_SSL=${MAIL_SMTP_SSL}
MAIL_SMTP_STARTTLS=${MAIL_SMTP_STARTTLS}

JAVA_OPTS="${JAVA_OPTS}"
DB_INNODB_BUFFER_POOL_SIZE=${DB_POOL}

GT_VERSION=latest
EOF
info "Configuration written to .env (file permissions restricted to your user)."

# --------------------------------------------------------------------------
# Start
# --------------------------------------------------------------------------
echo
bold "Daily download schedule"
randomize_cron

echo
bold "Downloading images and starting Grafioschtrader ..."
pull_images

# The prebuilt GHCR images may not be published yet (or may be private), in
# which case the pull above failed with "denied" and 'up' would fail too.
# Detect any image the stack needs that is neither present locally nor
# pullable, and offer to build it from source instead of failing cryptically.
missing=""
for img in $(docker compose config --images 2>/dev/null | sort -u); do
  docker image inspect "$img" >/dev/null 2>&1 || missing="${missing} ${img}"
done
if [ -n "$missing" ]; then
  warn "These images are not available locally and could not be downloaded:"
  for img in $missing; do warn "    ${img}"; done
  info "The prebuilt images may not be published yet, or their GHCR packages are"
  info "still private. You can build them locally from source instead (the Angular"
  info "build needs roughly 4 GB of free RAM and is slow on a Raspberry Pi)."
  read -r -p "Build the images locally now? [y/N] " b
  if [ "${b:-N}" = "y" ] || [ "${b:-N}" = "Y" ]; then
    docker compose -f docker-compose.yml -f docker-compose.build.yml build
    start_stack -f docker-compose.yml -f docker-compose.build.yml
  else
    fail "Cannot start without the images. Re-run install.sh once they are published/public, or build locally."
  fi
else
  start_stack
fi

echo
if [ -n "$DOMAIN" ]; then
  URL="https://${DOMAIN}/grafioschtrader/"
else
  host_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  port_suffix=""; [ "$GT_HTTP_PORT" != "80" ] && port_suffix=":${GT_HTTP_PORT}"
  URL="http://${host_ip:-localhost}${port_suffix}/grafioschtrader/"
fi
bold "=== Done! ==="
info "The backend and database healthchecks passed. View logs with:"
info "    docker compose logs -f backend"
echo
info "Then open:  ${URL}"
[ -z "$LAN_ADDRESS" ] || info "In the local network also:  http://${LAN_ADDRESS}/grafioschtrader/"
info "Register with ${ADMIN_EMAIL} — this first account receives admin rights."
