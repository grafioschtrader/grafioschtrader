# Interactive front ends. The plain prompts and the whiptail dialogs render the same question model: conditions,
# defaults and validation come only from gt_question_applies, gt_default and gt_validate_answer. Whiptail is chosen
# for the flows that ask questions when a usable terminal of at least 80 x 24 cells exists; --plain, --yes, a dumb
# terminal or a missing whiptail select the plain prompts. Results travel through --output-fd, so no answer or
# credential ever appears in a command line.

# Exit status of a dialog sequence whose first item was left with Back; the caller returns to its own last step.
GT_BACK=75

gt_terminal_size() {
  local fd size=''
  { exec {fd}<>/dev/tty; } 2>/dev/null || return 1
  [[ ! -t "$fd" ]] || size=$(stty size <&"$fd" 2>/dev/null)
  exec {fd}<&-
  [[ "$size" =~ ^([0-9]+)\ ([0-9]+)$ ]] || return 1
  TERM_ROWS=${BASH_REMATCH[1]} TERM_COLS=${BASH_REMATCH[2]}
}

gt_frontend_select() {
  FRONTEND=plain
  case "$MODE" in --bootstrap|--install-core|--dry-run|--prepare) ;; *) return 0 ;; esac
  [[ "$PLAIN_FORCED" != yes && "$CORE_CONFIRM" != yes ]] || return 0
  [[ -n "${TERM:-}" && "$TERM" != dumb ]] || return 0
  command -v whiptail >/dev/null || return 0
  gt_terminal_size || return 0
  (( TERM_ROWS >= 24 && TERM_COLS >= 80 )) || return 0
  FRONTEND=whiptail
}

gt_tty_open() {
  if [[ -z "$QUESTION_FD" ]]; then { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2; fi
  QUESTION_OUTPUT=$QUESTION_FD
}

# Scrollable texts keep the focus on the text, so the key help names Tab for reaching the buttons.
gt_wt_backtitle() {
  gt_text 'Grafioschtrader installation  ·  Tab: buttons  ·  Esc: abort' \
    'Grafioschtrader-Installation  ·  Tab: Schaltflächen  ·  Esc: Abbruch'
}

# Runs one dialog on the terminal and prints its result on stdout; the exit status is whiptail's: 0 OK or Yes,
# 1 Cancel, Back or No, 255 Esc.
gt_wt() {
  gt_tty_open || return 2
  whiptail --backtitle "$(gt_wt_backtitle)" --output-fd 3 "$@" \
    3>&1 1>&"$QUESTION_FD" 2>&"$QUESTION_FD" <&"$QUESTION_FD"
}

gt_wt_width() { if (( TERM_COLS > 104 )); then echo 100; else echo $((TERM_COLS - 4)); fi; }
gt_wt_height() { echo $((TERM_ROWS - 4)); }

# Esc asks before anything is abandoned; 0 means the user wants to abort.
gt_wt_abort() {
  gt_wt --title "$(gt_text 'Abort' 'Abbrechen')" --defaultno \
    --yes-button "$(gt_text 'Abort' 'Abbrechen')" --no-button "$(gt_text 'Continue' 'Weiter')" \
    --yesno -- "$(gt_text 'Abort the installer? Nothing confirmed so far is executed.' \
      'Installer abbrechen? Bisher nicht bestätigte Schritte werden nicht ausgeführt.')" 9 "$(gt_wt_width)" >/dev/null
}

# Shows a file in a scrollable text box and adds it to the transcript printed when the run ends.
gt_wt_textbox() {
  local title=$1 file=$2 status
  cat -- "$file" >> "$TRANSCRIPT"
  while :; do
    status=0
    gt_wt --title "$title" --scrolltext --ok-button "$(gt_text Continue Weiter)" \
      --textbox -- "$file" "$(gt_wt_height)" "$(gt_wt_width)" >/dev/null || status=$?
    (( status == 255 )) || return 0
    if gt_wt_abort; then return 130; fi
  done
}

# The inventory and compatibility report: printed in the plain front end, a text box in whiptail.
gt_show_report() {
  if [[ "$FRONTEND" != whiptail ]]; then gt_report; return 0; fi
  gt_report > "$SCRATCH/report.txt"
  gt_wt_textbox "$(gt_text 'Inventory and compatibility' 'Bestandsaufnahme und Kompatibilität')" \
    "$SCRATCH/report.txt"
}

# The plan report. With a confirmation word, whiptail asks Install/Cancel on the plan itself, the plain front end
# asks for the word; without one the plan is only shown.
gt_show_plan() {
  if [[ "$FRONTEND" != whiptail ]]; then gt_plan_report; return 0; fi
  gt_plan_report > "$SCRATCH/plan.txt"
  gt_wt_textbox "$(gt_text 'Summary and action plan' 'Zusammenfassung und Aktionsplan')" "$SCRATCH/plan.txt"
}

gt_confirm() {
  local word=$1 prompt reply status
  if [[ "$FRONTEND" == whiptail ]]; then
    while :; do
      status=0
      gt_wt --title "$(gt_text 'Summary and action plan' 'Zusammenfassung und Aktionsplan')" --scrolltext \
        --yes-button "$(gt_text Install Installieren)" --no-button "$(gt_text Cancel Abbrechen)" \
        --yesno -- "$(< "$SCRATCH/plan.txt")" "$(gt_wt_height)" "$(gt_wt_width)" >/dev/null || status=$?
      case "$status" in 0) return 0 ;; 1) return 130 ;; esac
      if gt_wt_abort; then return 130; fi
    done
  fi
  prompt=$(gt_text 'Type the following to execute this plan' 'Zur Ausführung dieses Plans Folgendes eingeben')
  printf '%s: %s: ' "$prompt" "$word" >&"$QUESTION_OUTPUT"
  IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == "$word" ]] || return 130
}

