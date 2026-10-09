#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/root" "$ROOT/var/lib"
  FACT[source.commit]=0000000000000000000000000000000000000001
  FACT[java.suitable]=/opt/jdk-25 FACT[maven.path]=/usr/bin/mvn
  FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent
  ANSWER[ADMIN_EMAIL]=admin@example.org ANSWER[SMTP_CONFIGURE]=no ANSWER[DOMAIN]=''
  SECRET[DB_ROOT_PASSWORD]='root-fixture-only'
  SECRET[DB_PASSWORD]=' db-fixture " '\'' \ $HOME = '
  SECRET[JASYPT_PASSWORD]='key-fixture-only'
  SECRET[JWT_SECRET]=012345678901234567890123456789012345678901234567
}

@test "atomic core journal and secret storage preserve originals and never persist database root credentials" {
  gt_core_begin
  local original=${SECRET[DB_PASSWORD]} jwt=${SECRET[JWT_SECRET]} contents
  [ "$(stat -c %a "$ROOT/var/lib/gt-install")" = 700 ]
  [ "$(stat -c %a "$ROOT/var/lib/gt-install/state")" = 600 ]
  [ "$(stat -c %a "$ROOT/root/.gt-install/secrets")" = 600 ]
  contents=$(cat "$ROOT/var/lib/gt-install/state" "$ROOT/root/.gt-install/secrets")
  [[ "$contents" != *root-fixture-only* && "$contents" != *DB_ROOT_PASSWORD* ]]
  STATE=() ANSWER=() SECRET=()
  gt_state_load
  gt_secrets_load
  [ "${SECRET[DB_PASSWORD]}" = "$original" ]
  [ "${SECRET[JWT_SECRET]}" = "$jwt" ]
  [ -z "${SECRET[DB_ROOT_PASSWORD]:-}" ]
  [ "${ANSWER[ADMIN_EMAIL]}" = admin@example.org ]
  [ "${STATE[status]}" = running ]
}

@test "missing secrets block recovery without inventing replacement credentials" {
  gt_core_begin
  rm "$ROOT/root/.gt-install/secrets"
  SECRET=()
  run gt_secrets_load
  [ "$status" -ne 0 ]
  [ "${#SECRET[@]}" -eq 0 ]
  [ ! -e "$ROOT/root/.gt-install/secrets" ]
}

@test "secret store must belong to the same journal and excludes unknown or root-password keys" {
  gt_core_begin
  local original
  original=$(cat "$ROOT/root/.gt-install/secrets")
  for line in 'DB_ROOT_PASSWORD=forbidden' 'UNKNOWN=value' 'DB_PASSWORD=duplicate' 'JWT_SECRET=wrong'; do
    printf '%s\n%s\n' "$original" "$line" > "$ROOT/root/.gt-install/secrets"
    run gt_secrets_load
    [ "$status" -ne 0 ]
  done
  printf '%s\n' "$original" > "$ROOT/root/.gt-install/secrets"
  STATE[run_id]=00000000000000000000000000000000
  run gt_secrets_load
  [ "$status" -ne 0 ]
}

@test "an open SMTP change accepts the secrets file on either side of its password write" {
  local key
  gt_core_begin
  ANSWER+=([SMTP_CONFIGURE]=yes [SMTP_HOST]=mail.example.org [SMTP_PORT]=587 [SMTP_AUTH]=yes
    [SMTP_USER]=gt@example.org [SMTP_SECURITY]=starttls [SMTP_TEST]=yes)
  for key in "${!ANSWER[@]}"; do STATE[answer.$key]=${ANSWER[$key]}; done
  gt_state_save
  run gt_secrets_load
  [ "$status" -ne 0 ]
  STATE[step.mail_change]=intent
  gt_state_save
  STATE=() ANSWER=()
  gt_state_load
  gt_secrets_load
  [ -z "${SECRET[SMTP_PASSWORD]+set}" ]
  SECRET[SMTP_PASSWORD]=mail-fixture-only
  gt_secrets_save
  SECRET=()
  gt_secrets_load
  [ "${SECRET[SMTP_PASSWORD]}" = mail-fixture-only ]
}

@test "journal is never evaluated and rejects secret answers unknown keys NUL and invalid source identity" {
  gt_core_begin
  local original
  original=$(cat "$ROOT/var/lib/gt-install/state")
  for line in 'x=$(touch /tmp/not-executed)' 'answer.DB_PASSWORD=forbidden' 'schema=1' 'planned_commit=unknown'; do
    printf '%s\n%s\n' "$original" "$line" > "$ROOT/var/lib/gt-install/state"
    run gt_state_load
    [ "$status" -ne 0 ]
  done
  printf '%s\nextra=left\0right\n' "$original" > "$ROOT/var/lib/gt-install/state"
  run gt_state_load
  [ "$status" -ne 0 ]
}

