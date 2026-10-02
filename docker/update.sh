#!/usr/bin/env bash
#
# Update a Docker-based Grafioschtrader installation.
#
#   ./update.sh              update to the version currently set in .env
#   ./update.sh 0.37.3      switch to an exact version
#   ./update.sh latest       track the newest release
#   ./update.sh --build      build the images from source instead of pulling
#   ./update.sh --replace-modified   also replace deployment files edited here
#   ./update.sh --skip-files         leave the deployment files as they are
#
# The script first brings the deployment files (docker-compose.yml, this script,
# the helpers) to the target release, then backs up the database, .env and
# config/, migrates renamed configuration keys if the release needs it, obtains
# the new images — by pulling the published ones, or by building them from
# source if pulling is not possible — and finally waits until the backend has
# finished its database migrations. Safe to re-run; nothing is deleted.
#
set -euo pipefail
cd "$(dirname "$0")"
ORIG_ARGS=("$@")

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
info()  { printf '  %s\n' "$*"; }
warn()  { printf '\033[33m  ! %s\033[0m\n' "$*"; }
fail()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------
# 1. Arguments
# --------------------------------------------------------------------------
FORCE_BUILD=false
REPLACE_MODIFIED=false
SKIP_FILES=false
TARGET=""
for arg in "$@"; do
  case "$arg" in
    --build) FORCE_BUILD=true ;;
    --replace-modified) REPLACE_MODIFIED=true ;;
    --skip-files) SKIP_FILES=true ;;
    -h|--help) sed -n '3,17p' "$0" | sed 's/^# \?//'; exit 0 ;;
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
# 1b. Deployment files
# --------------------------------------------------------------------------
# An image update alone never reaches the files on this host: docker-compose.yml,
# this script, the other scripts here and the two helpers in ../util/shellscripts
# would otherwise stay at whatever the original checkout brought, so a changed
# container variable or health check never arrives (issue #240). They are brought
# to the target release here, before anything else changes. A file is replaced
# only while it is still the one a release delivered; a file edited on this host
# stops the update instead, unless --replace-modified is given. .env, config/ and
# the Docker volumes are never touched. .gt-deployment.sha256 records what was
# delivered, so the next update can tell a delivered file from an edited one.
# When this script itself changes, the new one takes over and starts over.
GT_REPO_URL=https://github.com/grafioschtrader/grafioschtrader.git
GT_REPO_ARCHIVE=https://github.com/grafioschtrader/grafioschtrader/archive/refs/tags
GT_RELEASE_API=https://api.github.com/repos/grafioschtrader/grafioschtrader/releases/latest
DEPLOYMENT_MANIFEST=.gt-deployment.sha256
DOCKER_DIR="$(basename "$PWD")"

# True when the parent directory is the top of a git checkout (the installation
# was made with git clone, as the README describes).
IS_GIT=false
if command -v git >/dev/null 2>&1 \
    && [ "$(git -C .. rev-parse --is-inside-work-tree 2>/dev/null || true)" = true ] \
    && [ -z "$(git -C .. rev-parse --show-prefix 2>/dev/null)" ]; then
  IS_GIT=true
fi

file_hash() { sha256sum < "$1" | cut -d' ' -f1; }

