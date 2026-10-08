#!/usr/bin/env bats
load check-helpers

# The fixture host has the default route on 192.168.1.2 and a second, intranet-only address 10.0.0.2.
setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  gt_network
  PLAN_BLOCKERS=()
}

@test "the LAN address defaults to the default route's source, and the prompt offers every host address" {
  [ "${FACT[network.ipv4_addresses]}" = '192.168.1.2 10.0.0.2' ]
  [ "$(gt_default LAN_ADDRESS)" = 192.168.1.2 ]
  QUESTION_OUTPUT=2
  exec {QUESTION_FD}<<<'10.0.0.2'
  run gt_ask LAN_ADDRESS
  [ "$status" -eq 0 ]
  [[ "$output" == *'(LAN_ADDRESS) 192.168.1.2 10.0.0.2 [192.168.1.2]:'* ]]
}

@test "the chosen intranet address is served, matched against vhosts and shown in the domain checklist" {
  ANSWER[LAN_ADDRESS]=10.0.0.2 ANSWER[DOMAIN]=example.org
  gt_stage_contract
  : > "$PROBES"
  [ "$(gt_lan_address)" = 10.0.0.2 ]
  run grep -q 'route get' "$PROBES"
  [ "$status" -ne 0 ]
  [[ "$(gt_dns_checklist)" == *'LAN=10.0.0.2'* ]]
  WEB=('/etc/nginx/sites-enabled/intranet server_name 10.0.0.2' '/etc/nginx/sites-enabled/intranet root /var/www/intra')
  [ "$(gt_vhost_root_default)" = /var/www/intra ]
  # A foreign site on that address would take the LAN site's place.
  run gt_stage_contract
  [ "$status" -ne 0 ]
  gt_stage_contract || true
  [[ "${PLAN_BLOCKERS[*]}" == *'The LAN address 10.0.0.2 belongs to a foreign vhost'* ]]
}

@test "an address that belongs to no interface of this host blocks the plan and every web stage" {
  run gt_validate_answer LAN_ADDRESS 127.0.0.1
  [ "$status" -ne 0 ]
  run gt_validate_answer LAN_ADDRESS 2001:db8::1
  [ "$status" -ne 0 ]
  ANSWER[LAN_ADDRESS]=203.0.113.9
  gt_validate_answer LAN_ADDRESS 203.0.113.9
  run gt_stage_contract
  [ "$status" -ne 0 ]
  gt_stage_contract || true
  [[ "${PLAN_BLOCKERS[*]}" == *'LAN_ADDRESS 203.0.113.9 is not assigned to this host; choose one of: 192.168.1.2 10.0.0.2.'* ]]
  run gt_lan_address
  [ "$status" -eq 2 ]
  [[ "$output" == *'LAN address 203.0.113.9 is not assigned to an interface of this host.'* ]]
}

@test "a journal written before LAN_ADDRESS existed keeps the default route's source address" {
  gt_validate_answer LAN_ADDRESS ''
  : > "$PROBES"
  [ "$(gt_lan_address)" = 192.168.1.2 ]
  grep -q '^ip -4 route get 1.1.1.1$' "$PROBES"
  # The source address must still be on the host; an interface that lost it stops the web stage.
  LAN_ADDRESSES=10.0.0.2
  run gt_lan_address
  [ "$status" -eq 2 ]
}
