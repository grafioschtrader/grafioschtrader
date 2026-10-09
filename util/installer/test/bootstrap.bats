#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  MODE=--bootstrap CORE_CONFIRM=yes ANSWERS_FILE=fixture
  CORE_HOME="$ROOT/home/grafioschtrader"
  mkdir -m 700 "$ROOT/var/lib/gt-install"
  ANSWER=([WEBSERVER]=nginx [DOMAIN]='' [DOCROOT]=/var/www/gt [TIMEZONE]=UTC
    [SMTP_CONFIGURE]=no [BACKEND_PORT]=9090 [BACKEND_HTTP_PORT]=8080)
}

bootstrap_boundary_fixture() {
  INVENTORIES=0
  gt_completed() { return 3; }
  gt_inventory() {
    INVENTORIES=$((INVENTORIES+1))
    FACT[host.class]=fresh FACT[database.vendor]=absent FACT[source.commit]=0000000000000000000000000000000000000001
  }
  gt_compatibility() { :; }
  gt_report() { :; }
  gt_answers_file() { :; }
  gt_prepare_root() { :; }
  gt_file_questions() { :; }
  gt_prepare_secrets() { SECRET[DB_PASSWORD]=original; }
  gt_bootstrap_plan() {
    FACT[bootstrap.plan]=fixed FACT[bootstrap.apt]=fixed FACT[bootstrap.web]=fixed
  }
  gt_plan_report() { :; }
  gt_core_begin() {
    STATE[scope]=core STATE[run_id]=original-id
    gt_state_save
  }
  gt_build_record() { :; }
  gt_core_execute() {
    echo core >> "$ROOT/events"
    [[ "${STATE[scope]}" == bootstrap && "$BOOTSTRAP_APPROVED" == yes ]] || return 99
    [[ "${STATE[resource.bootstrap_plan]}" =~ ^[a-f0-9]{64}$ ]] || return 99
    STATE[step.core]=complete
    return 10
  }
  gt_bootstrap_execute() {
    [[ "${STATE[run_id]}:${SECRET[DB_PASSWORD]}" == original-id:original ]] || return 99
    echo remaining >> "$ROOT/events"
    return 10
  }
}

@test "full scope is journaled only after stable approval and retains identity and secrets" {
  bootstrap_boundary_fixture
  run gt_install_core
  [ "$status" -eq 10 ]
  [ "$(cat "$ROOT/events")" = $'core\nremaining' ]
  grep -qx scope=bootstrap "$ROOT/var/lib/gt-install/state"
}

@test "full plan drift prevents all changes and scope transition" {
  bootstrap_boundary_fixture
  gt_bootstrap_plan() {
    FACT[bootstrap.plan]=fixed FACT[bootstrap.apt]=fixed FACT[bootstrap.web]=$INVENTORIES
  }
  run gt_install_core
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/events" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
}

@test "populated-schema resume bypasses root credentials and every core execution step" {
  bootstrap_boundary_fixture
  touch "$ROOT/var/lib/gt-install/state"
  gt_state_load() { STATE[step.core]=complete STATE[step.app_start]=intent STATE[run_id]=original-id; }
  gt_secrets_load() { SECRET[DB_PASSWORD]=original; }
  gt_collect_secret() { echo unexpected-root-question; return 99; }
  gt_build_record() { echo unexpected-build-record; return 99; }
  gt_core_execute() { echo unexpected-core-execution; return 99; }
  run gt_install_core
  [ "$status" -eq 10 ]
  [[ "$output" != *unexpected* ]]
  [ "$(cat "$ROOT/events")" = remaining ]
}

@test "full plan blockers cannot install DNS prerequisites or publish credentials" {
  bootstrap_boundary_fixture
  gt_bootstrap_plan() { return 2; }
  gt_prepare_dns() { touch "$ROOT/unexpected-dns-mutation"; }
  run gt_install_core
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/unexpected-dns-mutation" ]
  [ ! -e "$ROOT/var/lib/gt-install/state" ]
}

