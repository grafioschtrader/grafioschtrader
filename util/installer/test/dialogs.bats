#!/usr/bin/env bats
load check-helpers

# The whiptail front end against a scripted whiptail; the plain front end through a file instead of a terminal.
setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  collect_check
  gt_source_revision
  FACT[source.requirements]=remote FACT[source.commit]=0000000000000000000000000000000000000001
  FACT[source.collation]=yes FACT[apt.age_hours]=0
  FACT[java.suitable]=/opt/jdk-25 FACT[java.version]=25
  FACT[maven.version]=3.9.9 FACT[maven.path]=/opt/maven/bin/mvn
  FACT[node.version]=24.15.0 FACT[node.path]=/usr/bin/node
  FACT[network.public_ipv4]=198.51.100.12 FACT[network.global_ipv6]='' FACT[network.lan_ipv4]=192.168.1.2
  gt_compatibility
  gt_question_model
  mkdir -p "$ROOT/usr/share/zoneinfo/Europe" "$BATS_TEST_TMPDIR/bin"
  : > "$ROOT/usr/share/zoneinfo/Europe/Zurich"
  cp "$BATS_TEST_DIRNAME/whiptail-stub.sh" "$BATS_TEST_TMPDIR/bin/whiptail"
  chmod +x "$BATS_TEST_TMPDIR/bin/whiptail"
  export PATH="$BATS_TEST_TMPDIR/bin:$PATH"
  export WHIPTAIL_SCRIPT="$BATS_TEST_TMPDIR/script" WHIPTAIL_LOG="$BATS_TEST_TMPDIR/log"
  : > "$WHIPTAIL_SCRIPT"; : > "$WHIPTAIL_LOG"
  exec {QUESTION_FD}<>"$BATS_TEST_TMPDIR/terminal"
  QUESTION_OUTPUT=$QUESTION_FD
  TERM_ROWS=40 TERM_COLS=120 MODE=--bootstrap OPENSSL_REAL=yes
}

script() { printf '%s\n' "$@" > "$WHIPTAIL_SCRIPT"; }
titles() { cut -d '|' -f 1 "$WHIPTAIL_LOG"; }

whiptail_answers() {
  FRONTEND=whiptail
  script $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno'
  gt_questions
}

# The plain prompts read one line per applicable question in model order; Enter takes the default.
plain_answers() {
  local key input=''
  FRONTEND=plain ANSWER=()
  for key in "${QUESTIONS[@]}"; do
    gt_question_applies "$key" && [[ "${Q_TYPE[$key]}" != secret ]] || continue
    if [[ "$key" == WEBSERVER && ( "${REASON[web]:-}" == nginx || "${REASON[web]:-}" == apache2 ) ]]; then
      ANSWER[$key]=${REASON[web]}; continue
    fi
    case "$key" in
      ADMIN_EMAIL) ANSWER[$key]=admin@example.org; input+=$'admin@example.org\n' ;;
      SMTP_CONFIGURE) ANSWER[$key]=no; input+=$'no\n' ;;
      *) ANSWER[$key]=$(gt_default "$key"); input+=$'\n' ;;
    esac
  done
  exec {QUESTION_FD}<<< "$input"
  QUESTION_OUTPUT=2
  gt_questions 2> /dev/null
}

@test "whiptail is selected only for a usable terminal of at least 80 x 24 cells" {
  gt_terminal_size() { TERM_ROWS=$ROWS TERM_COLS=$COLS; }
  export TERM=xterm
  ROWS=24 COLS=80; gt_frontend_select; [ "$FRONTEND" = whiptail ]
  ROWS=23 COLS=80; gt_frontend_select; [ "$FRONTEND" = plain ]
  ROWS=24 COLS=79; gt_frontend_select; [ "$FRONTEND" = plain ]
  ROWS=50 COLS=200
  PLAIN_FORCED=yes; gt_frontend_select; [ "$FRONTEND" = plain ]; PLAIN_FORCED=no
  CORE_CONFIRM=yes; gt_frontend_select; [ "$FRONTEND" = plain ]; CORE_CONFIRM=no
  TERM=dumb; gt_frontend_select; [ "$FRONTEND" = plain ]; TERM=xterm
  MODE=--check; gt_frontend_select; [ "$FRONTEND" = plain ]; MODE=--bootstrap
  gt_terminal_size() { return 1; }
  gt_frontend_select; [ "$FRONTEND" = plain ]
  gt_terminal_size() { TERM_ROWS=50 TERM_COLS=200; }
  PATH="$BATS_TEST_TMPDIR/empty" gt_frontend_select; [ "$FRONTEND" = plain ]
  gt_frontend_select; [ "$FRONTEND" = whiptail ]
}

