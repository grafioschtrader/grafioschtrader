#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  collect_check
  gt_question_model
  MODE=--prepare OPENSSL_REAL=yes
  ANSWER[SMTP_CONFIGURE]=no
}

input() { exec {QUESTION_FD}<<<"$1"; }

file_fixture() {
  (( EUID == 0 )) || skip 'root-owned answers files are tested in the root suite'
  ANSWERS_FILE="$SCRATCH/answers"
  printf '%s\n' 'ADMIN_EMAIL=admin@example.org' 'SMTP_CONFIGURE=no' 'TIMEZONE=UTC' \
    'DB_ROOT_PASSWORD=root-test-only' 'DB_PASSWORD=database-test-only' 'JASYPT_PASSWORD=encryption-test-only' > "$ANSWERS_FILE"
  chmod 600 "$ANSWERS_FILE"
}

@test "new MariaDB root always needs a confirmed password, existing successful inventory does not" {
  FACT[database.vendor]=absent FACT[database.root_socket]=1
  gt_question_applies DB_ROOT_PASSWORD
  input $'new-root\nnew-root'
  gt_prepare_root
  [ "${SECRET[DB_ROOT_PASSWORD]}" = new-root ]
  FACT[database.vendor]=mariadb FACT[database.query]=ok FACT[database.root_socket]=0
  FACT[database.gt_user]=absent FACT[database.schemas]=''
  ! gt_question_applies DB_ROOT_PASSWORD
  FACT[database.query]=unknown
  gt_question_applies DB_ROOT_PASSWORD
}

@test "secret entry preserves whitespace quotes backslashes substitutions and equals literally" {
  local literal='  $HOME $(exit 73) `uname` \ " '\'' = # ; end  '
  input "$literal"$'\n'"$literal"
  gt_ask_secret DB_PASSWORD yes > "$SCRATCH/out" 2>&1
  [ "${SECRET[DB_PASSWORD]}" = "$literal" ]
  ! grep -Fq -- "$literal" "$SCRATCH/out"
  [ -z "${ANSWER[DB_PASSWORD]:-}" ]
}

@test "new passwords retry empty or mismatched confirmation, current passwords are asked once" {
  input $'\nfirst\nwrong\ncorrect\ncorrect'
  gt_ask_secret JASYPT_PASSWORD yes > "$SCRATCH/out" 2>&1
  [ "${SECRET[JASYPT_PASSWORD]}" = correct ]
  grep -q 'mismatched' "$SCRATCH/out"
  input 'existing-password'
  gt_ask_secret SMTP_PASSWORD
  [ "${SECRET[SMTP_PASSWORD]}" = existing-password ]
}

@test "NUL CR and controls in secret input are rejected instead of silently discarded" {
  for byte in '\0' '\r' '\t' '\033' '\177'; do
    printf 'left%bmiddle\nvalid-secret\n' "$byte" > "$SCRATCH/input"
    exec {QUESTION_FD}<"$SCRATCH/input"
    gt_ask_secret SMTP_PASSWORD > "$SCRATCH/out" 2>&1
    [ "${SECRET[SMTP_PASSWORD]}" = valid-secret ]
    grep -q 'invalid' "$SCRATCH/out"
  done
}

@test "a DuckDNS token must be a UUID at the prompt, in an answers file and in the secret store" {
  local token=0123abcd-4567-89ab-cdef-0123456789ab
  input $'not-a-token\n0123ABCD-4567-89AB-CDEF-0123456789AB\n'"$token"
  gt_ask_secret DUCKDNS_TOKEN > "$SCRATCH/out" 2>&1
  [ "${SECRET[DUCKDNS_TOKEN]}" = "$token" ]
  [ "$(grep -c 'try again' "$SCRATCH/out")" -eq 2 ]
  run grep -q "$token" "$SCRATCH/out"
  [ "$status" -ne 0 ]
  gt_secret_valid_for DUCKDNS_TOKEN "$token"
  run gt_secret_valid_for DUCKDNS_TOKEN "$token&ip=1"
  [ "$status" -ne 0 ]
  gt_secret_valid_for SMTP_PASSWORD 'any & thing'
}

@test "EOF cancels without retaining a partial secret" {
  printf partial > "$SCRATCH/input"
  exec {QUESTION_FD}<"$SCRATCH/input"
  run gt_ask_secret DB_PASSWORD yes
  [ "$status" -eq 130 ]
  [[ "$output" != *partial* ]]
}

