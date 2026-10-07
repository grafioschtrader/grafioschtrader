#!/bin/bash
set -Eeuo pipefail

# gtupdate.sh already stopped the service before invoking us. Recover it even on a setup failure.
service_stopped=1
finish() {
  local status=$?
  trap - EXIT
  if (( service_stopped )); then
    if ! sudo systemctl start grafioschtrader.service; then
      echo "ERROR: Could not restart grafioschtrader.service; inspect its journal." >&2
      status=1
    fi
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

echo "Build backend and frontend; parallel backend output follows the frontend output."
# shellcheck source=/dev/null
. "$HOME/gtvar.sh"
: "${builddir:?Set builddir in gtvar.sh}"
memorytotal=$(free -m | awk '/^Mem:/ { print $2 }')
[[ "$memorytotal" =~ ^[0-9]+$ ]] || { echo "ERROR: Cannot determine RAM size." >&2; exit 1; }
rm -rf -- "$builddir/.deps"
cd "$HOME"
export GT_UPDATE_COORDINATED=1
if (( memorytotal > 4000 )); then
  "$HOME/gtupfrontend.sh" 2>&1 | tee "$builddir/frontbuild.log" &
  front_pid=$!
  "$HOME/gtupbackend.sh" > "$builddir/backbuild.log" 2>&1 &
  back_pid=$!
  front_status=0
  back_status=0
  wait "$front_pid" || front_status=$?
  wait "$back_pid" || back_status=$?
  cat "$builddir/backbuild.log"
  if (( front_status != 0 || back_status != 0 )); then
    echo "ERROR: Update failed (frontend=$front_status, backend=$back_status). See $builddir/*build.log." >&2
    exit 1
  fi
else
  sudo systemctl stop grafioschtrader.service
  service_stopped=1
  "$HOME/gtupfrontend.sh"
  "$HOME/gtupbackend.sh"
fi
service_stopped=0
