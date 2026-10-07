#!/bin/bash
# Real APT acceptance: a fresh disposable Ubuntu container, no host services or volumes.
set -euo pipefail
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset
umask 077
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
apt-get update -qq
# Prepare the inventory prerequisites just as the documented CLI requires.
# This fixture setup may update their dependencies in an older container image;
# the installer transaction below must still refuse all package upgrades.
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends git curl openssl python3
gt_packages
gt_plan_base_packages
gt_plan_packages
gt_report_rows 'Base package plan' "${PLAN[@]}"
gt_report_rows Blockers "${PLAN_BLOCKERS[@]}"
(( ${#PLAN_BLOCKERS[@]} == 0 ))
transactions=0
# Journal persistence has separate coverage. Keep this acceptance's state in memory.
gt_core_mark() { STATE[$1]=$2; }
gt_core_run() {
  [[ "$*" == 'env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade '* ]]
  transactions=$((transactions+1))
  "$@"
}
gt_core_base_packages
[[ ${STATE[step.base_packages]} == complete && $transactions == 1 ]]
while IFS= read -r package; do
  [[ $(dpkg-query -W -f='${db:Status-Status}' "$package") == installed ]]
done < <(gt_base_package_names)
gt_core_base_packages
[[ $transactions == 1 ]]
echo 'PASS: real base package transaction, installed-package verification and repeat without APT.'
