gt_core_begin() {
  local key field
  gt_private_dir "$(gt_path /var/lib/gt-install)" && gt_private_dir "$(gt_path /root/.gt-install)" || return 2
  [[ ! -e "$(gt_path /root/.gt-install/secrets)" && ! -e "$(gt_path /var/lib/gt-install/state)" ]] || return 2
  STATE=([schema]=1 [status]=running [scope]=core [planned_commit]="${FACT[source.commit]}"
    [java_home]="${FACT[toolchain.java.path]:-${FACT[java.suitable]}}"
    [maven]="${FACT[toolchain.maven.path]:-${FACT[maven.path]}}"
    [new_database_server]=no [database_before]=no [account_before]=no)
  STATE[run_id]=$(openssl rand -hex 16) || return 2
  [[ "${FACT[database.vendor]}" != absent ]] || STATE[new_database_server]=yes
  [[ "${FACT[database.gt_tables]}" != 0 ]] || STATE[database_before]=yes
  [[ "${FACT[database.gt_user]}" != present ]] || STATE[account_before]=yes
  if [[ -n "${FACT[toolchain.java.path]:-}" ]]; then
    STATE[step.toolchains]=pending
    for key in java maven; do
      STATE[toolchain.$key.package]=${FACT[toolchain.$key.package]}
      STATE[toolchain.$key.version]=${FACT[toolchain.$key.version]}
      [[ "${FACT[toolchain.$key.package]}" == archive ]] || continue
      # The confirmed archive, not a newer vendor release, is what resumption downloads and verifies.
      for field in source file checksum; do STATE[toolchain.$key.$field]=${FACT[toolchain.$key.$field]}; done
    done
  fi
  for key in "${!ANSWER[@]}"; do STATE[answer.$key]=${ANSWER[$key]}; done
  # A crash between these writes leaves a journal with missing secrets: recovery must stop,
  # never invent new passwords for resources which could already depend on them.
  gt_state_save && gt_secrets_save
}

gt_core_mark() { STATE[$1]=$2; gt_state_save; }

gt_core_run() {
  # Only non-secret commands use this channel. SQL and Maven output use private captures.
  if [[ "$BOOTSTRAP_APPROVED" == yes && "$1 ${2:-} ${3:-}" == 'env DEBIAN_FRONTEND=noninteractive apt-get' ]]; then
    gt_bootstrap_apt_run "$@"; return $?
  fi
  "$@"
}