# A short explanation of what a rejected value has to look like, shown above the reopened dialog.
gt_validation_hint() {
  case "${Q_TYPE[$1]}" in
    domain) gt_text 'Enter a DNS name such as gt.example.org, or leave it empty for LAN only.' \
      'Einen DNS-Namen wie gt.example.org eingeben oder für nur LAN leer lassen.' ;;
    host) gt_text 'Enter a host name such as smtp.example.org.' 'Einen Hostnamen wie smtp.example.org eingeben.' ;;
    email) gt_text 'Enter an e-mail address such as admin@example.org.' \
      'Eine E-Mail-Adresse wie admin@example.org eingeben.' ;;
    port) gt_text 'Enter a port from 1 to 65535.' 'Einen Port von 1 bis 65535 eingeben.' ;;
    integer) gt_text 'Enter a positive whole number.' 'Eine positive ganze Zahl eingeben.' ;;
    path) gt_text 'Enter an absolute path of letters, digits, dots, underscores, hyphens and slashes.' \
      'Einen absoluten Pfad aus Buchstaben, Ziffern, Punkten, Unter- und Bindestrichen und Schrägstrichen eingeben.' ;;
    lineage) gt_text 'Enter a Certbot certificate name, or - for a new certificate.' \
      'Einen Certbot-Zertifikatsnamen eingeben, oder - für ein neues Zertifikat.' ;;
    timezone) gt_text 'Enter a time zone such as Europe/Zurich or UTC.' \
      'Eine Zeitzone wie Europe/Zurich oder UTC eingeben.' ;;
    address) gt_text 'Enter an IPv4 or IPv6 address, or leave it empty.' \
      'Eine IPv4- oder IPv6-Adresse eingeben oder leer lassen.' ;;
    lan) gt_text 'Enter an IPv4 address of this host outside 127.0.0.0/8.' \
      'Eine IPv4-Adresse dieses Hosts ausserhalb von 127.0.0.0/8 eingeben.' ;;
    heap) gt_text 'Enter -Xms<size> -Xmx<size> with m or g; the minimum must not exceed the maximum.' \
      '-Xms<Größe> -Xmx<Größe> mit m oder g eingeben; das Minimum darf das Maximum nicht übersteigen.' ;;
    *) gt_text 'Choose one of the offered values.' 'Einen der angebotenen Werte wählen.' ;;
  esac
}

