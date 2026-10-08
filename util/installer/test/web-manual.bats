#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  ANSWER=([WEBSERVER]=none [DOMAIN]=example.org [TLS_SOURCE]=existing [DOCROOT]=/var/www/gt [BACKEND_PORT]=9090
    [TLS_CERT]=/etc/ssl/site.pem [TLS_KEY]=/etc/ssl/site.key)
  STATE=([run_id]=00000000000000000000000000000001 [step.app]=complete)
  FACT[web.lan]=192.0.2.20
  mkdir -p "$ROOT/root" "$ROOT/var/lib/gt-install" "$ROOT/etc/nginx/snippets"
  chmod 700 "$ROOT/var/lib/gt-install"
  # Stage prerequisites that other suites cover; this suite tests the manual web paths only.
  gt_stage_preflight() { :; }
  gt_core_config_valid() { :; }
  gt_app_artifacts() { :; }
  gt_app_verify() { :; }
  gt_web_firewall() { :; }
  gt_app_root_file() { cp "$2" "$3"; }
  ip() { echo '1.1.1.1 via 192.0.2.1 dev eth0 src 192.0.2.20 uid 0'; }
  LAN=fail DOMAIN_TLS=fail
  gt_web_verify() {
    printf '%s\n' "$*" >> "$SCRATCH/verified"
    case "$1" in http://*) [[ "$LAN" == ok ]] ;; https://*) [[ "$DOMAIN_TLS" == ok ]] ;; esac
  }
}

@test "the proposal for another web server reaches the backend over HTTP and keeps the request scheme" {
  gt_web_manual_render > "$SCRATCH/proposal"
  grep -q '^# Grafioschtrader web integration proposed by gt-install. Nothing in this file is active.' \
    "$SCRATCH/proposal"
  grep -q 'http://192.0.2.20/ (LAN, port 80) and https://example.org/' "$SCRATCH/proposal"
  grep -q '^location ^~ /api {' "$SCRATCH/proposal"
  grep -q 'proxy_pass http://127.0.0.1:9090/api;' "$SCRATCH/proposal"
  grep -q 'ProxyPass http://127.0.0.1:9090/api' "$SCRATCH/proposal"
  grep -q 'ProxyPass ws://127.0.0.1:9090/socket' "$SCRATCH/proposal"
  grep -q 'RequestHeader set X-Forwarded-Proto "expr=%{REQUEST_SCHEME}"' "$SCRATCH/proposal"
  run grep -E 'ajp://|included by gt-install' "$SCRATCH/proposal"
  [ "$status" -ne 0 ]
}

@test "WEBSERVER=none stays pending until the own web server answers, then completes on the next run" {
  local status=0
  gt_install_manual_web || status=$?
  [ "$status" -eq 10 ]
  [ "${STATE[step.web]}" = pending ]
  [ "${STATE[resource.web_manual]}" = /root/gt-install-webserver.conf ]
  [ -s "$ROOT/root/gt-install-webserver.conf" ]
  gt_result_milestones
  [ "${RESULT[web]}:${RESULT[status]}" = pending:incomplete ]
  gt_result_actions
  [[ "${RESULT[action.web]}" == *'Add the routes from /root/gt-install-webserver.conf to your web server'* ]]
  LAN=ok DOMAIN_TLS=ok status=0
  gt_install_manual_web || status=$?
  [ "$status" -eq 10 ]
  [ "${STATE[step.web]}:${STATE[step.tls]}" = complete:complete ]
  grep -qx 'https://example.org example.org:443:127.0.0.1' "$SCRATCH/verified"
}

@test "behind an upstream proxy an unverifiable public HTTPS check does not hold back the web milestone" {
  ANSWER[TLS_SOURCE]=proxy LAN=ok DOMAIN_TLS=fail
  gt_install_manual_web || true
  [ "${STATE[step.web]}:${STATE[step.tls]}" = complete:unverified ]
  # LAN-only installations need only the LAN routes.
  ANSWER[DOMAIN]='' STATE=([run_id]=00000000000000000000000000000001 [step.app]=complete)
  gt_install_manual_web || true
  [ "${STATE[step.web]}" = complete ]
  [ -z "${STATE[step.tls]:-}" ]
}

@test "an existing proposal edited by hand is not overwritten" {
  gt_app_root_file() { [[ ! -e "$3" ]] || cmp -s "$2" "$3" || return 2; cp "$2" "$3"; }
  printf '# edited\n' > "$ROOT/root/gt-install-webserver.conf"
  run gt_install_manual_web
  [ "$status" -eq 2 ]
  [[ "$output" == *'remove it to regenerate'* ]]
  [ "$(cat "$ROOT/root/gt-install-webserver.conf")" = '# edited' ]
}

@test "an unresolvable include target publishes the snippet and stays pending until the manual include verifies" {
  ANSWER[WEBSERVER]=nginx ANSWER[VHOST_INCLUDE]=yes
  local status=0
  gt_vhost_web || status=$?
  [ "$status" -eq 3 ]
  [ "${STATE[step.vhost_include]}" = manual ]
  [ "${STATE[resource.web_manual]}" = /etc/nginx/snippets/grafioschtrader.conf ]
  grep -q '^location ^~ /api {' "$ROOT/etc/nginx/snippets/grafioschtrader.conf"
  [ -z "${STATE[step.tls]:-}" ]
  DOMAIN_TLS=ok
  gt_vhost_web
  [ "${STATE[step.vhost_include]}:${STATE[step.tls]}" = complete:complete ]
  # A completed manual include is verified through the domain, never re-edited.
  gt_vhost_target() { touch "$SCRATCH/parsed"; return 2; }
  gt_vhost_web
  [ ! -e "$SCRATCH/parsed" ]
  DOMAIN_TLS=fail
  run gt_vhost_web
  [[ "$output" == *'no longer answers through https://example.org'* ]]
}

@test "the full-plan web review of WEBSERVER=none checks no web server, only the LAN address" {
  STATE[resource.web_lan]=192.0.2.20
  gt_probe() { echo '1.1.1.1 via 192.0.2.1 dev eth0 src 192.0.2.20 uid 0'; }
  gt_site_owned() { touch "$SCRATCH/sites-checked"; return 2; }
  gt_bootstrap_web_review
  [ "${FACT[bootstrap.web]}" = 'none:192.0.2.20:' ]
  [ ! -e "$SCRATCH/sites-checked" ]
  STATE[resource.web_lan]=192.0.2.99
  run gt_bootstrap_web_review
  [ "$status" -ne 0 ]
}