@test "SMTP needs an explicit skip or host sender authentication and security choices" {
  [ "$(gt_default SMTP_CONFIGURE)" = yes ]
  ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_PORT]=587
  gt_question_applies SMTP_PASSWORD
  [ "$(gt_default SMTP_SECURITY)" = starttls ]
  ! gt_validate_answer SMTP_HOST ''
  ! gt_validate_answer SMTP_USER login-without-email
  gt_validate_answer SMTP_USER sender@example.org
  ANSWER[SMTP_AUTH]=no
  ! gt_question_applies SMTP_PASSWORD
  gt_question_applies SMTP_USER
  ANSWER[SMTP_PORT]=465
  [ "$(gt_default SMTP_SECURITY)" = tls ]
  ANSWER[SMTP_PORT]=2525
  [ -z "$(gt_default SMTP_SECURITY)" ]
}

@test "DuckDNS token is required only for a local updater" {
  ANSWER[DOMAIN]=example.duckdns.org ANSWER[DUCKDNS_UPDATER]=no
  ! gt_question_applies DUCKDNS_TOKEN
  ANSWER[DUCKDNS_UPDATER]=yes
  gt_question_applies DUCKDNS_TOKEN
}

@test "answers files are data and preserve all characters after the first equals" {
  file_fixture
  local literal='  " $HOME $(exit 73) `uname` \ = # ;  '
  printf 'SMTP_CONFIGURE=no\nDB_PASSWORD=%s\n' "$literal" > "$ANSWERS_FILE"
  gt_answers_file "$ANSWERS_FILE"
  [ "${FILE_ANSWERS[DB_PASSWORD]}" = "$literal" ]
  gt_collect_secret DB_PASSWORD yes
  [ "${SECRET[DB_PASSWORD]}" = "$literal" ]
}

@test "answers parser rejects unknown duplicate malformed control and NUL input without printing values" {
  file_fixture
  for content in $'UNKNOWN=TOP_SECRET' $'DB_PASSWORD=TOP_SECRET\nDB_PASSWORD=again' \
      'TOP_SECRET' $' DB_PASSWORD=TOP_SECRET' $'DB_PASSWORD=TOP_SECRET\r' 'JWT_SECRET=TOP_SECRET'; do
    printf '%s\n' "$content" > "$ANSWERS_FILE"
    run gt_answers_file "$ANSWERS_FILE"
    [ "$status" -eq 2 ]
    [[ "$output" != *TOP_SECRET* ]]
  done
  printf 'DB_PASSWORD=TOP\0SECRET\n' > "$ANSWERS_FILE"
  run gt_answers_file "$ANSWERS_FILE"
  [ "$status" -eq 2 ]
  [[ "$output" != *SECRET* ]]
}

@test "answers file must be a root-owned mode 600 regular file, not a symlink" {
  local file="$SCRATCH/options"
  echo DB_PASSWORD=TOP_SECRET > "$file"
  chmod 644 "$file"
  run gt_answers_file "$file"
  [ "$status" -eq 2 ]
  chmod 600 "$file"
  ln -s "$file" "$SCRATCH/link"
  run gt_answers_file "$SCRATCH/link"
  [ "$status" -eq 2 ]
  if (( EUID != 0 )); then
    run gt_answers_file "$file"
    [ "$status" -eq 2 ]
  fi
}

@test "unattended fresh preparation needs every secret and never silently generates passwords" {
  file_fixture
  gt_answers_file "$ANSWERS_FILE"
  gt_prepare_root
  gt_file_questions
  gt_prepare_secrets
  [ "${SECRET[DB_PASSWORD]}" = database-test-only ]
  [ "${SECRET[JASYPT_PASSWORD]}" = encryption-test-only ]
  [ "${#SECRET[JWT_SECRET]}" -eq 48 ]
  local jwt=${SECRET[JWT_SECRET]}
  gt_prepare_secrets
  [ "${SECRET[JWT_SECRET]}" = "$jwt" ]
  unset 'FILE_ANSWERS[DB_PASSWORD]'
  run gt_prepare_secrets
  [ "$status" -eq 2 ]
}

@test "unattended questions reject inactive secrets and missing mandatory SMTP details" {
  file_fixture
  gt_answers_file "$ANSWERS_FILE"
  FILE_ANSWERS[DUCKDNS_TOKEN]=TOP_SECRET
  run gt_file_questions
  [ "$status" -eq 2 ]
  [[ "$output" != *TOP_SECRET* ]]
  unset 'FILE_ANSWERS[DUCKDNS_TOKEN]'
  FILE_ANSWERS[SMTP_CONFIGURE]=yes
  run gt_file_questions
  [ "$status" -eq 2 ]
  [[ "$output" == *SMTP_HOST* ]]
}

