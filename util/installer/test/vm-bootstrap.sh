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
      {
        printf 'WEBSERVER=%s\n' "${2:-nginx}"
        [[ "${2:-nginx}" != apache2 ]] || printf 'BACKEND_HTTP_PORT=8080\n'
        cat <<'ANSWERS'
DOMAIN=
BACKEND_PORT=9090
DOCROOT=/var/www/gt
ADMIN_EMAIL=admin@example.invalid
SMTP_CONFIGURE=yes
SMTP_HOST=127.0.0.1
SMTP_PORT=2526
SMTP_AUTH=no
SMTP_USER=sender@example.invalid
SMTP_SECURITY=none
SMTP_TEST=yes
ALLOWED_USERS=20
JAVA_HEAP=-Xms256m -Xmx2048m
TIMEZONE=Etc/UTC
ANSWERS
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
    [[ "$installer_result" == 0 ]]
    load_verifiers
    [[ ${STATE[status]} == complete && ${STATE[scope]} == bootstrap ]]
    [[ ${STATE[run_id]} == "$(cat "$acceptance/run-id")" ]]
    sha256sum --check "$acceptance/secrets.sha256"
    [[ $(wc -l < "$acceptance/messages") == 1 ]]
    gt_app_verify; gt_app_artifacts
    FACT[web.lan]=${STATE[resource.web_lan]}
    gt_web_verify
    cat /proc/sys/kernel/random/boot_id > "$acceptance/boot-id"
    sha256sum /var/lib/gt-install/state /var/lib/gt-install/result /var/lib/gt-install/app-build.log \
      > "$acceptance/completed.sha256"
    printf 'Source commit: %s\n' "${STATE[built_commit]}"
    echo 'PASS: modeless resume, same secrets/identity, real backend/web verification and one accepted mail.'
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
    env -u GT_INSTALL_SOURCE_ONLY bash /opt/gt-acceptance/installer.sh --yes
    sha256sum --check "$acceptance/completed.sha256"
    sha256sum --check "$acceptance/secrets.sha256"
    [[ $(wc -l < "$acceptance/messages") == 1 ]]
    printf 'Original boot: %s\nCurrent boot: %s\n' "$(cat "$acceptance/boot-id")" "$(cat /proc/sys/kernel/random/boot_id)"
    echo 'PASS: reboot, automatic startup, read-only completed rerun, one mail and unchanged artifacts/secrets.'
    ;;
  *) exit 2 ;;
esac