bootstrap_stages_fixture() {
  CORE_CONFIRM=no BOOTSTRAP_WEB=approved
  gt_install_app() { [[ "$CORE_CONFIRM" == yes ]] || return 99; echo app >> "$ROOT/events"; return 10; }
  gt_domain_plan() { :; }
  gt_bootstrap_web_review() { FACT[bootstrap.web]=approved; }
  gt_install_web() { echo web >> "$ROOT/events"; return 10; }
  gt_check_mail() { echo mail-handover >> "$ROOT/events"; return 10; }
}

@test "approved controller chains app web mail and propagates incomplete handover" {
  bootstrap_stages_fixture
  run gt_bootstrap_execute
  [ "$status" -eq 10 ]
  [ "$(cat "$ROOT/events")" = $'app\nweb\nmail-handover' ]
  [ "$CORE_CONFIRM" = no ]
}

@test "failed application cannot reach web or mail" {
  bootstrap_stages_fixture
  gt_install_app() { return 2; }
  run gt_bootstrap_execute
  [ "$status" -eq 2 ]
  [ ! -e "$ROOT/events" ]
}

@test "web drift during build requires another plan before touching the web server" {
  bootstrap_stages_fixture
  gt_bootstrap_web_review() { FACT[bootstrap.web]=changed; }
  run gt_bootstrap_execute
  [ "$status" -eq 2 ]
  [ "$(cat "$ROOT/events")" = app ]
}

@test "complete and failed mail outcomes retain their exact exit codes" {
  bootstrap_stages_fixture
  gt_check_mail() { return 0; }
  gt_bootstrap_execute
  gt_check_mail() { return 1; }
  run gt_bootstrap_execute
  [ "$status" -eq 1 ]
}

@test "APT execution pins approved dependencies and rejects changed added upgraded or removed packages" {
  BOOTSTRAP_APPROVED=yes BOOTSTRAP_APT=([nginx]=1.2 [libfixture]=2.3)
  APT_OUTPUT=$'Inst nginx (1.2 Debian:stable [amd64])\nInst libfixture (2.3 Debian:stable [amd64])'
  env() { printf '%s\n' "$*" > "$ROOT/apt-executed"; }
  gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade install nginx
  grep -q 'nginx=1.2 libfixture=2.3' "$ROOT/apt-executed"
  local transaction
  for transaction in 'Inst nginx (1.3 Debian [amd64])' 'Inst unknown (1.0 Debian [amd64])' \
      'Inst nginx [1.0] (1.2 Debian [amd64])' 'Remv foreign [1.0]'; do
    rm -f "$ROOT/apt-executed"
    APT_OUTPUT=$transaction
    run gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade install nginx
    [ "$status" -eq 2 ]
    [ ! -e "$ROOT/apt-executed" ]
  done
}

@test "full APT planner approves dependencies but rejects stale metadata and upgrades" {
  PLAN_PACKAGES=([nginx]=install)
  FACT[dpkg.lock]=free FACT[apt.age_hours]=0
  APT_OUTPUT=$'Inst nginx (1.2 Debian [amd64])\nInst libfixture (2.3 Debian [amd64])'
  gt_bootstrap_apt_plan
  [ "${BOOTSTRAP_APT[libfixture]}" = 2.3 ]
  FACT[apt.age_hours]=25
  run gt_bootstrap_apt_plan
  [ "$status" -eq 2 ]
  FACT[apt.age_hours]=0
  APT_OUTPUT='Inst nginx [1.0] (1.2 Debian [amd64])'
  run gt_bootstrap_apt_plan
  [ "$status" -eq 2 ]
}

@test "modeless CLI selects bootstrap and still rejects conflicting explicit modes" {
  [ "$EUID" -eq 0 ] || skip 'The installation CLI requires root'
  gt_install_core() { printf 'selected=%s\n' "$MODE"; }
  run main --plain --answers fixture --yes
  [ "$status" -eq 0 ]
  [[ "$output" == *selected=--bootstrap* ]]
  run main --check --install-core
  [ "$status" -eq 2 ]
}

