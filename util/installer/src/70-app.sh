# While armbian-ramlog keeps /var/log in RAM, Armbian's boot-time hardware optimization rewrites /var/log/ to
# /var/log.hdd/ in every /etc/logrotate.d file, and back once it is disabled. Both spellings are the installer's
# content, which stays as Armbian left it.
gt_app_root_digest() {
  local digest
  if [[ "$1" == app_logrotate ]]; then digest=$(sed 's#/var/log\.hdd/#/var/log/#g' "$2" | sha256sum) || return 2
  else digest=$(sha256sum < "$2") || return 2; fi
  printf '%s' "${digest%% *}"
}

# Privileged configuration has a separate publisher: app-owned helpers must never
# determine the ownership or contents of sudoers, units or logrotate configuration.
gt_app_root_file() {
  local id=$1 input=$2 target=$3 mode=$4 digest current=absent temporary
  gt_no_symlinks "$target" || return 2
  digest=$(sha256sum "$input"); digest=${digest%% *}
  if [[ -e "$target" ]]; then
    [[ -f "$target" && "$(stat -c '%u:%a' "$target")" == "0:$mode" ]] || return 2
    current=$(gt_app_root_digest "$id" "$target") || return 2
    # Only the installer's own, unedited content is replaced, so a newer installer can repair what an older one
    # wrote; .previous covers an interrupted replacement.
    [[ -n "${STATE[file.$id]:-}" && ( "$current" == "${STATE[file.$id]}" ||
       "$current" == "${STATE[file.$id.previous]:-}" ) ]] || return 2
    [[ "$current" != "$digest" || "${STATE[file.$id]}" != "$digest" ]] || return 0
  else
    [[ -z "${STATE[file.$id]:-}" || "${STATE[file.$id]}" == "$digest" ||
       "${STATE[file.$id.previous]:-}" == absent ]] || return 2
  fi
  STATE[file.$id.previous]=$current
  gt_core_mark "file.$id" "$digest" || return 2
  temporary=$(mktemp "${target}.gt-install.XXXXXX") || return 2
  PRIVATE_FILES+=("$temporary")
  install -o root -g root -m "$mode" "$input" "$temporary" && mv -T "$temporary" "$target"
}

gt_app_sql() {
  local file result=0
  file=$(gt_db_options DB_PASSWORD grafioschtrader) || return 2
  mariadb "--defaults-file=$file" --protocol=TCP --host=127.0.0.1 --port=3306 \
    --user=grafioschtrader --database=grafioschtrader --batch --skip-column-names \
    --connect-timeout=4 2>/dev/null || result=$?
  rm -f -- "$file"
  return "$result"
}

