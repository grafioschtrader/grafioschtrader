gt_mail_resolve() {
  local directory jar status=0
  local -a jars=()
  while IFS= read -r -d '' jar; do jars+=("$jar"); done < <(
    find "$CORE_HOME" -maxdepth 1 -name 'grafioschtrader-server-*.jar' -print0)
  (( ${#jars[@]} == 1 )) || return 2
  directory=$(mktemp -d /tmp/gt-mail-config.XXXXXX) || return 2
  PRIVATE_DIRS+=("$directory")
  chown grafioschtrader:grafioschtrader "$directory" || return 2
  # Match the service working directory and resolve config from the deployed JAR.
  # No key in argv/environment, no Spring application context and no public endpoint.
  # shellcheck disable=SC2016
  printf '%s' "${SECRET[JASYPT_PASSWORD]}" | runuser -u grafioschtrader -- env -i \
    HOME="$CORE_HOME" PATH=/usr/bin:/bin bash -c 'cd "$1" && shift && exec "$@"' gt-mail \
    "$CORE_HOME" "${STATE[java_home]}/bin/java" -Duser.language=en \
    -Dloader.main=grafiosch.installer.InstallerMailConfiguration -cp "${jars[0]}" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$directory/config" \
    > "$SCRATCH/mail-configuration.log" 2>&1 || status=2
  if (( status == 0 )); then
    [[ -f "$directory/config" && ! -L "$directory/config" ]] &&
      cp "$directory/config" "$SCRATCH/mail-configuration" || status=2
  fi
  rm -rf -- "$directory"
  (( status == 0 )) || gt_core_error \
    'Application mail configuration could not be resolved/decrypted. Use the matching backend build; details suppressed.'
  return "$status"
}

gt_mail_probe() {
  local send=${1:-${ANSWER[SMTP_TEST]}} index key
  local -a configuration=() fields=(SMTP_HOST SMTP_PORT SMTP_USER '' SMTP_AUTH SMTP_SECURITY ADMIN_EMAIL)
  rm -f -- "$SCRATCH/mail-result"
  gt_mail_resolve || return 2
  mapfile -d '' -t configuration < "$SCRATCH/mail-configuration"
  rm -f -- "$SCRATCH/mail-configuration"
  (( ${#configuration[@]} == 7 )) || return 2
  # Refuse an unexpected server/recipient/transport before contacting it. Password
  # intentionally comes only from the deployed application's decrypted property.
  for index in 0 1 2 4 5 6; do
    key=${fields[$index]}
    [[ "${configuration[$index]}" == "${ANSWER[$key]}" ]] || {
      gt_core_error "Application mail setting differs from the confirmed answer: $key"; return 2;
    }
  done
  # Credentials travel only on stdin. Neither exception text nor SMTP debug output
  # is exposed: server replies may repeat authentication material.
  cat > "$SCRATCH/mail-check.py" <<'PY'
# @python mail-probe.py
PY
  printf '%s\0' "${configuration[@]}" "$send" \
    "<gt-install-${STATE[run_id]}@localhost>" | python3 "$SCRATCH/mail-check.py" > "$SCRATCH/mail-result" || return 1
}

gt_check_mail() {
  local state_dir field reply result send
  local completed_status
  completed_status=0
  gt_completed || completed_status=$?
  (( completed_status == 3 )) || return "$completed_status"
  state_dir=$(gt_path /var/lib/gt-install)
  gt_question_model
  gt_state_load && gt_secrets_load || return 2
  gt_stage_preflight || return 2
  gt_no_symlinks "$state_dir/lock" || return 2
  if [[ -z "$LOCK_FD" ]]; then exec {LOCK_FD}<"$state_dir/lock" || return 2; fi
  flock -n "$LOCK_FD" || return 2
  [[ "${STATE[step.app]:-}" == complete ]] && gt_core_config_valid && gt_app_artifacts && gt_app_verify || return 2
  if [[ "${ANSWER[SMTP_CONFIGURE]}" == no ]]; then
    gt_core_mark step.mail skipped || return 2
    gt_text 'Mail skipped; registration cannot be completed. Installation remains incomplete.' 'Mail übersprungen; Registrierung kann nicht abgeschlossen werden. Installation bleibt unvollständig.'
    gt_handover; return $?
  fi
  for field in SMTP_HOST SMTP_PORT SMTP_USER SMTP_AUTH SMTP_SECURITY SMTP_TEST ADMIN_EMAIL; do
    gt_validate_answer "$field" "${ANSWER[$field]:-}" || return 2
  done
  [[ "${ANSWER[SMTP_AUTH]}:${ANSWER[SMTP_SECURITY]}" != yes:none ]] || return 2
  [[ "${ANSWER[SMTP_AUTH]}" != yes ]] || gt_valid_secret "${SECRET[SMTP_PASSWORD]:-}" || return 2
  if [[ "${STATE[resource.mail_delivery]:-}" == accepted &&
      "${STATE[resource.mail_configuration]:-}" == application-v1 ]]; then
    gt_core_mark step.mail complete || return 2
  fi
  if [[ "${STATE[step.mail]:-}" == complete && "${STATE[resource.mail_configuration]:-}" == application-v1 ]]; then
    gt_text 'Mail milestone already verified; no duplicate test message sent.' \
      'Mail-Prüfung bereits erfolgreich; keine erneute Testnachricht gesendet.'
    gt_handover; return $?
  fi
  printf 'SMTP: %s:%s; transport=%s; auth=%s; sender=%s; recipient=%s; send=%s\n' \
    "${ANSWER[SMTP_HOST]}" "${ANSWER[SMTP_PORT]}" "${ANSWER[SMTP_SECURITY]}" "${ANSWER[SMTP_AUTH]}" "${ANSWER[SMTP_USER]}" "${ANSWER[ADMIN_EMAIL]}" "${ANSWER[SMTP_TEST]}"
  if [[ "${STATE[step.mail]:-}" == intent ]]; then
    gt_text 'Previous attempt was interrupted; the same Message-ID may be submitted again.' 'Vorheriger Versuch unterbrochen; dieselbe Message-ID wird möglicherweise erneut gesendet.'
  fi
  if [[ "$CORE_CONFIRM" != yes ]]; then
    { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
    printf 'check-mail: ' >&"$QUESTION_FD"
    IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == check-mail ]] || return 130
  fi
  gt_core_mark step.mail intent || return 2
  send=${ANSWER[SMTP_TEST]}
  [[ "${STATE[resource.mail_delivery]:-}" != accepted ]] || send=no
  rm -f -- "$SCRATCH/mail-result"
  gt_mail_probe "$send"; result=$?
  if [[ -f "$SCRATCH/mail-result" ]] && grep -qx accepted "$SCRATCH/mail-result"; then
    gt_core_mark resource.mail_delivery accepted || return 2
    gt_text 'Test message accepted by the SMTP server; actual inbox delivery is not verified.' 'Testnachricht vom SMTP-Server angenommen; tatsächliche Zustellung im Postfach nicht geprüft.'
  elif (( result == 0 )) && [[ -f "$SCRATCH/mail-result" ]] && grep -qx connected "$SCRATCH/mail-result"; then
    [[ "${STATE[resource.mail_delivery]:-}" == accepted ]] || gt_core_mark resource.mail_delivery not-requested || return 2
    gt_text 'SMTP connection, selected TLS and authentication verified; no test message requested.' 'SMTP-Verbindung, gewähltes TLS und Anmeldung geprüft; keine Testnachricht gewünscht.'
  else
    gt_core_error 'Mail check failed; resolve SMTP settings/server access, then repeat --check-mail.'
    if (( result == 2 )); then gt_handover blocked; return $?; fi
    gt_core_mark step.mail failed || return 1
    gt_handover; return $?
  fi
  gt_core_mark resource.mail_configuration application-v1 && gt_core_mark step.mail complete || return 2
  gt_handover
}
