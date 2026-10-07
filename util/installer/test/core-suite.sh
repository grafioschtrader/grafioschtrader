#!/bin/bash
# Dedicated disposable Docker container only. See core.Dockerfile and test/README.md.
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 ]]
run_report() {
  local name=$1
  shift
  if "$@" > "/tmp/gt-$name.log" 2>&1; then
    tail -n 4 "/tmp/gt-$name.log"
  else
    cat "/tmp/gt-$name.log"
    return 1
  fi
}
run_report unit setpriv --reuid=1000 --regid=1000 --clear-groups env HOME=/tmp bash util/installer/test/check-shell.sh
run_report root bats util/installer/test/credentials.bats util/installer/test/core.bats
run_report terminal python3 util/installer/test/secret-terminal.py
run_report integration bash util/installer/test/core-container.sh