@test "journal and secret readers reject symlinks and permissive files or directories" {
  gt_core_begin
  chmod 644 "$ROOT/var/lib/gt-install/state"
  run gt_state_load
  [ "$status" -ne 0 ]
  chmod 600 "$ROOT/var/lib/gt-install/state"
  chmod 755 "$ROOT/root/.gt-install"
  run gt_secrets_load
  [ "$status" -ne 0 ]
  chmod 700 "$ROOT/root/.gt-install"
  mv "$ROOT/root/.gt-install/secrets" "$ROOT/root/original"
  ln -s "$ROOT/root/original" "$ROOT/root/.gt-install/secrets"
  run gt_secrets_load
  [ "$status" -ne 0 ]
}

@test "failed atomic rename preserves the previous store and cleans its temporary file" {
  gt_core_begin
  local original
  original=$(cat "$ROOT/root/.gt-install/secrets")
  SECRET[DB_PASSWORD]=different
  mv() { return 1; }
  run gt_secrets_save
  [ "$status" -ne 0 ]
  [ "$(cat "$ROOT/root/.gt-install/secrets")" = "$original" ]
  [ "$(find "$ROOT/root/.gt-install" -type f | wc -l)" -eq 1 ]
}

@test "failed durability flush preserves the previous secret store before publication" {
  gt_core_begin
  local original
  original=$(cat "$ROOT/root/.gt-install/secrets")
  SECRET[DB_PASSWORD]=different
  sync() { return 1; }
  run gt_secrets_save
  [ "$status" -ne 0 ]
  [ "$(cat "$ROOT/root/.gt-install/secrets")" = "$original" ]
  [ "$(find "$ROOT/root/.gt-install" -type f | wc -l)" -eq 1 ]
}

@test "properties renderer preserves comments order unrelated cron keys and literal escaping" {
  printf '# heading\nmail.password = ENC(old)\ncron=*/5 * * * *\n' > "$SCRATCH/template"
  PROPERTIES=([mail.password]='  a\b = # end  ')
  gt_properties_render "$SCRATCH/template" "$SCRATCH/output"
  [ "$(head -n 1 "$SCRATCH/output")" = '# heading' ]
  grep -Fxq 'mail.password=\ \ a\\b\ =\ #\ end\ \ ' "$SCRATCH/output"
  grep -Fxq 'cron=*/5 * * * *' "$SCRATCH/output"
}

@test "properties renderer blocks missing duplicated and developer-controlled template keys" {
  PROPERTIES=([mail.password]=replacement)
  for content in 'other=1' '!mail.password=old' $'mail.password=one\nmail.password=two'; do
    printf '%s\n' "$content" > "$SCRATCH/template"
    run gt_properties_render "$SCRATCH/template" "$SCRATCH/output"
    [ "$status" -ne 0 ]
  done
  : > "$SCRATCH/template"
  gt_properties_render "$SCRATCH/template" "$SCRATCH/output" yes
  grep -Fxq 'mail.password=replacement' "$SCRATCH/output"
}

