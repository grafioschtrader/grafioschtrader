gt_action() { ACTION[$1]=$2 REASON[$1]=$3; [[ "$2" != block ]] || BLOCKS=$((BLOCKS+1)); }
gt_compatibility() {
  local distro=${FACT[os.ID]} release=${FACT[os.VERSION_ID]} arch=${FACT[architecture]} candidate version device
  ACTION=() REASON=() DISKS=() DISK_FREE=() DISK_NEED=()
  BLOCKS=0
  unset 'FACT[disk.unknown]'
  if [[ "$distro" != debian && "$distro" != ubuntu ]]; then
    case " ${FACT[os.ID_LIKE]} " in *' ubuntu '*) distro=ubuntu ;; *' debian '*) distro=debian ;; esac
  fi
  case "$distro:$release" in
    debian:12|debian:13|ubuntu:24.04|ubuntu:26.04) gt_action platform reuse 'supported primary release' ;;
    # security.debian.org no longer serves bullseye packages, so APT fails at the first security update; a host
    # without security updates must not serve the application either.
    debian:11) gt_action platform block 'Debian 11 LTS ended 2026-08-31; upgrade to Debian 12 or 13' ;;
    ubuntu:22.04) gt_action platform reuse 'legacy release'
      gt_note WARN legacy 'Ubuntu 22.04 standard support ends 2027-04' ;;
    *) gt_action platform block 'unsupported or unknown distribution/release' ;;
  esac
  case "$arch" in amd64|arm64) gt_action architecture reuse "$arch" ;; armhf)
    if [[ "$(date -u +%F)" < 2027-04-30 ]]; then
      gt_action architecture reuse armhf; gt_note WARN legacy 'armhf requires Node 22; support ends 2027-04-30'
    else gt_action architecture block 'armhf Node 22 support ended 2027-04-30'; fi ;;
    *) gt_action architecture block 'unsupported or unknown architecture' ;;
  esac
  if [[ "${FACT[init]}" == systemd ]]; then gt_action init reuse systemd
  else gt_action init block 'PID 1 must be systemd'; fi
  case "${FACT[host.class]}" in
    classic|completed) gt_action host reuse 'existing installation; bootstrap disabled'
      gt_note WARN existing './gtupdate.sh (grafioschtrader)' ;;
    docker) gt_action host reuse 'existing Docker installation; bootstrap disabled'
      gt_note WARN existing docker/update.sh ;;
    foreign-partial|invalid-state) gt_action host block 'existing foreign pieces or invalid installer state'
      gt_note WARN partial ;;
    unknown) gt_action host block 'installation inventory UNKNOWN' ;;
    unfinished)
      # A modeless run resumes its own journal; only a staged installation continues with the stage options.
      if [[ "${FACT[state.scope]:-}" == bootstrap ]]; then
        gt_action host block 'resumption requires a run without a mode and a valid journal'; gt_note WARN resume
      else
        gt_action host block 'resumption requires --install-core and a valid core journal'; gt_note WARN running
      fi ;;
    *) gt_action host install 'fresh host' ;;
  esac
  candidate=${CANDIDATE[openjdk-$JAVA_REQUIRED-jdk-headless]:-unknown}
  if [[ "${FACT[java.suitable]}" != absent ]]; then
    gt_action java reuse "${FACT[java.suitable]}; preserve system alternatives"
    version=${FACT[java.version]%%.*}
    (( version == JAVA_REQUIRED )) || gt_note WARN unavailable 'Java newer than the tested major'
  elif [[ "$candidate" != unknown && "$candidate" != '(none)' && "$arch" != armhf ]]; then
    gt_action java install "distribution JDK candidate $candidate; preserve alternatives"
  elif [[ "$arch" == armhf ]]; then gt_action java isolate 'verified Liberica JDK archive; alternatives unchanged'
  else gt_action java isolate 'verified Eclipse Temurin JDK archive; alternatives unchanged'; fi
  version=${FACT[maven.version]}
  candidate=${CANDIDATE[maven]:-unknown}; candidate=${candidate#*:}; candidate=${candidate%%-*}
  if gt_version_at_least "$version" 3.8; then gt_action maven reuse "${FACT[maven.path]}"
  elif gt_version_at_least "$candidate" 3.8; then gt_action maven install "distribution Maven $candidate"
  else gt_action maven isolate 'verified Apache Maven 3 archive'; fi
  if gt_node_satisfies "${FACT[node.version]}" "$NODE_REQUIRED"; then gt_action node reuse "${FACT[node.path]}"
  elif [[ "${FACT[node.version]}" != absent && "${FACT[node.consumers]}" != no ]]; then
    gt_action node isolate 'official Node tarball; other consumers must keep their runtime'
  else gt_action node install 'isolated official Node 24 archive on amd64/arm64; Node 22 on armhf'; fi
  if gt_version_at_least "${FACT[angular.version]}" "$CLI_REQUIRED"; then
    gt_action angular_cli reuse "${FACT[angular.version]}"
  else gt_action angular_cli install "Angular CLI $CLI_REQUIRED using selected Node/npm"; fi
  if [[ "${FACT[semver.version]}" =~ ^[0-9]+\. ]]; then gt_action semver reuse "${FACT[semver.version]}"
  else gt_action semver install 'global npm semver using selected Node/npm'; fi
  case "${FACT[database.vendor]}" in
    absent)
      gt_action mariadb install 'MariaDB server/client'
      candidate=${CANDIDATE[mariadb-server]:-unknown}; candidate=${candidate#*:}; candidate=${candidate%%-*}
      if gt_version_at_least "$candidate" 11.5 && [[ "${FACT[source.collation]}" != yes ]]; then
        gt_action mariadb block 'planned connection collation initialization absent or unknown'
      fi ;;
    mariadb)
      # 10.6 is the oldest MariaDB a supported release ships (Ubuntu 22.04) and the oldest the migrations were
      # accepted on.
      if gt_version_at_least "${FACT[database.version]}" 10.6; then gt_action mariadb reuse "${FACT[database.version]}"
      else gt_action mariadb block 'MariaDB 10.6 or newer required'; fi
      if gt_version_at_least "${FACT[database.version]}" 11.5 && [[ "${FACT[source.collation]}" != yes ]]; then
        gt_action mariadb block 'planned connection collation initialization absent or unknown'
      fi ;;
    *) gt_action mariadb block 'Oracle MySQL or unknown database vendor' ;;
  esac
  case "${FACT[database.gt_tables]}" in
    absent) gt_action database install 'create grafioschtrader database' ;;
    0) gt_action database reuse 'empty database; confirmation required in question stage' ;;
    unknown)
      if [[ "${FACT[database.vendor]}" == absent ]]; then gt_action database install 'create after installing MariaDB'
      else gt_action database block 'database contents UNKNOWN; authenticate before planning changes'; fi ;;
    *) gt_action database block 'non-empty grafioschtrader database must not be adopted' ;;
  esac
  case "${FACT[database.gt_user]}" in
    present) gt_action database_user reuse 'password must be supplied and verified later; never rotate it' ;;
    absent) gt_action database_user install 'grafioschtrader@localhost' ;;
    *) gt_action database_user block 'database accounts UNKNOWN until authenticated inventory' ;;
  esac
  if [[ "${FACT[database.vendor]}" == absent ]]; then
    gt_action database_user install 'create after installing MariaDB'
  fi
  gt_web_recommendation
  version=${FACT[memory.MemTotal]}
  if [[ "$version" =~ ^[0-9]+$ ]]; then
    (( version >= 2000 )) || gt_note WARN memory 'backend build may be slow; swap advised'
    (( version >= 3700 )) || gt_note WARN memory 'downloaded frontend can be newer than the backend source'
    FACT[frontend.mode]=build; (( version >= 3700 )) || FACT[frontend.mode]=download
    if (( version < 4000 )) && [[ "${FACT[memory.SwapTotal]}" == 0 ]]; then
      gt_swap_support
      if [[ "${FACT[swap.method]}" == none ]]; then
        gt_action swap reuse 'no swap file possible'
        gt_note WARN swap "${FACT[swap.reason]}"
      else
        gt_action swap install "offer $SWAP_MB MiB /swapfile in question stage (${FACT[swap.method]})"
        gt_disk / "$SWAP_MB"
      fi
    else gt_action swap reuse 'no additional swap proposed'; fi
    if [[ "${FACT[memory.MemAvailable]}" =~ ^[0-9]+$ ]] && (( ${FACT[memory.MemAvailable]} < version / 2 )); then
      gt_note WARN memory 'other processes use more than half of RAM'
      FACT[memory.largest_processes]=$(gt_probe ps -eo pid,comm,rss --sort=-rss | head -n 6) ||
        FACT[memory.largest_processes]=unknown
    fi
  else gt_action memory block 'RAM size UNKNOWN'; fi
  gt_disk /home 4096
  gt_disk /var/www 0
  gt_disk /opt "$(gt_opt_need)"
  gt_disk "${FACT[database.datadir]}" 2048
  gt_action disk reuse 'requirements aggregated by filesystem device'
  [[ "${FACT[disk.unknown]:-no}" != yes ]] || gt_action disk block 'disk capacity UNKNOWN'
  for device in "${!DISK_NEED[@]}"; do
    if (( DISK_FREE[$device] < DISK_NEED[$device] )); then
      gt_action disk block "device $device needs ${DISK_NEED[$device]} MiB; ${DISK_FREE[$device]} MiB free"
    fi
  done
  # A check never authorizes a package transaction or adopts an existing installation.
}

