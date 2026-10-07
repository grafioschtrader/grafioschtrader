#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  touch "$ROOT/var/lib/gt-install/lock"
  STATE=([schema]=1 [scope]=core [status]=running [run_id]=00000000000000000000000000000001
    [planned_commit]=0000000000000000000000000000000000000001
    [built_commit]=0000000000000000000000000000000000000001
    [java_home]=/opt/jdk-25 [maven]=/usr/bin/mvn
    [new_database_server]=no [database_before]=no [account_before]=no
    [step.core]=complete [step.app]=complete [step.app_build]=complete [step.app_start]=complete
    [step.web]=complete [step.mail]=complete [resource.web_lan]=192.0.2.20
    [resource.mail_delivery]=accepted [resource.mail_configuration]=application-v1)
  ANSWER=([DOMAIN]='' [SMTP_CONFIGURE]=yes [SMTP_TEST]=yes [ADMIN_EMAIL]=admin@example.test)
  save_answers
}

save_answers() {
  local key
  for key in "${!ANSWER[@]}"; do STATE[answer.$key]=${ANSWER[$key]}; done
}

@test "old running schema remains readable without new completion fields" {
  gt_state_save
  gt_state_load
  [ "${STATE[status]}" = running ]
  [ -z "${STATE[installer_sha256]:-}" ]
}

@test "bootstrap completion requires its approved plan and preserves read-only reruns" {
  STATE[scope]=bootstrap
  run gt_handover
  [ "$status" -eq 2 ]
  STATE[resource.bootstrap_plan]=$(printf approved | sha256sum)
  STATE[resource.bootstrap_plan]=${STATE[resource.bootstrap_plan]%% *}
  gt_handover
  gt_state_load
  [ "${STATE[scope]}" = bootstrap ]
  gt_secrets_load() { echo unexpected-secrets; return 99; }
  MODE=--bootstrap
  run gt_install_core
  [ "$status" -eq 0 ]
  [[ "$output" != *unexpected-secrets* ]]
}

@test "hand-over publishes a private complete report with pinned identity and warnings" {
  NOTES=('WARN: low memory') PLAN_WARNINGS=('frontend may be newer')
  SECRET[SMTP_PASSWORD]='secret-only-fixture'
  gt_handover
  [ "${STATE[status]}" = complete ]
  [ "${RESULT[tls]}" = skipped ]
  [ "${STATE[installer_sha256]}" = "$(sha256sum "$REPO/util/installer/gt-install.sh" | cut -d ' ' -f 1)" ]
  [ "$(stat -c %a "$ROOT/var/lib/gt-install/result")" = 600 ]
  grep -qx 'status=complete' "$ROOT/var/lib/gt-install/result"
  grep -q 'WARN: low memory' "$ROOT/var/lib/gt-install/result"
  ! grep -q secret-only-fixture "$ROOT/var/lib/gt-install/"{state,result}
  gt_state_load
  [ "${STATE[status]}" = complete ]
  gt_installation
  [ "${FACT[host.class]}" = completed ]
}

@test "mail without sending is complete only when the confirmed test was not requested" {
  ANSWER[SMTP_TEST]=no STATE[resource.mail_delivery]=not-requested
  save_answers
  gt_handover
  [ "${RESULT[mail]}" = ok ]
  ANSWER[SMTP_TEST]=yes
  gt_result_milestones
  [ "${RESULT[mail]}" = pending ]
  [ "${RESULT[status]}" = incomplete ]
}

@test "missing web skipped mail failed mail and incomplete TLS remain resumable" {
  local scenario
  for scenario in web mail-skip mail-failed tls-failed tls-issued tls-reused tls-pending; do
    setup
    case "$scenario" in
      web) unset 'STATE[step.web]' ;;
      mail-skip) ANSWER[SMTP_CONFIGURE]=no ;;
      mail-failed) STATE[step.mail]=failed ;;
      tls-*) ANSWER[DOMAIN]=gt.example ANSWER[TLS_SOURCE]=existing STATE[step.tls]=${scenario#tls-} ;;
    esac
    save_answers
    run gt_handover
    [ "$status" -eq 10 ]
    grep -qx 'status=running' "$ROOT/var/lib/gt-install/state"
    grep -qx 'status=incomplete' "$ROOT/var/lib/gt-install/result"
    ! grep -q '^completed_at=' "$ROOT/var/lib/gt-install/state"
    gt_state_load
  done
}

