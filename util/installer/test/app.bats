#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/var/log" "$ROOT/etc/systemd/system" "$ROOT/etc/sudoers.d" "$ROOT/etc/logrotate.d"
  chmod 700 "$ROOT/var/lib/gt-install"
  STATE[planned_commit]=0000000000000000000000000000000000000001
  ANSWER[BACKEND_PORT]=9090 ANSWER[BACKEND_HTTP_PORT]=8080 ANSWER[WEBSERVER]=nginx ANSWER[DOCROOT]=/var/www/gt
  gt_core_login() { return 0; }
}

@test "populated schema is refused before a journaled first start" {
  gt_app_sql() { echo 42; }
  run gt_app_database
  [ "$status" -eq 2 ]
  [[ "$output" == *'no longer empty'* ]]
}

@test "journaled first start resumes only with a readable successful Flyway history" {
  STATE[step.app_start]=intent
  gt_app_sql() {
    local query; read -r query
    if [[ "$query" == *information_schema* ]]; then echo 42; else echo 0; fi
  }
  gt_app_database
  gt_app_sql() { echo 42; }
  run gt_app_database
  [ "$status" -eq 2 ]
  gt_app_sql() { return 1; }
  run gt_app_database
  [ "$status" -ne 0 ]
}

@test "privileged publication refuses foreign files and links and resumes its own rename" {
  [ "$EUID" -eq 0 ] || skip 'Root file ownership requires root'
  printf 'unit fixture\n' > "$SCRATCH/input"
  local target="$ROOT/etc/systemd/system/fixture.service"
  cp "$SCRATCH/input" "$target"
  chmod 644 "$target"
  run gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$status" -ne 0 ]
  rm "$target"
  mv() { return 1; }
  run gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$status" -ne 0 ]
  unset -f mv
  STATE[file.fixture]=$(sha256sum "$SCRATCH/input"); STATE[file.fixture]=${STATE[file.fixture]%% *}
  gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$(stat -c '%u:%a' "$target")" = 0:644 ]
  gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  # A newer installer replaces its own unedited file, also when the replacement was interrupted after journaling.
  printf 'unit fixture, repaired\n' > "$SCRATCH/input"
  mv() { return 1; }
  if gt_app_root_file fixture "$SCRATCH/input" "$target" 644; then false; fi
  unset -f mv
  [ "$(cat "$target")" = 'unit fixture' ]
  [ "${STATE[file.fixture.previous]}" != absent ]
  gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$(cat "$target")" = 'unit fixture, repaired' ]
  echo drift >> "$target"
  run gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$status" -ne 0 ]
  rm "$target"
  ln -s "$SCRATCH/input" "$target"
  run gt_app_root_file fixture "$SCRATCH/input" "$target" 644
  [ "$status" -ne 0 ]
}

@test "Armbian's boot-time /var/log.hdd rewrite of the own logrotate file is neither drift nor reverted" {
  [ "$EUID" -eq 0 ] || skip 'Root file ownership requires root'
  local target="$ROOT/etc/logrotate.d/grafioschtrader"
  mkdir -p "${target%/*}"
  printf '/var/log/grafioschtrader.log {\n    weekly\n}\n' > "$SCRATCH/input"
  gt_app_root_file app_logrotate "$SCRATCH/input" "$target" 644
  sed -i 's#/var/log/#/var/log.hdd/#g' "$target"
  ANSWER[DOCROOT]=/var/www/gt STATE[step.app_start]=complete
  gt_app_targets
  gt_app_root_file app_logrotate "$SCRATCH/input" "$target" 644
  grep -q '^/var/log.hdd/grafioschtrader.log' "$target"
  # Only the Armbian path spelling is the installer's content; any other edit still blocks.
  sed -i 's/weekly/daily/' "$target"
  run gt_app_targets
  [ "$status" -eq 2 ]
  run gt_app_root_file app_logrotate "$SCRATCH/input" "$target" 644
  [ "$status" -ne 0 ]
}

@test "build failure cannot cross the first-start boundary" {
  gt_state_load() { :; }
  gt_secrets_load() { :; }
  gt_app_preflight() { :; }
  gt_app_scripts() { :; }
  gt_app_resources() { :; }
  gt_app_service() { :; }
  gt_app_cron() { :; }
  gt_app_build() { return 2; }
  gt_app_start() { touch "$ROOT/started"; }
  touch "$ROOT/var/lib/gt-install/lock" "$ROOT/var/lib/gt-install/state"
  CORE_CONFIRM=yes
  ANSWER[TIMEZONE]=UTC
  run gt_install_app
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/started" ]
}