gt_port_free() {
  [[ "${FACT[listeners]}" != unknown ]] || return 1
  ! awk -v port="$1" '$4 ~ (":" port "$") {found=1} END {exit !found}' <<< "${FACT[listeners]}"
}
gt_next_port() {
  local port=$1
  [[ "${FACT[listeners]}" != unknown ]] || { echo unknown; return; }
  while (( port < 65536 )); do gt_port_free "$port" && { echo "$port"; return; }; port=$((port+1)); done
  echo unknown
}
gt_proxy_port_default() {
  local port=8081
  [[ "${FACT[listeners]:-unknown}" != unknown ]] || { echo unknown; return; }
  while (( port < 65536 )); do
    if [[ "$port" != "${ANSWER[BACKEND_PORT]:-${FACT[ports.backend_primary]:-9090}}" &&
          "$port" != "${ANSWER[BACKEND_HTTP_PORT]:-${FACT[ports.backend_http]:-8080}}" ]] && gt_port_free "$port"; then
      echo "$port"; return
    fi
    port=$((port+1))
  done
  echo unknown
}
gt_web_recommendation() {
  local owners
  owners=$(awk '$4 ~ /:(80|443)$/ {print}' <<< "${FACT[listeners]}")
  if [[ "${FACT[listeners]}" == unknown ]]; then gt_action web block 'port owners UNKNOWN'
  elif [[ -n "$owners" && "$owners" != *nginx* && "$owners" != *apache2* ]]; then
    gt_action web reuse 'foreign proxy owns 80/443; choose proxy topology or no web integration later'
  elif [[ "$owners" == *nginx* && "$owners" != *apache2* ]]; then gt_action web reuse nginx
  elif [[ "$owners" == *apache2* && "$owners" != *nginx* ]]; then gt_action web reuse apache2
  elif [[ "${FACT[web.nginx]}" != absent && "${FACT[web.apache2]}" == absent ]]; then gt_action web reuse nginx
  elif [[ "${FACT[web.apache2]}" != absent && "${FACT[web.nginx]}" == absent ]]; then gt_action web reuse apache2
  else gt_action web install 'selection required; default nginx'; fi
  FACT[ports.backend_primary]=$(gt_next_port 9090)
  FACT[ports.backend_http]=$(gt_next_port 8080)
  if [[ "${FACT[ports.backend_primary]}" == "${FACT[ports.backend_http]}" &&
      "${FACT[ports.backend_primary]}" != unknown ]]; then
    FACT[ports.backend_primary]=$(gt_next_port "$((${FACT[ports.backend_primary]}+1))")
  fi
  FACT[ports.proxy_http]=$(gt_proxy_port_default)
  return 0
}

