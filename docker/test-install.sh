#!/usr/bin/env bash
# Installer regression checks with simulated host services and Compose results.
# Never contacts Docker, changes real services or downloads images.
set -euo pipefail
docker_dir="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"
export PATH="$work/bin:$PATH" TEST_STATE="$work/state" TEST_LOG="$work/commands"

cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$TEST_LOG"
case "$*" in
  'compose version') exit 0 ;;
  # Like the Docker CLI (verified with Docker 29): DOCKER_HOST wins over
  # DOCKER_CONTEXT, which wins over the saved context; an unknown context fails.
  'context inspect --format {{.Endpoints.docker.Host}}')
    if [ -n "${DOCKER_HOST:-}" ]; then printf '%s\n' "$DOCKER_HOST"
    elif [ -n "${DOCKER_CONTEXT:-}" ]; then
      case "$DOCKER_CONTEXT" in
        remote) printf 'ssh://remote.invalid\n' ;;
        local) printf 'unix:///var/run/docker.sock\n' ;;
        *) exit 1 ;;
      esac
    else printf '%s\n' "$TEST_ENDPOINT"; fi ;;
  'compose config duckdns')
    printf 'services:\n  duckdns:\n    environment:\n'
    [ "$TEST_WIRED" = no ] || printf '      UPDATE_IP: ipv6\n'
    printf '    volumes:\n'
    [ "$TEST_WIRED" = no ] || printf '      - target: /etc/gt-duckdns-update.sh\n' ;;
  info)
    # Like docker.socket, this can start the daemon without enabling boot startup.
    touch "$TEST_STATE/active"
    ;;
  'compose pull --ignore-pull-failures') exit 0 ;;
  'compose config --images') printf 'test/backend\n' ;;
  'image inspect '*) exit "$TEST_IMAGE_MISSING" ;;
  'compose -f docker-compose.yml -f docker-compose.build.yml build') exit 0 ;;
  'compose up -d --wait --wait-timeout 1800' | \
  'compose -f docker-compose.yml -f docker-compose.build.yml up -d --wait --wait-timeout 1800')
    exit "$TEST_START_STATUS" ;;
  *) printf 'Unexpected Docker command: %s\n' "$*" >&2; exit 99 ;;
esac
EOF
cat > "$work/bin/systemctl" <<'EOF'
#!/usr/bin/env bash
printf 'systemctl %s\n' "$*" >> "$TEST_LOG"
case "$*" in
  'show docker.service --property=LoadState --value') printf '%s\n' "$TEST_LOAD_STATE" ;;
  'is-enabled docker.service')
    cat "$TEST_STATE/enabled"
    [ "$(cat "$TEST_STATE/enabled")" = enabled ] ;;
  'is-active --quiet docker.service') [ -f "$TEST_STATE/active" ] ;;
  'enable --now docker.service')
    [ "$TEST_ENABLE_STATUS" = 0 ] || exit "$TEST_ENABLE_STATUS"
    if [ "$TEST_ENABLE_EFFECTIVE" = yes ]; then
      printf 'enabled\n' > "$TEST_STATE/enabled"
      touch "$TEST_STATE/active"
    fi ;;
  *) exit 99 ;;
esac
EOF
cat > "$work/bin/id" <<'EOF'
#!/usr/bin/env bash
[ "$*" = -u ] || exit 99
printf '%s\n' "$TEST_UID"
EOF
cat > "$work/bin/sudo" <<'EOF'
#!/usr/bin/env bash
printf 'sudo %s\n' "$*" >> "$TEST_LOG"
exec "$@"
EOF
cat > "$work/bin/ss" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$work/bin/"*

fail() { printf 'FAIL: %s\n' "$*" >&2; cat "$work/output" >&2; exit 1; }
reset_case() {
  install_dir="$work/$1"
  mkdir -p "$install_dir" "$TEST_STATE"
  cp "$docker_dir/install.sh" "$install_dir/install.sh"
  printf 'DB_PASSWORD=existing-secret\n' > "$install_dir/.env"
  printf 'disabled\n' > "$TEST_STATE/enabled"
  rm -f "$TEST_STATE/active"
  : > "$TEST_LOG"
  unset DOCKER_HOST DOCKER_CONTEXT
  export TEST_ENDPOINT=unix:///var/run/docker.sock TEST_LOAD_STATE=loaded TEST_UID=0
  export TEST_ENABLE_STATUS=0 TEST_ENABLE_EFFECTIVE=yes TEST_START_STATUS=0 TEST_IMAGE_MISSING=0
  export TEST_WIRED=yes
}
run_existing() {
  printf 'y\n' | bash "$install_dir/install.sh" > "$work/output" 2>&1
}
assert_no_success() {
  if grep -qE '=== Done! ===|Grafioschtrader is running' "$work/output"; then
    fail 'reported success after failure'
  fi
}
assert_no_host_changes() {
  if grep -q '^systemctl ' "$TEST_LOG"; then fail 'accessed unrelated host service'; fi
}