# Release path (docker/..., util/...) -> path seen from this directory / from the parent.
host_path()   { case "$1" in docker/*) printf '%s\n' "${1#docker/}" ;; *) printf '../%s\n' "$1" ;; esac; }
parent_path() { case "$1" in docker/*) printf '%s\n' "$DOCKER_DIR/${1#docker/}" ;; *) printf '%s\n' "$1" ;; esac; }

# "latest" is resolved to the newest published release, the one the latest image tag points to.
resolve_release() {
  if [ "$1" != latest ]; then printf '%s\n' "$1"; return; fi
  { command -v curl >/dev/null 2>&1 \
      && curl -fsSL "$GT_RELEASE_API" 2>/dev/null | grep -m1 -oP '"tag_name":\s*"V\K[^"]+'; } \
    || { command -v git >/dev/null 2>&1 \
      && git ls-remote --tags --refs "$GT_REPO_URL" 2>/dev/null \
         | sed -n 's#.*refs/tags/V\([0-9][0-9.]*\)$#\1#p' | sort -V | tail -n1; } \
    || true
}

# Extracts docker/ and the two helpers of release V<version> into a directory.
fetch_release() {
  local tag="V$1" dir="$2"
  local -a depth=()
  mkdir -p "$dir"
  if $IS_GIT; then
    if ! git -C .. rev-parse -q --verify "refs/tags/$tag^{commit}" >/dev/null; then
      # Deepening is fine, but a complete clone must not be turned into a shallow one.
      if [ "$(git -C .. rev-parse --is-shallow-repository)" = true ]; then depth=(--depth 1); fi
      timeout 120 git -C .. fetch -q ${depth[@]+"${depth[@]}"} origin "refs/tags/$tag:refs/tags/$tag" 2>/dev/null \
        || timeout 120 git -C .. fetch -q ${depth[@]+"${depth[@]}"} "$GT_REPO_URL" \
             "refs/tags/$tag:refs/tags/$tag" 2>/dev/null \
        || return 1
    fi
    git -C .. archive "$tag" docker util/shellscripts/gt_to_g_rename.sh util/shellscripts/gtcronrandom.sh \
      | tar -x -C "$dir" || return 1
  else
    command -v curl >/dev/null 2>&1 || return 1
    curl -fsSL "$GT_REPO_ARCHIVE/$tag.tar.gz" \
      | tar -xz -C "$dir" --strip-components=1 --wildcards '*/docker/*' \
          '*/util/shellscripts/gt_to_g_rename.sh' '*/util/shellscripts/gtcronrandom.sh' || return 1
  fi
  [ -f "$dir/docker/docker-compose.yml" ]
}

# Hash of the file as a release delivered it to this host, or nothing if unknown:
# the record of the last update, else the git index of the checkout, else the
# files of the release named in .env before this update.
baseline_hash() {
  if [ -f "$DEPLOYMENT_MANIFEST" ]; then
    awk -v p="$1" '$2 == p { print $1; exit }' "$DEPLOYMENT_MANIFEST"
  elif $IS_GIT && git -C .. cat-file -e ":$1" 2>/dev/null; then
    git -C .. show ":$1" | sha256sum | cut -d' ' -f1
  elif [ -n "$OLD_BUNDLE" ] && [ -f "$OLD_BUNDLE/$1" ]; then
    file_hash "$OLD_BUNDLE/$1"
  fi
}