gt_choice_label() {
  case "$1:$2" in
    *:yes) gt_text Yes Ja ;;
    *:no) gt_text No Nein ;;
    DNS_FAMILY:ipv4) gt_text 'IPv4 only' 'Nur IPv4' ;;
    DNS_FAMILY:ipv6) gt_text 'IPv6 only (DS-Lite, CGNAT)' 'Nur IPv6 (DS-Lite, CGNAT)' ;;
    DNS_FAMILY:both) gt_text 'IPv4 and IPv6' 'IPv4 und IPv6' ;;
    TLS_SOURCE:letsencrypt)
      gt_text "Let's Encrypt certificate through certbot" "Let's-Encrypt-Zertifikat über certbot" ;;
    TLS_SOURCE:existing) gt_text 'Existing certificate on this host' 'Vorhandenes Zertifikat auf diesem Host' ;;
    TLS_SOURCE:proxy) gt_text 'TLS-terminating proxy in front of this host' \
      'TLS-terminierender Proxy vor diesem Host' ;;
    WEBSERVER:nginx) printf 'nginx' ;;
    WEBSERVER:apache2) printf 'Apache' ;;
    WEBSERVER:none) gt_text 'Own web server, configured by hand' 'Eigener Webserver, von Hand konfiguriert' ;;
    SMTP_SECURITY:starttls) printf 'STARTTLS' ;;
    SMTP_SECURITY:tls) printf 'TLS' ;;
    SMTP_SECURITY:none) gt_text 'None, unauthenticated relay only' 'Keine, nur Relay ohne Anmeldung' ;;
    *) printf '%s' "$2" ;;
  esac
}

# One question as a dialog. 0 stores the answer, 1 means Back, 130 abort. An earlier answer is offered again unless
# it was the default of its time, in which case the default is recomputed from the current answers.
gt_wt_ask() {
  local key=$1 value default prompt text error='' status item width
  local -a items=() selected=()
  width=$(gt_wt_width)
  default=$(gt_default "$key")
  if [[ -n "${ANSWER[$key]+set}" && "${ANSWER_DEFAULTED[$key]:-}" != yes ]]; then default=${ANSWER[$key]}; fi
  prompt=$(gt_text "${Q_EN[$key]}" "${Q_DE[$key]}")
  if [[ "$key" == LAN_ADDRESS && "${FACT[network.ipv4_addresses]:-unknown}" != unknown ]]; then
    prompt+=$'\n'"$(gt_text 'Addresses of this host' 'Adressen dieses Hosts'): ${FACT[network.ipv4_addresses]}"
  fi
  while :; do
    text=$prompt
    [[ -z "$error" ]] || text="$error"$'\n\n'"$prompt"
    status=0
    case "${Q_TYPE[$key]}" in
      yesno|choice)
        items=() selected=()
        for item in ${Q_CHOICES[$key]}; do items+=("$item" "$(gt_choice_label "$key" "$item")"); done
        [[ -z "$default" ]] || selected=(--default-item "$default")
        value=$(gt_wt --title "$key" --notags --ok-button OK --cancel-button "$(gt_text Back Zurück)" \
          "${selected[@]}" --menu -- "$text" $(( ${#items[@]} / 2 + 14 )) "$width" $(( ${#items[@]} / 2 )) \
          "${items[@]}") || status=$?
        ;;
      *)
        value=$(gt_wt --title "$key" --ok-button OK --cancel-button "$(gt_text Back Zurück)" \
          --inputbox -- "$text" 14 "$width" "$default") || status=$?
        ;;
    esac
    case "$status" in
      0) ;;
      1) return 1 ;;
      *) if gt_wt_abort; then return 130; fi; continue ;;
    esac
    # As in the plain prompts, an empty field takes the current default.
    [[ -n "$value" ]] || value=$(gt_default "$key")
    [[ "$key" != DOMAIN ]] || value=${value,,}
    if gt_validate_answer "$key" "$value"; then
      ANSWER[$key]=$value
      if [[ "$value" == "$(gt_default "$key")" ]]; then ANSWER_DEFAULTED[$key]=yes; else ANSWER_DEFAULTED[$key]=no; fi
      return 0
    fi
    error="$(gt_text 'Invalid value' 'Ungültiger Wert'): $(gt_safe "$value")"$'\n'"$(gt_validation_hint "$key")"
    default=$value
  done
}