gt_core_base_packages() {
  local package transaction line
  local -a base_missing=()
  gt_packages
  [[ "${FACT[packages]}" == known ]] || {
    gt_core_error 'Base package inventory failed; no APT transaction started.'; return 2;
  }
  while IFS= read -r package; do
    [[ -n "${PACKAGE[$package]:-}" ]] || base_missing+=("$package")
  done < <(gt_base_package_names)
  if (( ${#base_missing[@]} )); then
    [[ "${STATE[step.base_packages]:-}" != complete ]] || {
      gt_core_error 'Previously verified base packages are missing; restore them before resuming.'; return 2;
    }
    transaction=$(gt_probe env LC_ALL=C apt-get --simulate \
      -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= install "${base_missing[@]}") || return 2
    while IFS= read -r line; do
      if [[ "$line" == 'Remv '* || "$line" =~ ^Inst\ [^\ ]+\ \[ ]]; then
        gt_core_error 'Base package transaction would remove or upgrade existing packages.'; return 2
      fi
    done <<< "$transaction"
    gt_core_mark step.base_packages running || return 2
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install "${base_missing[@]}" || return 2
    gt_packages
    [[ "${FACT[packages]}" == known ]] || return 2
    for package in "${base_missing[@]}"; do
      [[ -n "${PACKAGE[$package]:-}" ]] || {
        gt_core_error "Base package still missing after APT: $package"; return 2;
      }
    done
  fi
  gt_core_mark step.base_packages complete
}

# The swap file and its fstab line are the installer's own resources. A foreign /swapfile or fstab entry is
# never adopted; a resumed run recognizes its own by the journal.
gt_swap_plan() {
  local swapfile fstab
  [[ "${ANSWER[SWAP]:-no}" == yes ]] || return 0
  swapfile=$(gt_path /swapfile) fstab=$(gt_path /etc/fstab)
  if [[ "${STATE[step.swap]:-}" == complete ]]; then
    gt_plan_row reuse /swapfile 'Installer-created swap file; verify it is active and listed in /etc/fstab' \
      'Vom Installer angelegte Swap-Datei; prüfen, dass sie aktiv und in /etc/fstab eingetragen ist'
    return 0
  fi
  if [[ "${ACTION[swap]:-}" != install && -z "${STATE[resource.swapfile]:-}" ]]; then
    gt_plan_block 'A swap file is only created on a host with less than 4000 MB RAM and no swap.' \
      'Eine Swap-Datei wird nur auf einem Host mit weniger als 4000 MB RAM und ohne Swap angelegt.'
    return 0
  fi
  [[ -n "${FACT[swap.method]:-}" ]] || gt_swap_support
  [[ "${FACT[swap.method]}" != none ]] || gt_plan_block "Swap file unsupported: ${FACT[swap.reason]}"
  if [[ ( -e "$swapfile" || -L "$swapfile" ) && -z "${STATE[resource.swapfile]:-}" ]]; then
    gt_plan_block 'A foreign /swapfile exists; it is not adopted.' \
      'Eine fremde /swapfile existiert; sie wird nicht übernommen.'
  fi
  if gt_swap_listed "$fstab" && [[ -z "${STATE[resource.fstab_backup]:-}" ]]; then
    gt_plan_block '/etc/fstab already lists /swapfile; it is not adopted.' \
      '/etc/fstab enthält bereits /swapfile; der Eintrag wird nicht übernommen.'
  fi
  gt_plan_row create /swapfile "$SWAP_MB MiB swap file (${FACT[swap.method]}), mode 600, activated now" \
    "$SWAP_MB MiB Swap-Datei (${FACT[swap.method]}), Modus 600, sofort aktiviert"
  gt_plan_row backup '/etc/fstab.gt-install.<timestamp>' 'Before adding the swap entry' \
    'Vor Ergänzung des Swap-Eintrags'
  gt_plan_row modify /etc/fstab "Append '/swapfile none swap sw 0 0'; swap stays active after a reboot" \
    "'/swapfile none swap sw 0 0' anfügen; Swap bleibt nach einem Neustart aktiv"
}

gt_swap_listed() { grep -qE '^[[:space:]]*/swapfile[[:space:]]' "$1" 2>/dev/null; }

gt_swap_valid() {
  [[ -f "$1" && ! -L "$1" && "$(stat -c '%u:%a:%s' "$1")" == "$EUID:600:$((SWAP_MB * 1048576))" ]] || return 2
  [[ "$(blkid -p -s TYPE -o value -- "$1" 2>/dev/null)" == swap ]]
}

gt_swap_active() {
  local path name
  path=$(readlink -f -- "$1") || return 2
  while IFS= read -r name; do [[ "$name" != "$path" ]] || return 0; done < <(swapon --show=NAME --noheadings)
  return 1
}

# Write the swap file under a private name and publish it by rename, so an interrupted dd or mkswap never leaves a
# partial /swapfile behind.
gt_swap_create() {
  local swapfile=$1 temporary
  temporary="${swapfile%/*}/.gt-swapfile-${STATE[run_id]}"
  gt_swap_support
  [[ "${FACT[swap.method]}" != none ]] || { gt_core_error "Swap file unsupported: ${FACT[swap.reason]}"; return 2; }
  gt_core_mark resource.swapfile intent || return 2
  rm -f -- "$temporary" || return 2
  if [[ "${FACT[swap.method]}" == btrfs ]]; then
    gt_core_run btrfs filesystem mkswapfile --size "${SWAP_MB}m" "$temporary" >/dev/null || return 2
  else
    (umask 077 && gt_core_run dd if=/dev/zero of="$temporary" bs=1M count="$SWAP_MB" status=none) || return 2
    gt_core_run mkswap "$temporary" >/dev/null || return 2
  fi
  chmod 600 "$temporary" || return 2
  mv -T -- "$temporary" "$swapfile"
}

# Append the entry through a copy beside fstab; the timestamped backup stays for the administrator.
gt_swap_fstab() {
  local fstab=$1 backup temporary digest
  if [[ -n "${STATE[file.fstab]:-}" ]]; then
    gt_core_error 'The installer-written /swapfile line left /etc/fstab.'; return 2
  fi
  backup="$fstab.gt-install.$(date -u +%Y%m%dT%H%M%SZ)"
  cp -p -- "$fstab" "$backup" || return 2
  gt_core_mark resource.fstab_backup "$backup" || return 2
  temporary=$(mktemp "$fstab.XXXXXX") || return 2
  PRIVATE_FILES+=("$temporary")
  { cat -- "$fstab"; [[ -z "$(tail -c 1 -- "$fstab")" ]] || printf '\n'; printf '/swapfile none swap sw 0 0\n'; } \
    > "$temporary" || return 2
  chmod --reference="$fstab" -- "$temporary" || return 2
  chown --reference="$fstab" -- "$temporary" || return 2
  mv -T -- "$temporary" "$fstab" || return 2
  digest=$(sha256sum -- "$fstab") || return 2
  gt_core_mark file.fstab "${digest%% *}"
}

# Swap comes before any build: a small host needs it to compile the backend.
gt_core_swap() {
  local swapfile fstab
  [[ "${ANSWER[SWAP]:-no}" == yes ]] || return 0
  swapfile=$(gt_path /swapfile) fstab=$(gt_path /etc/fstab)
  gt_no_symlinks "$swapfile" || return 2
  gt_no_symlinks "$fstab" || return 2
  if [[ "${STATE[step.swap]:-}" != complete ]]; then
    if [[ -e "$swapfile" ]]; then
      if [[ -z "${STATE[resource.swapfile]:-}" ]]; then
        gt_core_error 'A foreign /swapfile exists; it is not adopted.'; return 2
      fi
    else
      [[ "${STATE[resource.swapfile]:-intent}" == intent ]] || return 2
      gt_swap_create "$swapfile" || return 2
    fi
    if ! gt_swap_listed "$fstab"; then gt_swap_fstab "$fstab" || return 2
    elif [[ -z "${STATE[resource.fstab_backup]:-}" ]]; then
      gt_core_error '/etc/fstab already lists /swapfile; it is not adopted.'; return 2
    fi
  fi
  gt_swap_valid "$swapfile" || { gt_core_error '/swapfile is not the installer-created swap file.'; return 2; }
  [[ "$(grep -cE '^[[:space:]]*/swapfile[[:space:]]+none[[:space:]]+swap[[:space:]]' "$fstab")" == 1 ]] || {
    gt_core_error '/etc/fstab must list /swapfile exactly once.'; return 2;
  }
  gt_swap_active "$swapfile" || gt_core_run swapon -- "$swapfile" || return 2
  gt_swap_active "$swapfile" || return 2
  [[ "${STATE[step.swap]:-}" != complete ]] || return 0
  gt_core_mark resource.swapfile owned || return 2
  gt_core_mark step.swap complete
}

gt_as_app() {
  local build_path=''
  [[ -z "${STATE[build.node_home]:-}" ]] || build_path="${STATE[build.node_home]}/bin:${STATE[build.prefix]}/bin:"
  runuser -u grafioschtrader -- env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS \
    -u MAVEN_OPTS -u MAVEN_ARGS -u NODE_OPTIONS -u NODE_PATH HOME="$CORE_HOME" MAVEN_SKIP_RC=1 GIT_TERMINAL_PROMPT=0 \
    NG_CLI_ANALYTICS=false npm_config_prefix="${STATE[build.prefix]:-/usr/local}" \
    JAVA_HOME="${STATE[java_home]}" \
    PATH="${build_path}${STATE[java_home]}/bin:${STATE[maven]%/*}:/usr/local/bin:/usr/bin:/bin" "$@"
}

gt_core_user() {
  local entry _name _password uid _gid description home shell
  if entry=$(getent passwd grafioschtrader); then
    IFS=: read -r _name _password uid _gid description home shell <<< "$entry"
    [[ "${STATE[resource.user]:-}" == intent || "${STATE[resource.user]:-}" == owned ]] || return 2
    [[ "$description" == "GT installer ${STATE[run_id]}" && "$home" == "$CORE_HOME" && "$uid" != 0 &&
      "$shell" == /bin/bash ]] || return 2
  else
    [[ "${STATE[resource.user]:-}" != owned ]] || return 2
    [[ ! -e "$CORE_HOME" && ! -L "$CORE_HOME" ]] && ! getent group grafioschtrader >/dev/null || return 2
    gt_core_mark resource.user intent || return 2
    gt_core_run useradd --create-home --user-group --shell /bin/bash --comment "GT installer ${STATE[run_id]}" \
      grafioschtrader || return 2
  fi
  gt_no_symlinks "$CORE_HOME" || return 2
  [[ "$(stat -c %U "$CORE_HOME")" == grafioschtrader ]] || return 2
  [[ "$(passwd -S grafioschtrader | awk '{print $2}')" == L ]] || return 2
  gt_core_mark resource.user owned
}

# DuckDNS updater: a script and its token in ~/duckdns, run by a systemd timer as grafioschtrader. systemd is a
# precondition of the installer, while cron is missing from minimal cloud images.
GT_DUCKDNS_UNIT='grafioschtrader-duckdns'

gt_duckdns_subdomain() {
  local domain=${ANSWER[DOMAIN]:-}
  [[ "$domain" =~ ^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\.duckdns\.org$ ]] || return 2
  printf '%s\n' "${domain%.duckdns.org}"
}

gt_duckdns_valid_token() { [[ "$1" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; }

# Spread installations over the five-minute cycle so they do not all call DuckDNS in the same second.
gt_duckdns_calendar() {
  local value
  value=$(printf '%s' "${FACT[host.name]:-$(hostname)}" | cksum) || return 2
  value=${value%% *}
  printf '*:%02d/5:%02d\n' $(( value % 5 )) $(( value / 5 % 60 ))
}

# Foreign updaters found by the inventory; the installer's own units are recognized by the journal.
gt_duckdns_foreign() {
  local file
  [[ "${FACT[containers]:-}" != *duckdns* ]] || printf 'container\n'
  for file in ${FACT[dns.updater_files]:-}; do
    case "$file" in
      */etc/systemd/system/"$GT_DUCKDNS_UNIT".service) [[ -n "${STATE[file.duckdns_service]:-}" ]] && continue ;;
      */etc/systemd/system/"$GT_DUCKDNS_UNIT".timer) [[ -n "${STATE[file.duckdns_timer]:-}" ]] && continue ;;
    esac
    printf '%s\n' "$file"
  done
}

gt_duckdns_plan() {
  local updaters
  [[ "${ANSWER[DUCKDNS_UPDATER]:-no}" == yes ]] || return 0
  gt_duckdns_subdomain >/dev/null || gt_plan_block 'DUCKDNS_UPDATER needs a lowercase <name>.duckdns.org domain.' \
    'DUCKDNS_UPDATER benötigt eine Domain <name>.duckdns.org in Kleinbuchstaben.'
  updaters=$(gt_duckdns_foreign | paste -sd ' ' -)
  [[ -z "$updaters" ]] || gt_plan_block "An existing DuckDNS updater must not be duplicated: $updaters" \
    "Ein vorhandener DuckDNS-Updater darf nicht dupliziert werden: $updaters"
  if [[ "${STATE[step.duckdns]:-}" == complete ]]; then
    gt_plan_row reuse "$GT_DUCKDNS_UNIT.timer" 'Installer-owned DuckDNS updater; verify files and timer' \
      'Installer-eigener DuckDNS-Updater; Dateien und Timer prüfen'
    return 0
  fi
  gt_plan_row create "$CORE_HOME/duckdns/duck.sh" \
    "Updater for ${ANSWER[DNS_FAMILY]:-ipv4}, mode 700; token in a 600 file beside it" \
    "Updater für ${ANSWER[DNS_FAMILY]:-ipv4}, Modus 700; Token in einer 600-Datei daneben"
  gt_plan_row create "/etc/systemd/system/$GT_DUCKDNS_UNIT.service, .timer" \
    "Every five minutes at $(gt_duckdns_calendar), as grafioschtrader" \
    "Alle fünf Minuten zu $(gt_duckdns_calendar), als grafioschtrader"
  gt_plan_row verify DuckDNS \
    'Update once before the build; KO stops before any certificate; wait up to 3 minutes for DNS' \
    'Vor dem Build einmal aktualisieren; KO stoppt vor jedem Zertifikat; bis zu 3 Minuten auf DNS warten'
}

gt_duckdns_script() {
  local subdomain=$1 family=$2
  printf '#!/bin/bash\n'
  printf '# DuckDNS updater installed by gt-install. The token stays in the protected file beside this script and\n'
  printf '# reaches curl through a private configuration file, never through the command line or the log.\n'
  printf 'set -uo pipefail\numask 077\n'
  printf 'dir=%q domains=%q family=%q\n' "$CORE_HOME/duckdns" "$subdomain" "$family"
  cat <<'SCRIPT'
log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" >> "$dir/duck.log"
  if (( $(wc -l < "$dir/duck.log") > 2000 )); then
    tail -n 1000 "$dir/duck.log" > "$dir/duck.log.new" && mv -f "$dir/duck.log.new" "$dir/duck.log"
  fi
}
token=$(< "$dir/token") || { log 'KO token file unreadable'; exit 2; }
if [[ ! "$token" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
  log 'KO token file invalid'
  exit 2
fi
ipv6=''
if [[ "$family" != ipv4 ]]; then
  # A delegated prefix changes, so the stable global address is read again on every run.
  interface=$(ip -6 route show default | awk '{for (i = 1; i < NF; i++) if ($i == "dev") {print $(i + 1); exit}}')
  if [[ -n "$interface" ]]; then
    ipv6=$(ip -6 addr show dev "$interface" scope global -temporary -deprecated |
      awk '$1 == "inet6" && $2 !~ /^(f[cd]|fe[89ab])/ {sub(/\/.*/, "", $2); print $2; exit}')
  fi
  [[ "$ipv6" =~ ^[0-9a-f:]+$ ]] || { log 'KO no stable global IPv6 address'; exit 3; }
fi
config=$(mktemp "$dir/.curl.XXXXXX") || exit 2
trap 'rm -f -- "$config"' EXIT
printf 'url = "https://www.duckdns.org/update?domains=%s&token=%s&ip=&ipv6=%s"\n' "$domains" "$token" "$ipv6" \
  > "$config"
# www.duckdns.org has IPv4 addresses only. The IPv6 address travels in the ipv6 parameter, and the empty ip
# parameter leaves the A record to the address DuckDNS sees, which an IPv6-only domain never gains.
answer=$(curl -4 --proto '=https' --tlsv1.2 -fsS --connect-timeout 10 --max-time 30 -K "$config" \
  2>/dev/null) || { log "KO curl exit $?"; exit 2; }
if [[ "$answer" == OK ]]; then log OK; exit 0; fi
log 'KO rejected by DuckDNS'
exit 1
SCRIPT
}

gt_duckdns_units() {
  {
    printf '[Unit]\nDescription=Grafioschtrader DuckDNS update\nWants=network-online.target\n'
    printf 'After=network-online.target\n\n[Service]\nType=oneshot\nUser=grafioschtrader\nExecStart=%s\n' \
      "$CORE_HOME/duckdns/duck.sh"
  } > "$SCRATCH/duckdns.service"
  printf '[Unit]\nDescription=Grafioschtrader DuckDNS update every five minutes\n\n[Timer]\nOnCalendar=%s\n' \
    "$(gt_duckdns_calendar)" > "$SCRATCH/duckdns.timer"
  printf 'OnBootSec=1min\nAccuracySec=1s\n\n[Install]\nWantedBy=timers.target\n' >> "$SCRATCH/duckdns.timer"
}

# The A/AAAA records of the domain match this host for the selected address families.
gt_duckdns_dns_ready() {
  local family records expected result
  gt_domain_network || return 1
  for family in A AAAA; do
    result=$(gt_probe dig +time=2 +tries=1 +noall +answer "${ANSWER[DOMAIN]}" "$family") || return 1
    records=$(awk -v type="$family" '$4==type {print tolower($5)}' <<< "$result" | LC_ALL=C sort -u |
      gt_normalize_addresses)
    expected=${FACT[network.global_ipv6]:-unknown}
    [[ "$family" != A ]] || expected=${FACT[network.public_ipv4]:-unknown}
    expected=$(gt_normalize_addresses <<< "$expected")
    gt_dns_records_match "$family" "$records" "$expected" || return 1
  done
}

gt_duckdns_update() {
  local reason
  if ! gt_core_run systemctl start "$GT_DUCKDNS_UNIT.service"; then
    reason=$(tail -n 1 "$CORE_HOME/duckdns/duck.log" 2>/dev/null) || reason=''
    case "$reason" in
      *'KO rejected by DuckDNS')
        gt_core_error 'DuckDNS rejected the update; check the token and the subdomain on duckdns.org.' ;;
      *'KO no stable global IPv6 address') gt_core_error 'No stable global IPv6 address for the DuckDNS AAAA record.' ;;
      *) gt_core_error "DuckDNS update failed: ${reason#* }" ;;
    esac
    return 2
  fi
}

gt_core_duckdns() {
  local dir subdomain deadline unit
  [[ "${ANSWER[DUCKDNS_UPDATER]:-no}" == yes ]] || return 0
  subdomain=$(gt_duckdns_subdomain) || return 2
  if ! gt_duckdns_valid_token "${SECRET[DUCKDNS_TOKEN]:-}"; then
    gt_core_error 'The DuckDNS token is missing or malformed.'; return 2
  fi
  dir=$CORE_HOME/duckdns
  gt_no_symlinks "$dir" || return 2
  if [[ ! -d "$dir" ]]; then install -d -o grafioschtrader -g grafioschtrader -m 700 "$dir" || return 2; fi
  [[ "$(stat -c '%U:%a' "$dir")" == grafioschtrader:700 ]] || return 2
  gt_duckdns_script "$subdomain" "${ANSWER[DNS_FAMILY]:-ipv4}" > "$SCRATCH/duck.sh" || return 2
  printf '%s\n' "${SECRET[DUCKDNS_TOKEN]}" > "$SCRATCH/duckdns-token" || return 2
  gt_core_publish duckdns_script "$SCRATCH/duck.sh" "$dir/duck.sh" 700 || return 2
  gt_core_publish duckdns_token "$SCRATCH/duckdns-token" "$dir/token" 600 || return 2
  rm -f -- "$SCRATCH/duckdns-token"
  gt_duckdns_units || return 2
  unit=$(gt_path "/etc/systemd/system/$GT_DUCKDNS_UNIT")
  gt_app_root_file duckdns_service "$SCRATCH/duckdns.service" "$unit.service" 644 || return 2
  gt_app_root_file duckdns_timer "$SCRATCH/duckdns.timer" "$unit.timer" 644 || return 2
  gt_core_run systemctl daemon-reload || return 2
  if [[ "${STATE[step.duckdns]:-}" != complete ]]; then
    gt_duckdns_update || return 2
    # Recursive resolvers may still hold the previous records for their TTL.
    if gt_dns_required; then
      deadline=$((SECONDS + 180))
      until gt_duckdns_dns_ready; do
        (( SECONDS < deadline )) || {
          gt_core_error "DuckDNS accepted the update, but ${ANSWER[DOMAIN]} does not resolve to this host's \
selected addresses after 3 minutes."
          return 2
        }
        sleep 10
      done
    fi
  fi
  gt_core_run systemctl enable --now "$GT_DUCKDNS_UNIT.timer" || return 2
  [[ "${STATE[step.duckdns]:-}" == complete ]] || gt_core_mark step.duckdns complete
}

gt_core_clone() {
  local stage="$CORE_HOME/build/.gt-source-${STATE[run_id]}" remote commit
  gt_no_symlinks "$CORE_REPO" && gt_no_symlinks "$stage" || return 2
  if [[ -e "$CORE_REPO" ]]; then
    [[ "${STATE[resource.clone]:-}" == intent || "${STATE[resource.clone]:-}" == owned ]] || return 2
  else
    gt_core_mark resource.clone intent || return 2
    gt_as_app mkdir -p "$CORE_HOME/build" || return 2
    if [[ ! -e "$stage" ]]; then gt_as_app git init -q "$stage" || return 2; fi
    remote=$(gt_as_app git -C "$stage" remote get-url origin 2>/dev/null) || remote=''
    if [[ -z "$remote" ]]; then gt_as_app git -C "$stage" remote add origin "$CORE_REMOTE" || return 2
    else [[ "$remote" == "$CORE_REMOTE" ]] || return 2; fi
    gt_as_app git -C "$stage" fetch -q --depth=1 origin "${STATE[planned_commit]}" || return 2
    if gt_as_app git -C "$stage" show-ref --verify --quiet refs/heads/master; then
      [[ "$(gt_as_app git -C "$stage" rev-parse HEAD)" == "${STATE[planned_commit]}" ]] || return 2
    else gt_as_app git -C "$stage" checkout -q -b master FETCH_HEAD || return 2; fi
    gt_as_app mv -T "$stage" "$CORE_REPO" || return 2
  fi
  remote=$(gt_as_app git -C "$CORE_REPO" remote get-url origin) || return 2
  commit=$(gt_as_app git -C "$CORE_REPO" rev-parse HEAD) || return 2
  [[ "$remote" == "$CORE_REMOTE" && "$commit" == "${STATE[planned_commit]}" ]] || return 2
  gt_core_mark resource.clone owned
}

gt_properties_escape() {
  local value=$1
  value=${value//\\/\\\\}; value=${value// /\\ }
  printf '%s' "$value"
}

gt_properties_render() {
  local input=$1 output=$2 allow_new=${3:-no} line key
  local -A seen=()
  : > "$output" || return 2
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    if [[ "$line" =~ ^[[:space:]]*([#!][[:space:]]*)?([^=]+)= ]]; then
      key=${BASH_REMATCH[2]}; key="${key#"${key%%[![:space:]]*}"}"; key="${key%"${key##*[![:space:]]}"}"
      if [[ -n "${PROPERTIES[$key]+set}" ]]; then
        [[ "$line" != *'!'* && -z "${seen[$key]:-}" ]] || return 2
        seen[$key]=yes
        printf '%s=%s\n' "$key" "$(gt_properties_escape "${PROPERTIES[$key]}")" >> "$output"
        continue
      fi
    fi
    printf '%s\n' "$line" >> "$output"
  done < "$input"
  for key in "${!PROPERTIES[@]}"; do
    [[ -z "${seen[$key]:-}" ]] || continue
    [[ "$allow_new" == yes ]] || return 2
    printf '%s=%s\n' "$key" "$(gt_properties_escape "${PROPERTIES[$key]}")" >> "$output"
  done
}

gt_core_publish() {
  local id=$1 input=$2 target=$3 mode=$4 original=${5:-absent} digest current temporary
  gt_no_symlinks "$target" || return 2
  digest=$(sha256sum "$input"); digest=${digest%% *}
  if [[ -e "$target" ]]; then
    [[ -f "$target" ]] || return 2
    current=$(sha256sum "$target"); current=${current%% *}
    [[ "$current" == "${STATE[file.$id]:-$original}" || "$current" == "${STATE[file.$id.previous]:-$original}" ]] ||
      return 2
    if [[ "$current" == "$digest" && "${STATE[file.$id]:-}" == "$digest" &&
        "$(stat -c '%U:%a' "$target")" == "grafioschtrader:$mode" ]]; then
      return 0
    fi
  fi
  STATE[file.$id.previous]=${current:-absent}
  gt_core_mark "file.$id" "$digest" || return 2
  temporary=$(mktemp "${target}.gt-install.XXXXXX") || return 2
  PRIVATE_FILES+=("$temporary")
  cat "$input" > "$temporary" && chmod "$mode" "$temporary" && chown grafioschtrader:grafioschtrader "$temporary" &&
    mv -T "$temporary" "$target"
}

gt_sql_literal() {
  # All mutating batches explicitly select NO_BACKSLASH_ESCAPES. Only quotes need doubling.
  local value=${1//\'/\'\'}
  printf "'%s'" "$value"
}

gt_core_sql() {
  local client options status=0
  client=$(command -v mariadb) || return 2
  options=$(gt_db_options DB_ROOT_PASSWORD root) || return 2
  # Caller supplies stdin. Neither SQL nor client errors can enter public logs or argv.
  "$client" "--defaults-file=$options" --protocol=SOCKET --user=root --batch --skip-column-names \
    --connect-timeout=4 2>/dev/null || status=$?
  rm -f -- "$options"
  return "$status"
}

gt_core_login() {
  local key=$1 user=$2 protocol=$3 identity=${4:-root} file dir value status=0
  local -a command=(mariadb)
  if [[ "$identity" == app ]]; then
    dir=$(mktemp -d /tmp/gt-dbcheck.XXXXXX) || return 2
    PRIVATE_DIRS+=("$dir")
    file=$(gt_db_options "$key" "$user") || return 2
    mv "$file" "$dir/client.cnf" && chown -R grafioschtrader:grafioschtrader "$dir" || return 2
    file="$dir/client.cnf"; command=(runuser -u grafioschtrader -- mariadb)
  else file=$(gt_db_options "$key" "$user") || return 2; fi
  command+=("--defaults-file=$file" "--protocol=$protocol" "--user=$user"
    --batch --skip-column-names --connect-timeout=4)
  [[ "$protocol" != TCP ]] || command+=(--host=127.0.0.1 --port=3306)
  value=$(printf 'SELECT CURRENT_USER();\n' | "${command[@]}" 2>/dev/null) || status=$?
  rm -f -- "$file"
  [[ -z "${dir:-}" ]] || rmdir "$dir"
  (( status == 0 )) && [[ "$value" == "$user@localhost" ]]
}

gt_core_packages() {
  local transaction line
  [[ "${STATE[new_database_server]}" == yes ]] || return 0
  if dpkg-query -W -f='${db:Status-Status}' mariadb-server 2>/dev/null | grep -qx installed; then
    [[ "${STATE[resource.mariadb]:-}" == intent || "${STATE[resource.mariadb]:-}" == owned ]] || return 2
  else
    [[ "${STATE[resource.mariadb]:-}" != owned ]] || return 2
    transaction=$(LC_ALL=C apt-get --simulate install mariadb-server mariadb-client 2>/dev/null) || return 2
    while IFS= read -r line; do
      [[ "$line" != 'Remv '* && ! "$line" =~ ^Inst\ [^\ ]+\ \[ ]] || return 2
    done <<< "$transaction"
    gt_core_mark resource.mariadb intent || return 2
    # A failed transaction stays resumable; do not automatically upgrade/remove existing packages.
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install mariadb-server mariadb-client || return 2
  fi
  gt_core_mark resource.mariadb owned || return 2
  # Debian's postinst also enables mariadb.socket, whose ListenStream=3306 accepts on every address and bypasses
  # bind-address. A server installed here serves loopback only, like the backend.
  if systemctl is-enabled --quiet mariadb.socket 2>/dev/null || systemctl is-active --quiet mariadb.socket; then
    gt_core_run systemctl disable --now mariadb.socket || return 2
    gt_core_run systemctl restart mariadb.service || return 2
  fi
  gt_core_run systemctl start mariadb.service || return 2
  gt_mariadb_loopback_only ||
    { gt_core_error 'The new MariaDB server listens beyond loopback on port 3306.'; return 2; }
}

gt_mariadb_loopback_only() {
  local listeners
  listeners=$(ss -Htln) || return 1
  ! awk '$4 ~ /:3306$/ && $4 !~ /^(127\.|\[?::1\]?:|\[::ffff:127\.)/ {found=1} END {exit !found}' <<< "$listeners"
}

gt_core_root_auth() {
  local mode wrong saved
  [[ "${STATE[new_database_server]}" == yes ]] || return 0
  # Authenticate under the fresh, disabled-login service identity: Linux root's socket
  # privilege must not make an incorrect password look valid.
  if ! gt_core_login DB_ROOT_PASSWORD root SOCKET app; then
    [[ "${STATE[root_auth]:-}" != verified ]] || return 2
    mode=$(gt_core_sql <<'SQL'
SELECT CASE
 WHEN JSON_VALUE(Priv,'$.plugin')='mysql_native_password'
  AND JSON_VALUE(Priv,'$.authentication_string')='invalid'
  AND JSON_SEARCH(Priv,'one','unix_socket') IS NOT NULL THEN 'dual-unset'
 WHEN JSON_VALUE(Priv,'$.plugin')='unix_socket' AND JSON_EXTRACT(Priv,'$.auth_or') IS NULL THEN 'socket-only'
 ELSE 'configured' END FROM mysql.global_priv WHERE User='root' AND Host='localhost';
SQL
    ) || return 2
    [[ "$mode" == dual-unset || "$mode" == socket-only ]] || return 2
    gt_core_mark root_auth intent || return 2
    if [[ "$mode" == dual-unset ]]; then
      { printf "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\nSET PASSWORD FOR 'root'@'localhost'=PASSWORD("
        gt_sql_literal "${SECRET[DB_ROOT_PASSWORD]}"; printf ');\n'; } | gt_core_sql >/dev/null || return 2
    else
      { printf "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\n"
        printf "ALTER USER 'root'@'localhost' IDENTIFIED VIA unix_socket OR mysql_native_password USING PASSWORD("
        gt_sql_literal "${SECRET[DB_ROOT_PASSWORD]}"; printf ');\n'; } | gt_core_sql >/dev/null || return 2
    fi
    gt_core_login DB_ROOT_PASSWORD root SOCKET app || return 2
  fi
  saved=${SECRET[DB_ROOT_PASSWORD]}
  wrong=$(openssl rand -hex 32) || return 2
  SECRET[DB_ROOT_PASSWORD]=$wrong
  if gt_core_login DB_ROOT_PASSWORD root SOCKET app; then SECRET[DB_ROOT_PASSWORD]=$saved; return 2; fi
  SECRET[DB_ROOT_PASSWORD]=''
  if ! gt_core_login DB_ROOT_PASSWORD root SOCKET; then SECRET[DB_ROOT_PASSWORD]=$saved; return 2; fi
  SECRET[DB_ROOT_PASSWORD]=$saved
  gt_core_mark root_auth verified
}

gt_core_database() {
  local result accounts host pool=384M mem=${FACT[memory.MemTotal]:-0} file
  gt_core_packages || return 2
  gt_core_root_auth || return 2
  result=$(printf "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='grafioschtrader';\n" |
    gt_core_sql) || return 2
  [[ "$result" == 0 ]] || return 2 # Never modify or adopt nonempty data, even on resumption.
  result=$(printf '%s\n' "SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA" \
    "WHERE SCHEMA_NAME='grafioschtrader';" | gt_core_sql) || return 2
  if [[ -n "$result" ]]; then
    [[ "$result" == $'utf8mb4\tutf8mb4_general_ci' ]] || return 2
    [[ "${STATE[database_before]}" == yes && "${ANSWER[DB_REUSE_EMPTY]:-}" == yes ||
      "${STATE[resource.database]:-}" == intent || "${STATE[resource.database]:-}" == owned ]] || return 2
  else
    [[ "${STATE[database_before]}" == no && "${STATE[resource.database]:-}" != owned ]] || return 2
    gt_core_mark resource.database intent || return 2
    printf 'CREATE DATABASE grafioschtrader CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n' |
      gt_core_sql >/dev/null || return 2
  fi
  if [[ "${STATE[database_before]}" == no ]]; then gt_core_mark resource.database owned || return 2; fi
  result=$(printf "SELECT COUNT(*) FROM mysql.user WHERE User='grafioschtrader' AND Host='localhost';\n" |
    gt_core_sql) || return 2
  if [[ "$result" == 1 ]]; then
    [[ "${STATE[account_before]}" == yes || "${STATE[resource.account]:-}" == intent ||
      "${STATE[resource.account]:-}" == owned ]] || return 2
    gt_core_login DB_PASSWORD grafioschtrader TCP || return 2
  elif [[ "$result" == 0 ]]; then
    [[ "${STATE[account_before]}" == no && "${STATE[resource.account]:-}" != owned ]] || return 2
    gt_core_mark resource.account intent || return 2
    { printf "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\nCREATE USER 'grafioschtrader'@'localhost' IDENTIFIED BY "
      gt_sql_literal "${SECRET[DB_PASSWORD]}"; printf ';\n'; } | gt_core_sql >/dev/null || return 2
  else return 2; fi
  if [[ "${STATE[account_before]}" == no ]]; then
    printf "GRANT ALL PRIVILEGES ON grafioschtrader.* TO 'grafioschtrader'@'localhost';\n" | gt_core_sql >/dev/null ||
      return 2
    gt_core_mark resource.account owned || return 2
  fi
  gt_core_login DB_PASSWORD grafioschtrader TCP || return 2
  result=$(printf "SHOW GRANTS FOR 'grafioschtrader'@'localhost';\n" | gt_core_sql) || return 2
  # shellcheck disable=SC2016
  [[ "$result" == *'GRANT ALL PRIVILEGES ON `grafioschtrader`.* TO '* ]] || return 2
  if [[ "${STATE[new_database_server]}" == yes ]]; then
    accounts=$(printf "SELECT Host FROM mysql.user WHERE User='';\n" | gt_core_sql) || return 2
    result=$(printf "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='test';\n" | gt_core_sql) ||
      return 2
    if [[ "${STATE[step.hardening]:-}" == complete ]]; then
      [[ -z "$accounts" && "$result" == 0 ]] || return 2
    else
    while IFS= read -r host; do
      [[ -n "$host" ]] || continue
      { printf "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\nDROP USER ''@"; gt_sql_literal "$host"; printf ';\n'; } |
        gt_core_sql >/dev/null || return 2
    done <<< "$accounts"
    printf 'DROP DATABASE IF EXISTS test;\n' | gt_core_sql >/dev/null || return 2
    gt_core_mark step.hardening complete || return 2
    fi
  fi
  if [[ "${ANSWER[BUFFER_POOL]:-no}" == yes ]]; then
    if (( mem >= 6000 )); then pool=2G; elif (( mem >= 3000 )); then pool=1G; fi
    file=/etc/mysql/mariadb.conf.d/60-grafioschtrader.cnf
    gt_no_symlinks "$file" || return 2
    printf '[mysqld]\ninnodb_buffer_pool_size=%s\n' "$pool" > "$SCRATCH/buffer.cnf"
    result=$(sha256sum "$SCRATCH/buffer.cnf"); result=${result%% *}
    if [[ -e "$file" ]]; then
      [[ "${STATE[file.buffer]:-}" == "$result" ]] && cmp -s "$file" "$SCRATCH/buffer.cnf" || return 2
    else
      gt_core_mark file.buffer "$result" || return 2
      install -m 644 "$SCRATCH/buffer.cnf" "$file" || return 2
    fi
    result=$(printf 'SELECT @@innodb_buffer_pool_size;\n' | gt_core_sql) || return 2
    case "$pool" in 2G) mem=2147483648 ;; 1G) mem=1073741824 ;; *) mem=402653184 ;; esac
    [[ "$result" == "$mem" ]] || gt_core_run systemctl restart mariadb.service || return 2
    gt_core_login DB_PASSWORD grafioschtrader TCP || return 2
  fi
  gt_core_mark step.database complete
}

gt_jasypt_call() (
  set +vx
  local goal=$1 directory=$2 marker=$3
  # File goals process literal bytes; Maven's value parameter can interpolate ${...}.
  # Only the key enters this child's environment; no plaintext enters command arguments.
  export JASYPT_ENCRYPTOR_PASSWORD="${SECRET[JASYPT_PASSWORD]}"
  # shellcheck disable=SC2016
  gt_as_app bash -c 'cd "$1" && shift && exec "$@"' gt-jasypt \
    "$CORE_REPO/backend/grafioschtrader-server" "${STATE[maven]}" -B -ntp \
    -Dstyle.color=never "-Djasypt.plugin.path=file:$directory/value.properties" \
    "-Djasypt.plugin.decrypt.prefix=${marker}_BEGIN" "-Djasypt.plugin.decrypt.suffix=${marker}_END" "jasypt:$goal"
)

gt_encrypt_secret() {
  local key=$1 directory output marker encrypted
  directory=$(mktemp -d /tmp/gt-crypto.XXXXXX) || return 2
  PRIVATE_DIRS+=("$directory")
  marker=$(openssl rand -hex 24) || return 2
  [[ "${SECRET[$key]}" != *"$marker"* ]] || return 2
  printf '%s_BEGIN%s%s_END\n' "$marker" "${SECRET[$key]}" "$marker" > "$directory/value.properties"
  chmod 600 "$directory/value.properties" && chown -R grafioschtrader:grafioschtrader "$directory" || return 2
  # Capture all diagnostics privately, including plaintext the plugin may emit on error/debug.
  output=$(gt_jasypt_call encrypt "$directory" "$marker" 2>&1) ||
    { gt_core_error "Jasypt encryption failed ($key); plugin output suppressed."; return 2; }
  encrypted=$(cat "$directory/value.properties")
  [[ "$encrypted" =~ ^ENC\([A-Za-z0-9+/=]+\)$ ]] ||
    { gt_core_error "Jasypt returned no unique ciphertext ($key)."; return 2; }
  output=$(gt_jasypt_call decrypt "$directory" "$marker" 2>&1) ||
    { gt_core_error "Jasypt decryption failed ($key); plugin output suppressed."; return 2; }
  # decrypt reports the result on stdout and deliberately leaves the encrypted file intact.
  [[ "$output" == *"${marker}_BEGIN${SECRET[$key]}${marker}_END"* ]] || {
    gt_core_error "Jasypt literal round-trip failed ($key)."; return 2;
  }
  rm -rf -- "$directory"
  ENCRYPTED=$encrypted
}

gt_core_config_valid() {
  local id target digest mode resources="$CORE_REPO/backend/grafioschtrader-server/src/main/resources"
  for id in properties production launcher variables; do
    case "$id" in
      properties) target="$resources/application.properties"; mode=600 ;;
      production) target="$resources/application-production.properties"; mode=600 ;;
      launcher) target="$CORE_HOME/grafioschtrader.sh"; mode=700 ;;
      variables) target="$CORE_HOME/gtvar.sh"; mode=600 ;;
    esac
    gt_no_symlinks "$target" && [[ -f "$target" ]] || return 2
    digest=$(sha256sum "$target"); digest=${digest%% *}
    [[ "$digest" == "${STATE[file.$id]:-}" || "$id" == properties && "${STATE[step.app_cron]:-}" == running &&
      "$digest" == "${STATE[file.properties.previous]:-}" ]] || return 2
    [[ "$(stat -c '%U:%a' "$target")" == "grafioschtrader:$mode" ]] || return 2
  done
}

gt_core_configure() {
  local template="$SCRATCH/application.template" production="$SCRATCH/production.template" original target key
  local auth=false starttls=false tls=false
  if [[ "${STATE[step.configuration]:-}" == complete ]]; then
    gt_core_config_valid || return 2
    [[ -z "${STATE[build.mode]:-}" ]] || gt_core_variables || return 2
    return 0
  fi
  target=backend/grafioschtrader-server/src/main/resources
  gt_as_app git -C "$CORE_REPO" show "${STATE[planned_commit]}:$target/application.properties" > "$template" || return 2
  if ! gt_as_app git -C "$CORE_REPO" show \
      "${STATE[planned_commit]}:$target/application-production.properties" > "$production" 2>/dev/null; then
    : > "$production"
  fi
  # Only the environment placeholder is allowed; application startup must not use a template key.
  # shellcheck disable=SC2016
  grep -Fxq 'jasypt.encryptor.password=${JASYPT_ENCRYPTOR_PASSWORD:}' "$template" || return 2
  PROPERTIES=([g.main.user.admin.mail]="${ANSWER[ADMIN_EMAIL]}" [g.allowed.users]="${ANSWER[ALLOWED_USERS]}"
    [spring.mail.host]='' [spring.mail.port]=587 [spring.mail.username]='' [spring.mail.password]=''
    [gt.connector.ajp.enabled]=false [gt.connector.ajp.port]="${ANSWER[BACKEND_PORT]}"
    [gt.connector.http.enabled]=true [gt.connector.http.port]="${ANSWER[BACKEND_PORT]}")
  if [[ "${ANSWER[WEBSERVER]}" == apache2 ]]; then
    PROPERTIES[gt.connector.ajp.enabled]=true PROPERTIES[gt.connector.http.enabled]=false
  fi
  gt_encrypt_secret DB_PASSWORD || return 2
  PROPERTIES[spring.datasource.password]=$ENCRYPTED
  gt_encrypt_secret JWT_SECRET || return 2
  PROPERTIES[g.jwt.secret]=$ENCRYPTED
  if [[ "${ANSWER[SMTP_CONFIGURE]}" == yes ]]; then
    PROPERTIES[spring.mail.host]=${ANSWER[SMTP_HOST]} PROPERTIES[spring.mail.port]=${ANSWER[SMTP_PORT]}
    PROPERTIES[spring.mail.username]=${ANSWER[SMTP_USER]}
    if [[ "${ANSWER[SMTP_AUTH]}" == yes ]]; then
      auth=true; gt_encrypt_secret SMTP_PASSWORD || return 2
      PROPERTIES[spring.mail.password]=$ENCRYPTED
    fi
    case "${ANSWER[SMTP_SECURITY]}" in starttls) starttls=true ;; tls) tls=true ;; esac
  fi
  PROPERTIES[spring.mail.properties.mail.smtp.auth]=$auth
  PROPERTIES[spring.mail.properties.mail.smtp.starttls.enable]=$starttls
  PROPERTIES[spring.mail.properties.mail.smtp.ssl.enable]=$tls
  gt_properties_render "$template" "$SCRATCH/application.config" || return 2
  original=$(sha256sum "$template"); original=${original%% *}
  gt_core_publish properties "$SCRATCH/application.config" "$CORE_REPO/$target/application.properties" 600 \
    "$original" || return 2
  PROPERTIES=([server.address]=127.0.0.1 [spring.mail.properties.mail.smtp.starttls.required]="$starttls")
  PROPERTIES[server.forward-headers-strategy]=none
  PROPERTIES[g.security.login.trusted-proxies]='127.0.0.1,::1'
  if [[ "${ANSWER[TLS_SOURCE]:-}" == proxy && -n "${ANSWER[TLS_PROXY_FROM]:-}" ]]; then
    PROPERTIES[g.security.login.trusted-proxies]+=",${ANSWER[TLS_PROXY_FROM]}"
  fi
  if [[ "${ANSWER[WEBSERVER]}" == apache2 ]]; then PROPERTIES[server.port]=${ANSWER[BACKEND_HTTP_PORT]}; fi
  gt_properties_render "$production" "$SCRATCH/production.config" yes || return 2
  original=$(sha256sum "$production"); original=${original%% *}
  gt_core_publish production "$SCRATCH/production.config" "$CORE_REPO/$target/application-production.properties" 600 \
    "$original" || return 2
  # printf %q is Bash syntax; use a Bash shebang for every generated launcher.
  {
    printf '#!/bin/bash\nexport JASYPT_ENCRYPTOR_PASSWORD=%q\n' "${SECRET[JASYPT_PASSWORD]}"
    printf 'exec %q %s -Duser.language=en -jar %s >> /var/log/grafioschtrader.log 2>&1\n' \
      "${STATE[java_home]}/bin/java" "${ANSWER[JAVA_HEAP]}" '/home/grafioschtrader/grafioschtrader-server-*.jar'
  } > "$SCRATCH/launcher"
  bash -n "$SCRATCH/launcher" || return 2
  gt_core_publish launcher "$SCRATCH/launcher" "$CORE_HOME/grafioschtrader.sh" 700 || return 2
  gt_core_variables || return 2
  gt_core_config_valid && gt_core_mark step.configuration complete
}

gt_core_variables() {
  {
    printf 'export docroot=%q\nexport builddir=%q\nexport basehref=grafioschtrader/\nexport NG_CLI_ANALYTICS=false\n' \
      "${ANSWER[DOCROOT]}" "$CORE_HOME/build"
    # shellcheck disable=SC2016
    printf 'export JAVA_HOME=%q\nexport PATH="$JAVA_HOME/bin:%s:$PATH"\n' "${STATE[java_home]}" "${STATE[maven]%/*}"
    if [[ -n "${STATE[build.node_home]:-}" ]]; then
      printf 'export npm_config_prefix=%q\nunset NODE_OPTIONS NODE_PATH\n' "${STATE[build.prefix]}"
      # shellcheck disable=SC2016
      printf 'export PATH="%s/bin:%s/bin:$PATH"\n' "${STATE[build.node_home]}" "${STATE[build.prefix]}"
    fi
  } > "$SCRATCH/variables"
  bash -n "$SCRATCH/variables" || return 2
  gt_core_publish variables "$SCRATCH/variables" "$CORE_HOME/gtvar.sh" 600 || return 2
}

# Guidance is display data. No download, repository enrollment or command evaluation occurs here.
gt_toolchain_guidance() {
  local architecture archive
  if [[ "${ACTION[java]:-reuse}" != reuse ]]; then
    printf '\n'; gt_text 'Java installation choices' 'Java-Installationswege'
    printf '  apt-cache policy openjdk-%s-jdk-headless\n' "$JAVA_REQUIRED"
    gt_text 'A suitable APT candidate is installed after confirmation; refresh stale lists with sudo apt-get update.' \
      'Ein passender APT-Kandidat wird nach Bestätigung installiert; alte Listen mit sudo apt-get update erneuern.'
    gt_text 'Otherwise the plan resolves a vendor JDK archive (Eclipse Temurin on amd64/arm64, BellSoft Liberica' \
      'Andernfalls ermittelt der Plan ein Hersteller-JDK-Archiv (Eclipse Temurin auf amd64/arm64, BellSoft Liberica'
    gt_text 'on armhf), verifies its vendor checksum and unpacks it into a new /opt/jdk-<version>-<vendor>.' \
      'auf armhf), prüft die Herstellerprüfsumme und entpackt es in ein neues /opt/jdk-<Version>-<Hersteller>.'
    gt_text 'Alternatives and the system Java selection stay unchanged.' \
      'Alternativen und die System-Java-Auswahl bleiben unverändert.'
    architecture=${FACT[architecture]:-unknown}
    case "$architecture" in
      amd64) archive=amd64 ;; arm64) archive=aarch64 ;; armhf) archive=arm32-vfp-hflt ;; *) archive=unknown ;;
    esac
    gt_text 'If the vendor metadata cannot be resolved, install it manually:' \
      'Falls die Herstellerdaten nicht ermittelt werden können, manuell installieren:'
    printf '  Liberica JDK %s: Linux %s (%s), tar.gz\n' "$JAVA_REQUIRED" "$archive" "$architecture"
    gt_text 'Select the current matching JDK archive, verify its vendor checksum, unpack into a new /usr/local/jdk-25' \
      'Aktuelles passendes JDK-Archiv wählen, Herstellerprüfsumme prüfen, in ein neues /usr/local/jdk-25 entpacken'
    gt_text 'and verify bin/java --version and bin/javac --version. The installer detects this directory.' \
      'und bin/java --version sowie bin/javac --version prüfen. Der Installer erkennt dieses Verzeichnis.'
    printf '  https://github.com/grafioschtrader/grafioschtrader/wiki/Install-Java\n'
    printf '  https://bell-sw.com/pages/downloads/\n'
  fi
  if [[ "${ACTION[maven]:-reuse}" != reuse ]]; then
    printf '\n'; gt_text 'Maven installation choices' 'Maven-Installationswege'
    printf '  apt-cache policy maven\n'
    gt_text 'APT Maven >= 3.8 is installed after confirmation. Otherwise the plan resolves the current stable' \
      'APT-Maven >= 3.8 wird nach Bestätigung installiert. Andernfalls ermittelt der Plan das aktuelle stabile'
    gt_text 'Maven 3 binary archive plus SHA-512 from Apache and unpacks it into a new /opt/apache-maven-<version>.' \
      'Maven-3-Binärarchiv samt SHA-512 von Apache und entpackt es in ein neues /opt/apache-maven-<Version>.'
    gt_text 'Neither the global PATH nor an /opt/maven symlink is changed. If the Apache metadata cannot be resolved,' \
      'Weder globaler PATH noch ein /opt/maven-Symlink werden geändert. Falls die Apache-Daten nicht ermittelbar'
    gt_text 'perform these steps manually; the installer detects the versioned directory.' \
      'sind, diese Schritte manuell ausführen; der Installer erkennt das Versionsverzeichnis.'
    printf '  https://github.com/grafioschtrader/grafioschtrader/wiki/Installing-the-Latest-Release-of-Apache-Maven\n'
    printf '  https://maven.apache.org/download.cgi\n'
  fi
}

