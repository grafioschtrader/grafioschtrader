#!/usr/bin/env bash
# Regression checks of container-ipv6.sh with simulated host routes, sysctl
# values and Docker answers. Never contacts Docker or changes the host.
set -euo pipefail
docker_dir="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"
export PATH="$work/bin:$PATH"

cat > "$work/bin/ip" <<'EOF'
#!/usr/bin/env bash
[ "$*" = '-6 route show default' ] || exit 99
printf '%s' "$TEST_ROUTES"
EOF
cat > "$work/bin/sysctl" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  '-n net/ipv6/conf/wlan0/accept_ra') printf '%s\n' "$TEST_ACCEPT_RA" ;;
  '-n net.ipv6.conf.all.forwarding') printf '%s\n' "$TEST_FORWARDING" ;;
  *) exit 1 ;;
esac
EOF
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
case "$*" in
  'context inspect --format {{.Endpoints.docker.Host}}') printf '%s\n' "$TEST_ENDPOINT" ;;
  'version --format {{.Server.Version}}') printf '%s\n' "$TEST_DOCKER_VERSION" ;;
  'network inspect grafioschtrader_default') [ "$TEST_NETWORK_EXISTS" = yes ] ;;
  *) printf 'Unexpected Docker command: %s\n' "$*" >&2; exit 99 ;;
esac
EOF
chmod +x "$work/bin/"*

fail() { printf 'FAIL: %s\n' "$*" >&2; cat "$work/output" >&2 2>/dev/null || true; exit 1; }

RA_ROUTE='default via fe80::1 dev wlan0 proto ra metric 600 pref medium'
reset_case() {
  case_dir="$work/$1"
  mkdir -p "$case_dir"
  unset GT_CONTAINER_IPV6
  export TEST_ROUTES="$RA_ROUTE" TEST_ACCEPT_RA=0 TEST_FORWARDING=1 TEST_ENDPOINT=unix:///var/run/docker.sock
  export TEST_DOCKER_VERSION=29.7.2 TEST_NETWORK_EXISTS=yes
}
# Runs setup_container_ipv6 as install.sh and update.sh do and prints the flag.
run_setup() {
  (cd "$case_dir" && info() { printf 'INFO %s\n' "$*"; } && warn() { printf 'WARN %s\n' "$*"; } \
    && . "$docker_dir/container-ipv6.sh" && setup_container_ipv6 && printf 'CHANGED=%s\n' "$CONTAINER_NETWORK_CHANGED") \
    > "$work/output" 2>&1 || fail "setup_container_ipv6 failed in $case_dir"
}
assert_override() { grep -q 'enable_ipv6: true' "$case_dir/docker-compose.override.yml" || fail "$1"; }
assert_no_override() { [ ! -f "$case_dir/docker-compose.override.yml" ] || fail "$1"; }
assert_output() { grep -q -- "$1" "$work/output" || fail "missing output: $1"; }

# The .87 case: NetworkManager handles RAs, Docker 29, the network already runs.
reset_case existing-network
run_setup
assert_override 'did not give the containers IPv6'
assert_output 'CHANGED=true'

reset_case fresh-install
export TEST_NETWORK_EXISTS=no
run_setup
assert_override 'did not give a fresh installation IPv6'
assert_output 'CHANGED=false'

reset_case no-ipv6-host
export TEST_ROUTES=''
run_setup
assert_no_override 'gave containers IPv6 on a host without it'

reset_case switched-off-env
export GT_CONTAINER_IPV6=off
run_setup
assert_no_override 'ignored GT_CONTAINER_IPV6=off in the environment'

reset_case switched-off-dotenv
printf 'GT_CONTAINER_IPV6="off"\n' > "$case_dir/.env"
run_setup
assert_no_override 'ignored GT_CONTAINER_IPV6=off in .env'

reset_case kernel-ra
export TEST_ACCEPT_RA=1
run_setup
assert_no_override 'enabled forwarding where the kernel processes router advertisements'
assert_output 'accept_ra = 2'

reset_case static-route
export TEST_ROUTES='default via 2001:db8::1 dev wlan0 metric 1024 pref medium' TEST_ACCEPT_RA=1
run_setup
assert_override 'refused a static IPv6 route'

for version in 26.1.4 ''; do
  reset_case "old-docker-${version:-unknown}"
  export TEST_DOCKER_VERSION="$version"
  run_setup
  assert_no_override "accepted Docker '${version}'"
  assert_output 'older than 27'
done

reset_case remote-engine
export TEST_ENDPOINT=ssh://remote.invalid
run_setup
assert_no_override 'judged a remote engine by the routes of this host'

# An existing override belongs to the administrator: never edited, only reported.
reset_case foreign-override
printf 'services:\n  backend:\n    logging:\n      driver: local\n' > "$case_dir/docker-compose.override.yml"
cp "$case_dir/docker-compose.override.yml" "$work/foreign"
run_setup
cmp -s "$case_dir/docker-compose.override.yml" "$work/foreign" || fail 'edited an existing override'
assert_output 'WARN .*already exists'
assert_output 'CHANGED=false'

reset_case own-override
printf 'networks:\n  default:\n    enable_ipv6: true\n' > "$case_dir/docker-compose.override.yml"
run_setup
if grep -q WARN "$work/output"; then fail 'warned about an override that already enables IPv6'; fi
assert_output 'CHANGED=false'

# Forwarding off after the start leaves the containers with a dead IPv6 route.
reset_case forwarding-off
printf 'networks:\n  default:\n    enable_ipv6: true\n' > "$case_dir/docker-compose.override.yml"
export TEST_FORWARDING=0
(cd "$case_dir" && warn() { printf 'WARN %s\n' "$*"; } && . "$docker_dir/container-ipv6.sh" && verify_container_ipv6) \
  > "$work/output" 2>&1
assert_output 'IPv6 forwarding is off'

printf 'PASS: IPv6 detection, opt-out, RA guard, Docker version, remote engine and override ownership\n'