@test "dialog texts and buttons follow the language" {
  FRONTEND=whiptail LANG_CODE=de
  script $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno'
  gt_questions
  grep -q -- '--cancel-button Zurück' "$WHIPTAIL_LOG"
  grep -q 'E-Mail-Adresse, deren Registrierung Administrator wird' "$WHIPTAIL_LOG"
  grep -q 'Grafioschtrader-Installation' "$WHIPTAIL_LOG"
  : > "$WHIPTAIL_LOG"; LANG_CODE=en
  script $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno'
  gt_questions
  grep -q -- '--cancel-button Back' "$WHIPTAIL_LOG"
  grep -q 'Email whose registration becomes administrator' "$WHIPTAIL_LOG"
}

@test "Back returns to the previous question, and Back on the first one asks before aborting" {
  FRONTEND=whiptail
  script $'ADMIN_EMAIL\t1' $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno'
  gt_questions
  mapfile -t seen < <(titles)
  for ((i = 0; i < ${#seen[@]}; i++)); do [[ "${seen[i]}" != ADMIN_EMAIL ]] || break; done
  (( i > 0 ))
  [ "${seen[i+1]}" = "${seen[i-1]}" ]
  [ "${seen[i+2]}" = ADMIN_EMAIL ]
  [ "${ANSWER[ADMIN_EMAIL]}" = admin@example.org ]
  : > "$WHIPTAIL_LOG"
  script $'DOMAIN\t1' $'Abort\t1' $'DOMAIN\t1' $'Abort\t0'
  run gt_questions
  [ "$status" -eq 130 ]
  [ "$(titles | tr '\n' ' ')" = 'DOMAIN Abort DOMAIN Abort ' ]
}

@test "a rejected value reopens the same dialog with the validator's message" {
  FRONTEND=whiptail
  script $'BACKEND_PORT\t0\t99999' $'BACKEND_PORT\t0\t9091' $'ADMIN_EMAIL\t0\tadmin' \
    $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno'
  gt_questions
  [ "$(grep -c '^BACKEND_PORT|' "$WHIPTAIL_LOG")" -eq 2 ]
  grep '^BACKEND_PORT|' "$WHIPTAIL_LOG" | tail -n 1 | grep -q 'Invalid value: 99999'
  grep '^BACKEND_PORT|' "$WHIPTAIL_LOG" | tail -n 1 | grep -q 'Enter a port from 1 to 65535.'
  grep '^ADMIN_EMAIL|' "$WHIPTAIL_LOG" | tail -n 1 | grep -q 'Enter an e-mail address'
  [ "${ANSWER[BACKEND_PORT]}" = 9091 ]
}

@test "a changed answer discards answers and secrets whose conditions no longer hold and recomputes defaults" {
  FRONTEND=whiptail
  ANSWER=([DOMAIN]=gt.duckdns.org [DNS_FAMILY]=ipv4 [DUCKDNS_UPDATER]=yes [TLS_SOURCE]=letsencrypt
    [LETSENCRYPT_EMAIL]=admin@example.org [SMTP_CONFIGURE]=yes [SMTP_PORT]=587 [SMTP_SECURITY]=starttls)
  ANSWER_DEFAULTED=([SMTP_SECURITY]=yes)
  SECRET[DUCKDNS_TOKEN]=00000000-0000-0000-0000-000000000000 SECRET_STATUS[DUCKDNS_TOKEN]=collected
  script $'DOMAIN\t0\t' $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_HOST\t0\tsmtp.example.org' $'SMTP_PORT\t0\t465' \
    $'SMTP_USER\t0\tmail@example.org'
  gt_wt_questions
  [ "${ANSWER[DOMAIN]}" = '' ]
  [ -z "${ANSWER[TLS_SOURCE]+set}" ] && [ -z "${ANSWER[DNS_FAMILY]+set}" ] && [ -z "${ANSWER[LETSENCRYPT_EMAIL]+set}" ]
  [ -z "${ANSWER[DUCKDNS_UPDATER]+set}" ] && [ -z "${SECRET[DUCKDNS_TOKEN]+set}" ]
  # SMTP_SECURITY had been the default for port 587; port 465 proposes TLS instead.
  grep '^SMTP_SECURITY|' "$WHIPTAIL_LOG" | grep -q -- '--default-item tls'
  [ "${ANSWER[SMTP_SECURITY]}" = tls ]
  ! grep -q '^DNS checklist|' "$WHIPTAIL_LOG"
}

@test "a domain shows the DNS checklist with Continue and Stop here" {
  FRONTEND=whiptail
  script $'DOMAIN\t0\texample.org' $'TLS_SOURCE\t0\tletsencrypt' $'DNS checklist\t1'
  run gt_questions
  [ "$status" -eq 130 ]
  grep '^DNS checklist|' "$WHIPTAIL_LOG" | grep -q -- '--yes-button Continue --no-button Stop here'
  grep '^DNS checklist|' "$WHIPTAIL_LOG" | grep -q 'Domain checklist'
}

@test "new passwords are confirmed in a second dialog, existing credentials are entered once" {
  FRONTEND=whiptail
  script $'JASYPT_PASSWORD\t0\tenter' $'JASYPT_PASSWORD\t0\tfirst-value' $'JASYPT_PASSWORD\t0\tother-value' \
    $'JASYPT_PASSWORD\t0\tsame-value' $'JASYPT_PASSWORD\t0\tsame-value'
  gt_wt_secret JASYPT_PASSWORD yes
  [ "${SECRET[JASYPT_PASSWORD]}" = same-value ]
  grep '^JASYPT_PASSWORD|' "$WHIPTAIL_LOG" | sed -n 4p | grep -q 'The two entries differ.'
  : > "$WHIPTAIL_LOG"
  script $'SMTP_PASSWORD\t0\texisting-value'
  gt_wt_secret SMTP_PASSWORD no
  [ "${SECRET[SMTP_PASSWORD]}" = existing-value ]
  [ "$(wc -l < "$WHIPTAIL_LOG")" -eq 1 ]
  grep -q -- '--passwordbox' "$WHIPTAIL_LOG"
  ! grep -Eq 'first-value|other-value|same-value|existing-value' "$WHIPTAIL_LOG"
}

@test "Back from the first credential returns to the last question" {
  FRONTEND=whiptail MODE=--prepare
  FACT[database.vendor]=mariadb FACT[database.gt_user]=absent
  script $'ADMIN_EMAIL\t0\tadmin@example.org' $'SMTP_CONFIGURE\t0\tno' $'DB_PASSWORD\t1' \
    $'DB_PASSWORD\t0\tgenerate' $'JASYPT_PASSWORD\t0\tgenerate'
  gt_interactive_answers
  mapfile -t seen < <(titles)
  for ((i = 0; i < ${#seen[@]}; i++)); do [[ "${seen[i]}" != DB_PASSWORD ]] || break; done
  [ "${seen[i-1]}" != DB_PASSWORD ]
  [ "${seen[i+1]}" = "${seen[i-1]}" ]
  [ "${seen[i+2]}" = DB_PASSWORD ]
  [ "${SECRET_STATUS[DB_PASSWORD]}" = generated ] && [ "${SECRET_STATUS[JASYPT_PASSWORD]}" = generated ]
}

@test "generation is an explicit choice for new passwords only, shown once and never planned" {
  FRONTEND=whiptail
  script $'DB_PASSWORD\t0\tgenerate'
  gt_wt_secret DB_PASSWORD yes
  [[ "${SECRET[DB_PASSWORD]}" =~ ^[a-zA-Z0-9]{24}$ ]]
  [ "${SECRET_STATUS[DB_PASSWORD]}" = generated ]
  ! grep -Fq "${SECRET[DB_PASSWORD]}" "$WHIPTAIL_LOG"
  [ -z "$(find "$SCRATCH" -name 'generated.*')" ]
  : > "$WHIPTAIL_LOG"
  script $'SMTP_PASSWORD\t0\tsmtp-value'
  gt_wt_secret SMTP_PASSWORD no
  ! grep -q -- '--menu' "$WHIPTAIL_LOG"
  # Plain prompts: the request must be typed; Enter alone never generates.
  FRONTEND=plain
  printf '\n!generate\n\n' > "$SCRATCH/input"
  exec {QUESTION_FD}<"$SCRATCH/input"
  QUESTION_OUTPUT=3
  gt_ask_secret JASYPT_PASSWORD yes 3> "$SCRATCH/out"
  [[ "${SECRET[JASYPT_PASSWORD]}" =~ ^[a-zA-Z0-9]{24}$ ]]
  [ "$(grep -c -F "${SECRET[JASYPT_PASSWORD]}" "$SCRATCH/out")" -eq 1 ]
  grep -q 'Empty, invalid or mismatched input' "$SCRATCH/out"
  printf '!generate\n' > "$SCRATCH/input"
  exec {QUESTION_FD}<"$SCRATCH/input"
  gt_ask_secret SMTP_PASSWORD 3> "$SCRATCH/out"
  [ "${SECRET[SMTP_PASSWORD]}" = '!generate' ]
  MODE=--dry-run; defaults_for_report
  ! gt_plan_report | grep -Fq "${SECRET[JASYPT_PASSWORD]}"
  gt_plan_report | grep -q 'JASYPT_PASSWORD | .* | generated'
}

defaults_for_report() {
  local key
  for key in "${QUESTIONS[@]}"; do
    gt_question_applies "$key" && [[ "${Q_TYPE[$key]}" != secret ]] || continue
    ANSWER[$key]=$(gt_default "$key")
  done
}

@test "the same answers produce the same plan in both front ends" {
  MODE=--dry-run
  whiptail_answers
  declare -A dialog_answers=()
  for key in "${!ANSWER[@]}"; do dialog_answers[$key]=${ANSWER[$key]}; done
  gt_plan || :
  dialog_plan=$(printf '%s\n' "${PLAN[@]}" "${PLAN_BLOCKERS[@]}" "${PLAN_WARNINGS[@]}")
  plain_answers
  [ "${#ANSWER[@]}" -eq "${#dialog_answers[@]}" ]
  for key in "${!ANSWER[@]}"; do [ "${ANSWER[$key]}" = "${dialog_answers[$key]}" ]; done
  gt_plan || :
  plain_plan=$(printf '%s\n' "${PLAN[@]}" "${PLAN_BLOCKERS[@]}" "${PLAN_WARNINGS[@]}")
  [ -n "$plain_plan" ]
  [ "$dialog_plan" = "$plain_plan" ]
}

@test "plain progress lines are unchanged and the gauge needs whiptail" {
  FRONTEND=plain
  run gt_progress core clone 'Core step: clone'
  [ "$output" = 'Core step: clone' ]
  gt_gauge_start
  [ -z "$GAUGE_PID" ]
  printf '[INFO] Building grafioschtrader-common 0.38.1 [3/5]\n' > "$SCRATCH/build.log"
  [ "$(gt_build_fraction "$SCRATCH/build.log")" -eq 67 ]
  printf 'BUILD SUCCESS\n' >> "$SCRATCH/build.log"
  [ "$(gt_build_fraction "$SCRATCH/build.log")" -eq 100 ]
}
