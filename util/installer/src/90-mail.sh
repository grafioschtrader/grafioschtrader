gt_mail_resolve() {
  local directory jar status=0 message
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
  message='Application mail configuration could not be resolved/decrypted.'
  (( status == 0 )) || gt_core_error "$message Use the matching backend build; details suppressed."
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

# The answers --check-mail --answers FILE may change until the mail milestone is verified, in question order.
GT_MAIL_ANSWERS='SMTP_CONFIGURE SMTP_HOST SMTP_PORT SMTP_AUTH SMTP_USER SMTP_SECURITY SMTP_PASSWORD SMTP_TEST'

# One line describing the SMTP selection held in ANSWER; the password never appears.
gt_mail_summary() {
  if [[ "${ANSWER[SMTP_CONFIGURE]}" != yes ]]; then gt_text 'no mail' 'keine Mail'; return 0; fi
  printf '%s:%s; transport=%s; auth=%s; sender=%s; send=%s\n' "${ANSWER[SMTP_HOST]}" "${ANSWER[SMTP_PORT]}" \
    "${ANSWER[SMTP_SECURITY]}" "${ANSWER[SMTP_AUTH]}" "${ANSWER[SMTP_USER]}" "${ANSWER[SMTP_TEST]}"
}

# Resolves the SMTP answers of FILE_ANSWERS into ANSWER in question order, so each condition and default sees the
# answers before it. A key missing from FILE keeps its saved value, or takes its default where it did not apply
# before. The password the new selection needs is left in the caller's local password.
gt_mail_answers() {
  local key
  for key in $GT_MAIL_ANSWERS; do
    if ! gt_question_applies "$key"; then
      [[ -z "${FILE_ANSWERS[$key]+set}" ]] || { gt_core_error "$key does not apply to the SMTP selection."; return 2; }
      unset "ANSWER[$key]"; continue
    fi
    if [[ "$key" == SMTP_PASSWORD ]]; then
      password=${FILE_ANSWERS[$key]-${SECRET[$key]-}}
      gt_valid_secret "$password" || { gt_core_error 'The SMTP selection needs a valid SMTP_PASSWORD.'; return 2; }
      continue
    fi
    if [[ -n "${FILE_ANSWERS[$key]+set}" ]]; then ANSWER[$key]=${FILE_ANSWERS[$key]}
    elif [[ -z "${ANSWER[$key]+set}" ]]; then ANSWER[$key]=$(gt_default "$key"); fi
    gt_validate_answer "$key" "${ANSWER[$key]}" || { gt_core_error "Invalid/missing answer: $key"; return 2; }
  done
  [[ "${ANSWER[SMTP_CONFIGURE]}:${ANSWER[SMTP_AUTH]:-}:${ANSWER[SMTP_SECURITY]:-}" != yes:yes:none ]] || {
    gt_core_error 'Authenticated SMTP requires STARTTLS or TLS.'; return 2;
  }
}

# A skipped or wrong SMTP selection shows up only in the mail check, after the backend was built with it. Until the
# mail milestone is verified, --check-mail --answers FILE changes the SMTP answers and the SMTP password; every
# other answer and secret in FILE must still match the journal. Database, Jasypt and JWT secrets, the installation
# ID and the owned resources stay. This function only journals the confirmed change; gt_mail_change_apply runs it.
gt_mail_change() {
  local key value before password='' changed=no
  local -A previous=()
  [[ "${STATE[step.app]:-}" == complete && "${STATE[step.mail]:-}" != complete &&
      "${STATE[resource.mail_configuration]:-}" != application-v1 ]] || {
    gt_core_error 'Only a mail configuration that is not yet verified accepts changed SMTP answers.'; return 2;
  }
  gt_answers_file "$ANSWERS_FILE" || return 2
  for key in "${!FILE_ANSWERS[@]}"; do
    [[ " $GT_MAIL_ANSWERS DB_ROOT_PASSWORD " != *" $key "* ]] || continue
    if [[ "${Q_TYPE[$key]}" == secret ]]; then value=${SECRET[$key]:-}; else value=${ANSWER[$key]:-}; fi
    [[ "${FILE_ANSWERS[$key]}" == "$value" ]] || {
      FILE_ANSWERS=()
      gt_core_error "$key differs from the installation; only the SMTP answers can change before mail is verified."
      return 2
    }
  done
  for key in "${!ANSWER[@]}"; do previous[$key]=${ANSWER[$key]}; done
  before=$(gt_mail_summary)
  if ! gt_mail_answers; then
    FILE_ANSWERS=() ANSWER=()
    for key in "${!previous[@]}"; do ANSWER[$key]=${previous[$key]}; done
    return 2
  fi
  FILE_ANSWERS=()
  for key in $GT_MAIL_ANSWERS; do
    [[ "${ANSWER[$key]-unset}" == "${previous[$key]-unset}" ]] || changed=yes
  done
  [[ "$password" == "${SECRET[SMTP_PASSWORD]-}" ]] || changed=yes
  [[ "$changed" == yes ]] || return 0
  gt_mail_change_plan "$before" "$password" || return $?
  # The answers reach the journal before the secrets file follows; gt_secrets_load accepts either password while
  # the intent is open, so an interruption between both writes resumes the change instead of the old build.
  for key in $GT_MAIL_ANSWERS; do
    [[ "$key" != SMTP_PASSWORD ]] || continue
    if [[ -n "${ANSWER[$key]+set}" ]]; then STATE[answer.$key]=${ANSWER[$key]}; else unset "STATE[answer.$key]"; fi
  done
  unset 'STATE[step.mail]' 'STATE[resource.mail_delivery]' 'STATE[resource.mail_configuration]'
  gt_core_mark step.mail_change intent || return 2
  if [[ -n "$password" ]]; then SECRET[SMTP_PASSWORD]=$password; else unset 'SECRET[SMTP_PASSWORD]'; fi
  gt_secrets_save
}

# Shows the SMTP change and asks for change-mail unless --yes was given.
gt_mail_change_plan() {
  local before=$1 password=$2 reply en de
  printf '%s: %s\n' "$(gt_text 'SMTP before' 'SMTP bisher')" "$before"
  printf '%s: %s\n' "$(gt_text 'SMTP after' 'SMTP neu')" "$(gt_mail_summary)"
  if [[ -n "$password" && "$password" != "${SECRET[SMTP_PASSWORD]-}" ]]; then
    gt_text 'SMTP_PASSWORD changes.' 'SMTP_PASSWORD ändert sich.'
  fi
  en='Regenerate the mail keys of application.properties and application-production.properties, rebuild the'
  en+=' backend of the installed commit, restart Grafioschtrader and check mail again. Database, Jasypt and JWT'
  en+=' secrets and every other answer stay.'
  de='Mail-Schlüssel in application.properties und application-production.properties neu erzeugen, Backend des'
  de+=' installierten Commits neu bauen, Grafioschtrader neu starten und Mail erneut prüfen. Datenbank-, Jasypt-'
  de+=' und JWT-Geheimnisse sowie alle anderen Antworten bleiben.'
  gt_text "$en" "$de"
  [[ "$CORE_CONFIRM" != yes ]] || return 0
  { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
  printf 'change-mail: ' >&"$QUESTION_FD"
  IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == change-mail ]] || return 130
}