# The DNS checklist of a domain with Continue and Stop here; 0 continues, 130 stops the run.
gt_wt_dns_checklist() {
  local text status
  text=$(gt_dns_checklist)
  [[ -n "$text" ]] || return 0
  while :; do
    status=0
    gt_wt --title "$(gt_text 'DNS checklist' 'DNS-Checkliste')" \
      --yes-button "$(gt_text Continue Weiter)" --no-button "$(gt_text 'Stop here' 'Hier anhalten')" \
      --yesno -- "$text" 16 "$(gt_wt_width)" >/dev/null || status=$?
    case "$status" in 0) return 0 ;; 1) return 130 ;; esac
    if gt_wt_abort; then return 130; fi
  done
}

# Walks the question model with Back. Leaving the first question with Back asks whether to abort. "last" re-enters at
# the last question asked, which is where Back from the first credential leads.
gt_wt_questions() {
  local from=${1:-} i=0 key status
  if [[ "$from" == last ]] && (( ${#WT_HISTORY[@]} )); then
    i=${WT_HISTORY[-1]}; unset 'WT_HISTORY[-1]'
  else WT_HISTORY=(); fi
  while (( i < ${#QUESTIONS[@]} )); do
    key=${QUESTIONS[i]}
    if ! gt_question_applies "$key"; then
      # A changed earlier answer can switch a question off; its answer or secret must not survive.
      unset 'ANSWER[$key]' 'ANSWER_DEFAULTED[$key]' 'SECRET[$key]' 'SECRET_STATUS[$key]'
      i=$((i+1)); continue
    fi
    if [[ "${Q_TYPE[$key]}" == secret ]]; then i=$((i+1)); continue; fi
    if [[ "$key" == WEBSERVER && ( "${REASON[web]:-}" == nginx || "${REASON[web]:-}" == apache2 ) ]]; then
      ANSWER[$key]=${REASON[web]}; i=$((i+1)); continue
    fi
    status=0; gt_wt_ask "$key" || status=$?
    case "$status" in
      0)
        WT_HISTORY+=("$i")
        if [[ "$key" == TLS_SOURCE ]]; then gt_wt_dns_checklist || return $?; fi
        i=$((i+1)) ;;
      1)
        if (( ${#WT_HISTORY[@]} == 0 )); then
          if gt_wt_abort; then return 130; fi
        else i=${WT_HISTORY[-1]}; unset 'WT_HISTORY[-1]'; fi ;;
      *) return "$status" ;;
    esac
  done
}

# Questions followed by credentials, with Back from the first credential leading to the last question.
gt_interactive_answers() {
  local status from=''
  while :; do
    gt_questions "$from" || return $?
    status=0; gt_prepare_secrets || status=$?
    (( status == GT_BACK )) || return "$status"
    from=last
  done
}

# A new password made of the alphabet of gen_secret in docker/install.sh.
gt_secret_generate() {
  local value
  value=$(gt_probe openssl rand -base64 48) || return 2
  value=${value//[$'/+=\n']/}; value=${value:0:24}
  [[ "$value" =~ ^[a-zA-Z0-9]{24}$ ]] || return 2
  SECRET[$1]=$value SECRET_STATUS[$1]=generated
}

# The generated value appears once on the terminal for the user to record, never in a log, plan or report.
gt_secret_show_generated() {
  set +vx
  local key=$1 file reply en de
  en="Generated password for $key. Record it now; it is not shown again."
  de="Erzeugtes Passwort für $key. Jetzt notieren; es wird nicht erneut angezeigt."
  if [[ "$FRONTEND" == whiptail ]]; then
    file=$(umask 077; mktemp "$SCRATCH/generated.XXXXXX") || return 2
    printf '%s\n\n    %s\n' "$(gt_text "$en" "$de")" "${SECRET[$key]}" > "$file"
    gt_wt --title "$key" --ok-button "$(gt_text 'Recorded' 'Notiert')" \
      --textbox -- "$file" 12 "$(gt_wt_width)" > /dev/null
    rm -f -- "$file"
  else
    printf '%s\n    %s\n%s' "$(gt_text "$en" "$de")" "${SECRET[$key]}" \
      "$(gt_text 'Press Enter once recorded. ' 'Nach dem Notieren Enter drücken. ')" >&"$QUESTION_OUTPUT"
    IFS= read -r -u "$QUESTION_FD" reply || return 130
  fi
  return 0
}

# A credential as password dialogs. New passwords are entered twice or generated by an explicit menu choice;
# credentials of existing accounts and tokens are entered once. 0 stores it, 1 means Back, 130 abort.
gt_wt_secret() {
  set +vx
  local key=$1 twice=${2:-no} back=${3:-yes} first second status error='' prompt text width action
  width=$(gt_wt_width)
  prompt=$(gt_text "${Q_EN[$key]}" "${Q_DE[$key]}")
  while :; do
    status=0
    if [[ "$twice" == yes && -z "$error" ]]; then
      action=$(gt_wt --title "$key" --notags --ok-button OK --cancel-button "$(gt_text Back Zurück)" \
        --default-item enter --menu -- "$prompt" 12 "$width" 2 \
        enter "$(gt_text 'Enter a password' 'Passwort eingeben')" \
        generate "$(gt_text 'Generate a password' 'Passwort erzeugen')") || status=$?
      if (( status == 0 )) && [[ "$action" == generate ]]; then
        gt_secret_generate "$key" || return 2
        gt_secret_show_generated "$key"; return $?
      fi
    fi
    if (( status == 0 )); then
      text=$prompt
      [[ -z "$error" ]] || text="$error"$'\n\n'"$prompt"
      first=$(gt_wt --title "$key" --ok-button OK --cancel-button "$(gt_text Back Zurück)" \
        --passwordbox -- "$text" 12 "$width") || status=$?
    fi
    if (( status == 0 )) && ! gt_secret_valid_for "$key" "$first"; then
      error=$(gt_text 'Empty or invalid input.' 'Leere oder ungültige Eingabe.'); continue
    fi
    if (( status == 0 )) && [[ "$twice" == yes ]]; then
      second=$(gt_wt --title "$key" --ok-button OK --cancel-button "$(gt_text Back Zurück)" \
        --passwordbox -- "$(gt_text 'Repeat password' 'Passwort wiederholen')" 10 "$width") || status=$?
      if (( status == 0 )) && [[ "$first" != "$second" ]]; then
        first='' second=''
        error=$(gt_text 'The two entries differ.' 'Die beiden Eingaben unterscheiden sich.'); continue
      fi
    fi
    case "$status" in
      0) SECRET[$key]=$first SECRET_STATUS[$key]=collected; first='' second=''; return 0 ;;
      1) [[ "$back" != yes ]] || return 1 ;;
    esac
    if gt_wt_abort; then return 130; fi
    error=''
  done
}

# Progress of the execution. The plain front end prints the given line as before; whiptail shows a gauge with the
# current step and the last line of its log, fed by a background reader of a small state file.
gt_progress() {
  local phase=$1 step=$2 line=${3:-}
  if [[ -n "$GAUGE_PID" ]]; then
    printf '%s %s %(%s)T\n' "$phase" "$step" -1 > "$GAUGE_STATE.new" && mv -f -- "$GAUGE_STATE.new" "$GAUGE_STATE"
  fi
  [[ -z "$line" ]] || printf '%s\n' "$line"
}

gt_gauge_weights() {
  printf '%s\n' 'core base_packages 4' 'core swap 1' 'core toolchains 8' 'core user 1' 'core duckdns 1' \
    'core buildtools 6' 'core clone 4' 'core database 2' 'core configure 3' \
    'app scripts 1' 'app resources 1' 'app service 1' 'app cron 1' 'app build 50' 'app start 6' \
    'web web 7' 'mail mail 3'
}

gt_gauge_label() {
  case "$1" in
    base_packages) gt_text 'Base packages' 'Basispakete' ;;
    swap) gt_text 'Swap file' 'Auslagerungsdatei' ;;
    toolchains) gt_text 'Java and Maven' 'Java und Maven' ;;
    user) gt_text 'Service user' 'Dienstbenutzer' ;;
    duckdns) printf 'DuckDNS' ;;
    buildtools) gt_text 'Node.js and build tools' 'Node.js und Build-Werkzeuge' ;;
    clone) gt_text 'Source code' 'Quellcode' ;;
    database) gt_text 'Database' 'Datenbank' ;;
    configure) gt_text 'Encrypted configuration' 'Verschlüsselte Konfiguration' ;;
    scripts) gt_text 'Update scripts' 'Update-Skripte' ;;
    resources) gt_text 'System resources' 'Systemressourcen' ;;
    service) gt_text 'Service unit' 'Dienst-Unit' ;;
    cron) gt_text 'Scheduled tasks' 'Zeitgesteuerte Aufgaben' ;;
    build) gt_text 'Build of frontend and backend' 'Build von Frontend und Backend' ;;
    start) gt_text 'First start and database migrations' 'Erster Start und Datenbankmigrationen' ;;
    web) gt_text 'Web server and TLS' 'Webserver und TLS' ;;
    mail) gt_text 'Mail check' 'Mail-Prüfung' ;;
    *) printf '%s' "$1" ;;
  esac
}

