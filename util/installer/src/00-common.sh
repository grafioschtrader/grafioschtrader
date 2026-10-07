#!/bin/bash
# Bootstrap inventory, planning, credential preparation and a resumable installation core.
# This file can be sourced for tests without inspecting or changing the machine.

gt_reset() {
  declare -gA FACT=() ACTION=() REASON=() PACKAGE=() CANDIDATE=() DISK_FREE=() DISK_NEED=()
  declare -ga NOTES=() JDKS=() DISKS=() CONSUMERS=() WEB=() CERTS=() NETWORK=()
  ROOT='' SCRATCH='' LOCK_FD='' LANG_CODE=en
  JAVA_REQUIRED=25 NODE_REQUIRED='^22.22.3 || ^24.15.0 || >=26.0.0' CLI_REQUIRED=22
  BLOCKS=0
  declare -gA ANSWER=() Q_TYPE=() Q_WHEN=() Q_CHOICES=() Q_EN=() Q_DE=() PLAN_PACKAGES=()
  declare -ga QUESTIONS=() PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=()
  QUESTION_FD='' QUESTION_OUTPUT=2
  declare -gA SECRET=() SECRET_STATUS=() FILE_ANSWERS=()
  MODE='' ANSWERS_FILE='' TTY_STATE='' SECRET_INPUT=''
  declare -gA STATE=() PROPERTIES=() BUILD=()
  declare -gA RESULT=()
  declare -gA BOOTSTRAP_APT=()
  BOOTSTRAP_APPROVED=no BOOTSTRAP_WEB=''
  declare -ga PRIVATE_DIRS=() PRIVATE_FILES=()
  CORE_CONFIRM=no CORE_HOME=/home/grafioschtrader
  CORE_REPO=/home/grafioschtrader/build/grafioschtrader
  CORE_REMOTE=https://github.com/grafioschtrader/grafioschtrader.git
  case "${LC_ALL:-${LC_MESSAGES:-${LANG:-en}}}" in de*) LANG_CODE=de ;; esac
}

gt_message() {
  local key=$1
  shift
  local en de
  case "$key" in
    title) en='Grafioschtrader installation check (read-only)'; de='Grafioschtrader Installationsprüfung (nur lesend)' ;;
    facts) en='Inventory'; de='Bestandsaufnahme' ;;
    actions) en='Compatibility recommendations (no changes made)'; de='Kompatibilitätsempfehlungen (keine Änderungen)' ;;
    notes) en='Notes'; de='Hinweise' ;;
    requirements) en='Source requirements unavailable; built-in floors are provisional'; de='Quellcode-Anforderungen unbekannt; eingebaute Mindestversionen sind vorläufig' ;;
    unavailable) en='Probe unavailable or failed: %s'; de='Prüfung nicht verfügbar oder fehlgeschlagen: %s' ;;
    stale) en='APT metadata is absent or older than 24 hours; package candidates may be stale'; de='APT-Metadaten fehlen oder sind älter als 24 Stunden; Paketkandidaten können veraltet sein' ;;
    existing) en='Existing installation: use %s; bootstrap must leave it untouched'; de='Bestehende Installation: %s verwenden; Erstinstallation darf sie nicht ändern' ;;
    partial) en='Foreign installation pieces must not be adopted or removed'; de='Fremde Installationsteile dürfen weder übernommen noch entfernt werden' ;;
    running) en='Unfinished installer state; resume with --install-core, --install-app, --install-web or --check-mail'; de='Unfertiger Installationszustand; mit --install-core, --install-app, --install-web oder --check-mail fortsetzen' ;;
    memory) en='Low RAM: %s'; de='Wenig RAM: %s' ;;
    legacy) en='Legacy platform: %s'; de='Ältere Plattform: %s' ;;
    footer) en='Check finished: %s blocking recommendations. Run without a mode to review the installation plan.'; de='Prüfung beendet: %s blockierende Empfehlungen. Ohne Modus starten, um den Installationsplan zu prüfen.' ;;
    root) en='Run inventory modes as root for a complete inventory.'; de='Bestandsaufnahme für einen vollständigen Bericht als root ausführen.' ;;
    mode) en='Use --check, --dry-run, --prepare, --install-core, --install-app, --install-web or --check-mail; see --help.'; de='--check, --dry-run, --prepare, --install-core, --install-app, --install-web oder --check-mail verwenden; siehe --help.' ;;
    pipe) en='Download the script to a file before running it; piped execution is refused.'; de='Skript vor dem Ausführen als Datei speichern; Ausführung über eine Pipe wird abgelehnt.' ;;
    lock) en='An installer is running or its existing lock cannot be read.'; de='Ein Installer läuft oder seine vorhandene Sperre kann nicht gelesen werden.' ;;
    *) en=$key; de=$key ;;
  esac
  # The format strings above are installer-owned translations, never probe output.
  # shellcheck disable=SC2059
  if [[ "$LANG_CODE" == de ]]; then printf "$de\n" "$@"; else printf "$en\n" "$@"; fi
}

gt_note() {
  local severity=$1 message
  shift
  message=$(gt_message "$@")
  NOTES+=("$severity: $message")
}