@test "first-start intent is persisted before service start and failure never enables boot" {
  gt_app_database() { :; }
  gt_app_artifacts() { :; }
  gt_app_start_boundary() { :; }
  gt_app_start_log() { :; }
  gt_core_run() {
    [[ "$2" != show ]] || { printf '%032d\n' 1; return; }
    [[ "$*" == 'systemctl start grafioschtrader.service' ]] || return 99
    grep -qx step.app_start=intent "$ROOT/var/lib/gt-install/state" || return 99
    echo started >> "$ROOT/events"
  }
  gt_app_verify() { return 2; }
  run gt_app_start
  [ "$status" -eq 2 ]
  [ "$(cat "$ROOT/events")" = started ]
}

@test "successful verification precedes boot enablement and app step remains incomplete on enable failure" {
  gt_app_database() { :; }
  gt_app_artifacts() { :; }
  gt_app_start_boundary() { :; }
  gt_app_start_log() { :; }
  gt_app_verify() { touch "$ROOT/verified"; }
  gt_core_run() {
    [[ "$2" != enable ]] || { [ -e "$ROOT/verified" ]; return 1; }
  }
  run gt_app_start
  [ "$status" -eq 2 ]
  ! grep -qx step.app_start=complete "$ROOT/var/lib/gt-install/state"
}

@test "startup scanner ignores historical errors and redacts fresh diagnostic content" {
  printf 'FlywayException old password=old-secret\n' > "$ROOT/var/log/grafioschtrader.log"
  STATE[resource.app_log_cursor]=$(gt_app_start_log cursor)
  gt_app_start_log scan
  printf 'Access denied for user secret-user password=new-secret\n' >> "$ROOT/var/log/grafioschtrader.log"
  run gt_app_start_log scan
  [ "$status" -eq 10 ]
  [ "$output" = database-authentication ]
}

@test "startup scanner handles rotation truncation regrowth and split error tokens" {
  local marker
  for marker in FlywayException 'APPLICATION FAILED TO START'; do
    printf '%100s\n' old > "$ROOT/var/log/grafioschtrader.log"
    STATE[resource.app_log_cursor]=$(gt_app_start_log cursor)
    printf '%65530s%s\n' new "$marker" > "$ROOT/var/log/grafioschtrader.log"
    run gt_app_start_log scan
    [ "$status" -eq 10 ]
  done
  STATE[resource.app_log_cursor]=$(gt_app_start_log cursor)
  mv "$ROOT/var/log/grafioschtrader.log" "$ROOT/var/log/old.log"
  echo FlywayException > "$ROOT/var/log/grafioschtrader.log"
  run gt_app_start_log scan
  [ "$status" -eq 10 ]
  STATE[resource.app_log_cursor]=bad
  run gt_app_start_log scan
  [ "$status" -eq 2 ]
}

@test "startup boundary persists before start and resumes its invocation across interrupted publication" {
  echo historical > "$ROOT/var/log/grafioschtrader.log"
  TEST_ACTIVE=inactive TEST_INVOCATION=''
  gt_core_run() {
    case "$3" in
      --property=ActiveState) echo "$TEST_ACTIVE" ;;
      --property=InvocationID) echo "$TEST_INVOCATION" ;;
      *) return 99 ;;
    esac
  }
  gt_app_start_boundary
  [ "${STATE[resource.app_start_pending]}" = yes ]
  local saved=${STATE[resource.app_log_cursor]}
  grep -q '^resource.app_log_cursor=' "$ROOT/var/lib/gt-install/state"
  TEST_ACTIVE=active TEST_INVOCATION=00000000000000000000000000000001
  echo FlywayException >> "$ROOT/var/log/grafioschtrader.log"
  gt_app_start_boundary
  [ "${STATE[resource.app_log_cursor]}" = "$saved" ]
  STATE[resource.app_start_pending]=no STATE[resource.app_start_invocation]=$TEST_INVOCATION
  gt_app_start_boundary
  TEST_INVOCATION=00000000000000000000000000000002
  run gt_app_start_boundary
  [ "$status" -eq 2 ]
}

@test "fatal startup diagnostics stop before polling or boot enablement" {
  gt_app_database() { :; }
  gt_app_artifacts() { :; }
  gt_app_start_boundary() { :; }
  gt_app_start_log() { echo migration; return 10; }
  gt_app_verify() { echo unexpected-verification; return 99; }
  sleep() { echo unexpected-wait; return 99; }
  gt_core_run() {
    [[ "$2" != enable ]] || { echo unexpected-enable; return 99; }
    [[ "$2" != show ]] || printf '%032d\n' 1
    return 0
  }
  run gt_app_start
  [ "$status" -eq 2 ]
  [[ "$output" == *'Startup stopped (migration)'* ]]
  [[ "$output" != *unexpected* ]]
}