# Fraction of the first build in percent, read from the build log: npm, the Angular bundle, then the Maven reactor
# with its [module/modules] counter.
gt_build_fraction() {
  local log=$1 reactor
  [[ -r "$log" ]] || { echo 0; return; }
  if grep -q 'BUILD SUCCESS' "$log"; then echo 100; return; fi
  reactor=$(grep -oE 'Building .* \[[0-9]+/[0-9]+\]' "$log" | tail -n 1 | grep -oE '[0-9]+/[0-9]+\]$') || reactor=''
  if [[ "$reactor" =~ ^([0-9]+)/([0-9]+)\]$ ]] && (( BASH_REMATCH[2] > 0 )); then
    echo $(( 45 + 55 * (BASH_REMATCH[1] - 1) / BASH_REMATCH[2] )); return
  fi
  if grep -qE 'Application bundle generation complete|Initial total|latest\.tar\.gz' "$log"; then echo 40; return; fi
  if grep -qE 'added [0-9]+ packages' "$log"; then echo 15; return; fi
  echo 0
}

gt_gauge_feed() {
  local phase step started total=0 done_weight=0 weight p s w found=no percent fraction log line minutes
  local build_log
  build_log="$(gt_path /var/lib/gt-install)/app-build.log"
  while read -r p s w; do [[ " $GAUGE_PHASES " != *" $p "* ]] || total=$((total + w)); done < <(gt_gauge_weights)
  (( total > 0 )) || total=1
  while [[ -e "$GAUGE_RUN" ]]; do
    if read -r phase step started < "$GAUGE_STATE" 2>/dev/null && [[ -n "${step:-}" ]]; then
      done_weight=0 weight=0 found=no
      while read -r p s w; do
        [[ " $GAUGE_PHASES " == *" $p "* ]] || continue
        if [[ "$p:$s" == "$phase:$step" ]]; then weight=$w found=yes; break; fi
        done_weight=$((done_weight + w))
      done < <(gt_gauge_weights)
      [[ "$found" == yes ]] || done_weight=0
      fraction=0 log=$EXEC_LOG
      if [[ "$step" == build ]]; then fraction=$(gt_build_fraction "$build_log"); log=$build_log; fi
      percent=$(( (100 * done_weight + weight * fraction) / total ))
      minutes=$(( ($(printf '%(%s)T' -1) - started) / 60 ))
      line=$(tail -n 1 "$log" 2>/dev/null) || line=''
      line=$(gt_safe "$line"); line=${line:0:$(( $(gt_wt_width) - 6 ))}
      printf 'XXX\n%d\n%s (%s min)\n\n%s\nXXX\n' "$percent" "$(gt_gauge_label "$step")" "$minutes" "$line"
    fi
    sleep 1
  done
}

