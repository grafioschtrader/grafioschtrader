#!/bin/bash
# Full CLI acceptance: only the disposable guest marked by vm-host.sh may run it.
set -Eeuo pipefail
trap 'printf "Bootstrap acceptance failed at line %s\n" "$LINENO" >&2' ERR
[[ $EUID == 0 && $(cat /etc/gt-installer-acceptance) == disposable-qemu && $(cat /proc/1/comm) == systemd ]]
umask 077
acceptance=/root/gt-bootstrap-acceptance
mkdir -p "$acceptance"

start_installer() {
  local phase=$1
  shift
  unit="gt-acceptance-$phase-$(date +%s)"
  systemd-run --quiet --unit="$unit" -p Type=exec -p KillMode=process -p RemainAfterExit=yes \
    -p "StandardOutput=append:$acceptance/$phase.log" -p StandardError=inherit \
    env -u GT_INSTALL_SOURCE_ONLY /bin/bash /opt/gt-acceptance/installer.sh --yes "$@"
  installer_pid=$(systemctl show --property=MainPID --value "$unit")
  [[ "$installer_pid" =~ ^[1-9][0-9]*$ ]]
}

wait_installer() {
  local deadline=$((SECONDS+3600)) pid
  while (( SECONDS < deadline )); do
    pid=$(systemctl show --property=MainPID --value "$unit")
    if [[ "$pid" == 0 ]]; then
      installer_result=$(systemctl show --property=ExecMainStatus --value "$unit")
      printf 'Installer exit: %s\n' "$installer_result"
      return 0
    fi
    sleep 2
  done
  echo 'Installer timeout; guest and logs preserved.' >&2
  return 2
}

# Small hosts: the installer's own heap and swap defaults apply. The swap file must be active, also after a reboot
# through /etc/fstab, and below 3700 MiB the frontend is the downloaded release instead of a local build.
verify_small_host() {
  local memory
  memory=$(awk '$1 == "MemTotal:" {print int($2 / 1024)}' /proc/meminfo)
  printf 'MemTotal: %s MiB, SWAP=%s, JAVA_HEAP=%s\n' "$memory" "${ANSWER[SWAP]:-not asked}" "${ANSWER[JAVA_HEAP]}"
  (( memory >= 4000 )) || [[ "${ANSWER[SWAP]:-}" == yes && "${STATE[step.swap]:-}" == complete ]]
  if [[ "${ANSWER[SWAP]:-no}" == yes ]]; then
    gt_swap_valid /swapfile
    gt_swap_active /swapfile
    [[ $(grep -cE '^/swapfile[[:space:]]+none[[:space:]]+swap[[:space:]]' /etc/fstab) == 1 ]]
  fi
  (( memory >= 3700 )) || grep -q 'releases/download/Latest/latest.tar.gz' /var/lib/gt-install/app-build.log
}

# A legacy release installs with a recorded warning that names the release and the end of its support.
verify_legacy() {
  local release
  # shellcheck source=/dev/null
  release=$(. /etc/os-release && printf '%s:%s' "$ID" "$VERSION_ID")
  case "$release" in
    ubuntu:22.04)
      grep -E '^warning\.[0-9]+=WARN: (Legacy platform|Ältere Plattform): ' /var/lib/gt-install/result ;;
  esac
}

load_verifiers() {
  if declare -F gt_cleanup >/dev/null && [[ -n "${SCRATCH:-}" ]]; then gt_cleanup; fi
  export GT_INSTALL_SOURCE_ONLY=1
  # shellcheck source=util/installer/gt-install.sh
  source /opt/gt-acceptance/installer.sh
  gt_reset; gt_question_model
  SCRATCH=$(mktemp -d)
  trap gt_cleanup EXIT
  gt_state_load; gt_secrets_load
}

case "${1:-}" in
  prepare)
    [[ "${2:-nginx}" == nginx || "$2" == apache2 ]]
    [[ "${3:-install}" == install || "$3" == later ]]
    if [[ ! -f "$acceptance/answers" ]]; then
      [[ ! -e /var/lib/gt-install/state && ! -e /home/grafioschtrader ]]
      export DEBIAN_FRONTEND=noninteractive
      apt-get update -qq
      # Bootstrap probe prerequisites and the test-only local mail sink. Java,
      # Maven, MariaDB, nginx/Apache and the base package set belong to the installer.
      apt-get -o DPkg::Lock::Timeout=600 install -y -qq git curl openssl python3 python3-aiosmtpd \
        iproute2 procps psmisc xz-utils
      cat > "$acceptance/smtp.py" <<'PY'
import os
import pathlib
import signal
from aiosmtpd.controller import Controller

class Handler:
    async def handle_DATA(self, server, session, envelope):
        with pathlib.Path('/root/gt-bootstrap-acceptance/messages').open('ab') as output:
            output.write(b'accepted\n')
            output.flush()
            os.fsync(output.fileno())
        return '250 accepted'