gt_report_rows() {
  local title=$1 row
  shift
  printf '\n[%s]\n' "$title"
  for row in "$@"; do gt_safe "$row"; printf '\n'; done
}
gt_report() {
  local key
  gt_message title
  gt_message facts
  while IFS= read -r key; do
    printf '%s=' "$key"; gt_safe "${FACT[$key]}"; printf '\n'
  done < <(printf '%s\n' "${!FACT[@]}" | LC_ALL=C sort)
  gt_report_rows JDKs "${JDKS[@]}"
  gt_report_rows disks "${DISKS[@]}"
  gt_report_rows consumers "${CONSUMERS[@]}"
  gt_report_rows web "${WEB[@]}"
  gt_report_rows certificates "${CERTS[@]}"
  gt_report_rows network "${NETWORK[@]}"
  printf '\n'; gt_message actions
  BLOCKS=0
  while IFS= read -r key; do
    [[ "${ACTION[$key]}" != block ]] || BLOCKS=$((BLOCKS+1))
    printf '%-18s %-8s ' "$key" "${ACTION[$key]}"; gt_safe "${REASON[$key]}"; printf '\n'
  done < <(printf '%s\n' "${!ACTION[@]}" | LC_ALL=C sort)
  printf '\n'; gt_message notes
  for key in "${NOTES[@]}"; do gt_safe "$key"; printf '\n'; done | LC_ALL=C sort -u
  gt_message footer "$BLOCKS"
  gt_toolchain_guidance
}
# Isolated Node and build tools; a vendor JDK adds its download and its unpacked tree, both on /opt.
gt_opt_need() {
  local need=300
  [[ "${ACTION[java]:-}" != isolate ]] || need=$((need + 600))
  [[ "${ACTION[maven]:-}" != isolate ]] || need=$((need + 30))
  echo "$need"
}