refresh_deployment_files() {
  local release="$1" previous="$2" work rel host base current delivered
  local -a changes=() conflicts=() existing=() manifest=()
  work="$(mktemp -d)"
  bold ""
  bold "Deployment files of release V$release"
  if ! fetch_release "$release" "$work/new"; then
    rm -rf "$work"
    fail "the deployment files of release V$release could not be fetched — nothing was changed.
       Check the network access to github.com, or run again with --skip-files to update the images alone."
  fi
  OLD_BUNDLE=""
  if [ ! -f "$DEPLOYMENT_MANIFEST" ] && ! $IS_GIT && [ -n "$previous" ] && [ "$previous" != latest ] \
      && [ "$previous" != "$release" ] && fetch_release "$previous" "$work/old"; then
    OLD_BUNDLE="$work/old"
  fi

  while IFS= read -r rel; do
    case "$rel" in docker/config/*|docker/.env) continue ;; esac
    # The helpers are only kept up to date where the installation has them.
    case "$rel" in util/*) [ -d ../util/shellscripts ] || continue ;; esac
    host="$(host_path "$rel")"
    delivered="$(file_hash "$work/new/$rel")"
    manifest+=("$delivered  $rel")
    if [ ! -f "$host" ]; then
      changes+=("$rel")
      continue
    fi
    current="$(file_hash "$host")"
    if [ "$current" = "$delivered" ]; then
      continue
    fi
    base="$(baseline_hash "$rel")"
    if [ -n "$base" ] && [ "$current" = "$base" ]; then
      changes+=("$rel")
    elif [ -n "$base" ] && [ "$base" = "$delivered" ] && ! $REPLACE_MODIFIED; then
      # Edited here, but the release did not change it: the local edit stays.
      info "kept local change: $host"
    elif $REPLACE_MODIFIED; then
      changes+=("$rel")
    else
      conflicts+=("$rel")
    fi
  done < <(cd "$work/new" && find . -type f | sed 's#^\./##' | sort)

  if [ ${#conflicts[@]} -gt 0 ]; then
    rm -rf "$work"
    warn "These files were changed on this host and differ from release V$release:"
    for rel in "${conflicts[@]}"; do warn "  $(host_path "$rel")"; done
    fail "update stopped — nothing was changed.
       Settings belong in .env or config/, which updates never touch. Move your change there if possible,
       then run again with --replace-modified: the files above are replaced by the release version and
       the edited ones are kept in a gt-deployment-*.tar.gz backup first.
       Release version for comparison: https://github.com/grafioschtrader/grafioschtrader/tree/V$release/docker"
  fi

  if [ ${#changes[@]} -eq 0 ]; then
    info "Already up to date."
  else
    for rel in "${changes[@]}"; do
      if [ -f "$(host_path "$rel")" ]; then existing+=("$(parent_path "$rel")"); fi
    done
    if [ ${#existing[@]} -gt 0 ]; then
      local backup
      backup="gt-deployment-$(date +%F-%H%M%S).tar.gz"
      tar -czf "$backup" -C .. "${existing[@]}" \
        || { rm -rf "$work"; fail "the backup $backup could not be written — nothing was changed."; }
      info "$backup"
    fi
    # Write beside the target and rename: a running script is never overwritten in place.
    for rel in "${changes[@]}"; do
      host="$(host_path "$rel")"
      mkdir -p "$(dirname "$host")"
      cp "$work/new/$rel" "$host.gt-new"
      chmod --reference="$work/new/$rel" "$host.gt-new"
      mv -f "$host.gt-new" "$host"
      info "updated: $host"
    done
  fi
  printf '%s\n' "${manifest[@]}" > "$DEPLOYMENT_MANIFEST"
  rm -rf "$work"

  if printf '%s\n' ${changes[@]+"${changes[@]}"} | grep -qx 'docker/update.sh'; then
    info "update.sh itself was updated — continuing with the new version."
    exec env GT_DEPLOYMENT_REFRESHED=1 bash ./update.sh ${ORIG_ARGS[@]+"${ORIG_ARGS[@]}"}
  fi
}

if $SKIP_FILES; then
  warn "--skip-files: the deployment files are left as they are."
elif [ "${GT_DEPLOYMENT_REFRESHED:-}" != 1 ]; then
  PREVIOUS_VERSION="$(grep -oP '^GT_VERSION=\K.*' .env || true)"
  RELEASE="$(resolve_release "${TARGET:-${PREVIOUS_VERSION:-latest}}")"
  [ -n "$RELEASE" ] || fail "the newest release could not be determined — nothing was changed.
       Name the version explicitly (./update.sh 0.37.3), or run with --skip-files to update the images alone."
  refresh_deployment_files "$RELEASE" "$PREVIOUS_VERSION"
fi

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
# 4b. Daily download schedule
# --------------------------------------------------------------------------
# Every GT installation ships with the same cron defaults, so without a change
# all of them query the free price and dividend providers in the same minute.
# This gives the installation its own slot, drawn in this host's local time and
# written as UTC (the backend evaluates every cron expression in UTC). It only
# fires while the schedule is still untouched — a time set by hand is never
# overwritten, and installations from before this feature are covered here.
# Set GT_CRON_RANDOMIZE=off to skip it.
RANDOMIZE=../util/shellscripts/gtcronrandom.sh
if [ -f config/application-production.properties ]; then
  if [ ! -f "$RANDOMIZE" ]; then
    warn "gtcronrandom.sh was not found next to this installation — the daily price"
    warn "and dividend jobs keep their current times."
  else
    bold ""
    bold "Daily download schedule"
    bash "$RANDOMIZE" --file config/application-production.properties \
      || warn "The cron randomization failed — the current times are kept."
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
