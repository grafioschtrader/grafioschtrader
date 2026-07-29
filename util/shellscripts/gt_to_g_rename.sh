#!/bin/bash
#
# Rename library-owned deployment properties from the "gt." to the "g." prefix.
#
# Background (GitHub issue #75): keys read by the reusable grafiosch library
# (grafiosch-base / grafiosch-server-base) are moving from the application
# prefix "gt." to the library prefix "g.". merger.sh matches property keys by
# exact string equality, so without this migration it would not recognise the
# user's "gt.jwt.secret" in the new "g.jwt.secret" template and would silently
# replace the encrypted secret with the template placeholder.
#
# The script is safe to run on every update. It only does something when the
# release actually carries the renamed keys - see "activation gate" below.
#
#   gt_to_g_rename.sh -s <template> -i <file> [-i <file> ...]
#   gt_to_g_rename.sh --force       -i <file> [-i <file> ...]
#
#   -s, --sample <file>   Activation gate: the application.properties of the
#                         new release. The mapping is applied only if that file
#                         already uses at least one of the new "g." keys.
#   -i, --input <file>    Properties file to migrate in place. May be repeated.
#                         A file that does not exist is skipped silently.
#   -f, --force           Apply the mapping without consulting a template.
#   -h, --help            Show this help.
#
# Exit codes:
#   0  migrated, or nothing to do
#   2  usage error
#   3  a file was named but cannot be read
#   4  the directory of a file to migrate is not writable
#   5  a file contains both a legacy key and its new name
#   6  temporary file could not be created
#   7  permissions could not be preserved
#   8  the migrated file could not be put in place
#
set -uo pipefail

usage() {
  cat >&2 <<'EOF'
Usage:
  gt_to_g_rename.sh -s <template> -i <file> [-i <file> ...]
  gt_to_g_rename.sh --force       -i <file> [-i <file> ...]

  -s, --sample <file>   Activation gate: the application.properties of the new
                        release. The mapping is applied only if that file
                        already uses at least one of the new "g." keys.
  -i, --input <file>    Properties file to migrate in place. May be repeated.
                        A file that does not exist is skipped silently.
  -f, --force           Apply the mapping without consulting a template.
  -h, --help            Show this help.
EOF
}