# Vendor archives isolate Java/Maven for Grafioschtrader in a new versioned directory. They never register
# alternatives or change the global PATH; gtvar.sh, the launcher and the unit name the selected paths.
gt_toolchain_source() {
  case "$1:$2" in
    java:amd64|java:arm64) echo temurin ;;
    java:armhf) echo liberica ;;
    maven:amd64|maven:arm64|maven:armhf) echo apache ;;
    *) return 2 ;;
  esac
}

gt_toolchain_file() {
  local tool=$1 source=$2 version=$3 arch=$4 major=${3%%[.+]*} build=${3/+/_}
  case "$tool:$source:$arch" in
    java:temurin:amd64) printf 'OpenJDK%sU-jdk_x64_linux_hotspot_%s.tar.gz\n' "$major" "$build" ;;
    java:temurin:arm64) printf 'OpenJDK%sU-jdk_aarch64_linux_hotspot_%s.tar.gz\n' "$major" "$build" ;;
    java:liberica:armhf) printf 'bellsoft-jdk%s-linux-arm32-vfp-hflt.tar.gz\n' "$version" ;;
    maven:apache:*) printf 'apache-maven-%s-bin.tar.gz\n' "$version" ;;
    *) return 2 ;;
  esac
}

# The archive's single top-level directory, checked before extraction.
gt_toolchain_root() {
  case "$1" in
    temurin) printf 'jdk-%s\n' "$2" ;;
    liberica) printf 'jdk-%s\n' "${2%%+*}" ;;
    apache) printf 'apache-maven-%s\n' "$2" ;;
    *) return 2 ;;
  esac
}

