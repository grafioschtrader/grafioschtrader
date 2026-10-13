# --uninstall removes what the installation journal records as the installer's own. The installation journal stays
# unchanged until the last step; the uninstall keeps its progress in /var/lib/gt-install/uninstall, so an
# interrupted run resumes and every other mode refuses meanwhile. The database and the packages stay unless
# --remove-database or --remove-packages asks for them; a file changed since the installation always stays.

GT_UNINSTALL_STEPS='service duckdns web tls firewall swap privileged log frontend database user toolchains
mariadb_settings packages alternatives'

gt_uninstall_error() {
  printf '%s: %s\n' "$(gt_text 'Uninstall stopped' 'Deinstallation angehalten')" "$1" >&2
  return 2
}

gt_uninstall_journal() { gt_path /var/lib/gt-install/uninstall; }
gt_uninstall_save() { gt_atomic_map "$(gt_uninstall_journal)" UNSTATE; }

gt_uninstall_load() {
  local line key value
  UNSTATE=()
  gt_private_read "$(gt_uninstall_journal)" || return 2
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    key=${line%%=*} value=${line#*=}
    [[ "$line" == *=* && "$key" =~ ^(schema|run_id|database|packages|dump|step\.[a-z_]+|(removed|kept)\.[0-9]+)$ &&
      -z "${UNSTATE[$key]+set}" && "$value" != *[$'\001'-$'\037'$'\177']* ]] || return 2
    UNSTATE[$key]=$value
  done <<< "$PRIVATE_CONTENT"
  PRIVATE_CONTENT=''
  [[ "${UNSTATE[schema]:-}" == 1 && "${UNSTATE[run_id]:-}" == "${STATE[run_id]}" &&
    "${UNSTATE[database]:-}" =~ ^(yes|no)$ && "${UNSTATE[packages]:-}" =~ ^(yes|no)$ ]]
}

# Notes are kept in the journal, so a resumed run reports what an earlier attempt already did.
gt_uninstall_note() {
  local kind=$1 text=$2 n=0
  while [[ -n "${UNSTATE[$kind.$n]+set}" ]]; do
    [[ "${UNSTATE[$kind.$n]}" != "$text" ]] || return 0
    n=$((n+1))
  done
  UNSTATE[$kind.$n]=$text
}

gt_uninstall_kept() {
  gt_uninstall_note kept "${1#"$ROOT"}: $(gt_text "$2" "$3")"
}

# Installer files of the web stage are first copied into a private stash, so a web server that fails its test after
# the removal can be returned to the previous state.
gt_uninstall_stash() {
  local target=$1 stash
  [[ -e "$target" || -L "$target" ]] || return 0
  stash="$(gt_path /var/lib/gt-install/uninstall-web)${target#"$ROOT"}"
  [[ -e "$stash" || -L "$stash" ]] && return 0
  # main runs with umask 077, so every directory of the stash is private.
  mkdir -p -- "${stash%/*}" && cp -a -- "$target" "$stash"
}

gt_uninstall_unstash() {
  local stash file target
  stash=$(gt_path /var/lib/gt-install/uninstall-web)
  [[ -d "$stash" ]] || return 0
  while IFS= read -r -d '' file; do
    target=$(gt_path "${file#"$stash"}")
    mkdir -p -- "${target%/*}" && cp -a -- "$file" "$target" || return 2
  done < <(find "$stash" \( -type f -o -type l \) -print0)
}

# An installer file is removed while its content is still the journaled one; other content stays and is reported.
gt_uninstall_file() {
  local id=$1 target=$2 digest
  [[ -e "$target" || -L "$target" ]] || return 0
  if ! gt_no_symlinks "$target" || [[ ! -f "$target" ]]; then
    gt_uninstall_kept "$target" 'not a regular file' 'keine reguläre Datei'; return 0
  fi
  digest=$(gt_app_root_digest "$id" "$target") || return 2
  if [[ -n "${STATE[file.$id]:-}" && ( "$digest" == "${STATE[file.$id]}" ||
      "$digest" == "${STATE[file.$id.previous]:-}" ) ]]; then
    [[ "${3:-}" != stash ]] || gt_uninstall_stash "$target" || return 2
    rm -f -- "$target" || return 2
    gt_uninstall_note removed "${target#"$ROOT"}"
  else
    gt_uninstall_kept "$target" 'changed since the installation' 'seit der Installation geändert'
  fi
}

# A directory the installer created; intent means an interrupted step that could only have created its own target.
gt_uninstall_tree() {
  local key=$1 target=$2
  [[ -e "$target" || -L "$target" ]] || return 0
  case "${STATE[resource.$key]:-}" in
    owned|intent)
      gt_no_symlinks "$target" || { gt_uninstall_kept "$target" 'path contains a symlink' 'Pfad enthält einen Symlink'
        return 0; }
      rm -rf -- "$target" || return 2
      gt_uninstall_note removed "${target#"$ROOT"}" ;;
    *) gt_uninstall_kept "$target" 'not recorded as the installer'"'"'s' 'nicht als Eigentum des Installers erfasst' ;;
  esac
}

