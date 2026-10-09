#!/usr/bin/env bats
load check-helpers

# A wrong SMTP password is found by the mail check only; --check-mail --answers FILE corrects it until mail is verified.
setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/repo/backend/grafioschtrader-server/src/main/resources"
  chmod 700 "$ROOT/var/lib/gt-install"
  touch "$ROOT/var/lib/gt-install/lock"
  CORE_REPO="$ROOT/repo" CORE_HOME="$ROOT/home" CORE_CONFIRM=yes MODE=--check-mail ANSWERS_FILE=/root/answers
  PROPERTIES_FILE="$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties"
  printf '%s\n' 'gt.eod.cron.quotation=0 17 05 * * ?' 'spring.mail.username=sender@example.test' \
    'spring.mail.password=ENC(old)' 'spring.mail.properties.mail.smtp.auth=true' > "$PROPERTIES_FILE"
  STATE=([run_id]=00000000000000000000000000000001 [planned_commit]=0000000000000000000000000000000000000001
    [step.app]=complete [step.app_build]=complete [step.mail]=failed)
  ANSWER=([SMTP_CONFIGURE]=yes [SMTP_HOST]=mail.example.test [SMTP_PORT]=587 [SMTP_USER]=sender@example.test
    [SMTP_AUTH]=yes [SMTP_SECURITY]=starttls [SMTP_TEST]=yes [ADMIN_EMAIL]=admin@example.test)
  SECRET=([SMTP_PASSWORD]=old-secret [DB_PASSWORD]=database-secret)
  declare -gA ANSWERS=([SMTP_PASSWORD]=new-secret [SMTP_HOST]=mail.example.test [DB_PASSWORD]=database-secret
    [DB_ROOT_PASSWORD]=root-secret)
  EVENTS="$SCRATCH/events"
  gt_answers_file() { FILE_ANSWERS=(); local key; for key in "${!ANSWERS[@]}"; do FILE_ANSWERS[$key]=${ANSWERS[$key]}; done; }
  gt_core_mark() { STATE[$1]=$2; printf 'mark %s=%s\n' "$1" "$2" >> "$EVENTS"; }
  gt_secrets_save() { printf 'secrets %s\n' "${SECRET[SMTP_PASSWORD]}" >> "$EVENTS"; }
  gt_core_config_valid() { :; }
  gt_encrypt_secret() { ENCRYPTED="ENC(${SECRET[$1]})"; }
  gt_core_publish() { cp "$2" "$3"; printf 'publish %s\n' "$1" >> "$EVENTS"; }
  gt_core_run() { printf '%s\n' "$*" >> "$EVENTS"; }
  gt_as_app() {
    if [[ "$1" == git ]]; then echo "${STATE[planned_commit]}"; else printf 'build %s\n' "${*: -1}" >> "$EVENTS"; fi
  }
  gt_app_artifacts() { :; }
  gt_app_start() { echo start >> "$EVENTS"; }
}

@test "a corrected password is journaled before the secret, replaces only that property and rebuilds the backend" {
  gt_mail_password
  [ "${SECRET[SMTP_PASSWORD]}" = new-secret ]
  [ "$(grep -n 'mark step.mail_password=intent' "$EVENTS" | cut -d: -f1)" -lt "$(grep -n '^secrets new-secret' "$EVENTS" | cut -d: -f1)" ]
  grep -qx 'spring.mail.password=ENC(new-secret)' "$PROPERTIES_FILE"
  grep -qx 'gt.eod.cron.quotation=0 17 05 \* \* ?' "$PROPERTIES_FILE"
  [ "$(wc -l < "$PROPERTIES_FILE")" -eq 4 ]
  # The service stops before the build and starts only after the new JAR is recorded.
  [ "$(grep -vE '^(mark|secrets)' "$EVENTS" | tr '\n' '|')" = \
    "publish properties|systemctl stop grafioschtrader.service|build $CORE_HOME/gtupbackend.sh|start|" ]
  [ "${STATE[step.app_build]}:${STATE[step.mail_password]}" = complete:complete ]
}

@test "another changed answer, a verified mail milestone or an unchanged password changes nothing" {
  ANSWERS[SMTP_PORT]=465
  run gt_mail_password
  [ "$status" -eq 2 ]
  [[ "$output" == *'SMTP_PORT differs from the installation'* ]]
  [[ "$output" != *new-secret* ]]
  unset 'ANSWERS[SMTP_PORT]'
  STATE[step.mail]=complete
  run gt_mail_password
  [ "$status" -eq 2 ]
  STATE[step.mail]=failed ANSWERS[SMTP_PASSWORD]=old-secret
  gt_mail_password
  [ ! -e "$EVENTS" ]
  grep -qx 'spring.mail.password=ENC(old)' "$PROPERTIES_FILE"
}

@test "an interrupted change resumes from the journal before the mail check, without the answers file" {
  ANSWERS_FILE='' STATE[step.mail_password]=intent SECRET[SMTP_PASSWORD]=new-secret
  gt_completed() { return 3; }
  gt_state_load() { :; }
  gt_secrets_load() { :; }
  gt_stage_preflight() { :; }
  gt_app_verify() { :; }
  gt_mail_probe() { echo connected > "$SCRATCH/mail-result"; }
  gt_handover() { :; }
  gt_check_mail
  grep -qx 'spring.mail.password=ENC(new-secret)' "$PROPERTIES_FILE"
  [ "${STATE[step.mail_password]}:${STATE[step.mail]}" = complete:complete ]
}