gt_app_database() {
  local count failed
  gt_core_login DB_PASSWORD grafioschtrader TCP || return 2
  count=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='grafioschtrader';\n" | gt_app_sql) || return 2
  [[ "$count" =~ ^[0-9]+$ ]] || return 2
  if [[ -z "${STATE[step.app_start]:-}" ]]; then
    [[ "$count" == 0 ]] || { gt_core_error 'Database is no longer empty before the first authorized start.'; return 2; }
  elif (( count > 0 )); then
    # Only a journaled first start may resume a populated schema. Failed migrations
    # require diagnosis; never automatically drop, repair or baseline anything.
    failed=$(printf 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=0;\n' | gt_app_sql) || return 2
    [[ "$failed" == 0 ]] || { gt_core_error 'Failed Flyway migration; inspect the application log before resuming.'; return 2; }
  fi
}

gt_app_preflight() {
  local command file path entry commit changed digest mode
  gt_stage_preflight || return 2
  [[ "${STATE[step.core]:-}:${STATE[step.database]:-}:${STATE[step.configuration]:-}" == complete:complete:complete &&
    "${STATE[step.buildtools]:-}" == complete ]] || { gt_core_error 'Complete --install-core first.'; return 2; }
  [[ "$(cat "$(gt_path /proc/1/comm)")" == systemd ]] || return 2
  for command in systemctl systemd-analyze visudo logrotate sudo wget curl python3 flock mariadb ss; do
    command -v "$command" >/dev/null || { gt_core_error "Missing prerequisite: $command"; return 2; }
  done
  gt_core_config_valid || { gt_core_error 'Managed application configuration changed.'; return 2; }
  for file in DOCROOT TIMEZONE BACKEND_PORT WEBSERVER; do
    gt_validate_answer "$file" "${ANSWER[$file]:-}" || return 2
  done
  entry=$(getent passwd grafioschtrader) || return 2
  [[ "$entry" == *":GT installer ${STATE[run_id]}:$CORE_HOME:/bin/bash" ]] || return 2
  [[ "$(passwd -S grafioschtrader | awk '{print $2}')" == L ]] || return 2
  gt_no_symlinks "$CORE_REPO" || return 2
  commit=$(gt_as_app git -C "$CORE_REPO" rev-parse HEAD) || return 2
  [[ "$commit" == "${STATE[planned_commit]}" &&
    "$(gt_as_app git -C "$CORE_REPO" remote get-url origin)" == "$CORE_REMOTE" ]] || return 2
  changed=$(gt_as_app git -C "$CORE_REPO" diff HEAD --name-only) || return 2
  while IFS= read -r file; do
    case "$file" in ''|backend/grafioschtrader-server/src/main/resources/application.properties|backend/grafioschtrader-server/src/main/resources/application-production.properties) ;;
      *) gt_core_error "Source changed: $file"; return 2 ;;
    esac
  done <<< "$changed"
  for file in gtupbackend.sh gtupfrontend.sh; do
    gt_as_app git -C "$CORE_REPO" show "${STATE[planned_commit]}:util/shellscripts/$file" > "$SCRATCH/$file" || return 2
    grep -q GT_INSTALL_BUILD_ONLY "$SCRATCH/$file" || { gt_core_error 'Pinned source lacks the installer build-only contract.'; return 2; }
  done
  for file in node buildtools; do
    [[ "$file" != node || "${STATE[build.mode]}" == archive ]] || continue
    path=${STATE[build.prefix]}
    [[ "$file" != node ]] || path=${STATE[build.node_home]}
    gt_no_symlinks "$path" || return 2
    digest=$(gt_build_digest "$path") || return 2
    [[ "$digest" == "${STATE[file.$file]:-}" ]] || return 2
  done
  gt_app_targets || return 2
  gt_app_database
}

