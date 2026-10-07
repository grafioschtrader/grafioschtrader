#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  STATE=([run_id]=00000000000000000000000000000001)
  ANSWER=([FIREWALL_ALLOW]=yes [DOMAIN]='')
  UFW_FAIL=no
  : > "$SCRATCH/ufw-rules"
  # ufw fixture: "show added" lists the user rules as commands; "allow" adds one unless it exists.
  ufw() {
    printf '%s\n' "$*" >> "$SCRATCH/ufw-calls"
    case "$1" in
      show) printf "Added user rules (see 'ufw status' for running firewall):\n"; cat "$SCRATCH/ufw-rules" ;;
      allow)
        [[ "$UFW_FAIL" == no ]] || return 1
        grep -Fxq -- "ufw $*" "$SCRATCH/ufw-rules" || printf 'ufw %s\n' "$*" >> "$SCRATCH/ufw-rules" ;;
      *) return 99 ;;
    esac
  }
}

rules() { gt_firewall_rules | paste -sd ';' -; }

@test "rules open only the selected web routes, never SSH" {
  [ "$(rules)" = 'allow 80/tcp' ]
  ANSWER[DOMAIN]=example.org
  for ANSWER[TLS_SOURCE] in letsencrypt existing; do
    [ "$(rules)" = 'allow 80/tcp;allow 443/tcp' ]
  done
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_LISTEN]=8081 ANSWER[TLS_PROXY_FROM]=192.0.2.5
  [ "$(rules)" = 'allow 80/tcp;allow from 192.0.2.5 to any port 8081 proto tcp' ]
  ANSWER[TLS_PROXY_FROM]=''
  [ "$(rules)" = 'allow 80/tcp;allow 8081/tcp' ]
  ANSWER[FIREWALL_ALLOW]=no
  [ -z "$(rules)" ]
  run grep -r 'allow 22' "$REPO/util/installer/src"
  [ "$status" -ne 0 ]
}

@test "the question defaults to yes only with an active ufw, and the plan lists every rule" {
  FACT[firewall.ufw]='Status: inactive'
  run gt_question_applies FIREWALL_ALLOW
  [ "$status" -ne 0 ]
  FACT[firewall.ufw]=$'Status: active\nLogging: on (low)'
  gt_question_applies FIREWALL_ALLOW
  [ "$(gt_default FIREWALL_ALLOW)" = yes ]
  ANSWER[DOMAIN]=example.org ANSWER[TLS_SOURCE]=letsencrypt
  gt_firewall_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'modify | ufw | ufw allow 80/tcp; SSH and existing rules unchanged'* ]]
  [[ "${PLAN[*]}" == *'modify | ufw | ufw allow 443/tcp;'* ]]
  # A firewall disabled after the answers were saved cannot take the confirmed rules.
  FACT[firewall.ufw]='Status: inactive' PLAN_BLOCKERS=()
  gt_firewall_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'FIREWALL_ALLOW requires an active ufw'* ]]
  PLAN_BLOCKERS=()
  gt_stage_contract || true
  [[ "${PLAN_BLOCKERS[*]}" != *FIREWALL_ALLOW* ]]
}

@test "missing rules are added and owned; an existing rule is recorded but never claimed" {
  local key80 key443
  ANSWER[DOMAIN]=example.org ANSWER[TLS_SOURCE]=letsencrypt
  printf 'ufw allow 80/tcp\n' > "$SCRATCH/ufw-rules"
  gt_web_firewall
  key80=$(gt_firewall_key 'allow 80/tcp') key443=$(gt_firewall_key 'allow 443/tcp')
  [ "${STATE[$key80]}" = 'preexisting:allow 80/tcp' ]
  [ "${STATE[$key443]}" = 'owned:allow 443/tcp' ]
  [ "${STATE[step.firewall]}" = complete ]
  [ "$(grep -c '^allow' "$SCRATCH/ufw-calls")" -eq 1 ]
  [ "$(grep -c 'allow 443/tcp' "$SCRATCH/ufw-rules")" -eq 1 ]
  # A repeated stage only verifies.
  gt_web_firewall
  [ "$(grep -c '^allow' "$SCRATCH/ufw-calls")" -eq 1 ]
  gt_state_load 2>/dev/null || true
}

@test "an interrupted rule becomes owned on resumption; a removed or failing rule stops the stage" {
  local key
  key=$(gt_firewall_key 'allow 80/tcp')
  # ufw added the rule, but the run ended before the journal recorded ownership.
  STATE[$key]='intent:allow 80/tcp'
  printf 'ufw allow 80/tcp\n' > "$SCRATCH/ufw-rules"
  gt_web_firewall
  [ "${STATE[$key]}" = 'owned:allow 80/tcp' ]
  [ ! -e "$SCRATCH/ufw-calls" ] || [ "$(grep -c '^allow' "$SCRATCH/ufw-calls")" -eq 0 ]
  # The administrator removed the installer's rule: it is reported, not silently re-added.
  : > "$SCRATCH/ufw-rules"
  run gt_web_firewall
  [ "$status" -ne 0 ]
  [[ "$output" == *'ufw rule disappeared: allow 80/tcp'* ]]
  STATE=([run_id]=00000000000000000000000000000001) UFW_FAIL=yes
  run gt_web_firewall
  [ "$status" -ne 0 ]
  [[ "$output" == *'ufw allow 80/tcp failed'* ]]
}

@test "both web stages apply the rules before any site, so HTTP-01 can pass ufw" {
  local body
  body=$(declare -f gt_install_extended_web)
  [[ "$body" == *'gt_web_firewall'* ]]
  [[ "${body%%gt_web_firewall*}" != *gt_site_activate* && "${body%%gt_web_firewall*}" != *gt_tls_issue* ]]
  body=$(declare -f gt_install_web)
  [[ "${body%%gt_web_firewall*}" != *gt_web_activate* ]]
}
