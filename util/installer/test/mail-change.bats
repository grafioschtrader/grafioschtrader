#!/usr/bin/env bats
load check-helpers

# A skipped or wrong SMTP selection is found by the mail check only; --check-mail --answers FILE changes the SMTP
# answers and the SMTP password until mail is verified, and nothing else.
setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/repo/backend/grafioschtrader-server/src/main/resources"
  chmod 700 "$ROOT/var/lib/gt-install"
  touch "$ROOT/var/lib/gt-install/lock"
  CORE_REPO="$ROOT/repo" CORE_HOME="$ROOT/home" CORE_CONFIRM=yes MODE=--check-mail ANSWERS_FILE=/root/answers
  RESOURCES="$CORE_REPO/backend/grafioschtrader-server/src/main/resources"
  PROPERTIES_FILE="$RESOURCES/application.properties" PRODUCTION_FILE="$RESOURCES/application-production.properties"
  STATE=([run_id]=00000000000000000000000000000001 [planned_commit]=0000000000000000000000000000000000000001
    [step.app]=complete [step.app_build]=complete [step.mail]=failed)
  ANSWER=([SMTP_CONFIGURE]=yes [SMTP_HOST]=mail.example.test [SMTP_PORT]=587 [SMTP_USER]=sender@example.test
    [SMTP_AUTH]=yes [SMTP_SECURITY]=starttls [SMTP_TEST]=yes [ADMIN_EMAIL]=admin@example.test)
  SECRET=([SMTP_PASSWORD]=old-secret [DB_PASSWORD]=database-secret)
  declare -gA ANSWERS=([SMTP_PASSWORD]=new-secret [SMTP_HOST]=mail.example.test [DB_PASSWORD]=database-secret
    [DB_ROOT_PASSWORD]=root-secret)
  write_properties mail.example.test sender@example.test 'ENC(old)' true true
  EVENTS="$SCRATCH/events"
  gt_answers_file() { FILE_ANSWERS=(); local key; for key in "${!ANSWERS[@]}"; do FILE_ANSWERS[$key]=${ANSWERS[$key]}; done; }
  gt_core_mark() { STATE[$1]=$2; printf 'mark %s=%s\n' "$1" "$2" >> "$EVENTS"; }
  gt_secrets_save() { printf 'secrets %s\n' "${SECRET[SMTP_PASSWORD]-none}" >> "$EVENTS"; }
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

# The deployed mail keys: host, username, password, smtp.auth and starttls; the cron slot and listener stay.
write_properties() {
  printf '%s\n' 'gt.eod.cron.quotation=0 17 05 * * ?' "spring.mail.host=$1" 'spring.mail.port=587' \
    "spring.mail.username=$2" "spring.mail.password=$3" "spring.mail.properties.mail.smtp.auth=$4" \
    "spring.mail.properties.mail.smtp.starttls.enable=$5" 'spring.mail.properties.mail.smtp.ssl.enable=false' \
    > "$PROPERTIES_FILE"
  printf '%s\n' 'server.address=127.0.0.1' "spring.mail.properties.mail.smtp.starttls.required=$5" > "$PRODUCTION_FILE"
}

# An installation completed with SMTP_CONFIGURE=no: mail skipped, no SMTP answers and no SMTP password.
skipped_installation() {
  ANSWER=([SMTP_CONFIGURE]=no [ADMIN_EMAIL]=admin@example.test)
  STATE[answer.SMTP_CONFIGURE]=no STATE[answer.ADMIN_EMAIL]=admin@example.test STATE[step.mail]=skipped
  SECRET=([DB_PASSWORD]=database-secret)
  write_properties '' '' '' false false
}

@test "a corrected password is journaled before the secret, renders only mail keys and rebuilds the backend" {
  gt_mail_change
  gt_mail_change_apply
  [ "${SECRET[SMTP_PASSWORD]}" = new-secret ]
  [ "$(grep -n 'mark step.mail_change=intent' "$EVENTS" | cut -d: -f1)" -lt \
    "$(grep -n '^secrets new-secret' "$EVENTS" | cut -d: -f1)" ]
  grep -qx 'spring.mail.password=ENC(new-secret)' "$PROPERTIES_FILE"
  grep -qx 'gt.eod.cron.quotation=0 17 05 \* \* ?' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.host=mail.example.test' "$PROPERTIES_FILE"
  [ "$(wc -l < "$PROPERTIES_FILE")" -eq 8 ]
  grep -qx 'server.address=127.0.0.1' "$PRODUCTION_FILE"
  # The service stops before the build and starts only after the new JAR is recorded.
  [ "$(grep -vE '^(mark|secrets)' "$EVENTS" | tr '\n' '|')" = \
    "publish properties|publish production|systemctl stop grafioschtrader.service|build $CORE_HOME/gtupbackend.sh|start|" ]
  [ "${STATE[step.app_build]}:${STATE[step.mail_change]}" = complete:complete ]
  [ -z "${STATE[step.mail]+set}" ]
}