@test "proxy unverified permits completion but other TLS sources need final verification" {
  ANSWER[DOMAIN]=gt.example ANSWER[TLS_SOURCE]=proxy STATE[step.tls]=unverified
  save_answers
  gt_handover
  [ "${RESULT[tls]}" = unverified ]
  [[ "${RESULT[action.tls]}" == *https://gt.example/grafioschtrader/* ]]
  ANSWER[TLS_SOURCE]=existing
  run gt_completion_valid
  [ "$status" -ne 0 ]
}

@test "completed parser rejects missing build identity or contradictory milestones" {
  gt_handover
  local key saved
  for key in built_commit completed_at installer_sha256 step.app_build step.app_start step.web step.mail; do
    saved=${STATE[$key]}
    unset 'STATE['"$key"']'
    gt_state_save
    run gt_state_load
    [ "$status" -ne 0 ]
    STATE[$key]=$saved
  done
  STATE[built_commit]=0000000000000000000000000000000000000002
  gt_state_save
  run gt_state_load
  [ "$status" -ne 0 ]
}

@test "all completed entrypoints are read-only even without secrets and after an update" {
  gt_handover
  local before metadata mode
  before=$(sha256sum "$ROOT/var/lib/gt-install/"{state,result})
  metadata=$(stat -c '%i:%s:%a:%Y:%Z' "$ROOT/var/lib/gt-install/"{state,result})
  gt_secrets_load() { echo 'unexpected secrets'; return 99; }
  gt_inventory() { echo 'unexpected inventory'; return 99; }
  gt_app_artifacts() { echo 'updated artifact hashes'; return 99; }
  gt_mail_probe() { echo 'unexpected mail'; return 99; }
  for mode in gt_check gt_dry_run gt_prepare gt_install_core gt_install_app gt_install_web gt_check_mail; do
    run "$mode"
    [ "$status" -eq 0 ]
    [[ "$output" == *host.class=completed* && "$output" == *./gtupdate.sh* ]]
    [[ "$output" != *unexpected* && "$output" != *'updated artifact hashes'* ]]
    [ "$(sha256sum "$ROOT/var/lib/gt-install/"{state,result})" = "$before" ]
    [ "$(stat -c '%i:%s:%a:%Y:%Z' "$ROOT/var/lib/gt-install/"{state,result})" = "$metadata" ]
  done
}

@test "invalid completion blocks every entrypoint before secrets or installation" {
  gt_handover
  unset 'STATE[built_commit]'
  gt_state_save
  local mode
  for mode in gt_check gt_dry_run gt_prepare gt_install_core gt_install_app gt_install_web gt_check_mail; do
    run "$mode"
    [ "$status" -eq 2 ]
    [[ "$output" == *'Invalid completed installation journal.'* ]]
  done
}

@test "German hand-over uses translated guidance and stable result keys" {
  LANG_CODE=de
  gt_handover
  [ "${RESULT[status]}" = complete ]
  [[ "${RESULT[registration]}" == *'registrieren'* ]]
  [[ "${RESULT[mail_delivery_note]}" == *'angenommen'* ]]
  [[ "${RESULT[production_configuration]}" == *'unverändert'* ]]
}

@test "interrupted result publication resumes without resending accepted mail" {
  gt_state_save
  gt_state_save() { return 1; }
  run gt_handover
  [ "$status" -eq 1 ]
  grep -qx 'status=complete' "$ROOT/var/lib/gt-install/result"
  grep -qx 'status=running' "$ROOT/var/lib/gt-install/state"
  unset -f gt_state_save
  source "$REPO/util/installer/src/50-state.sh"
  gt_state_load
  gt_secrets_load() { :; }
  gt_stage_preflight() { :; }
  gt_core_config_valid() { :; }
  gt_app_artifacts() { :; }
  gt_app_verify() { :; }
  gt_mail_probe() { echo 'unexpected mail'; return 99; }
  # The mail validator still requires all configured non-secret SMTP inputs.
  ANSWER[SMTP_HOST]=mail.example.test ANSWER[SMTP_PORT]=587 ANSWER[SMTP_USER]=sender@example.test
  ANSWER[SMTP_AUTH]=no ANSWER[SMTP_SECURITY]=starttls
  save_answers
  gt_state_save
  gt_check_mail
  [ "${STATE[status]}" = complete ]
}

@test "result refuses symlinks and permissive existing files without completing" {
  ln -s "$SCRATCH/foreign" "$ROOT/var/lib/gt-install/result"
  run gt_handover
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/foreign" ]
  rm "$ROOT/var/lib/gt-install/result"
  echo foreign > "$ROOT/var/lib/gt-install/result"
  chmod 644 "$ROOT/var/lib/gt-install/result"
  run gt_handover
  [ "$status" -eq 2 ]
  [ "$(cat "$ROOT/var/lib/gt-install/result")" = foreign ]
}

@test "delivery uncertainty and technical failure cannot report completion" {
  STATE[step.mail]=intent
  unset 'STATE[resource.mail_delivery]'
  gt_result_milestones
  [ "${RESULT[mail_delivery]}" = uncertain ]
  [ "${RESULT[status]}" = incomplete ]
  run gt_handover blocked
  [ "$status" -eq 2 ]
  grep -qx 'status=blocked' "$ROOT/var/lib/gt-install/result"
  run gt_handover failed
  [ "$status" -eq 1 ]
  grep -qx 'status=failed' "$ROOT/var/lib/gt-install/result"
}