# Destinations avoid '+' so they remain literal paths in gtvar.sh, the launcher and the systemd unit.
gt_toolchain_home() {
  case "$1" in
    java) printf '/opt/jdk-%s-%s\n' "${3/+/_}" "$2" ;;
    maven) printf '/opt/apache-maven-%s\n' "$3" ;;
    *) return 2 ;;
  esac
}

gt_toolchain_resource() { [[ "$1" == java ]] && echo jdk || echo maven_archive; }

gt_toolchain_urls() {
  local source=$1 version=$2 file=$3
  case "$source" in
    temurin) printf 'https://github.com/adoptium/temurin%s-binaries/releases/download/jdk-%s/%s\n' \
      "${version%%[.+]*}" "${version/+/%2B}" "$file" ;;
    liberica) printf 'https://github.com/bell-sw/Liberica/releases/download/%s/%s\n' "$version" "$file" ;;
    # The CDN serves current releases only; the archive keeps a journaled version available for resumption.
    apache) printf 'https://dlcdn.apache.org/maven/maven-3/%s/binaries/%s\n' "$version" "$file"
      printf 'https://archive.apache.org/dist/maven/maven-3/%s/binaries/%s\n' "$version" "$file" ;;
    *) return 2 ;;
  esac
}

# Journaled metadata is literal data. Download URLs and destinations derive from these validated fields only.
gt_toolchain_archive_valid() {
  local tool=$1 source=$2 version=$3 file=$4 checksum=$5 arch
  case "$source" in
    temurin) [[ "$checksum" =~ ^sha256:[a-f0-9]{64}$ ]] ;;
    # BellSoft publishes SHA-1 only; it is compared with a file downloaded over HTTPS from the vendor release.
    liberica) [[ "$checksum" =~ ^sha1:[a-f0-9]{40}$ ]] ;;
    apache) [[ "$checksum" =~ ^sha512:[a-f0-9]{128}$ ]] ;;
    *) false ;;
  esac || return 2
  if [[ "$tool" == java ]]; then [[ "$version" =~ ^[0-9]+(\.[0-9]+){0,3}\+[0-9]+$ ]] || return 2
  else [[ "$tool" == maven && "$version" =~ ^3\.[0-9]+\.[0-9]+$ ]] || return 2; fi
  for arch in amd64 arm64 armhf; do
    [[ "$(gt_toolchain_source "$tool" "$arch")" == "$source" &&
      "$(gt_toolchain_file "$tool" "$source" "$version" "$arch")" == "$file" ]] && return 0
  done
  return 2
}