# Reproduce the Orange Pi: working socket activation, disabled boot service.
reset_case socket-only
run_existing
[ "$(cat "$TEST_STATE/enabled")" = enabled ] || fail 'left Docker disabled at boot'
[ -f "$TEST_STATE/active" ] || fail 'did not start Docker'
cmp "$install_dir/.env" <(printf 'DB_PASSWORD=existing-secret\n') || fail 'changed existing configuration'
grep -q 'Grafioschtrader is running' "$work/output" || fail 'missing healthy success'

reset_case already-enabled
printf 'enabled\n' > "$TEST_STATE/enabled"
touch "$TEST_STATE/active"
run_existing
if grep -q 'enable --now' "$TEST_LOG"; then fail 'unnecessarily changed running service'; fi

reset_case runtime-only
printf 'enabled-runtime\n' > "$TEST_STATE/enabled"
export TEST_UID=1000
run_existing
grep -qx 'sudo systemctl enable --now docker.service' "$TEST_LOG" || fail 'did not use admin privileges'
[ "$(cat "$TEST_STATE/enabled")" = enabled ] || fail 'accepted temporary enablement'

for failure in denied ineffective masked; do
  reset_case "$failure"
  case "$failure" in
    denied) export TEST_ENABLE_STATUS=1 ;;
    ineffective) export TEST_ENABLE_EFFECTIVE=no ;;
    masked) export TEST_LOAD_STATE=masked ;;
  esac
  if run_existing; then fail "accepted service failure: $failure"; fi
  if grep -q '^docker info$' "$TEST_LOG"; then fail 'socket activation hid service failure'; fi
  assert_no_success
done

# The installer follows the endpoint the CLI resolves: DOCKER_HOST overrides
# DOCKER_CONTEXT, which overrides the saved context.
reset_case remote-context
export DOCKER_CONTEXT=remote
run_existing || fail 'failed with DOCKER_CONTEXT=remote'
assert_no_host_changes
reset_case remote-host
export DOCKER_HOST=tcp://remote.invalid:2376
run_existing || fail 'failed with a remote DOCKER_HOST'
assert_no_host_changes
reset_case local-context-remote-host
export DOCKER_CONTEXT=local DOCKER_HOST=tcp://remote.invalid:2376
run_existing || fail 'failed with a local DOCKER_CONTEXT and a remote DOCKER_HOST'
assert_no_host_changes
for context in remote does-not-exist; do
  reset_case "local-host-$context"
  export DOCKER_CONTEXT="$context" DOCKER_HOST=unix:///var/run/docker.sock
  run_existing || fail "DOCKER_HOST did not override DOCKER_CONTEXT=$context"
  [ "$(cat "$TEST_STATE/enabled")" = enabled ] || fail "left the local Docker disabled with DOCKER_CONTEXT=$context"
done
for endpoint in unix:///run/user/1000/docker.sock unix:///home/test/.docker/desktop/docker.sock npipe:////./pipe/docker_engine; do
  reset_case "separate-$RANDOM"
  export TEST_ENDPOINT="$endpoint"
  run_existing
  assert_no_host_changes
done
reset_case non-systemd
export TEST_LOAD_STATE=not-found
run_existing
if grep -q 'enable --now' "$TEST_LOG"; then fail 'changed unavailable service'; fi

# Neither the existing-config path nor either fresh-install path may claim
# success if Compose reports an unhealthy service, an exit or a wait timeout.
reset_case existing-unhealthy
export TEST_START_STATUS=1
if run_existing; then fail 'accepted unhealthy existing installation'; fi
assert_no_success
for mode in prebuilt build; do
  reset_case "fresh-$mode"
  rm "$install_dir/.env"
  export TEST_START_STATUS=1
  [ "$mode" != build ] || export TEST_IMAGE_MISSING=1
  if printf '\nadmin@example.com\nn\n\ny\n' | bash "$install_dir/install.sh" > "$work/output" 2>&1; then
    fail "accepted unhealthy $mode installation"
  fi
  grep -q 'did not become ready' "$work/output" || fail "did not check $mode readiness"
  assert_no_success
done
# A docker-compose.yml that ignores the DuckDNS address family would start the
# image's IPv4 updater; such a file must not start the stack.
reset_case duckdns-unwired
printf 'COMPOSE_PROFILES=duckdns\nDUCKDNS_UPDATE_IP="ipv6"\nDB_PASSWORD=existing-secret\n' > "$install_dir/.env"
export TEST_WIRED=no
if run_existing; then fail 'started an updater that ignores DUCKDNS_UPDATE_IP'; fi
grep -q 'does not hand DUCKDNS_UPDATE_IP=ipv6' "$work/output" || fail 'missing DuckDNS wiring message'
if grep -q '^docker compose up' "$TEST_LOG"; then fail 'started the stack with an unwired updater'; fi
assert_no_success
reset_case duckdns-wired
printf 'COMPOSE_PROFILES=duckdns\nDUCKDNS_UPDATE_IP=ipv6\nDB_PASSWORD=existing-secret\n' > "$install_dir/.env"
run_existing || fail 'refused a wired DuckDNS updater'
printf 'PASS: persistent autostart, endpoint precedence, service failures, readiness and DuckDNS wiring\n'