gt_uninstall_service() {
  gt_core_run systemctl disable --now grafioschtrader.service > /dev/null 2>&1 || :
  gt_uninstall_file app_unit "$(gt_path /etc/systemd/system/grafioschtrader.service)" || return 2
  gt_core_run systemctl daemon-reload || return 2
  gt_core_run systemctl reset-failed grafioschtrader.service > /dev/null 2>&1 || :
}

gt_uninstall_duckdns() {
  local unit
  [[ -n "${STATE[file.duckdns_timer]:-}${STATE[file.duckdns_service]:-}" ]] || return 0
  unit=$(gt_path "/etc/systemd/system/$GT_DUCKDNS_UNIT")
  gt_core_run systemctl disable --now "$GT_DUCKDNS_UNIT.timer" > /dev/null 2>&1 || :
  gt_core_run systemctl stop "$GT_DUCKDNS_UNIT.service" > /dev/null 2>&1 || :
  gt_uninstall_file duckdns_timer "$unit.timer" && gt_uninstall_file duckdns_service "$unit.service" || return 2
  gt_core_run systemctl daemon-reload
}

# The include line goes out of the administrator's vhost. While the vhost is unchanged since the insertion, its
# backup is the same file without that line; otherwise only the line is removed and the vhost is reported.
gt_uninstall_vhost() {
  local target backup digest line temporary file
  [[ -n "${STATE[resource.vhost_target]:-}" ]] || return 0
  target=$(gt_path "${STATE[resource.vhost_target]}") backup=${STATE[resource.vhost_backup]:-}
  line=$(gt_vhost_include_line)
  if [[ -f "$target" ]] && grep -Fxq -- "$line" "$target"; then
    gt_no_symlinks "$target" && gt_uninstall_stash "$target" || return 2
    digest=$(sha256sum < "$target"); digest=${digest%% *}
    if [[ "$digest" == "${STATE[file.vhost_target]:-}" && -n "$backup" && -f "$(gt_path "$backup")" ]]; then
      cp -p -- "$(gt_path "$backup")" "$target" || return 2
    else
      temporary=$(mktemp "$target.gt-install.XXXXXX") || return 2
      PRIVATE_FILES+=("$temporary")
      grep -Fxv -- "$line" "$target" > "$temporary" || (( $? == 1 )) || return 2
      chmod --reference="$target" -- "$temporary" && chown --reference="$target" -- "$temporary" &&
        mv -T -- "$temporary" "$target" || return 2
      gt_uninstall_kept "$target" 'changed since the installation; only the include line was removed' \
        'seit der Installation geändert; nur die Include-Zeile wurde entfernt'
    fi
    gt_uninstall_note removed "${target#"$ROOT"}: include $(gt_vhost_snippet_path)"
  fi
  gt_uninstall_file vhost_snippet "$(gt_path "$(gt_vhost_snippet_path)")" stash || return 2
  for file in "$target".gt-install.[0-9]*Z; do
    [[ -f "$file" ]] || continue
    rm -f -- "$file" && gt_uninstall_note removed "${file#"$ROOT"}" || return 2
  done
}

# The LAN site is site_lan in a domain installation and web_nginx in a LAN-only nginx installation; both use the
# same file. A link is removed together with its own site file.
gt_uninstall_site() {
  local id=$1 target=$2 link=${2/sites-available/sites-enabled} files points=''
  [[ -e "$target" || -L "$link" ]] || return 0
  files=$(gt_uninstall_site_files "$id" "$target") || return 2
  [[ ! -L "$link" ]] || points=$(readlink "$link")
  if [[ -n "$points" && ( "$points" == "$target" || "$points" == "${target#"$ROOT"}" ||
      "$points" == "../sites-available/${target##*/}" ) ]]; then
    if [[ "$files" == owned ]]; then
      gt_uninstall_stash "$link" && rm -f -- "$link" || return 2
      gt_uninstall_note removed "${link#"$ROOT"}"
    else gt_uninstall_kept "$link" 'its site file changed' 'die zugehörige Site-Datei wurde geändert'; fi
  fi
  gt_uninstall_file "$id" "$target" stash
}

