# Completion is a recorded installation outcome, not a health check after later updates.
# Reports use only selected public fields; neither secrets nor diagnostic logs are copied.
gt_result_remember_warnings() {
  local warning digest
  for warning in "${NOTES[@]}" "${PLAN_WARNINGS[@]/#/WARN: }"; do
    [[ "$warning" == 'WARN: '* ]] || continue
    digest=$(printf '%s' "$warning" | sha256sum) || return 1
    STATE[resource.warning.${digest%% *}]=$warning
  done
}

gt_result_milestones() {
  local key marker
  RESULT=([backend]=pending [web]=pending [tls]=pending [mail]=pending [mail_delivery]=pending)
  for key in backend web mail; do
    marker=$key; [[ "$key" != backend ]] || marker=app
    case "${STATE[step.$marker]:-}" in
      complete) RESULT[$key]=ok ;;
      failed) RESULT[$key]=failed ;;
      skipped) RESULT[$key]=skipped ;;
    esac
  done
  if [[ -z "${ANSWER[DOMAIN]:-}" ]]; then RESULT[tls]=skipped
  else
    case "${STATE[step.tls]:-}" in
      complete) RESULT[tls]=ok ;;
      failed) RESULT[tls]=failed ;;
      unverified) [[ "${ANSWER[TLS_SOURCE]:-}" != proxy ]] || RESULT[tls]=unverified ;;
    esac
  fi
  case "${STATE[resource.mail_delivery]:-}" in
    accepted|not-requested) RESULT[mail_delivery]=${STATE[resource.mail_delivery]} ;;
    *) [[ "${STATE[step.mail]:-}" != intent ]] || RESULT[mail_delivery]=uncertain ;;
  esac
  if [[ "${ANSWER[SMTP_CONFIGURE]:-}" == no ]]; then
    RESULT[mail]=skipped RESULT[mail_delivery]=skipped
  elif [[ "${RESULT[mail]}" == ok ]]; then
    if [[ "${STATE[resource.mail_configuration]:-}" != application-v1 ||
        ( "${ANSWER[SMTP_TEST]:-}" == yes && "${RESULT[mail_delivery]}" != accepted ) ||
        ( "${ANSWER[SMTP_TEST]:-}" != yes && "${RESULT[mail_delivery]}" != accepted &&
          "${RESULT[mail_delivery]}" != not-requested ) ]]; then RESULT[mail]=pending; fi
  fi
  RESULT[status]=incomplete RESULT[exit_code]=10
  if [[ "${RESULT[backend]}" != ok || "${RESULT[web]}" == failed ]]; then
    RESULT[status]=failed RESULT[exit_code]=1
  elif [[ "${RESULT[web]}:${RESULT[mail]}" == ok:ok &&
      "${RESULT[tls]}" =~ ^(ok|unverified|skipped)$ &&
      "${STATE[built_commit]:-}" =~ ^[a-f0-9]{40}$ ]]; then
    RESULT[status]=complete RESULT[exit_code]=0
  fi
}

gt_completion_valid() {
  local step
  if [[ "${STATE[scope]:-}" == bootstrap ]]; then
    [[ "${STATE[resource.bootstrap_plan]:-}" =~ ^[a-f0-9]{64}$ ]] || return 2
  fi
  [[ "${STATE[completed_at]:-}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ &&
      "${STATE[installer_sha256]:-}" =~ ^[a-f0-9]{64}$ &&
      "${STATE[built_commit]:-}" == "${STATE[planned_commit]:-}" &&
      "${STATE[built_commit]:-}" =~ ^[a-f0-9]{40}$ ]] || return 2
  for step in core app app_build app_start web mail; do
    [[ "${STATE[step.$step]:-}" == complete ]] || return 2
  done
  [[ "${ANSWER[SMTP_CONFIGURE]:-}" == yes && "${ANSWER[SMTP_TEST]:-}" =~ ^(yes|no)$ &&
      -n "${ANSWER[ADMIN_EMAIL]:-}" && -n "${STATE[resource.web_lan]:-}" ]] || return 2
  gt_result_milestones
  [[ "${RESULT[status]}" == complete ]]
}

gt_result_details() {
  local key index=0 address=${STATE[resource.web_lan]:-unknown}
  [[ "$address" != *:* ]] || address="[$address]"
  RESULT[url]="http://$address/grafioschtrader/"
  RESULT[lan_url]=${RESULT[url]}
  [[ -z "${ANSWER[DOMAIN]:-}" ]] || RESULT[url]="https://${ANSWER[DOMAIN]}/grafioschtrader/"
  for key in run_id planned_commit built_commit installer_sha256 completed_at; do
    RESULT[$key]=${STATE[$key]:-pending}
  done
  RESULT[admin_email]=${ANSWER[ADMIN_EMAIL]:-pending}
  RESULT[registration]=$(gt_text "Register with ${RESULT[admin_email]}; this account becomes administrator." \
    "Mit ${RESULT[admin_email]} registrieren; dieses Konto wird Administrator.")
  RESULT[update]=$(gt_text 'After hand-over, run ./gtupdate.sh as grafioschtrader in /home/grafioschtrader.' \
    'Nach der Übergabe ./gtupdate.sh als grafioschtrader in /home/grafioschtrader ausführen.')
  RESULT[configuration]=$(gt_text \
    'Updates merge template keys in application.properties and drop additional keys.' \
    'Updates übernehmen Vorlagenschlüssel in application.properties und entfernen zusätzliche Schlüssel.')
  RESULT[production_configuration]=$(gt_text \
    'Put additional keys in application-production.properties; updates preserve this file unchanged.' \
    'Zusätzliche Schlüssel in application-production.properties ablegen; Updates erhalten diese Datei unverändert.')
  RESULT[launchers]=$(gt_text 'Keep the generated launchers and assigned cron slot.' \
    'Erzeugte Startskripte und zugewiesenen Cron-Zeitpunkt beibehalten.')
  RESULT[recovery]=$(gt_text 'Resume pending stages with the original journal and secrets.' \
    'Offene Stufen mit ursprünglichem Journal und Geheimnissen fortsetzen.')
  RESULT[database]=$(gt_text 'Restoring files does not roll back database migrations.' \
    'Wiederhergestellte Dateien setzen Datenbankmigrationen nicht zurück.')
  while IFS= read -r key; do
    [[ "$key" == resource.warning.* ]] || continue
    RESULT[warning.$((++index))]=${STATE[$key]}
  done < <(printf '%s\n' "${!STATE[@]}" | LC_ALL=C sort)
  gt_result_actions
}