controller = Controller(Handler(), hostname='127.0.0.1', port=2526,
                        auth_exclude_mechanism=['PLAIN', 'LOGIN'])
controller.start()
signal.pause()
PY
      cat > /etc/systemd/system/gt-acceptance-smtp.service <<UNIT
[Unit]
Description=Disposable installer acceptance mail sink
[Service]
ExecStart=/usr/bin/python3 $acceptance/smtp.py
[Install]
WantedBy=multi-user.target
UNIT
      systemctl daemon-reload
      systemctl enable --now gt-acceptance-smtp.service
      cat > "$acceptance/mail-answers" <<'ANSWERS'
SMTP_CONFIGURE=yes
SMTP_HOST=127.0.0.1
SMTP_PORT=2526
SMTP_AUTH=no
SMTP_USER=sender@example.invalid
SMTP_SECURITY=none
SMTP_TEST=yes
ANSWERS
      chmod 600 "$acceptance/mail-answers"
      {
        printf 'WEBSERVER=%s\n' "${2:-nginx}"
        [[ "${2:-nginx}" != apache2 ]] || printf 'BACKEND_HTTP_PORT=8080\n'
        cat <<'ANSWERS'
DOMAIN=
BACKEND_PORT=9090
DOCROOT=/var/www/gt
ADMIN_EMAIL=admin@example.invalid
ALLOWED_USERS=20
TIMEZONE=Etc/UTC
ANSWERS
        # later: the mail answers arrive after the installation, through --check-mail --answers.
        if [[ "${3:-install}" == later ]]; then echo SMTP_CONFIGURE=no; else cat "$acceptance/mail-answers"; fi
        printf 'DB_ROOT_PASSWORD=%s\nDB_PASSWORD=%s\nJASYPT_PASSWORD=%s\n' \
          "$(openssl rand -hex 24)" "$(openssl rand -hex 24)" "$(openssl rand -hex 24)"
      } > "$acceptance/answers"
      chmod 600 "$acceptance/answers"
    fi
    echo 'PASS: fresh guest probe prerequisites and isolated SMTP fixture ready.'
    ;;
  install)
    if [[ ! -e "$acceptance/interrupted" ]]; then
      start_installer initial --answers "$acceptance/answers"
      deadline=$((SECONDS+3600))
      while (( SECONDS < deadline )); do
        if grep -qx step.app_start=intent /var/lib/gt-install/state 2>/dev/null &&
            systemctl is-active --quiet grafioschtrader.service; then
          kill -STOP "$installer_pid"
          break
        fi
        [[ "$(systemctl show --property=MainPID --value "$unit")" != 0 ]] || {
          echo 'Installer ended before the interruption checkpoint; inspect initial.log.' >&2; exit 2;
        }
        sleep 1
      done
      [[ "$(ps -o stat= -p "$installer_pid")" == T* ]]
      # The service continues while its installer is paused: migrations really
      # populate the schema before the journal can mark the first start complete.
      load_verifiers
      deadline=$((SECONDS+900))
      until gt_app_verify; do
        (( SECONDS < deadline )) || exit 2
        sleep 3
      done
      [[ ${STATE[step.app_start]} == intent ]]
      printf 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;\n' | gt_app_sql > "$acceptance/migrations"
      [[ $(cat "$acceptance/migrations") -gt 0 ]]
      sha256sum /root/.gt-install/secrets > "$acceptance/secrets.sha256"
      printf '%s\n' "${STATE[run_id]}" > "$acceptance/run-id"
      kill -TERM "$installer_pid"
      kill -CONT "$installer_pid"
      wait_installer
      [[ "$installer_result" == 143 ]]
      touch "$acceptance/interrupted"
      echo 'PASS: interrupted after actual migrations, before first-start completion.'
    fi
    start_installer resumed
    wait_installer
    load_verifiers
    if [[ ${ANSWER[SMTP_CONFIGURE]} == no ]]; then
      # Without mail the result stays incomplete (10) and carries the skipped-mail warning until --check-mail adds
      # the SMTP answers; the change rebuilds the backend with them and the same check then completes.
      [[ "$installer_result" == 10 && ${STATE[status]} == running && ! -e "$acceptance/messages" ]]
      grep -qx mail=skipped /var/lib/gt-install/result
      grep -qE 'SMTP skipped|Mail skipped' /var/lib/gt-install/result
      start_installer mail --check-mail --answers "$acceptance/mail-answers"
      wait_installer
      load_verifiers
      [[ ${ANSWER[SMTP_CONFIGURE]} == yes && ${STATE[step.mail_change]} == complete ]]
      if grep -qE 'SMTP skipped|Mail skipped' /var/lib/gt-install/result; then
        echo 'FAIL: the skipped-mail warning survived the SMTP change' >&2; exit 1
      fi
      echo 'PASS: the installation without mail gained it through --check-mail --answers.'
    fi
    [[ "$installer_result" == 0 ]]
    [[ ${STATE[status]} == complete && ${STATE[scope]} == bootstrap ]]
    [[ ${STATE[run_id]} == "$(cat "$acceptance/run-id")" ]]
    sha256sum --check "$acceptance/secrets.sha256"
    [[ $(wc -l < "$acceptance/messages") == 1 ]]
    gt_app_verify; gt_app_artifacts
    FACT[web.lan]=${STATE[resource.web_lan]}
    gt_web_verify
    verify_small_host
    verify_legacy
    cat /proc/sys/kernel/random/boot_id > "$acceptance/boot-id"
    sha256sum /var/lib/gt-install/state /var/lib/gt-install/result /var/lib/gt-install/app-build.log \
      > "$acceptance/completed.sha256"
    printf 'Source commit: %s\n' "${STATE[built_commit]}"
    echo 'PASS: modeless resume, same secrets/identity, real backend/web verification and one accepted mail.'
    ;;
  locales)
    # The tester's terminal side, not an installer prerequisite: both dialog languages need their UTF-8 locale.
    export DEBIAN_FRONTEND=noninteractive
    apt-get -o DPkg::Lock::Timeout=600 install -y -qq locales
    sed -i -E 's/^# *((de_CH|en_US)\.UTF-8 UTF-8)/\1/' /etc/locale.gen
    locale-gen > /dev/null
    locale -a | grep -qi '^de_CH\.utf-\?8$'
    locale -a | grep -qi '^en_US\.utf-\?8$'
    echo 'PASS: de_CH.UTF-8 and en_US.UTF-8 available for the dialog sessions.'
    ;;
  dialog-check)
    # The installation was driven only by whiptail dialogs over SSH; no answers file was used.
    load_verifiers
    [[ ${STATE[status]} == complete && ${STATE[scope]} == bootstrap ]]
    [[ $(wc -l < "$acceptance/messages") == 1 ]]
    gt_app_verify; gt_app_artifacts
    FACT[web.lan]=${STATE[resource.web_lan]}
    gt_web_verify
    verify_small_host
    verify_legacy
    # Typed and generated credentials never reach the installer's logs, journal or result.
    for key in DB_PASSWORD JASYPT_PASSWORD; do
      [[ -n "${SECRET[$key]:-}" ]]
      if grep -rFq -- "${SECRET[$key]}" /var/lib/gt-install; then
        echo "FAIL: $key appears in /var/lib/gt-install" >&2; exit 1
      fi
      # The application's own startup log is outside the installer; a finding is recorded, not hidden.
      if grep -Fq -- "${SECRET[$key]}" /var/log/grafioschtrader.log; then
        echo "NOTE: the application log contains $key (logged by the backend, not by the installer)"
      fi
    done
    cat /proc/sys/kernel/random/boot_id > "$acceptance/boot-id"
    sha256sum /var/lib/gt-install/state /var/lib/gt-install/result /var/lib/gt-install/app-build.log \
      > "$acceptance/completed.sha256"
    sha256sum /root/.gt-install/secrets > "$acceptance/secrets.sha256"
    printf 'Source commit: %s\n' "${STATE[built_commit]}"
    echo 'PASS: dialog-driven installation complete, one accepted mail, no credential in installer logs or result.'
    ;;
  reboot-check)
    load_verifiers
    [[ $(cat /proc/sys/kernel/random/boot_id) != "$(cat "$acceptance/boot-id")" ]]
    for service in mariadb grafioschtrader "${ANSWER[WEBSERVER]}"; do
      systemctl is-enabled --quiet "$service.service"
      systemctl is-active --quiet "$service.service"
    done
    deadline=$((SECONDS+900))
    until gt_app_verify; do (( SECONDS < deadline )) || exit 2; sleep 3; done
    FACT[web.lan]=${STATE[resource.web_lan]}
    gt_web_verify; gt_core_config_valid; gt_app_artifacts
    verify_small_host
    env -u GT_INSTALL_SOURCE_ONLY bash /opt/gt-acceptance/installer.sh --yes
    sha256sum --check "$acceptance/completed.sha256"
    sha256sum --check "$acceptance/secrets.sha256"
    [[ $(wc -l < "$acceptance/messages") == 1 ]]
    printf 'Original boot: %s\nCurrent boot: %s\n' "$(cat "$acceptance/boot-id")" "$(cat /proc/sys/kernel/random/boot_id)"
    echo 'PASS: reboot, automatic startup, read-only completed rerun, one mail and unchanged artifacts/secrets.'
    ;;
  *) exit 2 ;;
esac
