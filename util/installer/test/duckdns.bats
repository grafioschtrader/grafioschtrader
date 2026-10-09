#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  TOKEN=0123abcd-4567-89ab-cdef-0123456789ab
  ANSWER=([DOMAIN]=demo.duckdns.org [DUCKDNS_UPDATER]=yes [DNS_FAMILY]=both [TLS_SOURCE]=letsencrypt)
  STATE=([run_id]=00000000000000000000000000000001)
  SECRET=([DUCKDNS_TOKEN]=$TOKEN)
  CORE_HOME=$ROOT/home/grafioschtrader
  mkdir -p "$CORE_HOME/duckdns" "$SCRATCH/bin" "$ROOT/var/lib/gt-install"
  chmod 700 "$CORE_HOME/duckdns" "$ROOT/var/lib/gt-install"
  printf '%s\n' "$TOKEN" > "$CORE_HOME/duckdns/token"
  # curl fixture: records its arguments and the configuration it was given, then answers like DuckDNS.
  cat > "$SCRATCH/bin/curl" <<'CURL'
#!/bin/bash
printf '%s\n' "$*" >> "$CURL_ARGS"
while (( $# )); do [[ "$1" != -K ]] || cat "$2" >> "$CURL_CONFIGS"; shift; done
[[ "$CURL_ANSWER" != fail ]] || exit 6
printf '%s' "$CURL_ANSWER"
CURL
  cat > "$SCRATCH/bin/ip" <<'IP'
#!/bin/bash
case "$*" in
  *'route show default'*) [[ -z "$NO_IPV6" ]] && echo 'default via fe80::1 dev eth0 proto ra' ;;
  *'addr show dev eth0'*)
    printf '    inet6 2001:db8::12/64 scope global dynamic mngtmpaddr\n    inet6 fd00::5/64 scope global\n' ;;
esac
IP
  chmod +x "$SCRATCH/bin/curl" "$SCRATCH/bin/ip"
  export CURL_ARGS="$SCRATCH/curl-args" CURL_CONFIGS="$SCRATCH/curl-configs" CURL_ANSWER=OK NO_IPV6=''
}

updater() {
  gt_duckdns_script demo "$1" > "$CORE_HOME/duckdns/duck.sh"
  chmod 700 "$CORE_HOME/duckdns/duck.sh"
  PATH="$SCRATCH/bin:$PATH" "$CORE_HOME/duckdns/duck.sh"
}

@test "the updater passes the token only through a private curl configuration and logs no secret" {
  updater both
  grep -qx "url = \"https://www.duckdns.org/update?domains=demo&token=$TOKEN&ip=&ipv6=2001:db8::12\"" "$CURL_CONFIGS"
  run grep -q "$TOKEN" "$CURL_ARGS" "$CORE_HOME/duckdns/duck.log"
  [ "$status" -ne 0 ]
  grep -q -- '^-4 ' "$CURL_ARGS"
  [ -z "$(find "$CORE_HOME/duckdns" -name '.curl.*')" ]
  [[ "$(tail -n 1 "$CORE_HOME/duckdns/duck.log")" == *' OK' ]]
}

@test "address families: ipv4 sends no IPv6 address, ipv6 sends it over IPv4 transport and needs a stable address" {
  updater ipv4
  grep -q 'ip=&ipv6="$' "$CURL_CONFIGS"
  : > "$CURL_ARGS"
  # www.duckdns.org has no AAAA record; the IPv6 address is a parameter, never the transport.
  updater ipv6
  grep -q -- '^-4 ' "$CURL_ARGS"
  # A whole argument only: the temporary path of the config file may contain "-6" (bats-run-6…).
  run grep -qE -- '(^| )-6( |$)' "$CURL_ARGS"
  [ "$status" -ne 0 ]
  NO_IPV6=yes
  run updater ipv6
  [ "$status" -eq 3 ]
  [[ "$(tail -n 1 "$CORE_HOME/duckdns/duck.log")" == *'KO no stable global IPv6 address' ]]
}

@test "KO, network failures and an invalid token file are distinct failures" {
  CURL_ANSWER=KO
  run updater ipv4
  [ "$status" -eq 1 ]
  [[ "$(tail -n 1 "$CORE_HOME/duckdns/duck.log")" == *'KO rejected by DuckDNS' ]]
  CURL_ANSWER=fail
  run updater ipv4
  [ "$status" -eq 2 ]
  [[ "$(tail -n 1 "$CORE_HOME/duckdns/duck.log")" == *'KO curl exit 6' ]]
  printf 'not-a-token\n' > "$CORE_HOME/duckdns/token"
  : > "$CURL_ARGS"
  run updater ipv4
  [ "$status" -eq 2 ]
  [ ! -s "$CURL_ARGS" ]
}