# Values from configurations and subprocesses never become shell code or terminal control sequences.
gt_safe() { printf '%s' "$1" | LC_ALL=C tr -d '\000-\010\013-\037\177'; }
gt_path() { printf '%s%s' "$ROOT" "$1"; }
gt_probe() {
  command -v "$1" >/dev/null 2>&1 || return 127
  timeout --signal=TERM --kill-after=2 12 "$@" 2>/dev/null
}
gt_capture() {
  local key=$1 value
  shift
  if value=$(gt_probe "$@"); then FACT[$key]=$value; else
    FACT[$key]=unknown
    gt_note UNKNOWN unavailable "$key"
  fi
}
gt_literal() {
  # Read a single literal key=value. Reject duplicate keys, substitutions and shell operators.
  local file=$1 key=$2 value
  [[ -r "$file" ]] || return 1
  value=$(awk -v key="$key" 'index($0,key "=")==1 {n++; v=substr($0,length(key)+2)} END {if(n==1) print v; else exit 1}' "$file") || return 1
  value=${value%$'\r'}
  if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value=${value:1:${#value}-2}; fi
  [[ "$value" != *[\$\`\;\\]* ]] || return 1
  printf '%s' "$value"
}

gt_version_at_least() {
  local actual=$1 required=$2 i
  local -a a b
  [[ "$actual" =~ ^[0-9]+(\.[0-9]+){0,3}$ && "$required" =~ ^[0-9]+(\.[0-9]+){0,3}$ ]] || return 1
  IFS=. read -r -a a <<< "$actual"
  IFS=. read -r -a b <<< "$required"
  for i in 0 1 2 3; do
    (( 10#${a[i]:-0} > 10#${b[i]:-0} )) && return 0
    (( 10#${a[i]:-0} < 10#${b[i]:-0} )) && return 1
  done
  return 0
}
gt_node_range_valid() {
  [[ "$1" =~ ^(\^|\>=)[0-9]+\.[0-9]+\.[0-9]+(\ \|\|\ (\^|\>=)[0-9]+\.[0-9]+\.[0-9]+)*$ ]]
}
gt_node_satisfies() {
  local version=${1#v} range=$2 clause required upper major minor patch
  [[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
  gt_node_range_valid "$range" || return 1
  while IFS= read -r clause; do
    required=${clause#^}; required=${required#>=}
    gt_version_at_least "$version" "$required" || continue
    [[ "$clause" == '>='* ]] && return 0
    IFS=. read -r major minor patch <<< "$required"
    if (( major > 0 )); then upper="$((major+1)).0.0"
    elif (( minor > 0 )); then upper="0.$((minor+1)).0"
    else upper="0.0.$((patch+1))"; fi
    gt_version_at_least "$version" "$upper" || return 0
  done < <(printf '%s\n' "$range" | sed 's/ || /\n/g')
  return 1
}
gt_parse_requirements() {
  local java node cli
  java=$(gt_literal "$1" java_required) && node=$(gt_literal "$1" node_required) &&
    cli=$(gt_literal "$1" angular_cli_required) || return 1
  [[ "$java" =~ ^[1-9][0-9]*$ && "$cli" =~ ^[1-9][0-9]*$ ]] && gt_node_range_valid "$node" || return 1
  JAVA_REQUIRED=$java NODE_REQUIRED=$node CLI_REQUIRED=$cli
}
gt_source_revision() {
  local remote commit base
  FACT[source.requirements]=fallback FACT[source.commit]=unknown FACT[source.collation]=unknown
  if [[ "${STATE[planned_commit]:-}" =~ ^[a-f0-9]{40}$ ]]; then remote="${STATE[planned_commit]} refs/heads/master"
  else remote=$(gt_probe git -c credential.helper= -c core.askPass= ls-remote \
      https://github.com/grafioschtrader/grafioschtrader.git refs/heads/master) || remote=''; fi
  if [[ -n "$remote" ]]; then
    read -r commit _ <<< "$remote"
    if [[ "$commit" =~ ^[a-f0-9]{40}$ ]]; then
      FACT[source.commit]=$commit
      base="https://raw.githubusercontent.com/grafioschtrader/grafioschtrader/$commit"
      if gt_probe curl --disable -fsS --connect-timeout 4 --max-time 10 \
          "$base/util/shellscripts/checkversion.sh" > "$SCRATCH/checkversion.sh" &&
        gt_probe curl --disable -fsS --connect-timeout 4 --max-time 10 \
          "$base/backend/grafioschtrader-server/src/main/resources/application.properties" > "$SCRATCH/application.properties" &&
        gt_parse_requirements "$SCRATCH/checkversion.sh"; then
        FACT[source.requirements]=remote
        FACT[source.collation]=no
        if grep -E '^spring\.datasource\.hikari\.connection-init-sql[[:space:]]*=' "$SCRATCH/application.properties" |
            grep -q "time_zone.*character_set_collations.*utf8mb3=utf8mb3_general_ci,utf8mb4=utf8mb4_general_ci"; then
          FACT[source.collation]=yes
        fi
      fi
    fi
  fi
  [[ "${FACT[source.requirements]}" == remote ]] || gt_note UNKNOWN requirements
  FACT[required.java]=$JAVA_REQUIRED FACT[required.node]=$NODE_REQUIRED FACT[required.angular_cli]=$CLI_REQUIRED
}