SAMPLE_FILE=""
FORCE=false
declare -a INPUT_FILES=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    -s|--sample)
      if [[ $# -lt 2 ]]; then
        echo "Error: $1 requires a file" >&2
        usage
        exit 2
      fi
      SAMPLE_FILE="$2"
      shift 2
      ;;
    -i|--input)
      if [[ $# -lt 2 ]]; then
        echo "Error: $1 requires a file" >&2
        usage
        exit 2
      fi
      INPUT_FILES+=("$2")
      shift 2
      ;;
    -f|--force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Error: Unknown argument: $1" >&2
      usage
      exit 2
      ;;
  esac
done

if [[ ${#INPUT_FILES[@]} -eq 0 ]]; then
  echo "Error: No input file was specified" >&2
  usage
  exit 2
fi

if [[ "$FORCE" != true && -z "$SAMPLE_FILE" ]]; then
  echo "Error: Either --sample or --force is required" >&2
  usage
  exit 2
fi

# The complete set of library-owned deployment properties. Application-owned
# keys such as gt.datafeed.*, gt.eod.*, gt.use.* and gt.gtnet.exchange.sync.cron
# are deliberately absent - they keep the "gt." prefix.
declare -A RENAME=(
  ["gt.jwt.secret"]="g.jwt.secret"
  ["gt.main.user.admin.mail"]="g.main.user.admin.mail"
  ["gt.allowed.users"]="g.allowed.users"
  ["gt.demo.account.pattern.de"]="g.demo.account.pattern.de"
  ["gt.demo.account.pattern.en"]="g.demo.account.pattern.en"
  ["gt.purge.cron.expression"]="g.purge.cron.expression"
  ["gt.purge.task.data"]="g.purge.task.data"
  ["gt.gtnet.log.aggregation.cron"]="g.gnet.log.aggregation.cron"
  ["gt.gtnet.future.message.cron"]="g.gnet.future.message.cron"
)

# Extract the key exactly as merger.sh does: leading whitespace and a single
# leading # or ! marker are ignored, and only lines containing '=' are
# properties. Returns 1 when the line carries no key.
extract_key() {
  local line="$1"
  local left

  [[ "$line" == *"="* ]] || return 1
  left="${line%%=*}"
  left="${left#"${left%%[![:space:]]*}"}"

  if [[ "$left" == \#* || "$left" == \!* ]]; then
    left="${left:1}"
    left="${left#"${left%%[![:space:]]*}"}"
  fi

  left="${left%"${left##*[![:space:]]}"}"
  [[ -n "$left" ]] || return 1
  printf '%s' "$left"
}

# Fill the associative array named by $1 with every property key of file $2.
collect_keys() {
  local -n _keys="$1"
  local file="$2"
  local line key

  while IFS= read -r line || [[ -n "$line" ]]; do
    key=$(extract_key "$line") || continue
    _keys["$key"]=1
  done < "$file"
}

# ---------------------------------------------------------------------------
# Activation gate
# ---------------------------------------------------------------------------
# The rename is applied only once the shipped template actually uses the new
# names. Before that release the script is a no-op, which is what makes it safe
# to call unconditionally from gtupdate.sh on every update. The gate is global
# rather than per key because g.gnet.log.aggregation.cron is not written to any
# properties file - it only exists as an annotation default.
if [[ "$FORCE" != true ]]; then
  if [[ ! -f "$SAMPLE_FILE" || ! -r "$SAMPLE_FILE" ]]; then
    echo "Error: Template does not exist or is not readable: $SAMPLE_FILE" >&2
    exit 3
  fi

  declare -A SAMPLE_KEYS=()
  collect_keys SAMPLE_KEYS "$SAMPLE_FILE"

  gate_open=false
  for old_key in "${!RENAME[@]}"; do
    if [[ -n "${SAMPLE_KEYS[${RENAME[$old_key]}]+present}" ]]; then
      gate_open=true
      break
    fi
  done

  if [[ "$gate_open" != true ]]; then
    echo "Property migration not applicable - this release still uses the gt.* names"
    exit 0
  fi
fi

# ---------------------------------------------------------------------------
# Preflight: inspect every file before a single one is written, so that a
# conflict in the second file cannot leave the first one half migrated.
# ---------------------------------------------------------------------------
declare -a PENDING=()
declare -a MISSING=()
total_legacy=0

for input_file in "${INPUT_FILES[@]}"; do
  if [[ ! -e "$input_file" ]]; then
    MISSING+=("$input_file")
    continue
  fi

  if [[ ! -f "$input_file" || ! -r "$input_file" ]]; then
    echo "Error: Input file is not a readable file: $input_file" >&2
    exit 3
  fi

  input_file=$(readlink -f -- "$input_file")

  declare -A FILE_KEYS=()
  collect_keys FILE_KEYS "$input_file"

  file_legacy=0
  for old_key in "${!RENAME[@]}"; do
    new_key="${RENAME[$old_key]}"
    if [[ -n "${FILE_KEYS[$old_key]+present}" && -n "${FILE_KEYS[$new_key]+present}" ]]; then
      echo "Error: Both '$old_key' and '$new_key' exist in $input_file" >&2
      echo "Resolve the conflicting values before retrying the update" >&2
      exit 5
    fi
    if [[ -n "${FILE_KEYS[$old_key]+present}" ]]; then
      file_legacy=$((file_legacy + 1))
    fi
  done
  unset FILE_KEYS

  if [[ $file_legacy -gt 0 ]]; then
    if [[ ! -w "$(dirname -- "$input_file")" ]]; then
      echo "Error: Directory is not writable: $(dirname -- "$input_file")" >&2
      exit 4
    fi
    PENDING+=("$input_file")
    total_legacy=$((total_legacy + file_legacy))
  fi
done

for missing in "${MISSING[@]-}"; do
  [[ -n "$missing" ]] && echo "Skipped, not present: $missing"
done

if [[ ${#PENDING[@]} -eq 0 ]]; then
  echo "No legacy Grafiosch properties found - nothing to migrate"
  exit 0
fi

# ---------------------------------------------------------------------------
# Rewrite. Only the key token is replaced; leading whitespace, the # or !
# comment marker, the spacing around '=' and the whole value are preserved so
# that merger.sh still sees the user's original user/developer-controlled state.
# ---------------------------------------------------------------------------
TEMP_FILE=""

cleanup() {
  if [[ -n "${TEMP_FILE:-}" && -e "$TEMP_FILE" ]]; then
    rm -f -- "$TEMP_FILE"
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

for input_file in "${PENDING[@]}"; do
  input_dir=$(dirname -- "$input_file")
  input_name=$(basename -- "$input_file")

  TEMP_FILE=$(mktemp "$input_dir/.${input_name}.gt-to-g.XXXXXX") || {
    echo "Error: Could not create a temporary file in $input_dir" >&2
    exit 6
  }

  while IFS= read -r line || [[ -n "$line" ]]; do
    key=$(extract_key "$line") || {
      printf '%s\n' "$line" >> "$TEMP_FILE"
      continue
    }

    if [[ -n "${RENAME[$key]+mapped}" ]]; then
      left="${line%%=*}"
      right="${line#*=}"
      prefix="${left%%"$key"*}"
      suffix="${left#*"$key"}"
      printf '%s%s%s=%s\n' "$prefix" "${RENAME[$key]}" "$suffix" "$right" >> "$TEMP_FILE"
    else
      printf '%s\n' "$line" >> "$TEMP_FILE"
    fi
  done < "$input_file"

  if ! chmod --reference="$input_file" "$TEMP_FILE"; then
    echo "Error: Could not preserve the permissions of $input_file" >&2
    exit 7
  fi

  if ! mv -f -- "$TEMP_FILE" "$input_file"; then
    echo "Error: Could not replace $input_file with the migrated file" >&2
    exit 8
  fi

  TEMP_FILE=""
  echo "Migrated $input_file"
done

trap - EXIT HUP INT TERM
echo "Renamed $total_legacy legacy Grafiosch property key(s)"