gt_result_actions() {
  if [[ "${RESULT[mail]}" == skipped ]]; then
    RESULT[action.mail]=$(gt_text 'Without mail nobody can complete registration, so there is no administrator yet.' \
      'Ohne Mail kann niemand die Registrierung abschließen; daher gibt es noch keinen Administrator.')
  elif [[ "${RESULT[mail]}" != ok ]]; then
    RESULT[action.mail]=$(gt_text 'Resolve SMTP settings or server access, then repeat --check-mail.' \
      'SMTP-Einstellungen oder Serverzugriff klären, danach --check-mail wiederholen.')
  fi
  [[ "${RESULT[web]}" == ok ]] || RESULT[action.web]=$(gt_text \
    'Complete web integration with --install-web, then repeat --check-mail.' \
    'Webanbindung mit --install-web abschließen, danach --check-mail wiederholen.')
  if [[ "${RESULT[tls]}" == unverified ]]; then
    RESULT[action.tls]=$(gt_text "Verify ${RESULT[url]} from outside this network." \
      "${RESULT[url]} von außerhalb dieses Netzes prüfen.")
  elif [[ "${RESULT[tls]}" == failed || "${RESULT[tls]}" == pending ]]; then
    RESULT[action.tls]=$(gt_text \
      'Use LAN access only until HTTPS works; sessions use bearer tokens. Resume --install-web.' \
      'Bis HTTPS funktioniert nur LAN-Zugriff nutzen; Sitzungen verwenden Bearer-Tokens. --install-web fortsetzen.')
  fi
  case "${RESULT[mail_delivery]}" in
    accepted) RESULT[mail_delivery_note]=$(gt_text 'SMTP accepted the test message; inbox delivery is not verified.' \
      'SMTP hat die Testnachricht angenommen; die Zustellung im Postfach ist nicht geprüft.') ;;
    not-requested) RESULT[mail_delivery_note]=$(gt_text 'SMTP verified without sending a test message.' \
      'SMTP ohne Versand einer Testnachricht geprüft.') ;;
    uncertain) RESULT[mail_delivery_note]=$(gt_text 'Interrupted attempt; delivery is uncertain. A retry may resend.' \
      'Unterbrochener Versuch; Zustellung ungewiss. Eine Wiederholung kann erneut senden.') ;;
  esac
}

gt_result_print() {
  local key
  gt_text 'Installation result (recorded milestones)' 'Installationsergebnis (gespeicherte Prüfergebnisse)'
  while IFS= read -r key; do
    printf '%s=%s\n' "$key" "$(gt_safe "${RESULT[$key]}")"
  done < <(printf '%s\n' "${!RESULT[@]}" | LC_ALL=C sort)
}

gt_handover() {
  local outcome=${1:-} digest state_dir
  state_dir=$(gt_path /var/lib/gt-install)
  gt_private_dir "$state_dir" || return 2
  gt_no_symlinks "$state_dir/result" || return 2
  if [[ -e "$state_dir/result" ]]; then gt_private_read "$state_dir/result" || return 2; PRIVATE_CONTENT=''; fi
  digest=$(sha256sum "${BASH_SOURCE[0]}") || return 1
  STATE[installer_sha256]=${digest%% *}
  gt_result_remember_warnings || return 1
  gt_result_milestones
  case "$outcome" in
    blocked) RESULT[status]=blocked RESULT[exit_code]=2 ;;
    failed) RESULT[status]=failed RESULT[exit_code]=1 ;;
    '') ;;
    *) return 2 ;;
  esac
  if [[ "${RESULT[status]}" == complete ]]; then
    STATE[completed_at]=$(date -u +%Y-%m-%dT%H:%M:%SZ) || return 1
    if ! gt_completion_valid; then
      unset 'STATE[completed_at]'
      gt_core_error 'Completion evidence is inconsistent; restore the original journal.'
      return 2
    fi
  fi
  gt_result_details
  # Publish the result first. A crash before the journal commit resumes without another SMTP send.
  gt_atomic_map "$state_dir/result" RESULT || return 1
  [[ "${RESULT[status]}" != complete ]] || STATE[status]=complete
  gt_state_save || return 1
  gt_result_print
  return "${RESULT[exit_code]}"
}

# 3 means normal entry may continue; 0 is completed and 2 is an invalid completed journal.
gt_completed() {
  local file
  file=$(gt_path /var/lib/gt-install/state)
  [[ -e "$file" || -L "$file" ]] || return 3
  [[ "$(gt_literal "$file" status)" == complete ]] || return 3
  gt_question_model
  gt_state_load || { gt_core_error 'Invalid completed installation journal.'; return 2; }
  gt_result_details
  printf 'host.class=completed\n'
  gt_text 'Installation completed; use the updater for subsequent changes.' \
    'Installation abgeschlossen; für weitere Updates den Updater verwenden.'
  gt_result_print
}
