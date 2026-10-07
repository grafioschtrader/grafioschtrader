#!/bin/bash
set -euo pipefail
cmd=$(basename "$0")
printf '%s %s\n' "$cmd" "$*" >> "$EVENTS"

if [[ "${FAIL:-}" == "$cmd" ]]; then
  echo "Injected $cmd failure" >&2
  exit 42
fi
case "$cmd" in
  sudo)
    [[ "$1" == systemctl && "$3" == grafioschtrader.service ]]
    if [[ "${FAIL:-}" == service-start && "$2" == start ]]; then exit 42; fi
    if [[ "$2" == stop ]]; then echo stopped > "$SERVICE"; else echo running > "$SERVICE"; fi
    ;;
  free)
    memory=${MEMORY:?The fixture must set MEMORY}
    # Exercise the downloaded bundle with parallel orchestration as well as sequential orchestration.
    if [[ "${FRONT_MEMORY:-}" && $(tr '\0' ' ' < "/proc/$PPID/cmdline") == *gtupfrontend.sh* ]]; then
      memory=$FRONT_MEMORY
    fi
    printf 'Mem: %s 0 0 0 0 0\n' "$memory"
    ;;
  mvn)
    [[ "$*" == 'clean package -DskipTests' ]]
    rm -f grafioschtrader-server/target/*.jar
    [[ "${JAR_MODE:-}" == missing ]] && exit 0
    printf 'new jar\n' > grafioschtrader-server/target/grafioschtrader-server-new.jar
    if [[ "${JAR_MODE:-}" == multiple ]]; then
      printf 'second jar\n' > grafioschtrader-server/target/grafioschtrader-server-second.jar
    elif [[ "${JAR_MODE:-}" == empty ]]; then
      : > grafioschtrader-server/target/grafioschtrader-server-new.jar
    fi
    ;;
  npm) [[ "$*" == ci ]] ;;
  node) [[ "$*" == scripts/sync-yaml-schemas.mjs ]] ;;
  ng)
    output=
    while (( $# )); do
      if [[ "$1" == --output-path ]]; then output=$2; shift; fi
      shift
    done
    [[ -n "$output" ]]
    mkdir -p "$output/browser"
    if [[ "${OUTPUT_MODE:-}" != missing ]]; then
      printf 'new frontend\n' > "$output/browser/index.html"
      printf 'new asset\n' > "$output/browser/main.js"
    fi
    if [[ "${OUTPUT_MODE:-}" == empty ]]; then : > "$output/browser/index.html"; fi
    ;;
  wget)
    [[ "$1" == -O ]]
    /bin/cp "$FIXTURE/latest.tar.gz" "$2"
    ;;
  tar) exec /bin/tar "$@" ;;
  mv)
    # Fail once after index.html has been published, proving partial replacement is restored.
    if [[ "${FAIL:-}" == publish-front && "$*" == *'/dist/browser/main.js '* && ! -e "$FIXTURE/move-failed" ]]; then
      : > "$FIXTURE/move-failed"
      exit 42
    fi
    if [[ "${FAIL:-}" == publish-back && "$*" == *'.gt-backend.'*'/grafioschtrader-server-new.jar '* ]]; then
      exit 42
    fi
    exec /bin/mv "$@"
    ;;
  cp) exec /bin/cp "$@" ;;
  tee) exec /usr/bin/tee "$@" ;;
  chown|touch) : ;; # installroot tests must not change real ownership or /var/log.
  git)
    if [[ "$1" == reset ]]; then mkdir -p "${builddir:?The fixture must set builddir}/grafioschtrader/frontend"; fi
    ;;
  *) echo "Unexpected command: $cmd" >&2; exit 1 ;;
esac
