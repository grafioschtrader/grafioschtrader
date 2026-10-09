gt_core_error() {
  printf '%s: %s\n' "$(gt_text 'Installation core stopped' 'Installationskern angehalten')" "$1" >&2
  return 2
}

# Every parent is checked before creating state or following a managed path. State never
# grants permission to adopt a symlink or a file edited after the last journal entry.
gt_no_symlinks() {
  local path=$1
  while [[ "$path" != / && -n "$path" ]]; do
    [[ ! -L "$path" ]] || return 1
    path=${path%/*}
  done
}

gt_private_dir() {
  local path=$1
  gt_no_symlinks "$path" || return 2
  if [[ ! -e "$path" ]]; then mkdir -m 700 -- "$path" || return 2; fi
  [[ -d "$path" && "$(stat -c '%u:%a' "$path")" == "$EUID:700" ]]
}

gt_private_read() {
  # Read once from the checked descriptor. NUL is an error, not a silently removed byte.
  local file=$1 fd before after
  PRIVATE_CONTENT=''
  gt_no_symlinks "$file" && [[ -f "$file" ]] || return 2
  before=$(stat -c '%u:%a:%d:%i' "$file") || return 2
  [[ "$before" == "$EUID:600:"* ]] || return 2
  exec {fd}<"$file" || return 2
  after=$(stat -Lc '%u:%a:%d:%i' "/dev/fd/$fd")
  if [[ "$before" != "$after" ]] || IFS= read -r -d '' PRIVATE_CONTENT <&"$fd"; then
    exec {fd}<&-; PRIVATE_CONTENT=''; return 2
  fi
  exec {fd}<&-
}

gt_atomic_map() {
  local file=$1 name=$2 temporary key
  local -n values=$name
  gt_no_symlinks "$file" || return 2
  temporary=$(umask 077; mktemp "${file}.XXXXXX") || return 2
  PRIVATE_FILES+=("$temporary")
  for key in "${!values[@]}"; do
    if [[ "$key" == *[$'\001'-$'\037'$'\177']* || "${values[$key]}" == *[$'\001'-$'\037'$'\177']* ]]; then
      rm -f -- "$temporary"; return 2
    fi
    printf '%s=%s\n' "$key" "${values[$key]}" >> "$temporary" || { rm -f -- "$temporary"; return 2; }
  done
  # A completed write-ahead entry must survive a host/VM power loss. Rename is
  # atomic for readers but does not itself flush either file data or directory metadata.
  if ! chmod 600 "$temporary" || ! sync -f "$temporary" || ! mv -T -- "$temporary" "$file"; then
    rm -f -- "$temporary"; return 2
  fi
  sync -f "${file%/*}"
}

gt_state_save() {
  gt_result_remember_warnings || return 1
  gt_atomic_map "$(gt_path /var/lib/gt-install/state)" STATE
}

gt_state_load() {
  local line key value
  STATE=() ANSWER=()
  [[ "$(stat -c '%u:%a' "$(gt_path /var/lib/gt-install)" 2>/dev/null)" == "$EUID:700" ]] || return 2
  gt_private_read "$(gt_path /var/lib/gt-install/state)" || return 2
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    key=${line%%=*}; value=${line#*=}
    [[ "$line" == *=* && "$key" =~ ^[a-zA-Z0-9_.+-]+$ && -z "${STATE[$key]+set}" &&
      "$value" != *[$'\001'-$'\037'$'\177']* ]] || return 2
    case "$key" in
      schema|status|scope|run_id|planned_commit|java_home|maven|new_database_server|database_before|account_before|\
        root_auth|step.*|resource.*|file.*) ;;
      built_commit) [[ "$value" =~ ^[a-f0-9]{40}$ ]] || return 2 ;;
      installer_sha256) [[ "$value" =~ ^[a-f0-9]{64}$ ]] || return 2 ;;
      completed_at) [[ "$value" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] || return 2 ;;
      toolchain.java.package)
        [[ "$value" == none || "$value" == archive || "$value" =~ ^openjdk-[0-9]+-jdk-headless$ ]] || return 2 ;;
      toolchain.maven.package) [[ "$value" == none || "$value" == maven || "$value" == archive ]] || return 2 ;;
      toolchain.java.version|toolchain.maven.version) [[ "$value" =~ ^[a-zA-Z0-9.+:~_-]+$ ]] || return 2 ;;
      # Archive fields are checked together after parsing (gt_toolchain_archive_valid).
      toolchain.java.source|toolchain.maven.source) [[ "$value" =~ ^(temurin|liberica|apache)$ ]] || return 2 ;;
      toolchain.java.file|toolchain.maven.file) [[ "$value" =~ ^[a-zA-Z0-9._+-]+\.tar\.gz$ ]] || return 2 ;;
      toolchain.java.checksum|toolchain.maven.checksum) [[ "$value" =~ ^sha(1|256|512):[a-f0-9]+$ ]] || return 2 ;;
      toolchain.alternatives) [[ "$value" == saved ]] || return 2 ;;
      alternative.*) [[ "${key#alternative.}" =~ ^[a-zA-Z0-9_.+-]+$ && "$value" == /* ]] || return 2 ;;
      build.*) gt_build_field "${key#build.}" "$value" || return 2 ;;
      answer.*) gt_validate_answer "${key#answer.}" "$value" || return 2; ANSWER[${key#answer.}]=$value ;;
      *) return 2 ;;
    esac
    STATE[$key]=$value
  done <<< "$PRIVATE_CONTENT"
  PRIVATE_CONTENT=''
  [[ "${STATE[schema]:-}" == 1 && "${STATE[scope]:-}" =~ ^(core|bootstrap)$ &&
      "${STATE[status]:-}" =~ ^(running|complete)$ &&
      "${STATE[run_id]:-}" =~ ^[a-f0-9]{32}$ && "${STATE[planned_commit]:-}" =~ ^[a-f0-9]{40}$ ]] || return 2
  for key in java_home maven; do
    if [[ "${STATE[$key]:-}" == pending ]]; then
      [[ "${STATE[step.toolchains]:-}" == pending || "${STATE[step.toolchains]:-}" == running ]] || return 2
    else gt_valid_path "${STATE[$key]:-}" || return 2; fi
  done
  if [[ -n "${STATE[step.toolchains]:-}" ]]; then
    [[ "${STATE[step.toolchains]}" =~ ^(pending|running|complete)$ ]] || return 2
    for key in java maven; do
      [[ -n "${STATE[toolchain.$key.package]:-}" && -n "${STATE[toolchain.$key.version]:-}" ]] || return 2
      if [[ "${STATE[toolchain.$key.package]}" == archive ]]; then
        gt_toolchain_archive_valid "$key" "${STATE[toolchain.$key.source]:-}" "${STATE[toolchain.$key.version]}" \
          "${STATE[toolchain.$key.file]:-}" "${STATE[toolchain.$key.checksum]:-}" || return 2
      else
        for value in source file checksum; do
          [[ -z "${STATE[toolchain.$key.$value]+set}" ]] || return 2
        done
      fi
    done
  fi
  for key in java maven; do
    for value in source file checksum; do
      [[ -n "${STATE[step.toolchains]:-}" || -z "${STATE[toolchain.$key.$value]+set}" ]] || return 2
    done
  done
  for key in new_database_server database_before account_before; do
    [[ "${STATE[$key]:-}" == yes || "${STATE[$key]:-}" == no ]] || return 2
  done
  if [[ "${STATE[scope]}" == bootstrap ]]; then
    [[ "${STATE[resource.bootstrap_plan]:-}" =~ ^[a-f0-9]{64}$ ]] || return 2
  fi
  if [[ "${STATE[status]}" == complete ]]; then
    gt_completion_valid || return 2
  else
    [[ -z "${STATE[completed_at]:-}" ]] || return 2
  fi
}

# The local map is consumed by gt_atomic_map through its nameref.
# shellcheck disable=SC2034
gt_secrets_save() {
  local key
  local -A persisted=([INSTALL_ID]="${STATE[run_id]}")
  for key in DB_PASSWORD JASYPT_PASSWORD JWT_SECRET SMTP_PASSWORD DUCKDNS_TOKEN; do
    [[ -z "${SECRET[$key]+set}" ]] || persisted[$key]=${SECRET[$key]}
  done
  gt_atomic_map "$(gt_path /root/.gt-install/secrets)" persisted
}

gt_secrets_load() {
  local line key value id=''
  local -A loaded=()
  [[ "$(stat -c '%u:%a' "$(gt_path /root/.gt-install)" 2>/dev/null)" == "$EUID:700" ]] || return 2
  gt_private_read "$(gt_path /root/.gt-install/secrets)" || return 2
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    key=${line%%=*}; value=${line#*=}
    [[ "$line" == *=* ]] || return 2
    case "$key" in
      INSTALL_ID) [[ -z "$id" ]] || return 2; id=$value; continue ;;
      DB_PASSWORD|JASYPT_PASSWORD|JWT_SECRET|SMTP_PASSWORD|DUCKDNS_TOKEN) ;;
      *) return 2 ;;
    esac
    [[ -z "${loaded[$key]+set}" ]] && gt_secret_valid_for "$key" "$value" || return 2
    loaded[$key]=$value
  done <<< "$PRIVATE_CONTENT"
  PRIVATE_CONTENT=''
  [[ "$id" == "${STATE[run_id]}" && "${loaded[JWT_SECRET]:-}" =~ ^[a-zA-Z0-9]{48}$ ]] || return 2
  for key in DB_PASSWORD JASYPT_PASSWORD SMTP_PASSWORD DUCKDNS_TOKEN; do
    if gt_question_applies "$key"; then gt_valid_secret "${loaded[$key]:-}" || return 2
    else [[ -z "${loaded[$key]+set}" ]] || return 2; fi
  done
  SECRET=()
  for key in "${!loaded[@]}"; do SECRET[$key]=${loaded[$key]}; done
}
