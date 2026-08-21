#!/bin/bash
#
# Spread the daily data-fetch jobs of this installation over a random time slot.
#
# Grafioschtrader ships every installation with the same cron defaults, so without a change
# every GT instance world-wide queries the free public data providers (Yahoo, Finnhub,
# Boursorama, ...) within the same minute. This script picks one random anchor between 05:00
# and 07:00 LOCAL time and moves the whole morning chain there, as long as all of its
# properties are still at the values Grafioschtrader shipped. A value that somebody changed
# is never overwritten - which also makes the script idempotent, because after the first run
# the values differ from the defaults.
#
#   gtcronrandom.sh --file <properties file> [--force] [--dry-run]
#
#   --file      properties file to patch (required)
#   --force     draw a new slot even when the values were changed already
#   --dry-run   print what would be written, change nothing
#
# Set GT_CRON_RANDOMIZE=off to skip the randomization entirely.
#
# The backend evaluates the cron expressions in UTC (@Scheduled(..., zone = "UTC")),
# independent of the server's time zone, so the local anchor is converted to UTC here.
#

set -uo pipefail

PROP_FILE=""
FORCE=false
DRY_RUN=false

# ---------------------------------------------------------------------------
# The morning chain, in execution order. The FIRST entry is the anchor; the
# offset of every other entry is derived from these defaults, so the relative
# gaps between the jobs are data rather than magic numbers.
#
# Those gaps are not cosmetic: gt.standing.order.execution needs the closing
# prices written by gt.eod.cron.quotation, and the two checks report on what
# the jobs before them have produced. Randomizing one key alone re-orders them.
#
# IMPORTANT: this table mirrors
# backend/grafioschtrader-server/src/main/resources/application.properties.
# When a default is changed there, change it here as well - otherwise this
# script mistakes the new default for a user setting and randomizes nothing.
# ---------------------------------------------------------------------------
CRON_KEYS=(
  "gt.eod.cron.quotation"
  "gt.dividend.update.data"
  "gt.standing.order.execution"
  "gt.check.inactive.dividend"
  "gt.hold.consistency.check"
)
CRON_DEFAULTS=(
  "0 54 05 * * ?"
  "0 0 06 * * ?"
  "0 15 06 * * ?"
  "0 30 06 * * ?"
  "0 45 06 * * ?"
)

# Anchor window in local time: 05:00 up to and including 06:59. The chain spans
# 51 minutes, so its last job still starts before 08:00 local time.
WINDOW_START_MIN=$((5 * 60))
WINDOW_SLOTS=120

usage() {
  sed -n '3,22p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -f|--file) PROP_FILE="${2:-}"; shift 2 ;;
    --force)   FORCE=true; shift ;;
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "gtcronrandom.sh: unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ "${GT_CRON_RANDOMIZE:-}" == "off" ]]; then
  echo "gtcronrandom.sh: GT_CRON_RANDOMIZE=off - the cron times are left unchanged."
  exit 0
fi

if [[ -z "$PROP_FILE" ]]; then
  echo "gtcronrandom.sh: --file is required" >&2
  usage >&2
  exit 2
fi
if [[ ! -f "$PROP_FILE" ]]; then
  echo "gtcronrandom.sh: properties file does not exist: $PROP_FILE" >&2
  exit 3
fi
if [[ ! -w "$PROP_FILE" ]] && [[ "$DRY_RUN" == false ]]; then
  echo "gtcronrandom.sh: properties file is not writable: $PROP_FILE" >&2
  exit 3
fi

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

# Value of the last ACTIVE (uncommented) definition of a key; empty when the key
# is absent or present only as a comment. A commented line is never a setting.
active_value() {
  local key="$1"
  local escaped="${key//./\\.}"
  sed -n "s|^[[:space:]]*${escaped}[[:space:]]*=[[:space:]]*\(.*\)\$|\1|p" "$PROP_FILE" \
    | sed 's/[[:space:]]*$//' | tail -n 1
}

# True when the key appears as a commented-out definition (# key=... or ! key=...).
has_commented_line() {
  local key="$1"
  local escaped="${key//./\\.}"
  grep -qE "^[[:space:]]*[#!][[:space:]]*${escaped}[[:space:]]*=" "$PROP_FILE"
}

