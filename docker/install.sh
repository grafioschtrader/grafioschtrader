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
docker info >/dev/null 2>&1 \
  || fail "cannot talk to the Docker daemon. Is it running? (You may need to add your user to the 'docker' group.)"

gen_secret() {
  local len="$1"
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 48 | tr -d '/+=\n' | head -c "$len"
  else
    head -c 64 /dev/urandom | base64 | tr -d '/+=\n' | head -c "$len"
  fi
}

# --------------------------------------------------------------------------
# Existing .env
# --------------------------------------------------------------------------
# Non-fatal pull: with no internet (or images not yet published) locally
# present images are still usable.
pull_images() {
  docker compose pull || warn "Image download failed — continuing with locally available images."
}

# Secrets carried over from an existing .env on reconfiguration. The database
# volume keeps the old credentials, so generating new ones would break the
# connection between backend and database.
OLD_DB_ROOT_PASSWORD=""; OLD_DB_PASSWORD=""; OLD_JWT_SECRET=""
if [ -f .env ]; then
  bold "An existing configuration (.env) was found."
  read -r -p "Keep it and just (re)start Grafioschtrader? [Y/n] " keep
  if [ "${keep:-Y}" != "n" ] && [ "${keep:-Y}" != "N" ]; then
    pull_images
    docker compose up -d
    bold "Grafioschtrader is starting with the existing configuration."
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
if [ -n "$DOMAIN" ]; then
  SITE_ADDRESS="$DOMAIN"
  warn "Ports 80 and 443 of this machine must be reachable from the internet"
  warn "(configure port forwarding on your router), otherwise the Let's Encrypt"
  warn "certificate cannot be issued."
fi

# --------------------------------------------------------------------------
# DuckDNS
# --------------------------------------------------------------------------
COMPOSE_PROFILES=""
DUCKDNS_SUBDOMAIN=""
DUCKDNS_TOKEN=""
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
  default_sub="${DOMAIN%%.duckdns.org}"
  read -r -p "DuckDNS subdomain [${default_sub}]: " DUCKDNS_SUBDOMAIN
  DUCKDNS_SUBDOMAIN="${DUCKDNS_SUBDOMAIN:-$default_sub}"
  while [ -z "$DUCKDNS_TOKEN" ]; do
    read -r -p "DuckDNS token (from https://www.duckdns.org): " DUCKDNS_TOKEN
  done
  COMPOSE_PROFILES="duckdns"
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
umask 077
cat > .env <<EOF
# Generated by install.sh on $(date -u +%Y-%m-%dT%H:%M:%SZ)
# You can edit this file and apply changes with: docker compose up -d

DB_ROOT_PASSWORD=${DB_ROOT_PASSWORD}
DB_PASSWORD=${DB_PASSWORD}
JWT_SECRET=${JWT_SECRET}

ADMIN_EMAIL=${ADMIN_EMAIL}
GT_ALLOWED_USERS=${GT_ALLOWED_USERS}

GT_SITE_ADDRESS=${SITE_ADDRESS}

${COMPOSE_PROFILES:+COMPOSE_PROFILES=${COMPOSE_PROFILES}}
DUCKDNS_SUBDOMAIN=${DUCKDNS_SUBDOMAIN}
DUCKDNS_TOKEN=${DUCKDNS_TOKEN}

MAIL_HOST=${MAIL_HOST}
MAIL_PORT=${MAIL_PORT}
MAIL_USER=${MAIL_USER}
MAIL_PASSWORD=${MAIL_PASSWORD}
MAIL_SMTP_AUTH=true
MAIL_SMTP_SSL=${MAIL_SMTP_SSL}
MAIL_SMTP_STARTTLS=${MAIL_SMTP_STARTTLS}

JAVA_OPTS=${JAVA_OPTS}
DB_INNODB_BUFFER_POOL_SIZE=${DB_POOL}

GT_VERSION=latest
EOF
info "Configuration written to .env (file permissions restricted to your user)."

# --------------------------------------------------------------------------
# Start
# --------------------------------------------------------------------------
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
    docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
  else
    fail "Cannot start without the images. Re-run install.sh once they are published/public, or build locally."
  fi
else
  docker compose up -d
fi

echo
if [ -n "$DOMAIN" ]; then
  URL="https://${DOMAIN}/grafioschtrader/"
else
  host_ip="$(hostname -I 2>/dev/null | awk '{print $1}' || true)"
  URL="http://${host_ip:-localhost}/grafioschtrader/"
fi
bold "=== Done! ==="
info "The first start runs all database migrations — this can take 1-3 minutes"
info "(on a Raspberry Pi somewhat longer). Watch it with:"
info "    docker compose logs -f backend"
echo
info "Then open:  ${URL}"
info "Register with ${ADMIN_EMAIL} — this first account receives admin rights."
