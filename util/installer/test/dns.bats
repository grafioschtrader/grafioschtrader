#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  MODE=--install-core CORE_CONFIRM=yes DNS_TOOL_AVAILABLE=no
  ANSWER[DOMAIN]=example.org ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_SOURCE]=letsencrypt
  INSTALLS=0
  gt_system() { FACT[dpkg.lock]=free FACT[apt.age_hours]=0; }
  gt_packages() { :; }
  gt_domain_network() { FACT[network.public_ipv4]=198.51.100.12 FACT[network.global_ipv6]=''; }
  gt_core_run() {
    [[ "$*" == *'apt-get -y --no-remove --no-upgrade'* && "${*: -1}" == bind9-dnsutils ]] || return 99
    INSTALLS=$((INSTALLS+1))
    DNS_TOOL_AVAILABLE=yes
  }
}

@test "confirmed DNS tool installation repeats DNS verification and is reusable" {
  gt_packages() { [[ "$INSTALLS" -eq 0 ]] || PACKAGE[bind9-dnsutils]=installed; }
  gt_prepare_dns
  [ "$INSTALLS" -eq 1 ]
  grep -q '^dig ' "$PROBES"
  [ "${FACT[plan.names]}" = 'example.org www.example.org' ]
  [ "${PACKAGE[bind9-dnsutils]}" = installed ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
  gt_prepare_dns
  [ "$INSTALLS" -eq 1 ]
}

@test "changed prerequisite transaction between confirmation and execution blocks APT" {
  PLANS=0
  gt_packages() {
    PLANS=$((PLANS+1))
    APT_OUTPUT="Inst bind9-dnsutils (1.$PLANS Debian)"
  }
  if gt_prepare_dns; then false; else [ "$?" -eq 2 ]; fi
  [ "$INSTALLS" -eq 0 ]
}

@test "stale metadata held locks and broken installed DNS package are explicit blockers" {
  gt_system() { FACT[dpkg.lock]=unknown FACT[apt.age_hours]=25; }
  gt_packages() { PACKAGE[bind9-dnsutils]=installed; }
  if gt_prepare_dns; then false; else [ "$?" -eq 2 ]; fi
  [[ "${PLAN_BLOCKERS[*]}" == *'package lock'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'absent/stale'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'dig is missing from PATH'* ]]
  [ "$INSTALLS" -eq 0 ]
}

@test "web installation repeats the DNS prerequisite gate before any site preflight or write" {
  MODE=--install-web STATE[step.app]=complete ANSWER[WEBSERVER]=nginx
  gt_core_config_valid() { :; }
  gt_app_artifacts() { :; }
  gt_app_verify() { :; }
  gt_extended_preflight() { touch "$ROOT/web-preflight"; return 99; }
  DNS_A=198.51.100.99
  if gt_install_extended_web; then false; else [ "$?" -eq 2 ]; fi
  [ "$INSTALLS" -eq 1 ]
  [ ! -e "$ROOT/web-preflight" ]
  DNS_A=198.51.100.12
  run gt_install_extended_web
  [ "$status" -eq 2 ]
  [ -e "$ROOT/web-preflight" ]
}

@test "a mismatch after tool installation blocks further installation" {
  DNS_A=198.51.100.99
  if gt_prepare_dns; then false; else [ "$?" -eq 2 ]; fi
  [ "$INSTALLS" -eq 1 ]
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
}

@test "read-only modes cannot install DNS tools and package removals or upgrades block" {
  for MODE in --check --dry-run --prepare; do
    run gt_prepare_dns
    [ "$status" -eq 2 ]
  done
  MODE=--install-core
  for APT_OUTPUT in 'Remv shared-service [1.0]' 'Inst shared-library [1.0] (2.0 Debian)'; do
    if gt_prepare_dns; then false; else [ "$?" -eq 2 ]; fi
  done
  [ "$INSTALLS" -eq 0 ]
}

@test "declined prerequisite installation writes no packages" {
  CORE_CONFIRM=no
  printf 'no\n' > "$SCRATCH/answer"
  exec {QUESTION_FD}<>"$SCRATCH/answer"
  run gt_prepare_dns
  [ "$status" -eq 130 ]
  [ "$INSTALLS" -eq 0 ]
}

@test "failed installation or missing dig after APT cannot reach DNS verification" {
  gt_core_run() { return 1; }
  run gt_prepare_dns
  [ "$status" -eq 2 ]
  ! grep -q '^dig ' "$PROBES"
  gt_core_run() { :; }
  run gt_prepare_dns
  [ "$status" -eq 2 ]
  [[ "$output" == *'dig is still unavailable'* ]]
}

@test "LAN and upstream proxy configurations need no local DNS tools" {
  ANSWER[TLS_SOURCE]=proxy
  gt_prepare_dns
  ANSWER[DOMAIN]='' ANSWER[TLS_SOURCE]=existing
  gt_prepare_dns
  [ "$INSTALLS" -eq 0 ]
  [ ! -s "$PROBES" ]
}