# Cron fields are "second minute hour ...", so "0 54 05 * * ?" is 05:54.
cron_to_minutes() {
  local fields
  read -r -a fields <<< "$1"
  echo $(( 10#${fields[2]} * 60 + 10#${fields[1]} ))
}

# The seconds field of the shipped default.
cron_seconds() {
  local fields
  read -r -a fields <<< "$1"
  echo "${fields[0]}"
}

# Everything after the hour field, e.g. "* * ?" - kept from the shipped default
# instead of being hardcoded.
cron_tail() {
  local fields
  read -r -a fields <<< "$1"
  echo "${fields[*]:3}"
}

hhmm() {
  printf '%02d:%02d' $(( $1 / 60 % 24 )) $(( $1 % 60 ))
}

# Minutes since midnight in local time -> minutes since midnight in UTC.
# The local time is turned into an epoch second first and only then rendered in
# UTC; "date -u -d HH:MM" alone would not do, because -u makes date read the
# input as UTC as well. Going through the epoch also gets the DST state that is
# in force today right. Two fallbacks for a date(1) without -d.
local_minutes_to_utc() {
  local local_min="$1"
  local stamp epoch utc offset
  stamp="$(hhmm "$local_min")"

  if epoch="$(date -d "$stamp" '+%s' 2>/dev/null)" && [[ "$epoch" =~ ^[0-9]+$ ]] \
      && utc="$(date -u -d "@$epoch" '+%H:%M' 2>/dev/null)" && [[ "$utc" =~ ^[0-9]{2}:[0-9]{2}$ ]]; then
    echo $(( 10#${utc%%:*} * 60 + 10#${utc##*:} ))
    return 0
  fi

  if offset="$(date '+%z' 2>/dev/null)" && [[ "$offset" =~ ^([+-])([0-9]{2})([0-9]{2})$ ]]; then
    local off_min=$(( 10#${BASH_REMATCH[2]} * 60 + 10#${BASH_REMATCH[3]} ))
    [[ "${BASH_REMATCH[1]}" == "-" ]] && off_min=$(( -off_min ))
    echo $(( ( local_min - off_min + 1440 ) % 1440 ))
    return 0
  fi

  echo "gtcronrandom.sh: cannot determine the UTC offset - treating the local time as UTC." >&2
  echo "$local_min"
}

# ---------------------------------------------------------------------------
# Guard: randomize only while every key is still at its shipped default
# ---------------------------------------------------------------------------
customized=false
for i in "${!CRON_KEYS[@]}"; do
  key="${CRON_KEYS[$i]}"
  default="${CRON_DEFAULTS[$i]}"
  current="$(active_value "$key")"
  if [[ -n "$current" && "$current" != "$default" ]]; then
    echo "gtcronrandom.sh: $key is '$current' instead of the shipped default '$default'."
    customized=true
  fi
done

if [[ "$customized" == true && "$FORCE" == false ]]; then
  echo "gtcronrandom.sh: the schedule in $PROP_FILE was adjusted already - nothing is changed."
  echo "gtcronrandom.sh: use --force to draw a new random slot for all of these properties."
  exit 0
fi

# ---------------------------------------------------------------------------
# Draw the anchor and derive the whole chain from it
# ---------------------------------------------------------------------------
anchor_local=$(( WINDOW_START_MIN + RANDOM % WINDOW_SLOTS ))
anchor_utc=$(local_minutes_to_utc "$anchor_local")
anchor_default_min=$(cron_to_minutes "${CRON_DEFAULTS[0]}")

echo "gtcronrandom.sh: random daily schedule for $PROP_FILE"
printf '  %-30s %-7s %-7s %s\n' "property" "local" "UTC" "cron"

tmp_file="$(mktemp "${TMPDIR:-/tmp}/gtcronrandom.XXXXXX")" || exit 4
trap 'rm -f "$tmp_file"' EXIT
cp "$PROP_FILE" "$tmp_file" || exit 4

appended=""
for i in "${!CRON_KEYS[@]}"; do
  key="${CRON_KEYS[$i]}"
  default="${CRON_DEFAULTS[$i]}"
  escaped="${key//./\\.}"

  offset=$(( $(cron_to_minutes "$default") - anchor_default_min ))
  new_utc=$(( ( anchor_utc + offset + 1440 ) % 1440 ))
  new_local=$(( ( anchor_local + offset + 1440 ) % 1440 ))
  new_value="$(printf '%s %d %02d %s' \
    "$(cron_seconds "$default")" $(( new_utc % 60 )) $(( new_utc / 60 )) "$(cron_tail "$default")")"

  printf '  %-30s %-7s %-7s %s\n' "$key" "$(hhmm "$new_local")" "$(hhmm "$new_utc")" "$new_value"

  if [[ -n "$(active_value "$key")" ]]; then
    # Replace the active definition in place, keeping its position in the file.
    sed -i "s|^\([[:space:]]*\)${escaped}[[:space:]]*=.*\$|\1${key}=${new_value}|" "$tmp_file"
  elif has_commented_line "$key"; then
    # Turn the first commented template line into an active one.
    sed -i "0,/^[[:space:]]*[#!][[:space:]]*${escaped}[[:space:]]*=.*\$/s||${key}=${new_value}|" "$tmp_file"
  else
    appended="${appended}${key}=${new_value}
"
  fi
done

if [[ -n "$appended" ]]; then
  {
    echo ""
    echo "# Daily data-fetch schedule, spread over a random slot by gtcronrandom.sh."
    echo "# The times are UTC. Change a value here and the script leaves all of them alone."
    printf '%s' "$appended"
  } >> "$tmp_file"
fi

if [[ "$DRY_RUN" == true ]]; then
  echo "gtcronrandom.sh: --dry-run - $PROP_FILE was not modified."
  exit 0
fi

cat "$tmp_file" > "$PROP_FILE" || exit 4
echo "gtcronrandom.sh: $PROP_FILE updated. Change any of these values to keep your own schedule."
