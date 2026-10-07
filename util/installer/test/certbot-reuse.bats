#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  ANSWER[DOMAIN]=example.org ANSWER[WEBSERVER]=nginx ANSWER[TLS_SOURCE]=letsencrypt
  ANSWER[LETSENCRYPT_CERT_NAME]=example.org
  STATE[run_id]=00000000000000000000000000000001
  FACT[plan.names]='example.org www.example.org'
  mkdir -p "$ROOT/etc/letsencrypt/renewal" "$ROOT/etc/letsencrypt/live/example.org" "$ROOT/var/lib/gt-install"
  printf 'authenticator = dns-provider\nwebroot_path = /existing/root\n' > "$ROOT/etc/letsencrypt/renewal/example.org.conf"
  chmod 600 "$ROOT/etc/letsencrypt/renewal/example.org.conf"
  gt_core_mark() { STATE[$1]=$2; }
}

certificate() {
  [ "$EUID" -eq 0 ] || skip 'Certbot renewal ownership requires root'
  OPENSSL_REAL=yes
  openssl req -x509 -newkey rsa:2048 -nodes -days 40 -subj /CN=example.org \
    -addext 'subjectAltName=DNS:example.org,DNS:www.example.org' \
    -keyout "$ROOT/etc/letsencrypt/live/example.org/privkey.pem" \
    -out "$ROOT/etc/letsencrypt/live/example.org/fullchain.pem" 2>/dev/null
  chmod 600 "$ROOT/etc/letsencrypt/live/example.org/privkey.pem"
  export SSL_CERT_FILE="$ROOT/etc/letsencrypt/live/example.org/fullchain.pem"
  CERTS=("$SSL_CERT_FILE: fixture")
}

@test "matching Certbot lineage is proposed and reuse never invokes certificate issuance" {
  certificate
  [ "$(gt_default LETSENCRYPT_CERT_NAME)" = example.org ]
  gt_certbot_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${FACT[tls.reuse]}" = yes ]
  [ "${FACT[tls.cert]}" = /etc/letsencrypt/live/example.org/fullchain.pem ]
  [[ "${PLAN[*]}" == *'Keep renewal configuration'* ]]
  gt_tls_certbot() { touch "$SCRATCH/unexpected-issuance"; return 99; }
  gt_tls_issue
  [ "${STATE[step.tls]}" = reused ]
  [ ! -e "$SCRATCH/unexpected-issuance" ]
  [ ! -e "$ROOT/var/lib/gt-install-acme" ]
}

@test "reuse rejects missing or unsafe renewal configuration and invalid certificate names" {
  certificate
  FACT[plan.names]=foreign.example
  gt_certbot_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'Certificate does not cover'* ]]
  PLAN_BLOCKERS=()
  chmod 666 "$ROOT/etc/letsencrypt/renewal/example.org.conf"
  gt_certbot_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'writable by group/others'* ]]
  PLAN_BLOCKERS=()
  rm "$ROOT/etc/letsencrypt/renewal/example.org.conf"
  gt_certbot_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'root-owned regular file'* ]]
  ! gt_validate_answer LETSENCRYPT_CERT_NAME ../foreign
}

@test "recorded reuse selection and renewal hash prevent silent changes on recovery" {
  certificate
  gt_certbot_plan
  STATE[resource.certbot_selection]=example.org
  STATE[resource.certbot_renewal_hash]=${FACT[tls.renewal_hash]}
  local before
  before=$(gt_tls_snapshot)
  echo changed >> "$ROOT/etc/letsencrypt/renewal/example.org.conf"
  gt_certbot_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'configuration changed'* ]]
  [ "$before" != "$(gt_tls_snapshot)" ]
  ANSWER[LETSENCRYPT_CERT_NAME]=-
  PLAN_BLOCKERS=()
  gt_certbot_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'selection changed'* ]]
}

@test "explicit new certificate choice and old issuer journals retain the installer lineage" {
  ANSWER[LETSENCRYPT_CERT_NAME]=-
  gt_certbot_plan
  [ "${FACT[tls.reuse]}" = no ]
  [ "${FACT[tls.lineage]}" = "gt-install-${STATE[run_id]}" ]
  unset 'ANSWER[LETSENCRYPT_CERT_NAME]'
  STATE[resource.acme_certificate]=intent
  gt_certbot_plan
  [ "${FACT[tls.reuse]}" = no ]
}

@test "reuse renewal test targets only the selected lineage and preserves configuration" {
  certificate
  gt_certbot_plan
  local before
  before=$(sha256sum "$ROOT/etc/letsencrypt/renewal/example.org.conf")
  gt_tls_certbot() { printf '%s\n' "$@" > "$SCRATCH/renew-args"; }
  gt_tls_renew
  grep -qx example.org "$SCRATCH/renew-args"
  grep -qx -- --dry-run "$SCRATCH/renew-args"
  ! grep -q 'certonly\|deploy-hook\|webroot' "$SCRATCH/renew-args"
  [ "$before" = "$(sha256sum "$ROOT/etc/letsencrypt/renewal/example.org.conf")" ]
  [ "${STATE[step.tls_renewal]}" = complete ]
  unset 'STATE[step.tls_renewal]'
  gt_tls_certbot() { return 1; }
  run gt_tls_renew
  [ "$status" -eq 2 ]
  [ -z "${STATE[step.tls_renewal]:-}" ]
}

@test "additional reload hook is scoped to the reused lineage and preserves other hooks" {
  certificate
  gt_certbot_plan
  local target="$ROOT/etc/letsencrypt/renewal-hooks/deploy"
  mkdir -p "$target" "$SCRATCH/bin"
  echo owner-hook > "$target/owner-hook"
  printf '#!/bin/sh\nprintf "%%s\\n" "$*" >> "%s/reloads"\n' "$SCRATCH" > "$SCRATCH/bin/systemctl"
  chmod +x "$SCRATCH/bin/systemctl"
  gt_tls_reuse_hook
  env PATH="$SCRATCH/bin:$PATH" RENEWED_LINEAGE=/etc/letsencrypt/live/other "$target/gt-install-${STATE[run_id]}"
  [ ! -e "$SCRATCH/reloads" ]
  env PATH="$SCRATCH/bin:$PATH" RENEWED_LINEAGE=/etc/letsencrypt/live/example.org "$target/gt-install-${STATE[run_id]}"
  grep -qx 'reload nginx.service' "$SCRATCH/reloads"
  [ "$(cat "$target/owner-hook")" = owner-hook ]
  gt_tls_reuse_hook
}