# Regenerates only the mail keys: application.properties also holds the cron slots chosen at installation, the
# production file the listener and proxy settings. Repeating it after an interruption builds once more.
gt_mail_change_apply() {
  local resources="$CORE_REPO/backend/grafioschtrader-server/src/main/resources" log commit starttls
  if gt_question_applies SMTP_PASSWORD; then
    gt_valid_secret "${SECRET[SMTP_PASSWORD]:-}" || {
      gt_core_error 'The interrupted SMTP change lost its password; repeat --check-mail --answers FILE.'; return 2;
    }
  elif [[ -n "${SECRET[SMTP_PASSWORD]+set}" ]]; then
    unset 'SECRET[SMTP_PASSWORD]'; gt_secrets_save || return 2
  fi
  gt_mail_properties || return 2
  starttls=${PROPERTIES[spring.mail.properties.mail.smtp.starttls.enable]}
  gt_properties_render "$resources/application.properties" "$SCRATCH/application.config" || return 2
  gt_core_publish properties "$SCRATCH/application.config" "$resources/application.properties" 600 || return 2
  PROPERTIES=([spring.mail.properties.mail.smtp.starttls.required]=$starttls)
  gt_properties_render "$resources/application-production.properties" "$SCRATCH/production.config" || return 2
  gt_core_publish production "$SCRATCH/production.config" "$resources/application-production.properties" 600 ||
    return 2
  # The properties are packaged into the JAR, so only a new backend build carries the mail settings.
  log="$(gt_path /var/lib/gt-install)/app-build.log"
  printf '%s\n' "Build log: $log"
  gt_core_run systemctl stop grafioschtrader.service || return 2
  gt_core_mark step.app_build running || return 2
  gt_as_app env GT_INSTALL_BUILD_ONLY=1 GT_CRON_RANDOMIZE=off bash "$CORE_HOME/gtupbackend.sh" >> "$log" 2>&1 || {
    gt_core_error "Backend build failed; inspect $log and repeat --check-mail."; return 2;
  }
  gt_core_config_valid && gt_app_artifacts || return 2
  commit=$(gt_as_app git -C "$CORE_REPO" rev-parse HEAD) || return 2
  [[ "$commit" == "${STATE[planned_commit]}" ]] || return 2
  gt_core_mark step.app_build complete || return 2
  gt_app_start || return 2
  gt_core_mark step.mail_change complete
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
  if [[ "$MODE" == --check-mail && -n "$ANSWERS_FILE" ]]; then gt_mail_change || return $?; fi
  if [[ "${STATE[step.mail_change]:-}" == intent ]]; then gt_mail_change_apply || return 2; fi
  [[ "${STATE[step.app]:-}" == complete ]] && gt_core_config_valid && gt_app_artifacts && gt_app_verify || return 2
  if [[ "${ANSWER[SMTP_CONFIGURE]}" == no ]]; then
    gt_core_mark step.mail skipped || return 2
    gt_text 'Mail skipped; registration cannot be completed. Installation remains incomplete.' \
      'Mail übersprungen; Registrierung kann nicht abgeschlossen werden. Installation bleibt unvollständig.'
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
    "${ANSWER[SMTP_HOST]}" "${ANSWER[SMTP_PORT]}" "${ANSWER[SMTP_SECURITY]}" "${ANSWER[SMTP_AUTH]}" \
    "${ANSWER[SMTP_USER]}" "${ANSWER[ADMIN_EMAIL]}" "${ANSWER[SMTP_TEST]}"
  if [[ "${STATE[step.mail]:-}" == intent ]]; then
    gt_text 'Previous attempt was interrupted; the same Message-ID may be submitted again.' \
      'Vorheriger Versuch unterbrochen; dieselbe Message-ID wird möglicherweise erneut gesendet.'
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
    gt_text 'Test message accepted by the SMTP server; actual inbox delivery is not verified.' \
      'Testnachricht vom SMTP-Server angenommen; tatsächliche Zustellung im Postfach nicht geprüft.'
  elif (( result == 0 )) && [[ -f "$SCRATCH/mail-result" ]] && grep -qx connected "$SCRATCH/mail-result"; then
    [[ "${STATE[resource.mail_delivery]:-}" == accepted ]] || gt_core_mark resource.mail_delivery not-requested ||
      return 2
    gt_text 'SMTP connection, selected TLS and authentication verified; no test message requested.' \
      'SMTP-Verbindung, gewähltes TLS und Anmeldung geprüft; keine Testnachricht gewünscht.'
  else
    gt_core_error 'Mail check failed; resolve SMTP settings/server access, then repeat --check-mail.'
    if (( result == 2 )); then gt_handover blocked; return $?; fi
    gt_core_mark step.mail failed || return 1
    gt_handover; return $?
  fi
  gt_core_mark resource.mail_configuration application-v1 && gt_core_mark step.mail complete || return 2
  gt_handover
}
