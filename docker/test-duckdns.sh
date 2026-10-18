#!/usr/bin/env bash
# Offline regression checks: no Docker daemon or real DNS updates are used.
set -euo pipefail
docker_dir="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"
export PATH="$work/bin:$PATH" REQUEST_LOG="$work/request" DETECTION_LOG="$work/detection"
export DOCKER_LOG="$work/docker"
export DOCKER_CONTEXT=gt-installer-test
export SUBDOMAINS=example TOKEN=test-token
export TEST_IPV4=198.51.100.23 TEST_IPV6=2001:db8::23 TEST_DIG_STATUS=0 TEST_RESPONSE=OK

cat > "$work/bin/dig" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DETECTION_LOG"
[ "$TEST_DIG_STATUS" = 0 ] || exit "$TEST_DIG_STATUS"
case " $* " in
  *" -6 "*) printf '"%s"\n' "$TEST_IPV6" ;;
  *" -4 "*) printf '"%s"\n' "$TEST_IPV4" ;;
  *) exit 1 ;;
esac
EOF
cat > "$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$@" >> "$REQUEST_LOG"
printf '%s\n' "$TEST_RESPONSE"
EOF
cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$DOCKER_LOG"
case "$*" in
  'context inspect '*) printf 'ssh://installer-test.invalid\n' ;;
esac
exit 0
EOF
cat > "$work/bin/ss" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$work/bin/"*

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
assert_line() { grep -qxF -- "$2" "$1" || fail "missing '$2' in $1"; }
run_update() {
  : > "$REQUEST_LOG"
  : > "$DETECTION_LOG"
  UPDATE_IP="$1" bash "$docker_dir/duckdns-update.sh" > "$work/output" 2>&1
}
no_update() {
  if run_update "$1"; then fail "unexpected success for mode '$1'"; fi
  [ ! -s "$REQUEST_LOG" ] || fail "sent a request after failed detection/configuration"
}

run_update ipv6
assert_line "$REQUEST_LOG" "ipv6=2001:db8::23"
if grep -q '^ip=' "$REQUEST_LOG"; then fail "IPv6 mode sent an IPv4 parameter"; fi
if grep -q -- ' -4 ' "$DETECTION_LOG"; then fail "IPv6 mode detected IPv4"; fi
run_update ipv4
assert_line "$REQUEST_LOG" "ip=198.51.100.23"
if grep -q '^ipv6=' "$REQUEST_LOG"; then fail "IPv4 mode sent an IPv6 parameter"; fi
run_update both
assert_line "$REQUEST_LOG" "ip=198.51.100.23"
assert_line "$REQUEST_LOG" "ipv6=2001:db8::23"

# The regression: empty IPv6 must never reach DuckDNS and trigger IPv4 autodetection.
TEST_IPV6= no_update ipv6
TEST_IPV6= no_update both
TEST_IPV6=fe80::23 no_update ipv6
TEST_IPV6=not-an-address no_update ipv6
TEST_DIG_STATUS=9 no_update ipv6
TEST_IPV4=999.1.2.3 no_update ipv4
no_update ""
no_update wrong
if TEST_RESPONSE=KO run_update ipv6; then fail "accepted a rejected DNS update"; fi
if grep -q "$TOKEN" "$work/output"; then fail "printed the token"; fi
printf 'PASS: address-family requests and failure handling\n'

# Exercise the actual interactive installer in disposable directories.
new_install() {
  install_dir="$work/$1"
  mkdir -p "$install_dir"
  cp "$docker_dir/install.sh" "$install_dir/install.sh"
}
for pair in 1:ipv4 2:ipv6 3:both; do
  new_install "fresh-${pair#*:}"
  printf 'example.duckdns.org\ny\n%s\n\ntest-token\nadmin@example.com\nn\n\n' "${pair%:*}" \
    | bash "$install_dir/install.sh" > "$work/installer-output"
  assert_line "$install_dir/.env" "DUCKDNS_UPDATE_IP=${pair#*:}"
  assert_line "$install_dir/.env" "COMPOSE_PROFILES=duckdns"
done

new_install retry-choice
printf 'example.duckdns.org\ny\n\ninvalid\n2\n\ntest-token\nadmin@example.com\nn\n\n' \
  | bash "$install_dir/install.sh" > "$work/installer-output"
assert_line "$install_dir/.env" "DUCKDNS_UPDATE_IP=ipv6"
new_install external-updater
printf 'example.duckdns.org\nn\nadmin@example.com\nn\n\n' \
  | bash "$install_dir/install.sh" > "$work/installer-output"
if grep -q '^COMPOSE_PROFILES=duckdns' "$install_dir/.env"; then fail "enabled second updater"; fi
new_install local-http
printf '\nadmin@example.com\nn\n\n' | bash "$install_dir/install.sh" > "$work/installer-output"
assert_line "$install_dir/.env" "GT_SITE_ADDRESS=:80"

new_install existing
cat > "$install_dir/.env" <<'EOF'
COMPOSE_PROFILES=duckdns
DB_PASSWORD=existing-secret
MAIL_HOST=existing-mail-host
EOF
printf 'y\n2\n' | bash "$install_dir/install.sh" > "$work/installer-output"
assert_line "$install_dir/.env" "DUCKDNS_UPDATE_IP=ipv6"
assert_line "$install_dir/.env" "DB_PASSWORD=existing-secret"
assert_line "$install_dir/.env" "MAIL_HOST=existing-mail-host"
cp "$install_dir/.env" "$work/expected-env"
printf 'y\n' | bash "$install_dir/install.sh" > "$work/installer-output"
cmp "$work/expected-env" "$install_dir/.env" || fail "changed existing explicit mode"

# A quoted mode is valid Compose dotenv syntax and must not prompt again.
sed -i 's/DUCKDNS_UPDATE_IP=ipv6/DUCKDNS_UPDATE_IP="ipv6"/' "$install_dir/.env"
printf 'y\n' | bash "$install_dir/install.sh" > "$work/installer-output"
assert_line "$install_dir/.env" 'DUCKDNS_UPDATE_IP="ipv6"'

# Merely omitting the profile would leave the old IPv4 updater running.
: > "$DOCKER_LOG"
printf 'n\nexample.duckdns.org\nn\nadmin@example.com\nn\n\n' \
  | bash "$install_dir/install.sh" > "$work/installer-output"
assert_line "$DOCKER_LOG" 'compose --profile duckdns stop duckdns'
assert_line "$install_dir/.env" 'DB_PASSWORD=existing-secret'
if grep -q '^COMPOSE_PROFILES=duckdns' "$install_dir/.env"; then fail "kept disabled updater"; fi
printf 'PASS: fresh, external, local and existing installer configurations\n'
