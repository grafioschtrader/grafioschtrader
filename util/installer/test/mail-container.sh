#!/bin/bash
set -Eeuo pipefail
[[ $EUID == 0 && -f /.dockerenv && ! -e /var/lib/gt-install ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset; gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap 'if [[ -n ${mail_pid:-} ]]; then kill "$mail_pid" 2>/dev/null || :; fi; gt_cleanup' EXIT
bash /repo/util/installer/test/test-certificates.sh
install -d -m 700 /var/lib/gt-install
touch /var/lib/gt-install/lock
ANSWER[SMTP_HOST]=localhost ANSWER[SMTP_PORT]=2525 ANSWER[SMTP_USER]=sender@example.test
ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_TEST]=yes
ANSWER[ADMIN_EMAIL]=admin@example.test ANSWER[SMTP_CONFIGURE]=yes
SECRET[SMTP_PASSWORD]='  fixture $`"\! = Grüsse 密码  '
printf %s "${SECRET[SMTP_PASSWORD]}" > "$SCRATCH/password"
STATE[run_id]=00000000000000000000000000000001 STATE[step.app]=complete
# Transport acceptance substitutes only config resolution; the Java/JAR acceptance
# verifies real Spring/Jasypt resolution separately.
gt_mail_resolve() {
  printf '%s\0' "${ANSWER[SMTP_HOST]}" "${ANSWER[SMTP_PORT]}" "${ANSWER[SMTP_USER]}" "${SECRET[SMTP_PASSWORD]}" \
    "${ANSWER[SMTP_AUTH]}" "${ANSWER[SMTP_SECURITY]}" "${ANSWER[ADMIN_EMAIL]}" > "$SCRATCH/mail-configuration"
}
python3 /repo/util/installer/test/mail-fixture.py "$SCRATCH" > "$SCRATCH/server.log" 2>&1 &
mail_pid=$!
for ((i=0;i<50;i++)); do [[ ! -e "$SCRATCH/ready" ]] || break; sleep .1; done
[[ -e "$SCRATCH/ready" ]]
gt_mail_probe
grep -qx accepted "$SCRATCH/mail-result"
grep -q 'To: admin@example.test' "$SCRATCH/messages"
grep -q 'From: sender@example.test' "$SCRATCH/messages"
grep -q '^Date: ' "$SCRATCH/messages"
[[ $(wc -l < "$SCRATCH/count") == 1 ]]

ANSWER[SMTP_PORT]=2465 ANSWER[SMTP_SECURITY]=tls
gt_mail_probe
[[ $(wc -l < "$SCRATCH/count") == 2 ]]
ANSWER[SMTP_TEST]=no
gt_mail_probe
grep -qx connected "$SCRATCH/mail-result"
[[ $(wc -l < "$SCRATCH/count") == 2 ]]

ANSWER[SMTP_TEST]=yes ANSWER[ADMIN_EMAIL]=reject@example.test
if gt_mail_probe > "$SCRATCH/reject.out" 2> "$SCRATCH/reject.err"; then exit 1; fi
ANSWER[ADMIN_EMAIL]=admin@example.test
SECRET[SMTP_PASSWORD]='wrong-fixture-password'
if gt_mail_probe > "$SCRATCH/auth.out" 2> "$SCRATCH/auth.err"; then exit 1; fi

# Require STARTTLS and certificate host validation; never silently fall back.
ANSWER[SMTP_PORT]=2526 ANSWER[SMTP_AUTH]=no ANSWER[SMTP_SECURITY]=starttls
if gt_mail_probe > "$SCRATCH/tls.out" 2> "$SCRATCH/tls.err"; then exit 1; fi
ANSWER[SMTP_HOST]=127.0.0.1 ANSWER[SMTP_PORT]=2465 ANSWER[SMTP_SECURITY]=tls
if gt_mail_probe > "$SCRATCH/name.out" 2> "$SCRATCH/name.err"; then exit 1; fi
ANSWER[SMTP_HOST]=localhost ANSWER[SMTP_PORT]=2526 ANSWER[SMTP_SECURITY]=none
gt_mail_probe
[[ $(wc -l < "$SCRATCH/count") == 3 ]]

# Only application/journal prerequisites are adapted; actual SMTP stays real.
gt_state_load() { :; }
gt_secrets_load() { :; }
gt_core_config_valid() { :; }
gt_app_artifacts() { :; }
gt_app_verify() { :; }
CORE_CONFIRM=yes
if gt_check_mail; then exit 1; else [[ $? == 10 ]]; fi
[[ ${STATE[step.mail]} == complete && ${STATE[resource.mail_delivery]} == accepted ]]
[[ $(wc -l < "$SCRATCH/count") == 4 ]]
if gt_check_mail; then exit 1; else [[ $? == 10 ]]; fi
[[ $(wc -l < "$SCRATCH/count") == 4 ]]
if grep -Fq -f "$SCRATCH/password" "$SCRATCH/"*.err "$SCRATCH/"*.out /var/lib/gt-install/state; then exit 1; fi
echo 'PASS: STARTTLS, implicit TLS, auth, literal password, distinct sender/admin, no-send, relay, rejection, trust and no duplicate mail.'