# A swap file needs a root filesystem whose blocks swapon can map directly. btrfs needs its own mkswapfile
# (btrfs-progs 6.1 and later); other filesystems are not offered a swap file.
gt_swap_support() {
  FACT[swap.filesystem]=$(stat -Lf -c %T "$(gt_path /)" 2>/dev/null) || FACT[swap.filesystem]=unknown
  FACT[swap.reason]=''
  case "${FACT[swap.filesystem]}" in
    ext2/ext3|xfs) FACT[swap.method]=mkswap ;;
    btrfs)
      if gt_probe btrfs filesystem mkswapfile --help >/dev/null; then FACT[swap.method]=btrfs
      else FACT[swap.method]=none FACT[swap.reason]='btrfs root without btrfs filesystem mkswapfile'; fi ;;
    *) FACT[swap.method]=none FACT[swap.reason]="root filesystem ${FACT[swap.filesystem]} cannot hold a swap file" ;;
  esac
}

gt_inventory() {
  gt_source_revision
  gt_system
  gt_packages
  gt_installation
  gt_java
  gt_runtimes
  gt_consumers
  gt_database
  gt_network
  gt_web
  gt_dynamic_dns
}
gt_check() {
  local completed_status
  completed_status=0
  gt_completed || completed_status=$?
  (( completed_status == 3 )) || return "$completed_status"
  gt_inventory
  gt_compatibility
  gt_report
  (( BLOCKS == 0 )) || return 2
}

# Question definitions contain no shell expressions. Conditions, defaults and validation are shared
# by the plain renderer and the planner; future front ends must use these same functions.