@test "password options file round-trips through the actual MariaDB option parser" {
  command -v mariadb >/dev/null || skip 'install mariadb-client for option parser integration'
  local file decoded literal='  a\\b\n$HOME"'\''= ;#end  '
  SECRET[DB_PASSWORD]=$literal
  file=$(gt_db_options DB_PASSWORD grafioschtrader)
  [ "$(stat -c %a "$file")" = 600 ]
  decoded=$(mariadb "--defaults-file=$file" --print-defaults)
  [[ "$decoded" == *"--password=$literal"* ]]
  rm -f "$file"
}

@test "root credentials refresh failed metadata using temporary options, never password arguments" {
  fake_executable mariadb
  PACKAGE[mariadb-server]=11.8 SERVICE_ACTIVE=yes SQL_PASSWORD_ONLY=yes
  gt_database
  [ "${FACT[database.query]}" = unknown ]
  input 'root-test-credential'
  gt_prepare_root
  [ "${FACT[database.query]}" = ok ]
  [ "${FACT[database.gt_tables]}" = 0 ]
  [ "${SECRET_STATUS[DB_ROOT_PASSWORD]}" = verified ]
  ! grep -q root-test-credential "$PROBES"
  [ -z "$(find "$SCRATCH" -name 'db.*' -print -quit)" ]
}

@test "failed or inactive root login does not assume the application account is absent" {
  fake_executable mariadb
  PACKAGE[mariadb-server]=11.8 SERVICE_ACTIVE=yes SQL_FAIL=yes
  gt_database
  input 'incorrect-root'
  run gt_prepare_root
  [ "$status" -eq 2 ]
  [[ "$output" != *incorrect-root* ]]
  [ "${FACT[database.gt_user]}" = unknown ]
  [ -z "$(find "$SCRATCH" -name 'db.*' -print -quit)" ]
  SERVICE_ACTIVE=no
  gt_database
  : > "$PROBES"
  run gt_prepare_root
  [ "$status" -eq 2 ]
  [ ! -s "$PROBES" ]
}

@test "existing application credentials use TCP and require the exact authenticated account" {
  fake_executable mariadb
  PACKAGE[mariadb-server]=11.8 SERVICE_ACTIVE=yes
  gt_database
  input $'db-existing\nencryption-new\nencryption-new'
  gt_prepare_secrets
  [ "${FACT[database.gt_auth]}" = ok ]
  grep -q -- '--protocol=TCP --host=127.0.0.1' "$PROBES"
  ! grep -qE 'db-existing|encryption-new' "$PROBES"
  [ -z "$(find "$SCRATCH" -name 'db.*' -print -quit)" ]
  SQL_GT_LOGIN=anonymous@localhost
  input $'db-existing\nencryption-new\nencryption-new'
  run gt_prepare_secrets
  [ "$status" -eq 2 ]
}

@test "report lists secret purposes and statuses without values or generation in dry-run" {
  FACT[host.class]=fresh
  SECRET[DB_PASSWORD]=TOP_SECRET SECRET[JWT_SECRET]=HIDDEN_JWT
  SECRET_STATUS[DB_PASSWORD]=collected
  run gt_plan_report
  [ "$status" -eq 0 ]
  [[ "$output" == *DB_ROOT_PASSWORD* && "$output" == *spring.datasource.password* && "$output" == *JASYPT_ENCRYPTOR_PASSWORD* ]]
  [[ "$output" != *TOP_SECRET* && "$output" != *HIDDEN_JWT* ]]
  ! grep -q 'openssl rand' "$PROBES"
}

@test "cleanup removes all temporary credentials and clears secret stores" {
  SECRET[DB_PASSWORD]=TOP_SECRET FILE_ANSWERS[SMTP_PASSWORD]=OTHER_SECRET SECRET_INPUT=PARTIAL
  local file
  file=$(gt_db_options DB_PASSWORD grafioschtrader)
  gt_cleanup
  [ ! -e "$file" ]
  [ "${#SECRET[@]}" -eq 0 ]
  [ "${#FILE_ANSWERS[@]}" -eq 0 ]
  [ -z "$SECRET_INPUT" ]
}

@test "check and dry-run refuse answer files and contradictory preparation modes" {
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --prepare --answers ''
  [ "$status" -eq 2 ]
  for mode in --check --dry-run; do
    run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" "$mode" --answers /nonexistent
    [ "$status" -eq 2 ]
    run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" "$mode" --prepare
    [ "$status" -eq 2 ]
  done
}