gt_uninstall_site_files() {
  local digest
  [[ -f "$2" ]] || { echo owned; return 0; }
  digest=$(sha256sum < "$2") || return 2; digest=${digest%% *}
  if [[ -n "${STATE[file.$1]:-}" && "$digest" == "${STATE[file.$1]}" ]]; then echo owned; else echo changed; fi
}

gt_uninstall_sites() {
  local id lan=site_lan
  [[ -n "${STATE[file.site_lan]:-}" || -z "${STATE[file.web_nginx]:-}" ]] || lan=web_nginx
  gt_uninstall_site "$lan" "$(gt_site_path lan)" || return 2
  for id in http domain; do gt_uninstall_site "site_$id" "$(gt_site_path "$id")" || return 2; done
  [[ "${ANSWER[WEBSERVER]}" == apache2 ]] || return 0
  gt_uninstall_file apache_listen "$(gt_path /etc/apache2/conf-enabled/grafioschtrader-listen.conf)" stash || return 2
  for id in tls lan; do
    [[ ! -f "$(gt_path "/var/lib/gt-install/apache-$id-before")" ]] || gt_apache_modules_restore "$id" || return 2
  done
  [[ -L "$(gt_path /etc/apache2/sites-enabled/000-default.conf)" ]] || gt_apache_default_restore
}

# The sites that existed before the installation (the journaled endpoints) must answer as before; a failed test or
# comparison puts the stashed installer files back.
gt_uninstall_web() {
  local web=${ANSWER[WEBSERVER]:-none} file attempt
  gt_uninstall_file web_manual "$(gt_path "$GT_WEB_MANUAL")" || return 2
  [[ "$web" == nginx || "$web" == apache2 ]] || return 0
  for file in extended-web-endpoints web-endpoints; do
    [[ -f "$(gt_path "/var/lib/gt-install/$file")" ]] || continue
    cp -- "$(gt_path "/var/lib/gt-install/$file")" "$SCRATCH/web-endpoints" && break
  done
  [[ ! -f "$SCRATCH/web-endpoints" ]] || gt_nginx_statuses > "$SCRATCH/web-before" || return 2
  gt_uninstall_vhost && gt_uninstall_sites || return 2
  if gt_site_test && gt_core_run systemctl reload "$web.service"; then
    [[ -f "$SCRATCH/web-endpoints" ]] || return 0
    for ((attempt=0; attempt<10; attempt++)); do
      gt_nginx_statuses > "$SCRATCH/web-after" && cmp -s "$SCRATCH/web-before" "$SCRATCH/web-after" && return 0
      sleep 1
    done
  fi
  gt_uninstall_unstash && gt_site_test && gt_core_run systemctl reload "$web.service"
  gt_uninstall_error "Removing the web integration changed or broke other sites; the installer files were restored."
}

# Only a certificate the installer requested goes; a reused or an administrator's certificate stays.
gt_uninstall_tls() {
  local name="gt-install-${STATE[run_id]}"
  gt_uninstall_file certbot_hook "$(gt_path "/etc/letsencrypt/renewal-hooks/deploy/$name")" || return 2
  if [[ -n "${STATE[resource.acme_certificate]:-}" && -e "$(gt_path "/etc/letsencrypt/renewal/$name.conf")" ]]; then
    gt_core_run certbot delete --cert-name "$name" --non-interactive >> "$SCRATCH/certbot-delete.log" 2>&1 ||
      { gt_uninstall_error "certbot could not delete the certificate $name."; return 2; }
    gt_uninstall_note removed "certificate $name"
  fi
  gt_uninstall_tree acme_root "$(gt_path /var/lib/gt-install-acme)"
}

