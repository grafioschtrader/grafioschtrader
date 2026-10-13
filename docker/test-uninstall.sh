#!/usr/bin/env bash
# Offline checks: record Docker arguments instead of touching a Docker daemon.
set -euo pipefail
docker_dir="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin" "$work/installation/config"
cp "$docker_dir/uninstall.sh" "$work/installation/uninstall.sh"
printf 'name: grafioschtrader\n' > "$work/installation/docker-compose.yml"
printf 'DB_PASSWORD=keep-this-secret\n' > "$work/installation/.env"
printf 'keep configuration\n' > "$work/installation/config/local.properties"
printf 'keep backup\n' > "$work/installation/gt-backup.sql.gz"
export PATH="$work/bin:$PATH" DOCKER_LOG="$work/docker-log" TEST_CONFIG_STATUS=0 TEST_DOWN_STATUS=0
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_LOG"
case "$*" in
  'compose version') exit 0 ;;
  'compose --profile * config --quiet') exit "$TEST_CONFIG_STATUS" ;;
  'compose --profile * config --services') printf 'mariadb\nbackend\nweb\nduckdns\n' ;;
  'compose --profile * down --remove-orphans' | 'compose --profile * down --remove-orphans --volumes')
    exit "$TEST_DOWN_STATUS" ;;
  *) exit 99 ;;
esac
EOF
chmod +x "$work/bin/docker"
fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
run() {
  : > "$DOCKER_LOG"
  bash "$work/installation/uninstall.sh" "$@" > "$work/output" 2>&1
}
assert_no_removal() {
  if grep -q ' down ' "$DOCKER_LOG"; then fail "removed resources unexpectedly"; fi
}
assert_command() {
  grep -qxF -- "$1" "$DOCKER_LOG" || fail "wrong removal command"
}

# Invoke from outside the installation; all profiles, including disabled DuckDNS,
# must be included, and neither the default nor preview may delete volumes.
cd "$work"
run
assert_command 'compose --profile * down --remove-orphans'
if grep -q -- '--volumes' "$DOCKER_LOG"; then fail "default removed data"; fi
run --purge --dry-run
assert_no_removal
if printf 'no\n' | run --purge; then fail "accepted refused confirmation"; fi
assert_no_removal
if run --purge < /dev/null; then fail "accepted missing confirmation"; fi
assert_no_removal
printf 'DELETE\n' | run --purge
assert_command 'compose --profile * down --remove-orphans --volumes'
run --purge --yes
assert_command 'compose --profile * down --remove-orphans --volumes'
run --purge --yes --dry-run
assert_no_removal

if TEST_CONFIG_STATUS=1 run --purge --yes; then fail "ignored invalid configuration"; fi
assert_no_removal
if TEST_DOWN_STATUS=1 run; then fail "ignored Docker removal failure"; fi
if grep -q 'Grafioschtrader containers and networks removed' "$work/output"; then
  fail "reported successful removal after Docker failed"
fi
if run --unknown; then fail "accepted unknown option"; fi
assert_no_removal

# Configuration and backup files must survive even the explicit purge path.
grep -qx 'DB_PASSWORD=keep-this-secret' "$work/installation/.env" || fail "changed secrets"
grep -qx 'keep configuration' "$work/installation/config/local.properties" || fail "changed config"
grep -qx 'keep backup' "$work/installation/gt-backup.sql.gz" || fail "changed backup"
mv "$work/installation/.env" "$work/installation/saved.env"
if run; then fail "accepted missing installation configuration"; fi
assert_no_removal
run --help
[ ! -s "$DOCKER_LOG" ] || fail "help invoked Docker"
printf 'PASS: default, purge, confirmation, preview, failure and file-preservation checks\n'
