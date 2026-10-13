#!/usr/bin/env bash
# Remove this Grafioschtrader Compose installation; keep persistent data by default.
set -euo pipefail
cd "$(dirname "$0")"

usage() {
  cat <<'EOF'
Usage: bash uninstall.sh [--purge] [--yes] [--dry-run]

Without options, remove the installation's containers (including DuckDNS),
networks and orphaned containers belonging to the same Compose project.
The database and Caddy certificate/configuration volumes are kept.

  --purge    Also permanently delete the Compose-managed volumes, including
             the database and HTTPS certificates. Requires confirmation.
  --yes      With --purge, explicitly confirm deletion without an input prompt.
  --dry-run  Validate the configuration and show the command without removing anything.
  -h, --help Show this help.

Docker Engine, images, installation files, .env, config/, backups, external
volumes and bind-mounted host data are always kept. No DNS records or router
settings are changed. Use the original Compose files and .env of the installation.
EOF
}

fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
purge=false
confirmed=false
dry_run=false
for arg in "$@"; do
  case "$arg" in
    --purge) purge=true ;;
    --yes) confirmed=true ;;
    --dry-run) dry_run=true ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown option: $arg. See --help." ;;
  esac
done

[ -f docker-compose.yml ] || fail "docker-compose.yml is missing next to this script."
[ -f .env ] || fail "The installation's .env is missing. Restore it before uninstalling."
command -v docker >/dev/null 2>&1 || fail "Docker is not installed."
docker compose version >/dev/null 2>&1 || fail "The Docker Compose plugin is missing."

# All profiles must participate even if .env no longer enables DuckDNS; otherwise
# an old updater could survive removal and keep changing the domain's address.
compose=(docker compose --profile '*')
"${compose[@]}" config --quiet || fail "Compose configuration is invalid; nothing was removed."
printf 'Installation directory: %s\n' "$PWD"
printf 'Services (all profiles):\n'
"${compose[@]}" config --services

command=("${compose[@]}" down --remove-orphans)
if $purge; then
  command+=(--volumes)
  printf '\nThis permanently deletes the database and HTTPS certificate volumes.\n'
  printf 'No automatic backup is made. Host files and existing backups are kept.\n'
else
  printf '\nDatabase and certificate volumes will be kept.\n'
fi
printf 'Command:'
printf ' %q' "${command[@]}"
printf '\n'

if $dry_run; then
  printf 'Dry run: nothing was removed.\n'
  exit 0
fi
if $purge && ! $confirmed; then
  answer=""
  read -r -p 'Type DELETE to permanently delete these volumes: ' answer \
    || fail "No confirmation received; nothing was removed."
  [ "$answer" = DELETE ] || fail "Deletion cancelled; nothing was removed."
fi

"${command[@]}"
printf '\nGrafioschtrader containers and networks removed.\n'
if $purge; then
  printf 'Compose-managed volumes removed.\n'
else
  printf 'Data kept. To start again: docker compose up -d\n'
fi
printf 'Docker, images, installation files and backups are kept.\n'
