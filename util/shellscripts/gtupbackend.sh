#!/bin/bash
# Build first; a failed build must never remove the deployed application.
set -Eeuo pipefail
umask 077
shopt -s nullglob

stage=
published=0
service_started=0

finish() {
  local status=$? old
  trap - EXIT
  if [[ -n "$stage" && -d "$stage" ]]; then
    if (( ! published )); then
      for old in "$stage/previous/"*.jar; do
        if ! mv -f -- "$old" "$HOME/"; then
          echo "ERROR: Could not restore $old; recover it manually." >&2
          status=1
        fi
      done
    fi
    # Retain any backup that could not be restored.
    local remaining=("$stage/previous/"*.jar)
    if (( published || ${#remaining[@]} == 0 )); then
      rm -rf -- "$stage" || status=1
    fi
  fi
  if (( ! service_started )) && [[ "${GT_INSTALL_BUILD_ONLY:-0}" != 1 ]]; then
    if ! sudo systemctl start grafioschtrader.service; then
      echo "ERROR: Could not start grafioschtrader.service; inspect its journal and application log." >&2
      status=1
    fi
  fi
  if (( status != 0 )); then
    echo "ERROR: Backend update failed. Check the build output and service log before retrying." >&2
    if (( published )); then
      echo "The new JAR was deployed. Restoring a JAR does not roll back database migrations." >&2
    fi
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'echo "ERROR: Backend command failed at line $LINENO." >&2' ERR

# shellcheck source=/dev/null
. "$HOME/gtvar.sh"
: "${builddir:?Set builddir in gtvar.sh}"
if [[ "${GT_INSTALL_BUILD_ONLY:-0}" != 1 ]]; then sudo systemctl stop grafioschtrader.service; fi
cd "$builddir/grafioschtrader/backend"

# Keep hand-edited schedules. Randomization remains optional, as in earlier updaters.
bash "$builddir/grafioschtrader/util/shellscripts/gtcronrandom.sh" \
  --file grafioschtrader-server/src/main/resources/application.properties \
  || echo "WARNING: the cron randomization failed - building with the configured times"

# Remove old reactor artifacts; the build does not need a locally installed GT release.
rm -rf -- "$HOME/.m2/repository/grafioschtrader"
# Compile tests too: grafiosch-server-base publishes a test-jar needed by the reactor.
mvn clean package -DskipTests
jars=(grafioschtrader-server/target/grafioschtrader-server-*.jar)
if (( ${#jars[@]} != 1 )) || [[ ! -s "${jars[0]}" ]]; then
  echo "ERROR: Expected exactly one non-empty executable JAR after Maven completed." >&2
  exit 1
fi

# Stage on the destination filesystem before moving any deployed JAR.
stage=$(mktemp -d "$HOME/.gt-backend.XXXXXX")
mkdir "$stage/previous"
cp -- "${jars[0]}" "$stage/"
old_jars=("$HOME/"grafioschtrader*.jar)
for old in "${old_jars[@]}"; do
  mv -- "$old" "$stage/previous/"
done
mv -- "$stage/$(basename "${jars[0]}")" "$HOME/"
published=1
if [[ "${GT_INSTALL_BUILD_ONLY:-0}" != 1 ]]; then sudo systemctl start grafioschtrader.service; fi
service_started=1