app_verify_fixture() {
  curl() { echo '{"databaseName":"grafioschtrader","activeProfile":"production"}'; }
  gt_core_run() { if [[ "$2" == show ]]; then echo 1234; fi; }
  ss() { echo 'LISTEN 0 100 127.0.0.1:9090 0.0.0.0:* users:(("java",pid=1234,fd=8))'; }
  gt_app_sql() {
    local query; read -r query
    if [[ "$query" == *success=1* ]]; then echo 10; else echo 0; fi
  }
  gt_app_database() { :; }
}

@test "health verifier rejects wrong database and unexpected wildcard or extra listeners" {
  app_verify_fixture
  gt_app_verify
  curl() { echo '{"databaseName":"grafioschtrader_t","activeProfile":"e2e"}'; }
  run gt_app_verify
  [ "$status" -ne 0 ]
  app_verify_fixture
  ss() { echo 'LISTEN 0 100 0.0.0.0:9090 0.0.0.0:* users:(("java",pid=1234,fd=8))'; }
  run gt_app_verify
  [ "$status" -eq 2 ]
  app_verify_fixture
  ss() {
    echo 'LISTEN 0 100 127.0.0.1:9090 0.0.0.0:* users:(("java",pid=1234,fd=8))'
    echo 'LISTEN 0 100 127.0.0.1:9999 0.0.0.0:* users:(("java",pid=1234,fd=9))'
  }
  run gt_app_verify
  [ "$status" -eq 2 ]
}

@test "health verifier rejects wrong collations and missing migrations" {
  app_verify_fixture
  gt_app_sql() { echo 1; }
  run gt_app_verify
  [ "$status" -eq 2 ]
  gt_app_sql() { echo 0; }
  run gt_app_verify
  [ "$status" -eq 2 ]
}

@test "health verifier accepts Java IPv4-mapped loopback but rejects mapped wildcard and other addresses" {
  app_verify_fixture
  ss() { echo 'LISTEN 0 100 [::ffff:127.0.0.1]:9090 *:* users:(("java",pid=1234,fd=8))'; }
  gt_app_verify
  ss() { echo 'LISTEN 0 100 [::ffff:7f00:1]:9090 *:* users:(("java",pid=1234,fd=8))'; }
  gt_app_verify
  ss() { echo 'LISTEN 0 100 [::ffff:0.0.0.0]:9090 *:* users:(("java",pid=1234,fd=8))'; }
  run gt_app_verify
  [ "$status" -eq 2 ]
  ss() { echo 'LISTEN 0 100 [::ffff:192.0.2.1]:9090 *:* users:(("java",pid=1234,fd=8))'; }
  run gt_app_verify
  [ "$status" -eq 2 ]
}

@test "app stage rejects answer overrides and does not weaken core confirmation flags" {
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --install-app --answers ignored --yes
  [ "$status" -eq 2 ]
}

app_build_fixture() {
  gt_core_run() { return 3; } # The service is inactive before the first build.
  gt_core_config_valid() { :; }
  gt_app_artifacts() { :; }
  gt_as_app() {
    if [[ "$1" == git ]]; then echo "${TEST_BUILT_COMMIT:-${STATE[planned_commit]}}"
    else
      [[ "$1 $2 $3" == 'env GT_INSTALL_BUILD_ONLY=1 GT_CRON_RANDOMIZE=off' ]] || return 99
      echo "$*" >> "$ROOT/build-events"
      echo 'private build diagnostics'
    fi
  }
}

@test "first build uses both build-only helpers records its commit and never rebuilds a completed target" {
  app_build_fixture
  gt_app_build
  [ "${STATE[built_commit]}" = "${STATE[planned_commit]}" ]
  [ "${STATE[step.app_build]}" = complete ]
  [ "$(stat -c %a "$ROOT/var/lib/gt-install/app-build.log")" = 600 ]
  [ "$(wc -l < "$ROOT/build-events")" -eq 2 ]
  grep -q gtupfrontend.sh "$ROOT/build-events"
  grep -q gtupbackend.sh "$ROOT/build-events"
  gt_app_build
  [ "$(wc -l < "$ROOT/build-events")" -eq 2 ]
}

@test "a changed checkout during the first build cannot complete the build milestone" {
  app_build_fixture
  TEST_BUILT_COMMIT=0000000000000000000000000000000000000002
  run gt_app_build
  [ "$status" -eq 2 ]
  ! grep -q '^built_commit=' "$ROOT/var/lib/gt-install/state"
  ! grep -q '^step.app_build=complete$' "$ROOT/var/lib/gt-install/state"
}