core_fixture() {
  gt_core_publish() { cp "$2" "$3"; chmod "$4" "$3"; printf '%s\n' "$1" >> "$SCRATCH/published"; }
  gt_app_root_file() { cp "$2" "$SCRATCH/$1"; printf '%s\n' "$1" >> "$SCRATCH/published"; }
  stat() { if [[ "$1 $2" == "-c %U:%a" ]]; then echo grafioschtrader:700; else command stat "$@"; fi; }
  gt_core_mark() { STATE[$1]=$2; }
  READY_AFTER=0
  gt_duckdns_dns_ready() { READY_AFTER=$((READY_AFTER - 1)); (( READY_AFTER < 0 )); }
  sleep() { SECONDS=$((SECONDS + ${1%s})); }
  gt_core_run() {
    printf '%s\n' "$*" >> "$SCRATCH/commands"
    if [[ "$*" == "systemctl start grafioschtrader-duckdns.service" ]]; then
      PATH="$SCRATCH/bin:$PATH" "$SCRATCH/run-updater"
    fi
  }
  # The unit would run the published script; here it runs the rendered one against the fixtures.
  printf '#!/bin/bash\nexec bash %q\n' "$CORE_HOME/duckdns/duck.sh" > "$SCRATCH/run-updater"
  chmod +x "$SCRATCH/run-updater"
}

@test "the core step updates once, waits for DNS, enables the timer, and a completed step only verifies" {
  core_fixture
  READY_AFTER=2
  gt_core_duckdns
  [ "$(grep -c 'systemctl start' "$SCRATCH/commands")" -eq 1 ]
  grep -qx 'systemctl enable --now grafioschtrader-duckdns.timer' "$SCRATCH/commands"
  [ "${STATE[step.duckdns]}" = complete ]
  grep -q 'OnCalendar=\*:0[0-4]/5:[0-5][0-9]' "$SCRATCH/duckdns_timer"
  grep -qx 'User=grafioschtrader' "$SCRATCH/duckdns_service"
  # Every directive belongs to a section; systemd ignores assignments before the first one.
  [ "$(head -n 1 "$SCRATCH/duckdns_service")" = '[Unit]' ]
  grep -qx 'Wants=network-online.target' "$SCRATCH/duckdns_service"
  [ "$(head -n 1 "$SCRATCH/duckdns_timer")" = '[Unit]' ]
  [ "$(cat "$CORE_HOME/duckdns/token")" = "$TOKEN" ]
  [ "$(stat -c '%a' "$CORE_HOME/duckdns/token")" = 600 ]
  gt_core_duckdns
  [ "$(grep -c 'systemctl start' "$SCRATCH/commands")" -eq 1 ]
}

@test "a rejected update or DNS that never converges stops before any certificate without the token" {
  core_fixture
  CURL_ANSWER=KO
  run gt_core_duckdns
  [ "$status" -ne 0 ]
  [[ "$output" == *'DuckDNS rejected the update; check the token and the subdomain'* ]]
  [[ "$output" != *"$TOKEN"* ]]
  run grep -c 'enable --now' "$SCRATCH/commands"
  [ "$output" = 0 ]
  CURL_ANSWER=OK READY_AFTER=1000
  run gt_core_duckdns
  [ "$status" -ne 0 ]
  [[ "$output" == *'does not resolve to this host'* ]]
  # A malformed stored token is refused before anything is written.
  SECRET[DUCKDNS_TOKEN]='bad token'
  run gt_core_duckdns
  [[ "$output" == *'token is missing or malformed'* ]]
}

@test "the plan rejects foreign updaters but recognizes the installer's own units" {
  local unit=$ROOT/etc/systemd/system/grafioschtrader-duckdns
  FACT[dns.updater_files]="$unit.timer $unit.service "
  gt_duckdns_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'must not be duplicated'* ]]
  STATE[file.duckdns_timer]=abc STATE[file.duckdns_service]=def PLAN_BLOCKERS=()
  gt_duckdns_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  FACT[containers]='linuxserver/duckdns:latest' PLAN_BLOCKERS=()
  gt_duckdns_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'duplicated: container'* ]]
  FACT[containers]='' ANSWER[DOMAIN]=Demo.DuckDNS.org PLAN_BLOCKERS=()
  gt_duckdns_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'lowercase <name>.duckdns.org'* ]]
}

@test "pending own updates turn a DNS mismatch into a warning with fixed certificate names" {
  FACT[network.public_ipv4]=198.51.100.12 FACT[network.global_ipv6]=2001:db8::12
  ONLINE=yes DNS_A=203.0.113.9 DNS_AAAA=''
  gt_plan_dns
  [ "${FACT[dns.status]}" = pending-update ]
  [ "${FACT[plan.names]}" = 'demo.duckdns.org www.demo.duckdns.org' ]
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN_WARNINGS[*]}" == *'updates DuckDNS before the build'* ]]
  STATE[step.duckdns]=complete PLAN_WARNINGS=()
  gt_plan_dns
  [ "${FACT[dns.status]}" = mismatch ]
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
}
