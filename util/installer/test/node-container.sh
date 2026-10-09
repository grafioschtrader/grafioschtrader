#!/bin/bash
# Mutating integration: dedicated fresh Docker container only, no host ports or writable repository mount.
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install && ! -e /home/grafioschtrader && ! -e /opt/nodejs-gt && ! -e /opt/gt-build-tools ]]
[[ ! -e /usr/local/bin/node && ! -e /usr/local/bin/npm ]]
printf '#!/bin/sh\nprintf "v18.20.0\\n"\n' > /usr/local/bin/node
printf '#!/bin/sh\nexit 99\n' > /usr/local/bin/npm
chmod 755 /usr/local/bin/node /usr/local/bin/npm
shared_node=$(sha256sum /usr/local/bin/node)
mkdir -p /usr/local/lib/node_modules/gt-fixture
printf '{"name":"gt-fixture","version":"1.0.0"}\n' > /usr/local/lib/node_modules/gt-fixture/package.json
export GT_INSTALL_SOURCE_ONLY=1
source util/installer/gt-install.sh
gt_reset; gt_question_model
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
umask 077
gt_parse_requirements util/shellscripts/checkversion.sh
gt_java; gt_runtimes
FACT[architecture]=$(dpkg --print-architecture)
FACT[source.commit]=0000000000000000000000000000000000000001
FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent
ANSWER[ADMIN_EMAIL]=admin@example.org ANSWER[SMTP_CONFIGURE]=no ANSWER[DOMAIN]='' ANSWER[DOCROOT]=/var/www/gt
SECRET[DB_PASSWORD]=fixture-db SECRET[JASYPT_PASSWORD]=fixture-key
SECRET[JWT_SECRET]=012345678901234567890123456789012345678901234567
gt_core_build_plan
if (( ${#PLAN_BLOCKERS[@]} )); then gt_plan_report; exit 1; fi
[[ "${BUILD[mode]}" == archive ]]
gt_core_begin; gt_build_record; gt_core_user
saved_secrets=$(cat /root/.gt-install/secrets)

# Interrupt after the archive is published but before its ownership marker is committed.
gt_core_mark() {
  [[ "$1:$2" != resource.node:owned ]] || return 2
  STATE[$1]=$2; gt_state_save
}
if gt_core_buildtools; then echo 'Expected interrupted Node publication' >&2; exit 1; fi
[[ -x /opt/nodejs-gt/bin/node && "${STATE[resource.node]}" == intent ]]
STATE=() ANSWER=() SECRET=()
gt_state_load; gt_secrets_load
gt_build_metadata() { echo 'Recovery must reuse pinned metadata' >&2; return 99; }
gt_build_resolve
gt_core_mark() { STATE[$1]=$2; gt_state_save; }
gt_core_buildtools
[[ "${STATE[step.buildtools]}" == complete && "$(cat /root/.gt-install/secrets)" == "$saved_secrets" ]]
[[ "$(sha256sum /usr/local/bin/node)" == "$shared_node" && -f /usr/local/lib/node_modules/gt-fixture/package.json ]]
gt_core_variables
# Exercise the real updater preflight with its generated environment and the service user's npm prefix.
# gt_as_app runs in the service user's home, so the script needs an absolute path.
gt_as_app bash "$PWD/util/shellscripts/checkversion.sh" > "$SCRATCH/version-check"
grep -q 'All checks passed!' "$SCRATCH/version-check"
gt_build_download() { echo 'Completed build tools must not download again' >&2; return 99; }
gt_core_buildtools
printf 'PASS: real Node archive and pinned Angular CLI/semver, interrupted publication, unchanged shared Node/npm and updater preflight as service user.\n'

printf '\nexternal change\n' >> /opt/gt-build-tools/lib/node_modules/semver/package.json
if gt_core_buildtools; then echo 'Modified build tools were accepted' >&2; exit 1; fi
printf 'PASS: modified build tools block recovery.\n'
