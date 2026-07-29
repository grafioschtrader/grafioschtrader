#!/usr/bin/env bash
#
# Update a Docker-based Grafioschtrader installation.
#
#   ./update.sh              update to the version currently set in .env
#   ./update.sh 0.36.3       switch to an exact version
#   ./update.sh latest       track the newest release
#   ./update.sh --build      build the images from source instead of pulling
#
# The script backs up the database, .env and config/ first, migrates renamed
# configuration keys if the release needs it, then obtains the new images — by
# pulling the published ones, or by building them from source if pulling is not
# possible — and finally waits until the backend has finished its database
# migrations. Safe to re-run; nothing is deleted.
#
set -euo pipefail
cd "$(dirname "$0")"

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
info()  { printf '  %s\n' "$*"; }
warn()  { printf '\033[33m  ! %s\033[0m\n' "$*"; }
fail()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------
# 1. Arguments
# --------------------------------------------------------------------------
FORCE_BUILD=false
TARGET=""
for arg in "$@"; do
  case "$arg" in
    --build) FORCE_BUILD=true ;;
    -h|--help) sed -n '3,14p' "$0" | sed 's/^# \?//'; exit 0 ;;
    -*) fail "unknown option: $arg" ;;
    *) TARGET="$arg" ;;
  esac
done

command -v docker >/dev/null 2>&1 \
  || fail "docker is not installed. See https://docs.docker.com/engine/install/"
docker compose version >/dev/null 2>&1 \
  || fail "the 'docker compose' plugin is missing."
[ -f .env ] || fail "no .env found. Run install.sh first."

DB_ROOT_PASSWORD="$(grep -oP '^DB_ROOT_PASSWORD=\K.*' .env || true)"
DB_NAME="$(grep -oP '^DB_NAME=\K.*' .env || true)"
DB_NAME="${DB_NAME:-grafioschtrader}"
[ -n "$DB_ROOT_PASSWORD" ] || fail "DB_ROOT_PASSWORD is missing from .env."

# --------------------------------------------------------------------------
# 2. Target version
# --------------------------------------------------------------------------
if [ -n "$TARGET" ]; then
  if grep -q '^GT_VERSION=' .env; then
    sed -i "s/^GT_VERSION=.*/GT_VERSION=$TARGET/" .env
  else
    printf 'GT_VERSION=%s\n' "$TARGET" >> .env
  fi
fi
GT_VERSION="$(grep -oP '^GT_VERSION=\K.*' .env || true)"
GT_VERSION="${GT_VERSION:-latest}"
bold ""
bold "=== Updating Grafioschtrader to '$GT_VERSION' ==="

# --------------------------------------------------------------------------
# 3. Database backup
# --------------------------------------------------------------------------
# Database migrations run automatically and cannot be undone, so the dump is
# the only way back to the previous release.
bold ""
bold "Backing up the database"
docker compose up -d mariadb >/dev/null
for _ in $(seq 1 30); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' \
        "$(docker compose ps -q mariadb)" 2>/dev/null)" = "healthy" ] && break
  sleep 5
done
BACKUP="gt-backup-$(date +%F-%H%M)-pre-$GT_VERSION.sql.gz"
docker compose exec -T mariadb \
  mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" --single-transaction --routines --events "$DB_NAME" \
  | gzip > "$BACKUP"
gzip -t "$BACKUP" 2>/dev/null || fail "the backup $BACKUP is not readable — aborting before any change."
[ "$(zcat "$BACKUP" | grep -c '^CREATE TABLE')" -gt 0 ] \
  || fail "the backup $BACKUP contains no tables — aborting before any change."
info "$BACKUP ($(du -h "$BACKUP" | cut -f1))"

# The host-side configuration is not in the dump, and section 4 may rewrite it.
STAMP="$(date +%F-%H%M)"
cp .env "gt-env-$STAMP.bak"
info "gt-env-$STAMP.bak"
if [ -f config/application-production.properties ]; then
  cp config/application-production.properties "gt-config-$STAMP.bak"
  info "gt-config-$STAMP.bak"
fi