@test "early application target review refuses foreign privileged files before core creation" {
  mkdir -p "$ROOT/etc/sudoers.d"
  touch "$ROOT/etc/sudoers.d/grafioschtrader"
  run gt_app_targets
  [ "$status" -eq 2 ]
  [[ "$output" == *'Foreign file'* ]]
  # A log left by a removed installation (Armbian's ramlog restores it from /var/log.hdd) is named as well.
  rm "$ROOT/etc/sudoers.d/grafioschtrader"
  mkdir -p "$ROOT/var/log"
  touch "$ROOT/var/log/grafioschtrader.log"
  ANSWER[DOCROOT]=/var/www/gt
  run gt_app_targets
  [ "$status" -eq 2 ]
  [[ "$output" == *'Foreign file: /var/log/grafioschtrader.log'* ]]
}

@test "an unreachable pinned build helper is named instead of reported as a foreign target" {
  ANSWER[DOCROOT]=/var/www/gt FACT[source.commit]=0000000000000000000000000000000000000001
  gt_app_targets() { :; }
  gt_probe() { return 6; }
  run gt_bootstrap_app_review
  [ "$status" -eq 2 ]
  [[ "$output" == *'Pinned build helper unavailable: gtupbackend.sh'* ]]
}

bootstrap_plan_fixture() {
  FACT[source.commit]=0000000000000000000000000000000000000001
  FACT[dpkg.lock]=free FACT[apt.age_hours]=0
  ACTION[platform]=reuse ACTION[architecture]=reuse ACTION[disk]=reuse
  gt_core_plan() {
    PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=([git]=reuse)
    gt_plan_row configure core preserved-core-row
  }
  gt_bootstrap_app_review() { :; }
  gt_bootstrap_web_review() { FACT[bootstrap.web]=fixed; }
}

@test "full planner retains core scope and adds web packages mail delivery and handover" {
  bootstrap_plan_fixture
  ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_HOST]=smtp.example.test ANSWER[SMTP_PORT]=587
  ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_USER]=sender@example.test
  ANSWER[ADMIN_EMAIL]=admin@example.test ANSWER[SMTP_TEST]=yes
  APT_OUTPUT='Inst nginx (1.2 Debian [amd64])'
  gt_bootstrap_plan
  [ "${BOOTSTRAP_APT[nginx]}" = 1.2 ]
  [[ "${PLAN[*]}" == *preserved-core-row* ]]
  [[ "${PLAN[*]}" == *'recipient=admin@example.test; send=yes'* ]]
  [[ "${PLAN[*]}" == *'/var/lib/gt-install/result'* ]]
}

@test "post-start full planner uses application verifier instead of empty-schema planning" {
  bootstrap_plan_fixture
  STATE[step.core]=complete STATE[step.app_start]=intent
  gt_core_plan() { echo unexpected-core-plan; return 99; }
  gt_app_preflight() { echo verified > "$ROOT/application-verified"; }
  run gt_bootstrap_plan
  [ "$status" -eq 0 ]
  [[ "$output" != *unexpected-core-plan* ]]
  [ -e "$ROOT/application-verified" ]
}

@test "missing DNS tooling blocks full scope before any prerequisite transaction" {
  bootstrap_plan_fixture
  ANSWER[DOMAIN]=example.test ANSWER[TLS_SOURCE]=letsencrypt ANSWER[DNS_FAMILY]=ipv4
  ANSWER[LETSENCRYPT_EMAIL]=admin@example.test ANSWER[LETSENCRYPT_CERT_NAME]=-
  DNS_TOOL_AVAILABLE=no
  gt_domain_network() { :; }
  run gt_bootstrap_plan
  [ "$status" -ne 0 ]
  [[ "$output" == *'Missing DNS check prerequisite'* ]]
}