# Also run before the full bootstrap has created its user or database.
gt_app_targets() {
  local path entry file digest mode changed
  # Do not mask a distribution unit or any foreign override, including runtime units.
  for path in /run/systemd/system /usr/lib/systemd/system /lib/systemd/system /etc/systemd/system; do
    [[ ! -e "$(gt_path "$path/grafioschtrader.service.d")" && ! -L "$(gt_path "$path/grafioschtrader.service.d")" ]] || return 2
    [[ "$path" == /etc/systemd/system ]] && continue
    [[ ! -e "$(gt_path "$path/grafioschtrader.service")" && ! -L "$(gt_path "$path/grafioschtrader.service")" ]] || return 2
  done
  for entry in 'unit:/etc/systemd/system/grafioschtrader.service' 'sudoers:/etc/sudoers.d/grafioschtrader' 'logrotate:/etc/logrotate.d/grafioschtrader'; do
    file=${entry#*:}; path=$(gt_path "$file")
    gt_no_symlinks "$path" || return 2
    [[ ! -e "$path" || -n "${STATE[file.app_${entry%%:*}]:-}" ]] || { gt_core_error "Foreign file: $file"; return 2; }
    if [[ -e "$path" ]]; then
      digest=$(gt_app_root_digest "app_${entry%%:*}" "$path") || return 2
      mode=644; [[ "${entry%%:*}" != sudoers ]] || mode=440
      [[ "$digest" == "${STATE[file.app_${entry%%:*}]}" && "$(stat -c '%u:%a' "$path")" == "0:$mode" ]] || return 2
    fi
  done
  path=$(gt_path "${ANSWER[DOCROOT]}/grafioschtrader")
  gt_no_symlinks "$path" || return 2
  [[ ! -e "$path" || "${STATE[resource.app_frontend]:-}" == intent || "${STATE[resource.app_frontend]:-}" == owned ]] || return 2
  path=$(gt_path /var/log/grafioschtrader.log)
  gt_no_symlinks "$path" || return 2
  [[ ! -e "$path" || -n "${STATE[resource.app_log]:-}" ]] || return 2
  if [[ -z "${STATE[step.app_build]:-}" && -d "$CORE_HOME" ]]; then
    [[ -z "$(find "$CORE_HOME" -maxdepth 1 -name 'grafioschtrader*.jar' -print -quit)" ]] || return 2
  fi
  if [[ -z "${STATE[step.app_start]:-}" ]]; then
    changed=$(ss -Htln) || return 2
    if awk -v p="${ANSWER[BACKEND_PORT]}" -v q="${ANSWER[BACKEND_HTTP_PORT]:-0}" \
      '$4 ~ (":" p "$") || $4 ~ (":" q "$") {found=1} END {exit !found}' <<< "$changed"; then
      gt_core_error 'Selected backend port is occupied.'; return 2
    fi
  fi
  return 0
}

gt_app_scripts() {
  local file
  for file in gtupdate.sh gtupbackend.sh gtupfrontend.sh gtupfrontback.sh checkversion.sh merger.sh gt_to_g_rename.sh gtcronrandom.sh; do
    gt_as_app git -C "$CORE_REPO" show "${STATE[planned_commit]}:util/shellscripts/$file" > "$SCRATCH/$file" || return 2
    bash -n "$SCRATCH/$file" || return 2
    gt_core_publish "app_$file" "$SCRATCH/$file" "$CORE_HOME/$file" 700 || return 2
  done
  gt_as_app bash "$CORE_HOME/checkversion.sh" > "$SCRATCH/app-versions.log" 2>&1 || {
    gt_core_error 'Selected toolchain does not satisfy the pinned checkversion.sh.'; return 2;
  }
}

gt_app_resources() {
  local path current
  path=$(gt_path "${ANSWER[DOCROOT]}")
  gt_no_symlinks "$path" || return 2
  if [[ ! -e "$path" ]]; then
    gt_core_mark resource.app_docroot intent || return 2
    (umask 022; mkdir -p "$path") || return 2
    gt_core_mark resource.app_docroot owned || return 2
  fi
  [[ -d "$path" ]] || return 2
  path+=/grafioschtrader
  if [[ ! -e "$path" ]]; then
    gt_core_mark resource.app_frontend intent || return 2
    install -d -o grafioschtrader -g grafioschtrader -m 755 "$path" || return 2
  fi
  gt_no_symlinks "$path" && [[ -d "$path" && "$(stat -c %U "$path")" == grafioschtrader ]] || return 2
  gt_core_mark resource.app_frontend owned || return 2
  path=$(gt_path /var/log/grafioschtrader.log)
  if [[ ! -e "$path" ]]; then
    gt_core_mark resource.app_log intent || return 2
    install -o grafioschtrader -g grafioschtrader -m 640 /dev/null "$path" || return 2
  fi
  [[ -f "$path" && "$(stat -c '%U:%a' "$path")" == grafioschtrader:640 ]] || return 2
  gt_core_mark resource.app_log owned || return 2
  current=$(gt_core_run timedatectl show --property=Timezone --value) || return 2
  if [[ "$current" != "${ANSWER[TIMEZONE]}" ]]; then
    [[ -z "${STATE[resource.app_timezone]:-}" || "$current" == "${STATE[resource.app_timezone]}" ]] || return 2
    gt_core_mark resource.app_timezone "$current" || return 2
    gt_core_run timedatectl set-timezone "${ANSWER[TIMEZONE]}" || return 2
  fi
  gt_core_mark resource.app_timezone "${ANSWER[TIMEZONE]}"
}

gt_app_service() {
  local systemctl_path
  systemctl_path=$(command -v systemctl) || return 2
  [[ "$systemctl_path" == /usr/bin/systemctl || "$systemctl_path" == /bin/systemctl ]] || return 2
  printf 'grafioschtrader ALL=(root) NOPASSWD: %s stop grafioschtrader.service, %s start grafioschtrader.service\n' \
    "$systemctl_path" "$systemctl_path" > "$SCRATCH/sudoers"
  visudo -cf "$SCRATCH/sudoers" >/dev/null || return 2
  cat > "$SCRATCH/grafioschtrader.service" <<'UNIT'
[Unit]
Description=Grafioschtrader
After=network.target mariadb.service

[Service]
User=grafioschtrader
WorkingDirectory=/home/grafioschtrader
ExecStart=/home/grafioschtrader/grafioschtrader.sh
Type=simple
UMask=0077

[Install]
WantedBy=multi-user.target
UNIT
  systemd-analyze verify "$SCRATCH/grafioschtrader.service" || return 2
  cat > "$SCRATCH/logrotate" <<'ROTATE'
/var/log/grafioschtrader.log {
    weekly
    rotate 8
    compress
    missingok
    notifempty
    copytruncate
    su grafioschtrader grafioschtrader
}
ROTATE
  logrotate --debug "$SCRATCH/logrotate" > "$SCRATCH/logrotate-check" 2>&1 || return 2
  gt_app_root_file app_sudoers "$SCRATCH/sudoers" "$(gt_path /etc/sudoers.d/grafioschtrader)" 440 &&
    gt_app_root_file app_unit "$SCRATCH/grafioschtrader.service" "$(gt_path /etc/systemd/system/grafioschtrader.service)" 644 &&
    gt_app_root_file app_logrotate "$SCRATCH/logrotate" "$(gt_path /etc/logrotate.d/grafioschtrader)" 644 || return 2
  gt_core_run systemctl daemon-reload
}

gt_app_cron() {
  local saved temporary target
  saved="$(gt_path /var/lib/gt-install)/cron.properties"
  [[ "${STATE[step.app_cron]:-}" != complete ]] || return 0
  target="$CORE_REPO/backend/grafioschtrader-server/src/main/resources/application.properties"
  gt_no_symlinks "$saved" || return 2
  if [[ ! -e "$saved" ]]; then
    gt_core_mark step.app_cron running || return 2
    temporary=$(gt_as_app mktemp "$CORE_HOME/.gt-cron.XXXXXX") || return 2
    PRIVATE_FILES+=("$temporary")
    gt_as_app cp "$target" "$temporary" &&
      gt_as_app env TZ="${ANSWER[TIMEZONE]}" GT_CRON_RANDOMIZE=on bash "$CORE_HOME/gtcronrandom.sh" --file "$temporary" > "$SCRATCH/cron.log" 2>&1 || return 2
    install -o root -g root -m 600 "$temporary" "$saved.pending" && mv -T "$saved.pending" "$saved" || return 2
  fi
  [[ "${STATE[step.app_cron]:-}" == running ]] && gt_private_read "$saved" || return 2
  PRIVATE_CONTENT=''
  gt_core_publish properties "$saved" "$target" 600 || return 2
  gt_core_mark step.app_cron complete
}

gt_app_artifacts() {
  local digest file
  local -a jars=()
  while IFS= read -r -d '' file; do jars+=("$file"); done < <(find "$CORE_HOME" -maxdepth 1 -name 'grafioschtrader-server-*.jar' -print0)
  (( ${#jars[@]} == 1 )) || return 2
  for file in "${jars[0]}" "$(gt_path "${ANSWER[DOCROOT]}/grafioschtrader/index.html")"; do
    gt_no_symlinks "$file" && [[ -s "$file" && -f "$file" && "$(stat -c %U "$file")" == grafioschtrader ]] || return 2
    digest=$(sha256sum "$file"); digest=${digest%% *}
    if [[ "${STATE[step.app_build]:-}" == complete ]]; then
      [[ "$digest" == "${STATE[file.app_artifact_${file##*/}]:-}" ]] || return 2
    else STATE[file.app_artifact_${file##*/}]=$digest; fi
  done
}

gt_app_build() {
  local log commit
  log="$(gt_path /var/lib/gt-install)/app-build.log"
  if [[ "${STATE[step.app_build]:-}" == complete ]]; then gt_app_artifacts; return $?; fi
  [[ -z "${STATE[step.app_start]:-}" ]] || return 2
  gt_core_run systemctl is-active --quiet grafioschtrader.service && return 2
  gt_core_mark step.app_build running || return 2
  gt_no_symlinks "$log" || return 2
  # Keep output private: dependency tools can echo configuration on failure.
  (umask 077; touch "$log") && chmod 600 "$log" || return 2
  printf '%s\n' "Build log: $log"
  # Use the approved checkout; gtupdate.sh is reserved for later updates to master.
  # Cron slots have already been journaled; builds must not modify configuration.
  gt_as_app env GT_INSTALL_BUILD_ONLY=1 GT_CRON_RANDOMIZE=off bash "$CORE_HOME/gtupfrontend.sh" >> "$log" 2>&1 &&
    gt_as_app env GT_INSTALL_BUILD_ONLY=1 GT_CRON_RANDOMIZE=off bash "$CORE_HOME/gtupbackend.sh" >> "$log" 2>&1 || return 2
  gt_core_config_valid && gt_app_artifacts || return 2
  commit=$(gt_as_app git -C "$CORE_REPO" rev-parse HEAD) || return 2
  [[ "$commit" == "${STATE[planned_commit]}" ]] || return 2
  gt_core_mark built_commit "$commit" || return 2
  gt_core_mark step.app_build complete
}

gt_app_verify() {
  local port=${ANSWER[BACKEND_PORT]} response count pid listeners expected
  [[ "${ANSWER[WEBSERVER]}" != apache2 ]] || port=${ANSWER[BACKEND_HTTP_PORT]}
  response=$(curl --noproxy '*' --fail --silent --max-time 5 "http://127.0.0.1:$port/api/gtinfo") || return 1
  printf '%s' "$response" | python3 -c '@python-inline app-health.py@' 2>/dev/null || return 1
  gt_core_run systemctl is-active --quiet grafioschtrader.service || return 2
  pid=$(gt_core_run systemctl show --property=MainPID --value grafioschtrader.service) || return 2
  [[ "$pid" =~ ^[1-9][0-9]*$ ]] || return 2
  # Java's dual-stack sockets may expose an IPv4 bind as an IPv4-mapped IPv6
  # address. Compare its actual address, retaining rejection of wildcard binds.
  listeners=$(set -o pipefail; ss -Htlnp | awk -v pid="$pid" 'index($0,"pid=" pid ",") {print $4}' | python3 -c '
@python-inline listener-addresses.py@
' | LC_ALL=C sort) || return 2
  expected="127.0.0.1:$port"
  [[ "${ANSWER[WEBSERVER]}" != apache2 ]] || expected=$(printf '%s\n127.0.0.1:%s\n' "$expected" "${ANSWER[BACKEND_PORT]}" | LC_ALL=C sort)
  [[ "$listeners" == "$expected" ]] || return 2
  count=$(printf 'SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;\n' | gt_app_sql) || return 2
  [[ "$count" =~ ^[1-9][0-9]*$ ]] || return 2
  count=$(printf "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='grafioschtrader' AND COLLATION_NAME LIKE '%%uca1400%%';\n" | gt_app_sql) || return 2
  [[ "$count" == 0 ]] || return 2
  gt_app_database
}

gt_app_start_log() {
  python3 - "$1" "$(gt_path /var/log/grafioschtrader.log)" "${STATE[resource.app_log_cursor]:-}" <<'PY'
# @python startup-log.py
PY
}

gt_app_start_boundary() {
  local active invocation
  active=$(gt_core_run systemctl show --property=ActiveState --value grafioschtrader.service) || return 2
  invocation=$(gt_core_run systemctl show --property=InvocationID --value grafioschtrader.service) || return 2
  [[ -z "$invocation" || "$invocation" =~ ^[a-f0-9]{32}$ ]] || return 2
  if [[ "$active" == active || "$active" == activating ]]; then
    [[ -n "$invocation" && -n "${STATE[resource.app_log_cursor]:-}" ]] || return 2
    if [[ "$invocation" != "${STATE[resource.app_start_invocation]:-}" ]]; then
      # Recover a crash between starting the service and saving InvocationID.
      [[ "${STATE[resource.app_start_pending]:-}" == yes &&
        "$invocation" != "${STATE[resource.app_start_previous]:-}" ]] || return 2
    fi
  elif [[ "$active" == inactive || "$active" == failed ]]; then
    STATE[resource.app_log_cursor]=$(gt_app_start_log cursor) || return 2
    STATE[resource.app_start_previous]=$invocation STATE[resource.app_start_pending]=yes
    gt_state_save || return 2
  else return 2; fi
}

gt_app_start() {
  local deadline=$((SECONDS+900)) status active
  gt_app_database && gt_app_artifacts || return 2
  # A healthy resumed service needs no new startup boundary and no restart.
  if [[ -n "${STATE[step.app_start]:-}" ]] && gt_app_verify; then
    gt_core_run systemctl enable grafioschtrader.service && gt_core_mark step.app_start complete
    return $?
  fi
  gt_app_start_boundary || {
    gt_core_error 'Cannot identify the current startup. Inspect /var/log/grafioschtrader.log; stop the owned service before retrying an unjournaled start.'; return 2;
  }
  if [[ -z "${STATE[step.app_start]:-}" ]]; then gt_core_mark step.app_start intent || return 2; fi
  # The write-ahead entry is the sole permission to resume a populated GT schema.
  gt_core_run systemctl start grafioschtrader.service || return 2
  STATE[resource.app_start_invocation]=$(gt_core_run systemctl show --property=InvocationID --value grafioschtrader.service) || return 2
  STATE[resource.app_start_pending]=no
  gt_state_save || return 2
  while (( SECONDS < deadline )); do
    local diagnostic
    status=0
    diagnostic=$(gt_app_start_log scan) || status=$?
    if (( status != 0 )); then
      gt_core_error "Startup stopped (${diagnostic:-log-unavailable}); inspect /var/log/grafioschtrader.log. No automatic database rollback."
      return 2
    fi
    gt_app_verify; status=$?
    if (( status == 0 )); then
      gt_core_run systemctl enable grafioschtrader.service || return 2
      gt_core_mark step.app_start complete
      return $?
    fi
    (( status != 2 )) || return 2
    active=$(gt_core_run systemctl show --property=ActiveState --value grafioschtrader.service) || return 2
    [[ "$active" == active || "$active" == activating ]] || return 2
    printf '%s\n' 'Waiting for /api/gtinfo; diagnostics: /var/log/grafioschtrader.log'
    sleep 5
  done
  return 2
}

gt_install_app() {
  local reply step before after state_dir
  local completed_status
  completed_status=0
  gt_completed || completed_status=$?
  (( completed_status == 3 )) || return "$completed_status"
  state_dir=$(gt_path /var/lib/gt-install)
  gt_question_model
  if ! gt_state_load || ! gt_secrets_load; then gt_core_error 'Valid core journal and original secrets required.'; return 2; fi
  gt_no_symlinks "$state_dir/lock" || return 2
  if [[ -z "$LOCK_FD" ]]; then exec {LOCK_FD}<"$state_dir/lock" || return 2; fi
  flock -n "$LOCK_FD" || return 2
  gt_app_preflight || { gt_core_error 'Application preflight failed; no application changes made.'; return 2; }
  before=$(sha256sum "$state_dir/state")
  gt_text 'Application stage: install update scripts, sudoers (start/stop only), systemd and logrotate; build the pinned commit, start migrations, verify loopback HTTP and enable the service. Web/TLS remain pending.' \
    'Anwendungsstufe: Update-Skripte, sudoers (nur Start/Stopp), systemd und Logrotation; bestätigten Commit bauen, Migrationen starten, Loopback-HTTP prüfen und Dienst aktivieren. Web/TLS bleiben offen.'
  printf 'Commit: %s\nDocument root: %s/grafioschtrader\nTimezone: %s\n' "${STATE[planned_commit]}" "${ANSWER[DOCROOT]}" "${ANSWER[TIMEZONE]}"
  if [[ "$CORE_CONFIRM" != yes ]]; then
    { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
    printf 'install-app: ' >&"$QUESTION_FD"
    IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == install-app ]] || return 130
  fi
  after=$(sha256sum "$state_dir/state")
  [[ "$before" == "$after" ]] && gt_app_preflight || return 2
  gt_core_mark step.app running || return 2
  for step in scripts resources service cron build start; do
    printf 'Application step: %s\n' "$step"
    "gt_app_$step" || { gt_core_error "$step; inspect /var/lib/gt-install/app-build.log and /var/log/grafioschtrader.log, then resume --install-app. No automatic database rollback."; return 2; }
  done
  gt_core_mark step.app complete || return 2
  gt_text 'Application running; installation remains unfinished (web/TLS/mail verification pending).' \
    'Anwendung läuft; Installation bleibt unvollständig (Web/TLS/Mail-Prüfung offen).'
  return 10
}

# The LAN stage owns one server file and its enablement link. Foreign configurations
# are inspected but never rewritten; unsupported topologies fail before publication.