gt_toolchain_fetch() {
  gt_probe curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout 5 --max-time 10 "$1" -o "$2"
}

gt_jdk_metadata() {
  python3 - "$1" "$JAVA_REQUIRED" "$2" "$3" <<'PY'
# @python jdk-metadata.py
PY
}

# Resolve the newest vendor archive for this architecture before confirmation.
gt_toolchain_resolve() {
  local tool=$1 arch=${FACT[architecture]:-unknown} source token url metadata version='' file='' checksum=''
  source=$(gt_toolchain_source "$tool" "$arch") || return 2
  case "$source" in
    temurin|liberica)
      if [[ "$source" == temurin ]]; then
        [[ "$arch" == amd64 ]] && token=x64 || token=aarch64
        url="https://api.adoptium.net/v3/assets/latest/$JAVA_REQUIRED/hotspot?architecture=$token"
        url+='&image_type=jdk&os=linux&vendor=eclipse'
      else
        token=arm32-vfp-hflt
        url="https://api.bell-sw.com/v1/liberica/releases?version-feature=$JAVA_REQUIRED&version-modifier=latest"
        url+='&bitness=32&os=linux&arch=arm&package-type=tar.gz&bundle-type=jdk'
      fi
      gt_toolchain_fetch "$url" "$SCRATCH/jdk-metadata" || return 2
      metadata=$(gt_jdk_metadata "$source" "$token" "$SCRATCH/jdk-metadata") || return 2
      IFS=$'\t' read -r version file checksum <<< "$metadata" ;;
    apache)
      gt_toolchain_fetch https://downloads.apache.org/maven/maven-3/ "$SCRATCH/maven-index" || return 2
      version=$(grep -oE 'href="3\.[0-9]+\.[0-9]+/"' "$SCRATCH/maven-index" | sed -E 's/^href="(.*)\/"$/\1/' |
        LC_ALL=C sort -t . -k1,1n -k2,2n -k3,3n | tail -n 1)
      [[ "$version" =~ ^3\.[0-9]+\.[0-9]+$ ]] || return 2
      file=$(gt_toolchain_file maven apache "$version" "$arch") || return 2
      url="https://downloads.apache.org/maven/maven-3/$version/binaries/$file.sha512"
      gt_toolchain_fetch "$url" "$SCRATCH/maven-sha512" || return 2
      checksum=sha512:$(awk 'NR == 1 {print $1}' "$SCRATCH/maven-sha512") ;;
  esac
  gt_toolchain_archive_valid "$tool" "$source" "$version" "$file" "$checksum" || return 2
  [[ "$(gt_toolchain_file "$tool" "$source" "$version" "$arch")" == "$file" ]] || return 2
  [[ "$tool" != java || "${version%%[.+]*}" == "$JAVA_REQUIRED" ]] || return 2
  FACT[toolchain.$tool.source]=$source FACT[toolchain.$tool.version]=$version
  FACT[toolchain.$tool.file]=$file FACT[toolchain.$tool.checksum]=$checksum
}