gt_gauge_start() {
  [[ "$FRONTEND" == whiptail && -z "$GAUGE_PID" ]] || return 0
  case "$MODE" in
    --bootstrap) GAUGE_PHASES='core app web mail' ;;
    --install-core) GAUGE_PHASES=core ;;
    *) return 0 ;;
  esac
  gt_tty_open || return 2
  GAUGE_STATE="$SCRATCH/gauge-state" GAUGE_RUN="$SCRATCH/gauge-run" EXEC_LOG="$SCRATCH/execution.log"
  : > "$GAUGE_STATE"; : > "$GAUGE_RUN"; : > "$EXEC_LOG"
  gt_gauge_feed | whiptail --backtitle "$(gt_text 'Grafioschtrader installation' 'Grafioschtrader-Installation')" \
    --title "$(gt_text 'Installing' 'Installation läuft')" --gauge -- '' 10 "$(gt_wt_width)" 0 \
    >&"$QUESTION_FD" 2>&1 &
  GAUGE_PID=$!
  # Step output would draw over the gauge; it goes to the execution log and is printed when the gauge ends.
  exec {OUT_SAVE}>&1 {ERR_SAVE}>&2
  exec >> "$EXEC_LOG" 2>&1
}

gt_gauge_stop() {
  [[ -n "$GAUGE_PID" ]] || return 0
  exec 1>&"$OUT_SAVE" 2>&"$ERR_SAVE" {OUT_SAVE}>&- {ERR_SAVE}>&-
  rm -f -- "$GAUGE_RUN"
  wait "$GAUGE_PID" 2>/dev/null || :
  GAUGE_PID=''
  cat -- "$EXEC_LOG" >> "$TRANSCRIPT"
}

# Ends the dialogs of a run: the execution result as a text box, then everything shown in dialogs is printed to the
# terminal so it stays in the scrollback.
gt_frontend_finish() {
  local file
  [[ "$FRONTEND" == whiptail ]] || return 0
  gt_gauge_stop
  if [[ -s "${EXEC_LOG:-}" ]]; then
    file=$EXEC_LOG
    [[ ! -s "$SCRATCH/result.txt" ]] || file="$SCRATCH/result.txt"
    gt_wt --title "$(gt_text 'Result' 'Ergebnis')" --scrolltext --ok-button OK \
      --textbox -- "$file" "$(gt_wt_height)" "$(gt_wt_width)" >/dev/null || :
  fi
}

gt_transcript_print() {
  [[ "$FRONTEND" == whiptail && -s "${TRANSCRIPT:-}" ]] || return 0
  printf '\n'
  cat -- "$TRANSCRIPT"
}
