#!/bin/bash
# Root configuration checks only in a new disposable container, never on a host.
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install && ! -e /home/grafioschtrader ]]
if getent passwd grafioschtrader >/dev/null; then exit 1; fi
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source util/installer/gt-install.sh
gt_reset
umask 077
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
mkdir -m 700 /var/lib/gt-install
STATE[run_id]=00000000000000000000000000000001
STATE[planned_commit]=0000000000000000000000000000000000000001
STATE[java_home]=/usr STATE[maven]=/usr/bin/mvn
ANSWER[DOCROOT]=/var/www/gt ANSWER[TIMEZONE]=Etc/UTC
gt_core_user
gt_core_run() {
  case "$*" in
    'timedatectl show --property=Timezone --value') echo Etc/UTC ;;
    'systemctl daemon-reload') : ;; # No systemd PID 1 in this container.
    *) "$@" ;;
  esac
}
printf '#!/bin/bash\nexit 0\n' > "$CORE_HOME/grafioschtrader.sh"
chown grafioschtrader:grafioschtrader "$CORE_HOME/grafioschtrader.sh"
chmod 700 "$CORE_HOME/grafioschtrader.sh"
gt_app_resources
gt_app_service
[[ $(stat -c '%U:%a' /etc/sudoers.d/grafioschtrader) == root:440 ]]
[[ $(stat -c '%U:%a' /etc/systemd/system/grafioschtrader.service) == root:644 ]]
[[ $(stat -c '%U:%a' /var/log/grafioschtrader.log) == grafioschtrader:640 ]]
runuser -u grafioschtrader -- sudo -n -l /usr/bin/systemctl start grafioschtrader.service >/dev/null
runuser -u grafioschtrader -- sudo -n -l /usr/bin/systemctl stop grafioschtrader.service >/dev/null
if runuser -u grafioschtrader -- sudo -n -l /usr/bin/systemctl restart mariadb.service >/dev/null 2>&1; then exit 1; fi
echo 'preserved diagnostics' >> /var/log/grafioschtrader.log
gt_app_resources
gt_app_service
grep -qx 'preserved diagnostics' /var/log/grafioschtrader.log
if grep -q '^Restart=' /etc/systemd/system/grafioschtrader.service; then exit 1; fi
grep -qx UMask=0077 /etc/systemd/system/grafioschtrader.service

# Artifacts are captured before the complete marker; resumes reject modifications.
# shellcheck disable=SC2016
runuser -u grafioschtrader -- bash -c 'echo jar > "$HOME/grafioschtrader-server-fixture.jar"; echo html > /var/www/gt/grafioschtrader/index.html'
gt_app_artifacts
STATE[step.app_build]=complete
gt_app_artifacts
echo changed >> "$CORE_HOME/grafioschtrader-server-fixture.jar"
if gt_app_artifacts; then exit 1; fi

# Real cron helper with protected properties: repeated invocation keeps the slot.
mkdir -p "$CORE_REPO/backend/grafioschtrader-server/src/main/resources"
cp util/shellscripts/gtcronrandom.sh "$CORE_HOME/gtcronrandom.sh"
properties="$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties"
printf 'gt.eod.cron.quotation=0 54 05 * * ?\ngt.dividend.update.data=0 0 06 * * ?\ngt.standing.order.execution=0 15 06 * * ?\ngt.check.inactive.dividend=0 30 06 * * ?\ngt.hold.consistency.check=0 45 06 * * ?\nspring.datasource.password=ENC(fixture)\n' > "$properties"
chown -R grafioschtrader:grafioschtrader "$CORE_HOME/build" "$CORE_HOME/gtcronrandom.sh"
STATE[file.properties]=$(sha256sum "$properties"); STATE[file.properties]=${STATE[file.properties]%% *}
gt_app_cron
first=$(sha256sum "$properties")
gt_app_cron
[[ "$first" == "$(sha256sum "$properties")" ]]
grep -qx 'spring.datasource.password=ENC(fixture)' "$properties"
[[ $(stat -c '%U:%a' /var/lib/gt-install/cron.properties) == root:600 ]]
[[ $(stat -c '%U:%a' "$properties") == grafioschtrader:600 ]]

echo '# foreign change' >> /etc/sudoers.d/grafioschtrader
if gt_app_service; then exit 1; fi
echo 'Application integration: real unit/sudoers/logrotate validation, narrow sudo rights, protected artifacts, cron and drift checks passed.'