# A resumed journal keeps its recorded archive; a new plan resolves the current one. The exact version, file and
# checksum are part of the confirmed plan.
gt_toolchain_archive_plan() {
  local tool=$1 key target label summary arch=${FACT[architecture]:-unknown}
  for key in python3 tar gzip; do
    command -v "$key" >/dev/null || { gt_plan_block "Toolchain archive installation requires $key."; return 0; }
  done
  if [[ "${STATE[toolchain.$tool.package]:-}" == archive ]]; then
    for key in source version file checksum; do FACT[toolchain.$tool.$key]=${STATE[toolchain.$tool.$key]}; done
    key=$(gt_toolchain_file "$tool" "${FACT[toolchain.$tool.source]}" "${FACT[toolchain.$tool.version]}" "$arch")
    [[ "$key" == "${FACT[toolchain.$tool.file]}" ]] ||
      gt_plan_block "Recorded $tool archive ${FACT[toolchain.$tool.file]} does not match architecture $arch."
  elif ! gt_toolchain_resolve "$tool"; then
    gt_plan_block "Could not resolve a verified $tool archive and checksum from its vendor; check network access \
or follow the alternative instructions above." "Kein geprüftes $tool-Archiv mit Prüfsumme vom Hersteller \
ermittelbar; Netzwerkzugang prüfen oder alternative Anleitung oben befolgen."
    return 0
  fi
  FACT[toolchain.$tool.package]=archive FACT[toolchain.$tool.path]=pending
  target=$(gt_toolchain_home "$tool" "${FACT[toolchain.$tool.source]}" "${FACT[toolchain.$tool.version]}")
  key=$(gt_toolchain_resource "$tool")
  if [[ ( -e "$target" || -L "$target" ) && -z "${STATE[resource.$key]:-}" ]]; then
    gt_plan_block "Foreign $tool destination exists: $target"
  fi
  case "${FACT[toolchain.$tool.source]}" in
    temurin) label='Eclipse Temurin' ;;
    liberica) label='BellSoft Liberica' ;;
    *) label='Apache Maven' ;;
  esac
  summary="$label ${FACT[toolchain.$tool.version]}; ${FACT[toolchain.$tool.file]}; ${FACT[toolchain.$tool.checksum]}"
  gt_plan_row isolate "$target" "$summary; alternatives and global PATH unchanged" \
    "$summary; Alternativen und globaler PATH unverändert"
}

gt_toolchain_download() {
  # A JDK is large; slow lines need more than the build-tool limit. The URL derives from validated metadata.
  curl --proto '=https' --proto-redir '=https' --tlsv1.2 -fsSL --connect-timeout 10 --max-time 3600 --retry 2 \
    "$1" -o "$2"
}

gt_toolchain_checksum() {
  local algorithm=${2%%:*} value
  case "$algorithm" in sha1|sha256|sha512) value=$("${algorithm}sum" -- "$1") || return 2 ;; *) return 2 ;; esac
  [[ "${value%% *}" == "${2#*:}" ]]
}

# Unpack into a private stage and publish it by rename, so an interrupted download or extraction is discarded and
# repeated on resumption. An existing destination is accepted only with its recorded tree digest.
gt_toolchain_archive_install() {
  local tool=$1 id source=${STATE[toolchain.$1.source]} version=${STATE[toolchain.$1.version]}
  local file=${STATE[toolchain.$1.file]} target stage archive url='' root
  target=$(gt_toolchain_home "$tool" "$source" "$version") || return 2
  id=$(gt_toolchain_resource "$tool"); stage="${target%/*}/.gt-$id-${STATE[run_id]}"
  if [[ ! -e "$target" && ! -L "$target" ]]; then
    gt_build_stage "$id" "$stage" || return 2
    # The download stays on the destination's filesystem, which the disk plan covers: a JDK archive can exceed
    # a tmpfs /tmp on small hosts. Discarding the stage also discards a partial download.
    archive="$stage/.gt-download.tar.gz"
    while IFS= read -r url; do
      gt_toolchain_download "$url" "$archive" && break
    done < <(gt_toolchain_urls "$source" "$version" "$file")
    [[ -n "$url" ]] || { gt_core_error "Download of $file failed."; return 2; }
    gt_toolchain_checksum "$archive" "${STATE[toolchain.$tool.checksum]}" || {
      gt_core_error "Checksum of $file differs from the confirmed plan."; return 2;
    }
    root=$(gt_toolchain_root "$source" "$version") || return 2
    gt_build_archive_safe "$archive" "$root" || return 2
    tar --extract --gzip --file "$archive" --directory "$stage" --strip-components=1 \
      --no-same-owner --no-same-permissions || return 2
    rm -f -- "$archive"
    chmod -R a+rX,go-w "$stage" || return 2
    chmod 755 "$stage" || return 2
  fi
  gt_build_publish "$id" "$stage" "$target"
}

# Prefer candidates from already configured APT sources; otherwise plan a verified vendor archive.
gt_core_toolchain_plan() {
  local tool package candidate installed home key value en
  FACT[toolchain.java.package]=none FACT[toolchain.maven.package]=none
  FACT[toolchain.java.version]=none FACT[toolchain.maven.version]=none
  FACT[toolchain.java.path]=${FACT[java.suitable]} FACT[toolchain.maven.path]=${FACT[maven.path]}
  for tool in java maven; do
    if [[ "$tool" == java ]]; then package=openjdk-$JAVA_REQUIRED-jdk-headless; home=${FACT[java.suitable]}
    else package=maven; home=${FACT[maven.path]}; fi
    if [[ -n "${STATE[scope]:-}" && "${STATE[step.toolchains]:-complete}" == complete ]]; then
      [[ "$tool" == java ]] && key=java_home || key=maven
      if [[ "$tool" == java && -n "${STATE[java_home]:-}" ]] && ! gt_jdk_jit "${STATE[java_home]}"; then
        gt_plan_block \
          "Recorded Java ${STATE[java_home]} is the interpreter-only Zero VM; reinstall the host with this installer."
        continue
      fi
      [[ "$home" == "${STATE[$key]}" ]] || gt_plan_block "Selected $tool path changed; restore ${STATE[$key]}."
      gt_plan_row reuse "$tool" "${STATE[$key]}"
      continue
    fi
    if [[ "${STATE[toolchain.$tool.package]:-}" == archive ]]; then
      gt_toolchain_archive_plan "$tool"
      continue
    elif [[ -n "${STATE[toolchain.$tool.package]:-}" ]]; then
      package=${STATE[toolchain.$tool.package]}
      candidate=${STATE[toolchain.$tool.version]}
      if [[ "$package" == none ]]; then
        [[ "$tool" == java ]] && key=java_home || key=maven
        [[ "$home" == "${STATE[$key]}" ]] || gt_plan_block "Selected $tool path changed; restore ${STATE[$key]}."
        continue
      fi
    elif [[ "${ACTION[$tool]}" == reuse ]]; then
      gt_plan_row reuse "$tool" "$home"
      continue
    else
      candidate=${CANDIDATE[$package]:-unknown}
      installed=${PACKAGE[$package]:-absent}
      # Maven can be installed but unable to start until the new JDK exists.
      if [[ "$tool" == maven && "${FACT[java.suitable]}" == absent ]] &&
          gt_version_at_least "${installed%%-*}" 3.8 && [[ -x "$(gt_path /usr/bin/mvn)" ]]; then
        FACT[toolchain.maven.path]=$(gt_path /usr/bin/mvn)
        gt_plan_row reuse maven 'Installed APT Maven; verify using the selected JDK after Java installation'
        continue
      fi
      value=${candidate#*:}; value=${value%%-*}
      # The armhf distribution JDK is the Zero VM (see gt_jdk_jit); Liberica ships HotSpot with a JIT.
      if [[ ! "$candidate" =~ ^[a-zA-Z0-9.+:~_-]+$ || "$candidate" == unknown ]] ||
          [[ "$tool" == java && "${FACT[architecture]}" == armhf ]] ||
          { [[ "$tool" == maven ]] && ! gt_version_at_least "$value" 3.8; } ||
          { [[ "$tool" == java ]] && [[ ! "$value" =~ ^$JAVA_REQUIRED([.+~-]|$) ]]; }; then
        gt_toolchain_archive_plan "$tool"
        continue
      fi
      if [[ -n "${PACKAGE[$package]:-}" ]]; then
        en="$package is installed but unusable; repair it or use the documented alternative."
        gt_plan_block "$en Automatic upgrades are not authorized."
        continue
      fi
    fi
    FACT[toolchain.$tool.package]=$package FACT[toolchain.$tool.version]=$candidate
    FACT[toolchain.$tool.path]=pending
    if [[ "${PACKAGE[$package]:-}" != "$candidate" ]]; then
      [[ "${CANDIDATE[$package]:-}" == "$candidate" ]] ||
        gt_plan_block "Recorded candidate $package=$candidate is unavailable; restore its APT source before resuming."
      gt_plan_package "$package=$candidate"
    fi
    gt_plan_row install "$tool" "$package=$candidate; verify executables before creating the application user"
  done
  # Only APT packages can change alternatives; vendor archives never register them.
  if [[ ! "${FACT[toolchain.java.package]}:${FACT[toolchain.maven.package]}" =~ ^(none|archive):(none|archive)$ ]]; then
    command -v update-alternatives >/dev/null ||
      gt_plan_block 'update-alternatives is required before toolchain installation.'
    en='Restore previous selections after APT, including failed transactions;'
    en+=' changed automatic selections become manual.'
    gt_plan_row preserve java-alternatives "$en" \
      'Bisherige Auswahl nach APT wiederherstellen, auch bei Fehlern; geänderte automatische Auswahlen werden manuell.'
  fi
}

gt_toolchain_verify() {
  local home=$1 maven=$2 value major javac version
  [[ -x "$home/bin/java" && -x "$home/bin/javac" && -x "$maven" ]] || return 2
  value=$(gt_probe env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS "$home/bin/java" --version) || return 2
  major=$(sed -nE 's/^(openjdk|java) ([0-9]+)([. ].*|$)/\2/p' <<< "$value" | head -n 1)
  javac=$(gt_probe env -u JAVA_TOOL_OPTIONS -u JDK_JAVA_OPTIONS -u _JAVA_OPTIONS "$home/bin/javac" -version) || return 2
  [[ "$major" =~ ^[0-9]+$ && "$javac" =~ javac[[:space:]]+$major([.]|$) ]] && (( major >= JAVA_REQUIRED )) || return 2
  value=$(gt_maven_probe "$maven" "$home") || return 2
  version=$(gt_maven_version "$value")
  gt_version_at_least "$version" 3.8 && [[ "$value" =~ Java[[:space:]]version:[[:space:]]$major([,.]|$) ]]
}

gt_alternatives_save() {
  local selections name mode path
  [[ "${STATE[toolchain.alternatives]:-}" != saved ]] || return 0
  selections=$(LC_ALL=C update-alternatives --get-selections) || return 2
  while read -r name mode path; do
    [[ -n "$name" ]] || continue
    [[ "$name" =~ ^[a-zA-Z0-9_.+-]+$ && "$mode" =~ ^(auto|manual)$ && "$path" == /* &&
      "$path" != *[$'\001'-$'\037'$'\177']* ]] || return 2
    STATE[alternative.$name]=$path
  done <<< "$selections"
  STATE[toolchain.alternatives]=saved
  gt_state_save
}

gt_alternatives_restore() {
  local key name current failed=0
  [[ "${STATE[toolchain.alternatives]:-}" == saved ]] || return 0
  for key in "${!STATE[@]}"; do
    [[ "$key" == alternative.* ]] || continue
    name=${key#alternative.}
    current=$(LC_ALL=C update-alternatives --query "$name") || { failed=1; continue; }
    current=$(sed -n 's/^Value: //p' <<< "$current")
    if [[ "$current" != "${STATE[$key]}" ]]; then
      gt_core_run update-alternatives --set "$name" "${STATE[$key]}" || failed=1
    fi
  done
  (( failed == 0 )) ||
    { gt_core_error 'Could not restore original alternatives; resolve this before resuming.'; return 2; }
}

gt_core_toolchains() {
  local tool package version transaction line home maven failed=0
  local -a requested=() archives=()
  if [[ "${STATE[step.toolchains]:-complete}" == complete ]]; then
    gt_toolchain_verify "${STATE[java_home]}" "${STATE[maven]}"
    return $?
  fi
  for tool in java maven; do
    package=${STATE[toolchain.$tool.package]}; version=${STATE[toolchain.$tool.version]}
    case "$package" in
      none) ;;
      archive) archives+=("$tool") ;;
      *) requested+=("$package=$version") ;;
    esac
  done
  if (( ${#requested[@]} )); then
    # Restore a selection changed by an interrupted previous transaction before doing more work.
    gt_alternatives_restore || return 2
    transaction=$(LC_ALL=C apt-get --simulate install "${requested[@]}" 2>/dev/null) || return 2
    while IFS= read -r line; do
      [[ "$line" != 'Remv '* && ! "$line" =~ ^Inst\ [^\ ]+\ \[ ]] || return 2
    done <<< "$transaction"
    gt_alternatives_save || return 2
    gt_core_mark step.toolchains running || return 2
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install "${requested[@]}" || failed=1
    gt_alternatives_restore || return 2
    (( failed == 0 )) || return 2
  fi
  if (( ${#archives[@]} )); then
    [[ "${STATE[step.toolchains]}" == running ]] || gt_core_mark step.toolchains running || return 2
    for tool in "${archives[@]}"; do
      gt_toolchain_archive_install "$tool" ||
        { gt_core_error "Could not install the confirmed $tool archive."; return 2; }
    done
  fi
  gt_java; gt_runtimes
  # An archive is selected by its own destination, never by whatever discovery happens to rank first.
  home=${FACT[java.suitable]} maven=${FACT[maven.path]}
  [[ "${STATE[toolchain.java.package]}" != archive ]] ||
    home=$(gt_toolchain_home java "${STATE[toolchain.java.source]}" "${STATE[toolchain.java.version]}")
  [[ "${STATE[toolchain.maven.package]}" != archive ]] ||
    maven=$(gt_toolchain_home maven apache "${STATE[toolchain.maven.version]}")/bin/mvn
  gt_toolchain_verify "$home" "$maven" || {
    gt_core_error 'Java, javac and Maven must run successfully with the selected JDK.'; return 2;
  }
  [[ "${STATE[java_home]}" == pending || "${STATE[java_home]}" == "$home" ]] || return 2
  [[ "${STATE[maven]}" == pending || "${STATE[maven]}" == "$maven" ]] || return 2
  STATE[java_home]=$home STATE[maven]=$maven
  gt_core_mark step.toolchains complete
}

# Build metadata is literal data: never derive executable shell fragments from registry responses.
gt_build_field() {
  case "$1" in
    mode) [[ "$2" == reuse || "$2" == archive ]] ;;
    node_version|cli_version|semver_version) [[ "$2" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ;;
    node_home|npm) gt_valid_path "$2" ;;
    prefix) [[ "$2" == /opt/gt-build-tools ]] ;;
    node_file) [[ "$2" == none || "$2" =~ ^node-v[0-9]+\.[0-9]+\.[0-9]+-linux-(x64|arm64|armv7l)\.tar\.xz$ ]] ;;
    node_sha) [[ "$2" == none || "$2" =~ ^[a-f0-9]{64}$ ]] ;;
    cli_integrity|semver_integrity) [[ "$2" =~ ^sha512-[A-Za-z0-9+/]{86}==$ ]] ;;
    *) return 2 ;;
  esac
}

gt_build_valid() {
  local key
  for key in mode node_version node_home npm prefix node_file node_sha cli_version cli_integrity semver_version \
      semver_integrity; do
    gt_build_field "$key" "${BUILD[$key]:-}" || return 2
  done
  gt_node_satisfies "${BUILD[node_version]}" "$NODE_REQUIRED" || return 2
  [[ "${BUILD[cli_version]%%.*}" == "$CLI_REQUIRED" ]] || return 2
  if [[ "${BUILD[mode]}" == archive ]]; then
    [[ "${BUILD[node_home]}" == /opt/nodejs-gt && "${BUILD[npm]}" == /opt/nodejs-gt/bin/npm &&
      "${BUILD[node_file]}" == node-v"${BUILD[node_version]}"-linux-*.tar.xz && "${BUILD[node_sha]}" != none ]] ||
        return 2
  else
    [[ "${BUILD[node_file]}:${BUILD[node_sha]}" == none:none && "${BUILD[npm]}" == "${BUILD[node_home]}/bin/npm" ]] ||
      return 2
    case "${BUILD[node_home]}" in /usr|/usr/local|/opt/*) ;; *) return 2 ;; esac
  fi
}

gt_build_metadata() {
  local package=$1 major=$2 output=$3
  gt_probe curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout 5 --max-time 30 \
    -H 'Accept: application/vnd.npm.install-v1+json' "https://registry.npmjs.org/$package" -o "$output" || return 2
  python3 - "$output" "$major" <<'PY'
# @python npm-metadata.py
PY
}

gt_build_resolve() {
  local arch line checksum filename extra metadata key
  BUILD=()
  if [[ -n "${STATE[build.mode]:-}" ]]; then
    for key in "${!STATE[@]}"; do [[ "$key" != build.* ]] || BUILD[${key#build.}]=${STATE[$key]}; done
    gt_build_valid
    return $?
  fi
  BUILD[prefix]=/opt/gt-build-tools
  if gt_node_satisfies "${FACT[node.version]:-absent}" "$NODE_REQUIRED" &&
      [[ "${FACT[node.path]:-}" == /usr/bin/node || "${FACT[node.path]:-}" == /usr/local/bin/node ||
        "${FACT[node.path]:-}" == /opt/*/bin/node ]] &&
      [[ -x "${FACT[node.path]%/node}/npm" ]]; then
    BUILD[mode]=reuse BUILD[node_home]=${FACT[node.path]%/bin/node} BUILD[npm]=${FACT[node.path]%/node}/npm
    BUILD[node_version]=${FACT[node.version]} BUILD[node_file]=none BUILD[node_sha]=none
  else
    line=24
    case "${FACT[architecture]}" in
      amd64) arch=x64 ;; arm64) arch=arm64 ;; armhf) arch=armv7l; line=22 ;; *) return 2 ;;
    esac
    [[ "$arch" != armv7l || "$(date -u +%F)" < 2027-04-30 ]] || return 2
    gt_probe curl --proto '=https' --tlsv1.2 -fsSL --connect-timeout 5 --max-time 30 \
      "https://nodejs.org/dist/latest-v$line.x/SHASUMS256.txt" -o "$SCRATCH/node-sums" || return 2
    while read -r checksum filename extra; do
      [[ "$filename" =~ ^node-v$line\.[0-9]+\.[0-9]+-linux-$arch\.tar\.xz$ && "$checksum" =~ ^[a-f0-9]{64}$ &&
        -z "$extra" ]] || continue
      [[ -z "${BUILD[node_file]:-}" ]] || return 2
      BUILD[node_file]=$filename BUILD[node_sha]=$checksum
      BUILD[node_version]=${filename#node-v}; BUILD[node_version]=${BUILD[node_version]%%-linux-*}
    done < "$SCRATCH/node-sums"
    BUILD[mode]=archive BUILD[node_home]=/opt/nodejs-gt BUILD[npm]=/opt/nodejs-gt/bin/npm
  fi
  metadata=$(gt_build_metadata '@angular%2fcli' "$CLI_REQUIRED" "$SCRATCH/cli-metadata") || return 2
  IFS=$'\t' read -r 'BUILD[cli_version]' 'BUILD[cli_integrity]' <<< "$metadata"
  metadata=$(gt_build_metadata semver any "$SCRATCH/semver-metadata") || return 2
  IFS=$'\t' read -r 'BUILD[semver_version]' 'BUILD[semver_integrity]' <<< "$metadata"
  gt_build_valid
}