gt_uninstall_firewall() {
  local key rule
  local -a words
  for key in "${!STATE[@]}"; do
    [[ "$key" == resource.ufw.* && "${STATE[$key]}" == owned:* ]] || continue
    rule=${STATE[$key]#owned:}
    read -r -a words <<< "$rule"
    gt_core_run ufw delete "${words[@]}" > /dev/null ||
      { gt_uninstall_error "ufw rule could not be removed: $rule"; return 2; }
    gt_uninstall_note removed "ufw $rule"
  done
}

gt_uninstall_swap() {
  local swapfile fstab temporary entry='/swapfile none swap sw 0 0' backup=${STATE[resource.fstab_backup]:-}
  [[ "${STATE[resource.swapfile]:-}" =~ ^(owned|intent)$ ]] || return 0
  swapfile=$(gt_path /swapfile) fstab=$(gt_path /etc/fstab)
  if grep -qE '^/swapfile[[:space:]]' "$(gt_path /proc/swaps)" 2>/dev/null; then
    gt_core_run swapoff "$swapfile" || return 2
  fi
  if grep -Fxq -- "$entry" "$fstab" 2>/dev/null; then
    temporary=$(mktemp "$fstab.gt-install.XXXXXX") || return 2
    PRIVATE_FILES+=("$temporary")
    grep -Fxv -- "$entry" "$fstab" > "$temporary" || (( $? == 1 )) || return 2
    chmod --reference="$fstab" -- "$temporary" && chown --reference="$fstab" -- "$temporary" &&
      mv -T -- "$temporary" "$fstab" || return 2
    gt_uninstall_note removed "/etc/fstab: $entry"
  fi
  if [[ -f "$swapfile" ]]; then rm -f -- "$swapfile" && gt_uninstall_note removed /swapfile || return 2; fi
  [[ -z "$backup" || ! -f "$(gt_path "$backup")" ]] || rm -f -- "$(gt_path "$backup")"
}

gt_uninstall_privileged() {
  gt_uninstall_file app_sudoers "$(gt_path /etc/sudoers.d/grafioschtrader)" &&
    gt_uninstall_file app_logrotate "$(gt_path /etc/logrotate.d/grafioschtrader)"
}

# Until the backend stops logging its JDBC URL, the application log may hold the database password.
gt_uninstall_log() {
  local file
  [[ -n "${STATE[resource.app_log]:-}" ]] || return 0
  for file in "$(gt_path /var/log)"/grafioschtrader.log* "$(gt_path /var/log.hdd)"/grafioschtrader.log*; do
    [[ -f "$file" ]] || continue
    rm -f -- "$file" && gt_uninstall_note removed "${file#"$ROOT"}" || return 2
  done
}

gt_uninstall_frontend() {
  local docroot
  [[ -n "${ANSWER[DOCROOT]:-}" ]] || return 0
  docroot=$(gt_path "${ANSWER[DOCROOT]}")
  gt_uninstall_tree app_frontend "$docroot/grafioschtrader" || return 2
  [[ "${STATE[resource.app_docroot]:-}" == owned && -d "$docroot" ]] || return 0
  if rmdir -- "$docroot" 2> /dev/null; then gt_uninstall_note removed "${docroot#"$ROOT"}"
  else gt_uninstall_kept "$docroot" 'not empty' 'nicht leer'; fi
}

gt_uninstall_user() {
  local home
  home=$(gt_path "$CORE_HOME")
  [[ "${STATE[resource.user]:-}" =~ ^(owned|intent)$ ]] || {
    [[ ! -e "$home" ]] || gt_uninstall_kept "$home" 'service user not created by the installer' \
      'Dienstbenutzer nicht vom Installer angelegt'
    return 0
  }
  if gt_probe getent passwd grafioschtrader > /dev/null; then
    gt_core_run userdel -r grafioschtrader > "$SCRATCH/userdel.log" 2>&1 || :
    if gt_probe getent passwd grafioschtrader > /dev/null; then
      gt_uninstall_error 'userdel grafioschtrader failed.'; return 2
    fi
  fi
  if gt_probe getent group grafioschtrader > /dev/null; then gt_core_run groupdel grafioschtrader || return 2; fi
  [[ ! -e "$home" ]] || rm -rf -- "$home" || return 2
  gt_uninstall_note removed "user grafioschtrader, $CORE_HOME"
}

gt_uninstall_toolchains() {
  local tool home dir
  for tool in java maven; do
    [[ "${STATE[toolchain.$tool.package]:-}" == archive ]] || continue
    home=$(gt_toolchain_home "$tool" "${STATE[toolchain.$tool.source]}" "${STATE[toolchain.$tool.version]}") ||
      return 2
    gt_uninstall_tree "$(gt_toolchain_resource "$tool")" "$(gt_path "$home")" || return 2
  done
  gt_uninstall_tree node "$(gt_path /opt/nodejs-gt)" && gt_uninstall_tree buildtools "$(gt_path /opt/gt-build-tools)" ||
    return 2
  # Staging directories of an interrupted toolchain step carry the installer's own prefix.
  for dir in "$(gt_path /opt)"/.gt-node-* "$(gt_path /opt)"/.gt-build-*; do
    [[ ! -e "$dir" ]] || rm -rf -- "$dir" || return 2
  done
}

gt_uninstall_mariadb_settings() {
  local file
  file=$(gt_path /etc/mysql/mariadb.conf.d/60-grafioschtrader.cnf)
  [[ -e "$file" ]] || return 0
  gt_uninstall_file buffer "$file" || return 2
  [[ ! -e "$file" ]] || return 0
  if gt_core_run systemctl is-active --quiet mariadb.service; then gt_core_run systemctl restart mariadb.service; fi
}

# Root access as during the installation: the socket login of the root account, or its password (asked or from
# --answers) written to a private option file. Prints the client options, nothing for the socket login.
gt_uninstall_root() {
  local client file
  client=$(command -v mariadb || command -v mysql) || return 2
  if [[ -z "${SECRET[DB_ROOT_PASSWORD]:-}" ]] && gt_probe "$client" --batch --skip-column-names -e 'SELECT 1' \
      > /dev/null; then
    return 0
  fi
  [[ -n "${SECRET[DB_ROOT_PASSWORD]:-}" ]] || return 2
  file=$(gt_db_options DB_ROOT_PASSWORD root) || return 2
  printf -- '--defaults-file=%s' "$file"
}

# The dump is written and verified before anything is dropped; a resumed run reuses a verified dump.
gt_uninstall_dump() {
  local options=$1 dumper target
  target=${UNSTATE[dump]:-}
  [[ -n "$target" && -f "$(gt_path "$target")" ]] && return 0
  dumper=$(command -v mariadb-dump || command -v mysqldump) || return 2
  target="/root/grafioschtrader-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
  ( set -o pipefail; umask 077
    "$dumper" ${options:+"$options"} --single-transaction --routines --triggers --databases grafioschtrader |
      gzip -c > "$(gt_path "$target").partial" ) || return 2
  gzip -t "$(gt_path "$target").partial" &&
    zcat "$(gt_path "$target").partial" | tail -n 1 | grep -q '^-- Dump completed' || return 2
  mv -T -- "$(gt_path "$target").partial" "$(gt_path "$target")" || return 2
  UNSTATE[dump]=$target
  gt_uninstall_save
}

gt_uninstall_database() {
  local options client
  if [[ "${UNSTATE[database]}" != yes ]]; then
    gt_uninstall_kept 'database grafioschtrader' 'kept; --remove-database removes it after a dump' \
      'behalten; --remove-database entfernt sie nach einem Dump'
    return 0
  fi
  [[ "${STATE[resource.database]:-}" == owned && "${STATE[resource.account]:-}" == owned ]] || {
    gt_uninstall_kept 'database grafioschtrader' 'not created by the installer' 'nicht vom Installer angelegt'
    return 0
  }
  client=$(command -v mariadb || command -v mysql) || return 2
  options=$(gt_uninstall_root) || { gt_uninstall_error 'No root access to MariaDB for --remove-database.'; return 2; }
  gt_uninstall_dump "$options" || { gt_uninstall_error 'The database dump failed; nothing was dropped.'; return 2; }
  "$client" ${options:+"$options"} --batch -e \
    "DROP DATABASE IF EXISTS grafioschtrader; DROP USER IF EXISTS 'grafioschtrader'@'localhost';" || return 2
  gt_uninstall_note removed "database grafioschtrader and account grafioschtrader@localhost (dump ${UNSTATE[dump]})"
}

# Exactly the packages of the installer's transactions, and only while nothing else needs them.
gt_uninstall_packages() {
  local package
  local -a recorded=() installed=() purged=()
  [[ "${UNSTATE[packages]}" == yes ]] || return 0
  read -r -a recorded <<< "${STATE[resource.packages]:-}"
  for package in "${recorded[@]}"; do
    [[ "$(dpkg-query -W -f='${db:Status-Status}' "$package" 2> /dev/null)" == installed ]] && installed+=("$package")
  done
  (( ${#installed[@]} )) || return 0
  mapfile -t purged < <(LC_ALL=C apt-get --simulate purge "${installed[@]}" 2> /dev/null |
    awk '/^Purg /{print $2}' | LC_ALL=C sort -u)
  if [[ "$(printf '%s\n' "${purged[@]}")" != "$(printf '%s\n' "${installed[@]}" | LC_ALL=C sort -u)" ]]; then
    gt_uninstall_error 'Purging the installer packages would remove other packages as well; they were kept.'
    return 2
  fi
  gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y purge "${installed[@]}" > "$SCRATCH/purge.log" 2>&1 ||
    { gt_uninstall_error 'apt-get purge failed; see the output above.'; cat -- "$SCRATCH/purge.log" >&2; return 2; }
  gt_uninstall_note removed "packages: ${installed[*]}"
}

# An alternative that was auto before the installation returns to auto once its best candidate is the recorded
# path again; otherwise it keeps the recorded path in manual mode.
gt_uninstall_alternatives() {
  local key name query
  for key in "${!STATE[@]}"; do
    [[ "$key" == alternative_mode.* && "${STATE[$key]}" == auto ]] || continue
    name=${key#alternative_mode.}
    query=$(LC_ALL=C update-alternatives --query "$name" 2> /dev/null) || continue
    [[ "$(sed -n 's/^Status: //p' <<< "$query")" == manual &&
      "$(sed -n 's/^Best: //p' <<< "$query")" == "${STATE[alternative.$name]:-}" ]] || continue
    gt_core_run update-alternatives --auto "$name" > /dev/null || return 2
    gt_uninstall_note removed "alternative $name: manual selection (auto again)"
  done
}

gt_uninstall_row() { UNINSTALL_ROWS+=("$1 | $2 | $(gt_text "$3" "$4")"); }

# Display rows of what each step will do; the steps decide again with the journal and the current digests.
gt_uninstall_plan() {
  local key web=${ANSWER[WEBSERVER]:-none}
  UNINSTALL_ROWS=() PLAN_BLOCKERS=()
  gt_uninstall_row remove grafioschtrader.service 'stop, disable and delete the unit' \
    'stoppen, deaktivieren und Unit löschen'
  [[ -z "${STATE[file.duckdns_timer]:-}" ]] || gt_uninstall_row remove "$GT_DUCKDNS_UNIT.timer" \
    'DuckDNS updater of the installer' 'DuckDNS-Updater des Installers'
  if [[ "$web" == nginx || "$web" == apache2 ]]; then
    gt_uninstall_row remove "$web" 'own sites, include and snippet; other sites must answer as before' \
      'eigene Sites, Include und Snippet; andere Sites müssen wie vorher antworten'
  fi
  [[ -z "${STATE[file.web_manual]:-}" ]] || gt_uninstall_row remove "$GT_WEB_MANUAL" 'web server proposal' \
    'Webserver-Vorschlag'
  [[ -z "${STATE[resource.acme_certificate]:-}" ]] || gt_uninstall_row remove "gt-install-${STATE[run_id]}" \
    'certificate requested by the installer, with its renewal hook' 'vom Installer bezogenes Zertifikat samt Hook'
  for key in "${!STATE[@]}"; do
    [[ "$key" != resource.ufw.* || "${STATE[$key]}" != owned:* ]] ||
      gt_uninstall_row remove "ufw ${STATE[$key]#owned:}" 'own firewall rule' 'eigene Firewall-Regel'
  done
  [[ ! "${STATE[resource.swapfile]:-}" =~ ^(owned|intent)$ ]] || gt_uninstall_row remove /swapfile \
    'swap off, file and its /etc/fstab line' 'Swap aus, Datei und ihre /etc/fstab-Zeile'
  gt_uninstall_row remove 'sudoers, logrotate, /var/log/grafioschtrader.log*' 'privileged files and log' \
    'privilegierte Dateien und Log'
  [[ -z "${ANSWER[DOCROOT]:-}" ]] || gt_uninstall_row remove "${ANSWER[DOCROOT]}/grafioschtrader" frontend Frontend
  gt_uninstall_plan_database
  gt_uninstall_row remove "grafioschtrader, $CORE_HOME" 'service user with clone, launchers and build caches' \
    'Dienstbenutzer mit Klon, Startskripten und Build-Caches'
  gt_uninstall_row remove /opt 'archive JDK/Maven, /opt/nodejs-gt, /opt/gt-build-tools if the installer added them' \
    'Archiv-JDK/-Maven, /opt/nodejs-gt, /opt/gt-build-tools, soweit vom Installer'
  [[ -z "${STATE[file.buffer]:-}" ]] || gt_uninstall_row remove 60-grafioschtrader.cnf \
    'MariaDB settings, then MariaDB restarts' 'MariaDB-Einstellungen, danach Neustart von MariaDB'
  gt_uninstall_plan_packages
  gt_uninstall_row remove '/root/.gt-install, /var/lib/gt-install' 'secrets and journals, last' \
    'Secrets und Journale, zuletzt'
  gt_uninstall_row keep 'changed files' 'a file changed since the installation stays and is listed in the result' \
    'eine seit der Installation geänderte Datei bleibt und steht im Ergebnis'
}

gt_uninstall_plan_database() {
  if [[ "${UNSTATE[database]}" != yes ]]; then
    gt_uninstall_row keep 'database grafioschtrader' 'kept with its account; --remove-database removes both' \
      'bleibt mit ihrem Konto; --remove-database entfernt beides'
  elif [[ "${STATE[resource.database]:-}" == owned && "${STATE[resource.account]:-}" == owned ]]; then
    gt_uninstall_row remove 'database grafioschtrader' \
      'and its account, after a verified dump into /root/grafioschtrader-<UTC>.sql.gz' \
      'samt Konto, nach einem geprüften Dump nach /root/grafioschtrader-<UTC>.sql.gz'
  else
    gt_uninstall_row keep 'database grafioschtrader' 'not created by the installer' 'nicht vom Installer angelegt'
  fi
}

gt_uninstall_plan_packages() {
  if [[ "${UNSTATE[packages]}" != yes ]]; then
    gt_uninstall_row keep packages 'APT packages stay; --remove-packages purges those of the installer' \
      'APT-Pakete bleiben; --remove-packages entfernt die des Installers'
  elif [[ -z "${STATE[resource.packages]+set}" ]]; then
    PLAN_BLOCKERS+=("$(gt_text 'This installation predates the package record; --remove-packages is not possible.' \
      'Diese Installation hat noch keine Paketliste; --remove-packages ist nicht möglich.')")
  else
    gt_uninstall_row remove packages "${STATE[resource.packages]:-none}" "${STATE[resource.packages]:-keine}"
  fi
}

gt_uninstall_report() {
  local row
  printf '[%s]\n' "$(gt_text 'Uninstall plan' 'Deinstallationsplan')"
  printf '%s\n' "${UNINSTALL_ROWS[@]}"
  if (( ${#PLAN_BLOCKERS[@]} )); then
    printf '\n[%s]\n' "$(gt_text 'Blockers' 'Blocker')"
    for row in "${PLAN_BLOCKERS[@]}"; do printf '%s\n' "$row"; done
  fi
}

# A resumed uninstall keeps the options it started with; other options would change an approved plan.
gt_uninstall_begin() {
  if [[ -e "$(gt_uninstall_journal)" ]]; then
    gt_uninstall_load || { gt_uninstall_error 'Invalid uninstall journal; nothing was changed.'; return 2; }
    [[ "$UNINSTALL_DATABASE$UNINSTALL_PACKAGES" == nono ||
      "$UNINSTALL_DATABASE$UNINSTALL_PACKAGES" == "${UNSTATE[database]}${UNSTATE[packages]}" ]] || {
      gt_uninstall_error 'The started uninstall used other options; resume it without them.'; return 2; }
  else
    UNSTATE=([schema]=1 [run_id]=${STATE[run_id]} [database]=$UNINSTALL_DATABASE [packages]=$UNINSTALL_PACKAGES)
  fi
}

gt_uninstall_drops_database() {
  [[ "${UNSTATE[database]}" == yes && "${UNSTATE[step.database]:-}" != complete &&
    "${STATE[resource.database]:-}" == owned && "${STATE[resource.account]:-}" == owned ]]
}

# The root password is needed only when the socket login of root fails; it comes from --answers or is asked.
gt_uninstall_prepare_root() {
  local client options
  gt_uninstall_drops_database || return 0
  client=$(command -v mariadb || command -v mysql) || { gt_uninstall_error 'No MariaDB client.'; return 2; }
  gt_probe "$client" --batch --skip-column-names -e 'SELECT 1' > /dev/null && return 0
  if [[ -n "$ANSWERS_FILE" ]]; then gt_answers_file "$ANSWERS_FILE" || return 2; fi
  gt_collect_secret DB_ROOT_PASSWORD no no || return $?
  if ! options=$(gt_uninstall_root) ||
      ! "$client" ${options:+"$options"} --batch -e 'SELECT 1' > /dev/null 2>&1; then
    gt_uninstall_error 'MariaDB rejected the root password; nothing was changed.'; return 2
  fi
}

gt_uninstall_confirm_database() {
  local reply
  gt_uninstall_drops_database || return 0
  printf '%s: delete-database: ' "$(gt_text 'The database is dropped after its dump. To approve, type' \
    'Die Datenbank wird nach ihrem Dump gelöscht. Zur Freigabe eingeben')" >&"$QUESTION_OUTPUT"
  IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == delete-database ]]
}

gt_uninstall_terminal() {
  if [[ "$CORE_CONFIRM" != yes ]] || gt_uninstall_drops_database; then
    { exec {QUESTION_FD}<>/dev/tty; } 2> /dev/null ||
      { gt_uninstall_error 'A terminal is required to approve this uninstall.'; return 2; }
    QUESTION_OUTPUT=$QUESTION_FD
  fi
}

gt_uninstall() {
  local step state_dir
  state_dir=$(gt_path /var/lib/gt-install)
  gt_question_model
  gt_installation
  case "${FACT[host.class]}" in
    completed|unfinished) ;;
    *) gt_uninstall_error "This host has no installation of this installer (${FACT[host.class]}); nothing was changed."
      return 2 ;;
  esac
  gt_state_load || { gt_uninstall_error 'Invalid installation journal; nothing was changed.'; return 2; }
  gt_uninstall_begin || return 2
  gt_uninstall_plan
  gt_uninstall_report > "$SCRATCH/plan.txt"
  if (( ${#PLAN_BLOCKERS[@]} )) || [[ "$FRONTEND" != whiptail || "$CORE_CONFIRM" == yes ]]; then
    cat -- "$SCRATCH/plan.txt"
  fi
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
  gt_uninstall_terminal && gt_uninstall_prepare_root || return $?
  if [[ "$CORE_CONFIRM" != yes ]]; then gt_confirm uninstall Uninstall Deinstallieren || return 130; fi
  gt_uninstall_confirm_database || return 130
  gt_no_symlinks "$state_dir/lock" || return 2
  if [[ -z "$LOCK_FD" ]]; then exec {LOCK_FD}>"$state_dir/lock" || return 2; fi
  flock -n "$LOCK_FD" || { gt_message lock >&2; return 2; }
  gt_uninstall_save || return 2
  for step in $GT_UNINSTALL_STEPS; do
    [[ "${UNSTATE[step.$step]:-}" != complete ]] || continue
    gt_text "Uninstall step: $step" "Deinstallationsschritt: $step"
    "gt_uninstall_$step" || { gt_uninstall_save; return 2; }
    [[ "$step" != web ]] || rm -rf -- "$(gt_path /var/lib/gt-install/uninstall-web)"
    UNSTATE[step.$step]=complete
    gt_uninstall_save || return 2
  done
  gt_uninstall_finish
}

# The result outlives the journal directory, which goes last.
gt_uninstall_finish() {
  local result key package
  local -a recorded=() remaining=()
  result=$(gt_path "/root/gt-uninstall-$(date -u +%Y%m%dT%H%M%SZ).result")
  read -r -a recorded <<< "${STATE[resource.packages]:-}"
  for package in "${recorded[@]}"; do
    [[ "$(dpkg-query -W -f='${db:Status-Status}' "$package" 2> /dev/null)" != installed ]] || remaining+=("$package")
  done
  {
    printf 'status=complete\nrun_id=%s\n' "${STATE[run_id]}"
    [[ -z "${UNSTATE[dump]:-}" ]] || printf 'dump=%s\n' "${UNSTATE[dump]}"
    while IFS= read -r key; do printf '%s=%s\n' "$key" "${UNSTATE[$key]}"; done < <(printf '%s\n' "${!UNSTATE[@]}" |
      grep -E '^(removed|kept)\.' | LC_ALL=C sort -t. -k1,1 -k2,2n)
    (( ${#remaining[@]} == 0 )) || printf 'packages.kept=%s\n' "${remaining[*]}"
    [[ "${UNSTATE[database]}" == yes ]] || printf 'note.database=%s\n' "$(gt_text \
      'A later installation refuses the kept database; restore it from a dump or a user data export instead.' \
      'Eine spätere Installation lehnt die behaltene Datenbank ab; stattdessen aus Dump oder Datenexport herstellen.')"
  } > "$result.partial" && chmod 600 -- "$result.partial" && mv -T -- "$result.partial" "$result" || return 2
  rm -rf -- "$(gt_path /root/.gt-install)" "$(gt_path /var/lib/gt-install)" || return 2
  gt_text "Uninstall complete; result in ${result#"$ROOT"}:" \
    "Deinstallation abgeschlossen; Ergebnis in ${result#"$ROOT"}:"
  cat -- "$result"
}