@test "an installation without mail gains it: defaults fill the gaps and the result becomes complete" {
  skipped_installation
  STATE[resource.warning.0]="WARN: $(gt_mail_skipped_warning core)"
  STATE[resource.warning.1]="WARN: $(gt_mail_skipped_warning bootstrap)"
  STATE[resource.warning.2]='WARN: Debian 13 legacy fixture warning'
  ANSWERS=([SMTP_CONFIGURE]=yes [SMTP_HOST]=smtp.example.test [SMTP_USER]=gt@example.test
    [SMTP_PASSWORD]='p a$s"w\rd' [ADMIN_EMAIL]=admin@example.test)
  gt_completed() { return 3; }
  gt_state_load() { :; }
  gt_secrets_load() { :; }
  gt_stage_preflight() { :; }
  gt_app_verify() { :; }
  gt_mail_probe() { echo accepted > "$SCRATCH/mail-result"; }
  gt_handover() { gt_result_remember_warnings; gt_result_milestones; }
  STATE+=([step.core]=complete [step.app_start]=complete [step.web]=complete
    [built_commit]=0000000000000000000000000000000000000001)
  gt_check_mail
  # Port, transport, authentication and the test message come from the installer's defaults.
  [ "${STATE[answer.SMTP_PORT]}:${STATE[answer.SMTP_SECURITY]}:${STATE[answer.SMTP_AUTH]}" = 587:starttls:yes ]
  [ "${STATE[answer.SMTP_CONFIGURE]}:${STATE[answer.SMTP_TEST]}" = yes:yes ]
  [ "${SECRET[SMTP_PASSWORD]}" = 'p a$s"w\rd' ]
  grep -qx 'spring.mail.host=smtp.example.test' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.username=gt@example.test' "$PROPERTIES_FILE"
  grep -qxF 'spring.mail.password=ENC(p\ a$s"w\\rd)' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.properties.mail.smtp.auth=true' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.properties.mail.smtp.starttls.enable=true' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.properties.mail.smtp.starttls.required=true' "$PRODUCTION_FILE"
  [ "${STATE[step.mail]}:${STATE[resource.mail_configuration]}" = complete:application-v1 ]
  [ "${RESULT[mail]}:${RESULT[status]}" = ok:complete ]
  # The skipped-mail warnings no longer describe the installation; every other warning stays.
  [ "$(printf '%s\n' "${!STATE[@]}" | grep -c '^resource.warning.')" -eq 1 ]
  [ "${STATE[resource.warning.2]}" = 'WARN: Debian 13 legacy fixture warning' ]
}

@test "a relay without authentication drops the password and a changed port keeps the explicit transport" {
  ANSWERS=([SMTP_AUTH]=no [SMTP_SECURITY]=tls [SMTP_PORT]=465 [SMTP_TEST]=no)
  gt_mail_change
  gt_mail_change_apply
  [ -z "${SECRET[SMTP_PASSWORD]+set}" ]
  grep -qx 'secrets none' "$EVENTS"
  [ "${STATE[answer.SMTP_AUTH]}:${STATE[answer.SMTP_PORT]}:${STATE[answer.SMTP_SECURITY]}" = no:465:tls ]
  grep -qx 'spring.mail.password=' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.port=465' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.properties.mail.smtp.ssl.enable=true' "$PROPERTIES_FILE"
  grep -qx 'spring.mail.properties.mail.smtp.starttls.required=false' "$PRODUCTION_FILE"
}