@test "SQL quoting preserves backslashes and doubles quotes for NO_BACKSLASH_ESCAPES sessions" {
  [ "$(gt_sql_literal "a'b\\c")" = "'a''b\\c'" ]
}

@test "read-only and preparation modes reject installation confirmation flags before inventory" {
  for mode in --check --dry-run --prepare; do
    run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" "$mode" --yes
    [ "$status" -eq 2 ]
  done
}

@test "publication journals unchanged template content and refuses unowned matching files" {
  gt_core_begin
  printf 'unchanged\n' > "$SCRATCH/input"
  cp "$SCRATCH/input" "$SCRATCH/target"
  local original
  original=$(sha256sum "$SCRATCH/input"); original=${original%% *}
  chown() { :; }
  gt_core_publish template "$SCRATCH/input" "$SCRATCH/target" 600 "$original"
  [ "${STATE[file.template]}" = "$original" ]
  [ "$(command stat -c %a "$SCRATCH/target")" = 600 ]
  cp "$SCRATCH/input" "$SCRATCH/unowned"
  run gt_core_publish unowned "$SCRATCH/input" "$SCRATCH/unowned" 600
  [ "$status" -ne 0 ]
  [ -z "${STATE[file.unowned]:-}" ]
}

@test "publication resumes either side of an interrupted rename but rejects drift" {
  gt_core_begin
  printf 'old\n' > "$SCRATCH/target"
  printf 'new\n' > "$SCRATCH/input"
  local old new
  old=$(sha256sum "$SCRATCH/target"); old=${old%% *}
  new=$(sha256sum "$SCRATCH/input"); new=${new%% *}
  STATE[file.pending]=$new STATE[file.pending.previous]=$old
  chown() { :; }
  gt_core_publish pending "$SCRATCH/input" "$SCRATCH/target" 600
  cmp -s "$SCRATCH/input" "$SCRATCH/target"
  printf 'external change\n' > "$SCRATCH/target"
  run gt_core_publish pending "$SCRATCH/input" "$SCRATCH/target" 600
  [ "$status" -ne 0 ]
  [ "$(cat "$SCRATCH/target")" = 'external change' ]
}

core_boundary_fixture() {
  MODE=--install-core CORE_CONFIRM=yes ANSWERS_FILE=fixture INVENTORIES=0 CHANGE_INVENTORY=no
  gt_inventory() {
    INVENTORIES=$((INVENTORIES+1))
    FACT[host.class]=fresh FACT[database.vendor]=absent
    [[ "$CHANGE_INVENTORY" != yes || "$INVENTORIES" != 2 ]] || PACKAGE[foreign-service]=new-version
    return 0
  }
  gt_compatibility() { :; }
  gt_report() { :; }
  gt_answers_file() { :; }
  gt_prepare_root() { :; }
  gt_file_questions() { :; }
  gt_prepare_secrets() { :; }
  gt_core_plan() { FACT[core.plan]=fixed; }
  gt_build_record() { :; }
  gt_plan_report() { :; }
  gt_core_execute() { touch "$ROOT/executed"; return 10; }
}

@test "execution boundary blocks a changed package inventory before writing state or secrets" {
  core_boundary_fixture
  CHANGE_INVENTORY=yes
  run gt_install_core
  [ "$status" -eq 2 ]
  [[ "$output" == *'Inventory changed'* ]]
  [ ! -e "$ROOT/executed" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
  [ ! -e "$ROOT/root/.gt-install/secrets" ]
}

@test "core stops before saving immutable answers when DNS cannot be verified" {
  core_boundary_fixture
  gt_prepare_dns() { return 2; }
  run gt_install_core
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/executed" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
  [ ! -e "$ROOT/root/.gt-install/secrets" ]
}

@test "a changed DNS result after core confirmation cannot reach execution" {
  core_boundary_fixture
  gt_prepare_dns() { :; }
  gt_verify_dns() { return 2; }
  run gt_install_core
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/executed" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
}

@test "confirmed unchanged scope writes protected state before entering execution and returns incomplete" {
  core_boundary_fixture
  gt_core_execute() {
    [[ -f "$ROOT/var/lib/gt-install/state" && -f "$ROOT/root/.gt-install/secrets" ]] || return 99
    touch "$ROOT/executed"; return 10
  }
  run gt_install_core
  [ "$status" -eq 10 ]
  [ -e "$ROOT/executed" ]
}

@test "another held core lock prevents state creation and execution" {
  core_boundary_fixture
  local holder
  mkdir -m 700 "$ROOT/var/lib/gt-install"
  exec {holder}>"$ROOT/var/lib/gt-install/lock"
  flock -n "$holder"
  run gt_install_core
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/executed" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
}

@test "commands of the service user run in its home, never in the caller's private working directory" {
  local private="$BATS_TEST_TMPDIR/administrator-home"
  mkdir -p "$private" "$BATS_TEST_TMPDIR/service-home"
  runuser() { pwd; }
  cd "$private"
  CORE_HOME="$BATS_TEST_TMPDIR/service-home"
  [ "$(gt_as_app true)" = "$CORE_HOME" ]
  CORE_HOME="$BATS_TEST_TMPDIR/not-created-yet"
  [ "$(gt_as_app true)" = / ]
  [ "$PWD" = "$private" ]
}

@test "commands of the service user run without the operator's locale" {
  runuser() { shift 3; "$@"; }
  CORE_HOME="$BATS_TEST_TMPDIR"
  LANGUAGE=de
  export LANGUAGE
  [ "$(gt_as_app bash -c 'printf %s "$LC_ALL:${LANGUAGE-unset}"')" = 'C.UTF-8:unset' ]
}