gt_core_build_plan() {
  local command target en
  for command in python3 tar xz; do
    command -v "$command" >/dev/null ||
      { gt_plan_block "Build tool preparation requires $command; install python3 and xz-utils, then rerun."; return 0; }
  done
  [[ "${ANSWER[NODE_REPLACE]:-no}" != yes ]] ||
    gt_plan_block 'Shared Node replacement is not supported by this core; select isolation (NODE_REPLACE=no).'
  if ! gt_build_resolve; then
    gt_plan_block 'Could not resolve compatible Node/Angular CLI/semver versions and checksums from official sources.'
    return 0
  fi
  target=${BUILD[prefix]}
  if [[ ( -e "$target" || -L "$target" ) && -z "${STATE[resource.buildtools]:-}" ]]; then
    gt_plan_block "Foreign build-tool prefix exists: $target"
  fi
  if [[ "${BUILD[mode]}" == archive ]]; then
    target=${BUILD[node_home]}
    if [[ ( -e "$target" || -L "$target" ) && -z "${STATE[resource.node]:-}" ]]; then
      gt_plan_block "Foreign Node destination exists: $target"
    fi
    gt_plan_row isolate "$target" "${BUILD[node_file]}; SHA-256 ${BUILD[node_sha]}; system Node unchanged"
  else gt_plan_row reuse node "${BUILD[node_home]}/bin/node; ${BUILD[node_version]}"; fi
  en="@angular/cli@${BUILD[cli_version]}, semver@${BUILD[semver_version]};"
  en+=' private global npm prefix, lifecycle scripts disabled'
  gt_plan_row install "${BUILD[prefix]}" "$en"
  gt_plan_row verify npm "${BUILD[cli_integrity]}; ${BUILD[semver_integrity]}"
  gt_plan_row configure gtvar.sh \
    'Selected Node/npm and build-tool prefix; disable Angular analytics; verify as grafioschtrader'
}

gt_build_record() {
  local key
  gt_build_valid || return 2
  if [[ -z "${STATE[build.mode]:-}" ]]; then
    for key in "${!BUILD[@]}"; do STATE[build.$key]=${BUILD[$key]}; done
    STATE[step.buildtools]=pending
    gt_state_save || return 2
  fi
}

# Hash contents, permissions and link destinations with relative names so publication by rename is resumable.
gt_build_digest() {
  python3 - "$1" <<'PY'
# @python tree-digest.py
PY
}

gt_build_publish() {
  local id=$1 stage=$2 destination=$3 digest current
  gt_no_symlinks "$destination" || return 2
  if [[ -e "$destination" ]]; then
    [[ "${STATE[resource.$id]:-}" == intent || "${STATE[resource.$id]:-}" == owned ]] || return 2
    current=$(gt_build_digest "$destination") || return 2
    [[ "$current" == "${STATE[file.$id]:-}" ]] || return 2
  else
    [[ "${STATE[resource.$id]:-}" == intent ]] || return 2
    digest=$(gt_build_digest "$stage") || return 2
    STATE[file.$id]=$digest
    gt_state_save || return 2
    mv -T -- "$stage" "$destination" || return 2
  fi
  gt_core_mark "resource.$id" owned
}

gt_build_stage() {
  local id=$1 stage=$2
  gt_no_symlinks "$stage" || return 2
  [[ "${STATE[resource.$id]:-}" != owned ]] || return 2
  if [[ -e "$stage" ]]; then
    [[ "${STATE[resource.$id]:-}" == intent && ( "$(stat -c '%u:%a' "$stage")" == "$EUID:700" ||
      "$(stat -c '%u:%a' "$stage")" == "$EUID:755" ) ]] || return 2
    rm -rf -- "$stage" || return 2
  fi
  gt_core_mark "resource.$id" intent || return 2
  mkdir -m 700 -- "$stage"
}

gt_build_archive_safe() {
  python3 - "$1" "$2" <<'PY'
# @python archive-safety.py
PY
}

gt_build_download() {
  # Execution downloads may take longer than inventory probes. All URLs are constructed from validated metadata.
  curl --proto '=https' --proto-redir '=https' --tlsv1.2 -fsSL --connect-timeout 10 --max-time 600 --retry 2 \
    "$1" -o "$2"
}

gt_build_npm() {
  local prefix=$1
  shift
  mkdir -p "$SCRATCH/npm-home" "$SCRATCH/build-cache" || return 2
  : > "$SCRATCH/npm-user-config"; : > "$SCRATCH/npm-global-config"
  (cd "$SCRATCH" && env -i HOME="$SCRATCH/npm-home" PATH="${STATE[build.node_home]}/bin:/usr/bin:/bin" \
    NG_CLI_ANALYTICS=false CI=true npm_config_userconfig="$SCRATCH/npm-user-config" \
    npm_config_globalconfig="$SCRATCH/npm-global-config" \
    npm_config_prefix="$prefix" npm_config_cache="$SCRATCH/build-cache" \
    npm_config_registry=https://registry.npmjs.org/ \
    npm_config_update_notifier=false npm_config_audit=false npm_config_fund=false npm_config_logs_max=0 \
    "${STATE[build.npm]}" "$@")
}

