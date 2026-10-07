#!/bin/bash
# Real systemd acceptance. Only the private QEMU guest created by vm-host.sh may run this.
set -Eeuo pipefail
trap 'printf "Guest acceptance failed at line %s\n" "$LINENO" >&2' ERR
[[ $EUID == 0 && $(cat /etc/gt-installer-acceptance) == disposable-qemu && $(cat /proc/1/comm) == systemd ]]
cd /opt/gt-acceptance/source
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /opt/gt-acceptance/installer.sh
gt_reset
gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
CORE_REMOTE=/opt/gt-acceptance/source
case "${1:-}" in
  core)
    [[ -e /var/lib/gt-install/state || ! -e /home/grafioschtrader ]]
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq openjdk-25-jdk-headless maven git curl wget sudo openssl logrotate \
      python3 xz-utils iproute2 procps dnsutils psmisc
    gt_parse_requirements util/shellscripts/checkversion.sh
    gt_java; gt_runtimes
    FACT[architecture]=$(dpkg --print-architecture)
    FACT[source.commit]=$(git rev-parse HEAD)
    FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent
    FACT[memory.MemTotal]=12288
    ANSWER[ADMIN_EMAIL]=installer@example.invalid ANSWER[ALLOWED_USERS]=20 ANSWER[SMTP_CONFIGURE]=no
    ANSWER[DOMAIN]='' ANSWER[DOCROOT]=/var/www/gt ANSWER[WEBSERVER]=${2:-nginx} ANSWER[BACKEND_PORT]=9090
    [[ "${ANSWER[WEBSERVER]}" != apache2 ]] || ANSWER[BACKEND_HTTP_PORT]=8080
    if [[ "${3:-no}" == yes ]]; then
      bash /opt/gt-acceptance/source/util/installer/test/test-certificates.sh
      ANSWER[DOMAIN]=gt.test ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_SOURCE]=existing
      ANSWER[TLS_CERT]=/root/gt-test-certificates/fullchain.pem ANSWER[TLS_KEY]=/root/gt-test-certificates/server.key
    fi
    ANSWER[BUFFER_POOL]=no ANSWER[JAVA_HEAP]='-Xms256m -Xmx2048m' ANSWER[TIMEZONE]=Etc/UTC
    if [[ ! -e /root/acceptance-root-password ]]; then
      openssl rand -hex 24 > /root/acceptance-root-password
      sync -f /root/acceptance-root-password
    fi
    SECRET[DB_ROOT_PASSWORD]=$(cat /root/acceptance-root-password)
    SECRET[DB_PASSWORD]=$(openssl rand -hex 24)
    SECRET[JASYPT_PASSWORD]=$(openssl rand -hex 24)
    SECRET[JWT_SECRET]=$(openssl rand -hex 24)
    if [[ -e /var/lib/gt-install/state ]]; then
      gt_state_load || { echo 'Core journal validation failed' >&2; exit 2; }
      gt_secrets_load || { echo 'Core secret validation failed' >&2; exit 2; }
      SECRET[DB_ROOT_PASSWORD]=$(cat /root/acceptance-root-password)
    fi
    gt_core_build_plan
    (( ${#PLAN_BLOCKERS[@]} == 0 )) || { gt_plan_report; exit 2; }
    if [[ ! -e /var/lib/gt-install/state ]]; then gt_core_begin; fi
    gt_build_record
    # Use real execution steps, including package installation and systemctl. The
    # sole source substitution is the isolated, locally committed test snapshot.
    gt_core_user; gt_core_buildtools; gt_core_clone; gt_core_database; gt_core_configure
    gt_core_mark step.core complete
    touch /var/lib/gt-install/lock
    echo 'PASS: actual core, MariaDB, encryption and isolated toolchains.'
    ;;
  application)
    CORE_CONFIRM=yes
    if gt_install_app; then result=0; else result=$?; fi
    [[ $result == 10 ]]
    cat /proc/sys/kernel/random/boot_id > /var/lib/gt-install/acceptance-boot-id
    sha256sum /root/.gt-install/secrets > /var/lib/gt-install/acceptance-secrets.sha256
    echo 'PASS: actual frontend/backend build, systemd first start and backend verification.'
    ;;
  web)
    CORE_CONFIRM=yes
    if gt_install_web; then result=0; else result=$?; fi
    [[ $result == 10 && ${STATE[step.web]} == complete ]]
    gt_web_verify
    [[ -z "${ANSWER[DOMAIN]}" ]] || gt_tls_verify
    echo 'PASS: real web server installation, application proxy, frontend via LAN and selected TLS.'
    ;;
  reboot-check)
    gt_state_load; gt_secrets_load
    [[ $(cat /proc/sys/kernel/random/boot_id) != "$(cat /var/lib/gt-install/acceptance-boot-id)" ]]
    [[ ${STATE[step.app]} == complete && ${STATE[built_commit]} == "${STATE[planned_commit]}" ]]
    systemctl is-enabled --quiet mariadb.service
    systemctl is-enabled --quiet grafioschtrader.service
    for ((attempt=0; attempt<180; attempt++)); do
      if gt_app_verify; then break; fi
      sleep 5
    done
    gt_app_verify
    systemctl is-enabled --quiet "${ANSWER[WEBSERVER]}.service"
    systemctl is-active --quiet "${ANSWER[WEBSERVER]}.service"
    FACT[web.lan]=${STATE[resource.web_lan]}
    gt_web_verify
    sha256sum --check /var/lib/gt-install/acceptance-secrets.sha256
    gt_core_config_valid; gt_app_artifacts
    # A repeated application stage must keep the JAR, index and original secrets.
    before=$(sha256sum /var/lib/gt-install/app-build.log)
    CORE_CONFIRM=yes
    if gt_install_app; then result=0; else result=$?; fi
    [[ $result == 10 && "$before" == "$(sha256sum /var/lib/gt-install/app-build.log)" ]]
    site=/etc/nginx/sites-available/grafioschtrader
    [[ "${ANSWER[WEBSERVER]}" != apache2 ]] || site=/etc/apache2/sites-available/grafioschtrader.conf
    web_before=$(sha256sum "$site")
    if gt_install_web; then result=0; else result=$?; fi
    [[ $result == 10 && "$web_before" == "$(sha256sum "$site")" ]]
    [[ -z "${ANSWER[DOMAIN]}" ]] || gt_tls_verify
    runuser -u www-data -- test -r "${ANSWER[DOCROOT]}/grafioschtrader/index.html"
    printf 'Original boot: %s\nCurrent boot: %s\nBuilt commit: %s\n' \
      "$(cat /var/lib/gt-install/acceptance-boot-id)" "$(cat /proc/sys/kernel/random/boot_id)" "${STATE[built_commit]}"
    gt_as_app java --version
    gt_as_app "${STATE[build.node_home]}/bin/node" --version
    printf 'MariaDB version and successful/failed migrations:\n'
    printf 'SELECT VERSION(); SELECT success,COUNT(*) FROM flyway_schema_history GROUP BY success;\n' | gt_app_sql
    echo 'PASS: new boot ID, automatic database/backend/web startup, LAN health, selected TLS, original secrets and resume without rebuilding.'
    ;;
  *) exit 2 ;;
esac