# --------------------------------------------------------------------------
# 4. Renamed configuration keys
# --------------------------------------------------------------------------
# config/ is mounted from the host and is never touched by an image update, so
# properties that moved from the gt. to the g. prefix (issue #75) have to be
# renamed here — the new code would otherwise ignore the old key and silently
# fall back to its built-in default. The compose file is the marker: it only
# passes the G_* container variables once the rename has landed.
MIGRATE=../util/shellscripts/gt_to_g_rename.sh
if grep -q 'G_JWT_SECRET' docker-compose.yml; then
  if [ ! -x "$MIGRATE" ]; then
    warn "gt_to_g_rename.sh was not found next to this installation."
    warn "Check config/application-production.properties for gt.* keys by hand."
  elif [ -f config/application-production.properties ]; then
    bold ""
    bold "Migrating renamed configuration keys"
    "$MIGRATE" --force -i config/application-production.properties \
      || fail "the configuration migration failed — no image was pulled, nothing was changed."
  fi
fi

# --------------------------------------------------------------------------
# 5. New images: pull, or build from source
# --------------------------------------------------------------------------
bold ""
bold "Fetching the new images"
BUILD_FILES=(-f docker-compose.yml -f docker-compose.build.yml)
build_locally() {
  [ -d ../backend ] && [ -d ../frontend ] \
    || fail "cannot build: this is only the docker/ directory, the source is missing.
       Clone the full repository (git clone https://github.com/grafioschtrader/grafioschtrader.git)
       and run update.sh from its docker/ directory."
  # Building uses the source checked out next to this script. That source is
  # only the requested release if the checkout was updated first — otherwise the
  # previous release would be rebuilt and mislabelled with the new version.
  local src
  src="$(grep -m1 -oP '<version>\K[^<]+' ../backend/pom.xml || true)"
  if [ "$GT_VERSION" != "latest" ] && [ -n "$src" ] && [ "$src" != "$GT_VERSION" ]; then
    fail "the source here is version $src, but $GT_VERSION was requested.
       Update the source first, then run this script again:
         git -C .. fetch --depth 1 origin master && git -C .. reset --hard FETCH_HEAD"
  fi
  info "Building version ${src:-unknown} from source — this takes a while"
  info "(the Angular build needs ~4 GB RAM; expect 20-40 minutes on a Raspberry Pi)."
  docker compose "${BUILD_FILES[@]}" build
}

if $FORCE_BUILD; then
  build_locally
elif docker compose pull; then
  info "Published images fetched."
else
  warn "The images could not be downloaded."
  warn "Usually the GHCR packages are not public, or a proxy blocks ghcr.io."
  warn "See the 'unauthorized / denied when pulling' entry in README.md."
  if [ -t 0 ]; then
    read -r -p "Build the images from source instead? [y/N] " b
    case "${b:-N}" in [yY]*) build_locally ;; *) fail "aborted — nothing was changed." ;; esac
  else
    fail "aborted — nothing was changed. Run interactively to build from source instead."
  fi
fi

# --------------------------------------------------------------------------
# 6. Restart and wait for the migrations
# --------------------------------------------------------------------------
bold ""
bold "Restarting"
docker compose up -d

info "Waiting for the backend — a release with many migrations can take several minutes."
deadline=$(( $(date +%s) + 1800 ))
while :; do
  status="$(docker inspect -f '{{.State.Health.Status}}' \
             "$(docker compose ps -q backend)" 2>/dev/null || echo unknown)"
  [ "$status" = "healthy" ] && break
  if [ "$(date +%s)" -ge "$deadline" ]; then
    warn "The backend is still '$status' after 30 minutes. Check: docker compose logs -f backend"
    exit 1
  fi
  sleep 10
done

# --------------------------------------------------------------------------
# 7. Report
# --------------------------------------------------------------------------
schema="$(docker compose exec -T mariadb \
  mariadb -uroot -p"$DB_ROOT_PASSWORD" -N -B "$DB_NAME" \
  -e 'select version from flyway_schema_history where success = 1 order by installed_rank desc limit 1' \
  2>/dev/null || true)"
bold ""
bold "Grafioschtrader is running."
info "Images:  $GT_VERSION"
info "Schema:  ${schema:-unknown}"
info "Backup:  $BACKUP"
info "Log:     docker compose logs -f backend"