@test "another changed answer or secret is refused and leaves journal, answers and files untouched" {
  local key value before
  before=$(cat "$PROPERTIES_FILE" "$PRODUCTION_FILE")
  for key in ADMIN_EMAIL DB_PASSWORD; do
    value=${ANSWERS[$key]-}
    ANSWERS[$key]=changed@example.test
    run gt_mail_change
    [ "$status" -eq 2 ]
    [[ "$output" == *"$key differs from the installation"* ]]
    [[ "$output" != *new-secret* && "$output" != *database-secret* ]]
    if [[ -n "$value" ]]; then ANSWERS[$key]=$value; else unset "ANSWERS[$key]"; fi
  done
  [ ! -e "$EVENTS" ]
  [ "$before" = "$(cat "$PROPERTIES_FILE" "$PRODUCTION_FILE")" ]
  [ "${ANSWER[SMTP_PORT]}:${STATE[step.mail]}" = 587:failed ]
}

@test "an invalid SMTP selection is refused and restores the previous answers" {
  local selection
  for selection in 'SMTP_SECURITY=none' 'SMTP_PORT=0' 'SMTP_CONFIGURE=no SMTP_HOST=mail.example.test'; do
    ANSWERS=([SMTP_PASSWORD]=new-secret)
    for pair in $selection; do ANSWERS[${pair%%=*}]=${pair#*=}; done
    [[ "$selection" != SMTP_CONFIGURE=no* ]] || unset 'ANSWERS[SMTP_PASSWORD]'
    run gt_mail_change
    [ "$status" -eq 2 ]
    gt_mail_change || :
    [ "${ANSWER[SMTP_SECURITY]}:${ANSWER[SMTP_PORT]}:${ANSWER[SMTP_CONFIGURE]}" = starttls:587:yes ]
  done
  # Authentication switched on without a password in the file or the secrets.
  skipped_installation
  ANSWERS=([SMTP_CONFIGURE]=yes [SMTP_HOST]=smtp.example.test [SMTP_USER]=gt@example.test)
  run gt_mail_change
  [ "$status" -eq 2 ]
  [[ "$output" == *'needs a valid SMTP_PASSWORD'* ]]
  [ ! -e "$EVENTS" ]
}

@test "a verified mail milestone or an unchanged selection changes nothing" {
  STATE[step.mail]=complete
  run gt_mail_change
  [ "$status" -eq 2 ]
  STATE[step.mail]=failed STATE[resource.mail_configuration]=application-v1
  run gt_mail_change
  [ "$status" -eq 2 ]
  unset 'STATE[resource.mail_configuration]'
  ANSWERS[SMTP_PASSWORD]=old-secret
  gt_mail_change
  [ ! -e "$EVENTS" ]
  [ -z "${STATE[step.mail_change]+set}" ]
  grep -qx 'spring.mail.password=ENC(old)' "$PROPERTIES_FILE"
}

@test "an interrupted change resumes from the journal before the mail check, without the answers file" {
  ANSWERS_FILE='' STATE[step.mail_change]=intent SECRET[SMTP_PASSWORD]=new-secret
  gt_completed() { return 3; }
  gt_state_load() { :; }
  gt_secrets_load() { :; }
  gt_stage_preflight() { :; }
  gt_app_verify() { :; }
  gt_mail_probe() { echo connected > "$SCRATCH/mail-result"; }
  gt_handover() { :; }
  gt_check_mail
  grep -qx 'spring.mail.password=ENC(new-secret)' "$PROPERTIES_FILE"
  [ "${STATE[step.mail_change]}:${STATE[step.mail]}" = complete:complete ]
}

@test "a change interrupted before its secret was written asks for the answers file again" {
  skipped_installation
  ANSWER+=([SMTP_CONFIGURE]=yes [SMTP_HOST]=smtp.example.test [SMTP_PORT]=587 [SMTP_USER]=gt@example.test
    [SMTP_AUTH]=yes [SMTP_SECURITY]=starttls [SMTP_TEST]=yes)
  STATE[step.mail_change]=intent
  run gt_mail_change_apply
  [ "$status" -eq 2 ]
  [[ "$output" == *'repeat --check-mail --answers FILE'* ]]
  [ ! -e "$EVENTS" ]
  # The repeated request carries the password: the same answers, the secret now new, the change proceeds.
  ANSWERS=([SMTP_PASSWORD]=new-secret)
  gt_mail_change
  gt_mail_change_apply
  grep -qx 'spring.mail.password=ENC(new-secret)' "$PROPERTIES_FILE"
  [ "${STATE[step.mail_change]}" = complete ]
}