gt_build_verify() {
  local value prefix=${STATE[build.prefix]} node=${STATE[build.node_home]}/bin/node
  value=$(gt_probe env NODE_OPTIONS= "$node" --version) || return 2
  [[ "$value" == "v${STATE[build.node_version]}" ]] && gt_node_satisfies "$value" "$NODE_REQUIRED" || return 2
  gt_build_npm "$prefix" --version >/dev/null || return 2
  value=$(gt_build_npm "$prefix" ls -g --depth=0 --json) || return 2
  python3 -c '@python-inline npm-versions.py@' \
    "${STATE[build.cli_version]}" "${STATE[build.semver_version]}" <<< "$value" || return 2
  value=$(gt_as_app "$prefix/bin/semver" "${STATE[build.node_version]}" -r "$NODE_REQUIRED") || return 2
  [[ "$value" == "${STATE[build.node_version]}" ]] || return 2
  value=$(cd /tmp && gt_as_app "$prefix/bin/ng" version) || return 2
  value=$(sed -nE 's/^Angular CLI[[:space:]]*:[[:space:]]*([0-9]+\.[0-9]+\.[0-9]+).*/\1/p' <<< "$value")
  [[ "$value" == "${STATE[build.cli_version]}" ]]
}

gt_core_buildtools() {
  local stage archive checksum package version integrity url value key target
  [[ -n "${STATE[build.mode]:-}" ]] || return 2
  if [[ "${STATE[build.mode]}" == archive ]]; then
    target=${STATE[build.node_home]}; stage="/opt/.gt-node-${STATE[run_id]}"
    if [[ ! -e "$target" && ! -L "$target" ]]; then
      gt_build_stage node "$stage" || return 2
      archive="$SCRATCH/${STATE[build.node_file]}"
      gt_build_download "https://nodejs.org/dist/v${STATE[build.node_version]}/${STATE[build.node_file]}" "$archive" ||
        return 2
      checksum=$(sha256sum "$archive"); [[ "${checksum%% *}" == "${STATE[build.node_sha]}" ]] || return 2
      gt_build_archive_safe "$archive" "${STATE[build.node_file]%.tar.xz}" || return 2
      tar --extract --xz --file "$archive" --directory "$stage" --strip-components=1 --no-same-owner \
        --no-same-permissions || return 2
      chmod -R a+rX,go-w "$stage" || return 2
      chmod 755 "$stage" || return 2
    fi
    gt_build_publish node "$stage" "$target" || return 2
  fi
  value=$(gt_probe env NODE_OPTIONS= "${STATE[build.node_home]}/bin/node" --version) || return 2
  [[ "$value" == "v${STATE[build.node_version]}" ]] || return 2
  target=${STATE[build.prefix]}; stage="/opt/.gt-build-${STATE[run_id]}"
  if [[ ! -e "$target" && ! -L "$target" ]]; then
    gt_build_stage buildtools "$stage" || return 2
    for key in cli semver; do
      version=${STATE[build.${key}_version]}; integrity=${STATE[build.${key}_integrity]}
      [[ "$key" == cli ]] && package=@angular/cli || package=semver
      url="https://registry.npmjs.org/$package/-/${key}-$version.tgz"
      archive="$SCRATCH/$key.tgz"
      gt_build_download "$url" "$archive" || return 2
      value="sha512-$(openssl dgst -sha512 -binary "$archive" | openssl base64 -A)"
      [[ "$value" == "$integrity" ]] || return 2
    done
    gt_build_npm "$stage" install --global --ignore-scripts --engine-strict --no-audit --no-fund "$SCRATCH/cli.tgz" \
      "$SCRATCH/semver.tgz" || return 2
    chmod -R a+rX,go-w "$stage" || return 2
    chmod 755 "$stage" || return 2
  fi
  gt_build_publish buildtools "$stage" "$target" || return 2
  gt_build_verify || return 2
  gt_core_mark step.buildtools complete
}

gt_core_plan() {
  local key database_check en de
  PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=()
  gt_stage_contract || true
  for key in platform architecture mariadb database disk; do
    if [[ "$key" == database && "${STATE[new_database_server]:-}" == yes && "${STATE[resource.mariadb]:-}" == owned &&
        "${FACT[database.active]:-}" == no ]]; then
      continue # The execution step may start its own stopped server, then recheck every object.
    fi
    [[ "${ACTION[$key]:-block}" != block ]] || gt_plan_block "$key: ${REASON[$key]:-unknown}"
  done
  [[ "${FACT[init]}" == systemd ]] || gt_plan_block 'Core installation requires systemd.'
  gt_plan_base_packages
  gt_swap_plan
  gt_duckdns_plan
  gt_core_toolchain_plan
  gt_core_build_plan
  [[ "${FACT[source.requirements]}" == remote ]] || gt_plan_block 'Pinned source requirements must be available.'
  # git, curl and openssl are base packages: a minimal image gets them from the planned transaction.
  for key in runuser useradd passwd git curl openssl flock sha256sum; do
    command -v "$key" >/dev/null || [[ "${PLAN_PACKAGES[$key]:-}" == install ]] ||
      gt_plan_block "Missing prerequisite: $key"
  done
  [[ "${FACT[host.class]}" == fresh || "${FACT[host.class]}" == unfinished &&
    "${STATE[scope]:-}" =~ ^(core|bootstrap)$ ]] ||
    gt_plan_block 'Only fresh hosts and this installer journal can be used.'
  for key in "${QUESTIONS[@]}"; do
    [[ "${Q_TYPE[$key]}" != secret ]] || continue
    [[ "$key" != DB_REUSE_EMPTY || "${STATE[database_before]:-}" != no ]] || continue
    if gt_question_applies "$key"; then
      gt_validate_answer "$key" "${ANSWER[$key]:-}" || gt_plan_block "Invalid/missing answer: $key"
    fi
  done
  if [[ "${FACT[database.gt_tables]}" == 0 && "${STATE[database_before]:-}" != no &&
      "${ANSWER[DB_REUSE_EMPTY]:-}" != yes ]]; then
    gt_plan_block 'Reuse of the existing empty database requires consent.'
  fi
  if [[ "${FACT[database.active]:-}" == yes && "${FACT[database.query]}" == ok ]]; then
    if [[ "${FACT[database.gt_user]}" == present ]]; then
      gt_core_login DB_PASSWORD grafioschtrader TCP ||
        gt_plan_block 'Existing database credentials no longer authenticate.'
    fi
    if [[ "${FACT[database.gt_tables]}" == 0 ]]; then
      database_check=$(printf '%s\n' \
        "SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA" \
        "WHERE SCHEMA_NAME='grafioschtrader';" | gt_core_sql) || database_check=unknown
      [[ "$database_check" == $'utf8mb4\tutf8mb4_general_ci' ]] ||
        gt_plan_block 'Existing empty database must use utf8mb4 / utf8mb4_general_ci.'
    fi
    if [[ "${FACT[database.gt_user]}" == present && -z "${STATE[resource.account]:-}" ]]; then
      database_check=$(printf "SHOW GRANTS FOR 'grafioschtrader'@'localhost';\n" | gt_core_sql) ||
        database_check=unknown
      # shellcheck disable=SC2016
      [[ "$database_check" == *'GRANT ALL PRIVILEGES ON `grafioschtrader`.* TO '* ]] ||
        gt_plan_block 'Existing account lacks required schema privileges; it will not be altered.'
    fi
  fi
  # MODE is the shared CLI selection from 00-common.sh, not a local file mode.
  # shellcheck disable=SC2153
  if [[ "$MODE" != --bootstrap ]]; then
    en='This scope stops after database and encrypted configuration;'
    en+=' no build, backend service, web server or TLS changes.'
    de='Diese Stufe endet nach Datenbank und verschlüsselter Konfiguration;'
    de+=' kein Build, Backend-Dienst, Webserver oder TLS.'
    gt_plan_row configure core "$en" "$de"
  fi
  gt_plan_row create /var/lib/gt-install 'Root-only lock and atomic resumption journal (700/600)'
  gt_plan_row create /root/.gt-install/secrets 'Application secrets only (600); no database root password'
  gt_plan_row create user:grafioschtrader 'Disabled password; owned home and source checkout'
  gt_plan_row clone "$CORE_REPO" "${FACT[source.commit]}"
  if [[ "${FACT[database.vendor]}" == absent ]]; then
    gt_plan_package mariadb-server; gt_plan_package mariadb-client
    en='Distribution packages; no removals/upgrades; root password plus unix_socket;'
    en+=' remove default anonymous accounts and test schema'
    gt_plan_row install 'mariadb-server mariadb-client' "$en"
  fi
  gt_plan_packages
  if gt_plan_needs_apt; then
    [[ "${FACT[dpkg.lock]}" == free && "${FACT[apt.age_hours]}" != unknown ]] ||
      gt_plan_block 'Package lock/metadata unavailable; install psmisc and run sudo apt-get update, then re-plan.'
    if [[ "${FACT[apt.age_hours]}" != unknown ]] && (( ${FACT[apt.age_hours]} > 24 )); then
      gt_plan_block 'APT metadata is stale; run sudo apt-get update and re-plan.'
    fi
  fi
  if [[ "${STATE[new_database_server]:-}" == yes && "${FACT[database.active]:-}" == no ]]; then
    en='Resume the installer-owned database server,'
    en+=' then verify original credentials and empty schema before any SQL changes'
    gt_plan_row start mariadb.service "$en"
  fi
  gt_plan_row configure database:grafioschtrader \
    'Empty utf8mb4/general_ci schema; existing account unchanged; TCP verification'
  [[ "${ANSWER[BUFFER_POOL]:-no}" != yes ]] ||
    gt_plan_row configure mariadb 'Buffer pool drop-in and restart; affects all databases on this server'
  gt_plan_row configure "$CORE_REPO/backend/grafioschtrader-server/src/main/resources" \
    'Encrypted application.properties and production override; encryption/decryption verified'
  gt_plan_row create "$CORE_HOME/gtvar.sh, grafioschtrader.sh" \
    'Selected Java, heap and protected encryption key; service not installed or started'
  FACT[core.plan]=$(printf '%s\n' "${PLAN[@]}" | sha256sum)
  (( ${#PLAN_BLOCKERS[@]} == 0 ))
}

gt_core_snapshot() {
  local key
  for key in "${!FACT[@]}"; do
    case "$key" in
      dns.status|plan.names|network.public_ipv4|network.global_ipv6|core.plan|host.*|source.*|os.*|architecture|init|\
        java.suitable|java.alternatives|javac.alternatives|maven.path|node.path|node.version|node.origin|toolchain.*|\
        swap.*|memory.SwapTotal|apt.candidate.*|database.vendor|database.version|database.gt_tables|database.gt_user|\
        database.users|database.schemas|database.buffer_pool_config|packages|dpkg.lock)
        printf '%s=%s\n' "$key" "${FACT[$key]}" ;;
    esac
  done
  for key in "${!PACKAGE[@]}"; do printf 'package:%s=%s\n' "$key" "${PACKAGE[$key]}"; done
}

gt_core_execute() {
  local step en de
  for step in base_packages swap toolchains user duckdns buildtools clone database configure; do
    printf '%s: %s\n' "$(gt_text 'Core step' 'Kernschritt')" "$step"
    "gt_core_$step" ||
      { gt_core_error "$step; resume with --install-core after resolving the cause. No automatic rollback."; return 2; }
  done
  gt_core_mark step.core complete || return 2
  de='Installationskern eingerichtet.'
  de+=' Installation bleibt unvollständig: Build, Dienst, Web/TLS und Anwendungsprüfungen stehen aus.'
  en='Core prepared.'
  en+=' Installation remains unfinished: build, service, web/TLS and application checks are pending.'
  gt_text "$en" "$de"
  return 10
}
