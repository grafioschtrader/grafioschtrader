gt_plan_row() { PLAN+=("$1 | $2 | $(gt_text "$3" "${4:-$3}")"); }
gt_plan_block() { PLAN_BLOCKERS+=("$(gt_text "$1" "${2:-$1}")"); }
gt_plan_warn() { PLAN_WARNINGS+=("$(gt_text "$1" "${2:-$1}")"); }
gt_plan_file() {
  if [[ -e "$(gt_path "$1")" || -L "$(gt_path "$1")" ]]; then
    gt_plan_block "Target already exists: $1; ownership must be resolved." \
      "Ziel existiert bereits: $1; Eigentümerschaft muss geklärt werden."
  fi
  gt_plan_row create "$1" "$2" "${3:-$2}"
}
gt_plan_package() {
  local name=$1
  [[ -z "${PLAN_PACKAGES[$name]:-}" ]] || return 0
  if [[ -n "${PACKAGE[$name]:-}" ]]; then
    PLAN_PACKAGES[$name]=reuse
    gt_plan_row reuse "package:$name" "${PACKAGE[$name]}"
  else
    PLAN_PACKAGES[$name]=install
    gt_plan_row install "package:$name" 'APT candidate; transaction below' 'APT-Kandidat; Transaktion siehe unten'
  fi
}
gt_plan_needs_apt() {
  local package
  for package in "${!PLAN_PACKAGES[@]}"; do
    [[ "${PLAN_PACKAGES[$package]}" != install ]] || return 0
  done
  return 1
}
gt_plan_packages() {
  local package value line
  local -a requested=()
  for package in "${!PLAN_PACKAGES[@]}"; do
    [[ "${PLAN_PACKAGES[$package]}" != install ]] || requested+=("$package")
  done
  (( ${#requested[@]} )) || return 0
  # --simulate neither downloads nor changes packages. --no-download would incorrectly
  # reject simulations for packages whose .deb files are not already cached.
  if ! value=$(gt_probe env LC_ALL=C apt-get --simulate \
      -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= install "${requested[@]}"); then
    gt_plan_block 'APT simulation failed or timed out; resolve package candidates before installation.' \
      'APT-Simulation fehlgeschlagen oder Zeitlimit erreicht; Paketkandidaten vor der Installation klären.'
    return 0
  fi
  while IFS= read -r line; do
    case "$line" in
      'Remv '*) gt_plan_row remove package "$line"
        gt_plan_block 'APT would remove packages; this increment cannot authorize removals.' \
          'APT würde Pakete entfernen; diese Stufe erlaubt keine Entfernungen.' ;;
      'Inst '*) gt_plan_row install package "$line"
        # Any existing package upgrade can affect a shared service. Show it and require resolution.
        if [[ "$line" =~ ^Inst[[:space:]]+[^[:space:]]+[[:space:]]+\[ ]]; then
          gt_plan_block "APT would upgrade an existing package: $line" \
            "APT würde ein vorhandenes Paket aktualisieren: $line"
        fi ;;
      'Conf '*) gt_plan_row configure package "$line" ;;
    esac
  done <<< "$value"
}
# Planning and execution consume the same package/module selections.
gt_base_package_names() {
  printf '%s\n' git curl wget ca-certificates gnupg sudo openssl logrotate whiptail tzdata
  # Domain TLS checks DNS with dig. A dig from another package is used as it is.
  if gt_dns_required && { ! gt_dns_tool_available || [[ -n "${PACKAGE[bind9-dnsutils]:-}" ]]; }; then
    printf '%s\n' bind9-dnsutils
  fi
}

gt_plan_base_packages() {
  local package
  while IFS= read -r package; do
    gt_plan_package "$package"
    if [[ "${STATE[step.base_packages]:-}" == complete && -z "${PACKAGE[$package]:-}" ]]; then
      gt_plan_block "Previously verified base package is missing: $package; restore it before resuming."
    fi
  done < <(gt_base_package_names)
}

gt_web_package_names() {
  [[ "${ANSWER[WEBSERVER]}" != none ]] || return 0
  printf '%s\n' "${ANSWER[WEBSERVER]}"
  [[ "${ANSWER[TLS_SOURCE]:-}" != letsencrypt ]] || printf '%s\n' certbot
}

gt_apache_module_names() {
  case "$1" in
    lan) printf '%s\n' proxy proxy_ajp proxy_http proxy_wstunnel headers alias dir mime authz_core deflate filter ;;
    tls) printf '%s\n' ssl ;;
    *) return 2 ;;
  esac
}

gt_plan_web_runtime() {
  local package modules
  while IFS= read -r package; do gt_plan_package "$package"; done < <(gt_web_package_names)
  [[ "${ANSWER[WEBSERVER]}" == apache2 ]] || return 0
  modules=$(gt_apache_module_names lan | paste -sd ' ')
  gt_plan_row enable apache-modules "$modules (a2enmod also enables required module dependencies)"
  if [[ -n "${ANSWER[DOMAIN]:-}" && "${ANSWER[TLS_SOURCE]:-}" != proxy ]]; then
    modules=$(gt_apache_module_names tls | paste -sd ' ')
    gt_plan_row enable apache-tls-modules "$modules (a2enmod also enables required module dependencies)"
  fi
}

gt_plan_toolchains() {
  local item
  gt_plan_base_packages
  # Archives resolve the same vendor release, file and checksum that the installing run confirms.
  case "${ACTION[java]}" in
    reuse) gt_plan_row reuse java "${REASON[java]}" ;;
    install) gt_plan_package "openjdk-$JAVA_REQUIRED-jdk-headless" ;;
    isolate) gt_toolchain_archive_plan java ;;
  esac
  case "${ACTION[maven]}" in
    install) gt_plan_package maven ;;
    isolate) gt_toolchain_archive_plan maven ;;
    reuse) gt_plan_row reuse maven "${REASON[maven]}" ;;
  esac
  if [[ "${ACTION[java]}" == install || "${ACTION[maven]}" == install ]]; then
    gt_plan_row preserve java-alternatives 'Restore every changed selection; automatic groups may become manual.' \
      'Alle geänderten Alternativen zurücksetzen; automatische Gruppen können auf manuell wechseln.'
  fi
  if [[ "${ACTION[node]}" == reuse ]]; then gt_plan_row reuse node "${REASON[node]}"
  elif [[ "${ANSWER[NODE_REPLACE]:-no}" != yes || "${FACT[architecture]}" == armhf ]]; then
    gt_plan_file /opt/nodejs-gt 'Official Node archive satisfying source requirements; verify SHASUMS256.txt' \
      'Offizielles Node-Archiv gemäß Quellcode-Anforderungen; SHASUMS256.txt prüfen'
  else
    gt_plan_file /etc/apt/keyrings/nodesource.gpg 'NodeSource signing key; verify fingerprint' \
      'NodeSource-Signaturschlüssel; Fingerabdruck prüfen'
    gt_plan_file /etc/apt/sources.list.d/nodesource.sources 'NodeSource 24.x source with signed-by' \
      'NodeSource-24.x-Quelle mit signed-by'
    [[ "${ANSWER[NODE_REPLACE]:-no}" == yes ]] && item=replace || item=install
    gt_plan_row "$item" package:nodejs 'NodeSource 24; affects the system runtime' \
      'NodeSource 24; betrifft die System-Laufzeit'
    gt_plan_block 'NodeSource candidate and full transaction are unverified until its repository is available.' \
      'NodeSource-Kandidat und vollständige Transaktion sind ungeprüft, bis die Paketquelle verfügbar ist.'
  fi
  if [[ "${ANSWER[NODE_REPLACE]:-no}" == yes ]]; then
    gt_plan_warn 'Node replacement requested; every shared consumer must be checked before execution.' \
      'Node-Ersatz gewünscht; alle betroffenen Anwendungen müssen vor Ausführung geprüft werden.'
  fi
  gt_plan_row configure npm \
    "@angular/cli@$CLI_REQUIRED and semver in /opt/gt-build-tools; selected Node; NG_CLI_ANALYTICS=false" \
    "@angular/cli@$CLI_REQUIRED und semver in /opt/gt-build-tools; gewähltes Node; NG_CLI_ANALYTICS=false"
}
gt_plan_database() {
  local pool=384M mem=${FACT[memory.MemTotal]:-0}
  if [[ "${FACT[database.vendor]}" == absent ]]; then
    gt_plan_package mariadb-server; gt_plan_package mariadb-client
    gt_plan_row configure mariadb 'Remove anonymous accounts and test schema only on a newly installed server.' \
      'Anonyme Konten und test-Schema nur auf einem neu installierten Server entfernen.'
  fi
  gt_plan_row "${ACTION[database]}" database:grafioschtrader \
    'utf8mb4 / utf8mb4_general_ci; never adopt non-empty data' \
    'utf8mb4 / utf8mb4_general_ci; niemals vorhandene Daten übernehmen'
  if [[ "${FACT[database.gt_tables]}" == 0 && "${ANSWER[DB_REUSE_EMPTY]:-no}" != yes ]]; then
    gt_plan_block 'Reuse of the empty database was not accepted.' \
      'Verwendung der leeren Datenbank wurde nicht bestätigt.'
  fi
  if [[ "${FACT[database.gt_user]}" == present ]]; then
    gt_plan_row verify 'account:grafioschtrader@localhost' \
      'Supply current password and verify TCP login before any database changes; never rotate it.' \
      'Aktuelles Passwort angeben und TCP-Anmeldung vor Datenbankänderungen prüfen; Passwort niemals ändern.'
    if [[ "$MODE" != --prepare || "${FACT[database.gt_auth]:-}" != ok ]]; then
      gt_plan_block 'Existing database account credentials remain unverified.' \
        'Zugangsdaten des vorhandenen Datenbankkontos bleiben ungeprüft.'
    fi
  elif [[ "${FACT[database.gt_user]}" == absent || "${FACT[database.vendor]}" == absent ]]; then
    gt_plan_row create 'account:grafioschtrader@localhost' 'Use the confirmed password; grant grafioschtrader.* only.' \
      'Bestätigtes Passwort verwenden; Rechte nur für grafioschtrader.* vergeben.'
  else
    gt_plan_row block 'account:grafioschtrader@localhost' \
      'Authenticate inventory before deciding whether the account exists.' \
      'Bestandsaufnahme authentifizieren, bevor über das Vorhandensein des Kontos entschieden wird.'
  fi
  if [[ "${ANSWER[BUFFER_POOL]:-no}" == yes ]]; then
    [[ "$mem" =~ ^[0-9]+$ ]] || mem=0
    if (( mem >= 6000 )); then pool=2G; elif (( mem >= 3000 )); then pool=1G; fi
    gt_plan_file /etc/mysql/mariadb.conf.d/60-grafioschtrader.cnf "innodb_buffer_pool_size=$pool"
    gt_plan_row restart mariadb 'Affects all listed schemas, including other applications.' \
      'Betrifft alle aufgeführten Schemas, auch andere Anwendungen.'
  fi
}

gt_plan_ports() {
  local key port
  local -A used=()
  for key in BACKEND_PORT BACKEND_HTTP_PORT TLS_PROXY_LISTEN; do
    port=${ANSWER[$key]:-}; [[ -n "$port" ]] || continue
    if ! gt_port_free "$port" || [[ -n "${used[$port]:-}" ]]; then
      gt_plan_block "Port $port ($key) is occupied, duplicated or UNKNOWN." \
        "Port $port ($key) ist belegt, doppelt vergeben oder UNKNOWN."
    fi
    used[$port]=$key
  done
  if [[ "${ANSWER[WEBSERVER]}" != none && "${ANSWER[TLS_SOURCE]:-}" != proxy ]]; then
    for port in 80 443; do
      [[ "$port" != 443 || -n "${ANSWER[DOMAIN]}" ]] || continue
      [[ -z "${used[$port]:-}" ]] || gt_plan_block "Backend port $port overlaps a planned web listener." \
        "Backend-Port $port überschneidet sich mit einem geplanten Web-Listener."
    done
  fi
  gt_plan_row configure backend \
    "127.0.0.1:${ANSWER[BACKEND_PORT]} (${ANSWER[WEBSERVER]}); HTTP=${ANSWER[BACKEND_HTTP_PORT]:-same}"
}
gt_plan_web() {
  local web=${ANSWER[WEBSERVER]} domain=${ANSWER[DOMAIN]} row label directive value name match='' root=''
  local unknown=no file kind=''
  local -A matches=()
  if [[ "$web" == none ]]; then
    gt_plan_file /root/gt-install-webserver.conf 'Manual proxy configuration; web milestone remains pending.' \
      'Manuelle Proxy-Konfiguration; Web-Meilenstein bleibt offen.'
    gt_plan_warn 'No web integration selected; installation would be incomplete.' \
      'Keine Web-Integration gewählt; Installation wäre unvollständig.'
    return 0
  fi
  gt_plan_web_runtime
  # Static inventory is evidence, never permission to rewrite an ambiguous foreign server block.
  for row in "${NOTES[@]}"; do [[ "$row" != *UNKNOWN*web* && "$row" != *UNKNOWN*vhost* ]] || unknown=yes; done
  for row in "${WEB[@]}"; do
    read -r label directive value <<< "$row"
    case "$directive" in server_name|ServerName|ServerAlias)
      for name in ${value//\"/}; do
        # In include mode the parser below, not this static evidence, resolves the domain's virtual host.
        if [[ -n "$domain" && " ${FACT[plan.names]:-$domain} " == *" $name "* ]] && ! gt_vhost_include_mode ||
            [[ "$name" == "$(gt_lan_planned)" ]]; then matches[$label]=1; fi
        [[ "$name" != *'*'* && "$name" != '~'* ]] || unknown=yes
      done ;;
    esac
  done
  if (( ${#matches[@]} == 1 )); then
    for match in "${!matches[@]}"; do :; done
    for row in "${WEB[@]}"; do
      read -r label directive value <<< "$row"; [[ "$label" == "$match" ]] || continue
      case "$directive" in
        server_kind) kind=$value ;;
        root|DocumentRoot) root=${value//\"/} ;;
      esac
    done
    [[ "$web:$kind" == nginx:nginx || "$web:$kind" == apache2:apache ]] || unknown=yes
  elif (( ${#matches[@]} > 1 )); then unknown=yes; fi
  FACT[plan.vhost_root]=$root
  if gt_vhost_include_mode; then
    # Read-only modes never run apache2ctl; the web stage resolves an Apache target through its vhost dump.
    if [[ "$web" == nginx ]]; then gt_vhost_plan
    else
      gt_plan_row create "$(gt_vhost_snippet_path)" \
        'GT routes; the HTTPS virtual host is resolved and backed up at the web stage' \
        'GT-Routen; der HTTPS-Vhost wird in der Web-Stufe ermittelt und gesichert'
    fi
  fi
  if [[ -n "$match" ]]; then
    gt_plan_block "An existing vhost serves the LAN address or the domain; the domain can only be shared with \
VHOST_INCLUDE=yes and TLS_SOURCE=existing." "Ein vorhandener Vhost bedient die LAN-Adresse oder die Domain; die \
Domain lässt sich nur mit VHOST_INCLUDE=yes und TLS_SOURCE=existing teilen."
  else
    if [[ "$web" == nginx ]]; then file=/etc/nginx/sites-available/grafioschtrader
    else file=/etc/apache2/sites-available/grafioschtrader.conf; fi
    gt_plan_file "$file" 'Own vhost: /api, /m2m, /socket/websocket, /ws, /grafioschtrader; preserve other sites.' \
      'Eigener Vhost: /api, /m2m, /socket/websocket, /ws, /grafioschtrader; andere Sites erhalten.'
    gt_plan_file "${file/sites-available/sites-enabled}" 'Enable own vhost' 'Eigenen Vhost aktivieren'
  fi
  [[ "$unknown" == no ]] || gt_plan_block 'Effective web configuration is ambiguous or includes unresolved context.' \
    'Effektive Web-Konfiguration ist mehrdeutig oder enthält ungeklärte Includes.'
  if [[ "${ANSWER[TLS_SOURCE]:-}" != proxy ]]; then
    for name in 80 443; do
      [[ "$name" != 443 || -n "$domain" ]] || continue
      if ! gt_port_free "$name" && ! awk -v port="$name" -v owner="$web" \
          '$4 ~ (":" port "$") && index($0,owner) {found=1} END {exit !found}' <<< "${FACT[listeners]}"; then
        gt_plan_block "Web port $name belongs to another process or is UNKNOWN." \
          "Web-Port $name gehört einem anderen Prozess oder ist UNKNOWN."
      fi
    done
  fi
  gt_plan_row verify "$web" \
    'Configuration test and existing vhost HTTP comparisons before/after reload; restore on failure.' \
    'Konfigurationstest und HTTP-Vergleich vorhandener Vhosts vor/nach Reload; bei Fehler wiederherstellen.'
}
gt_plan_storage() {
  local path=${ANSWER[DOCROOT]} actual device max mem=${FACT[memory.MemTotal]:-unknown}
  actual=$(gt_path "$path")
  if [[ -L "$actual" || -e "$actual" && ! -d "$actual" ]]; then
    gt_plan_block "Document root is a symlink or not a directory: $path" \
      "Dokumentenverzeichnis ist ein Symlink oder kein Verzeichnis: $path"
  elif [[ -e "$actual/grafioschtrader" || -L "$actual/grafioschtrader" ]]; then
    gt_plan_block "GT content already exists in $path" "GT-Inhalt existiert bereits in $path"
  elif [[ -d "$actual" && -n "$(find "$actual" -mindepth 1 -maxdepth 1 -print -quit)" &&
      "$path" != "${FACT[plan.vhost_root]:-}" ]]; then
    gt_plan_block "Non-empty document root is not the selected vhost root: $path" \
      "Nichtleeres Dokumentenverzeichnis gehört nicht zum gewählten Vhost: $path"
  fi
  gt_plan_row create "$path/grafioschtrader" \
    'Only this directory is writable by grafioschtrader; preserve shared content.' \
    'Nur dieses Verzeichnis ist für grafioschtrader beschreibbar; gemeinsame Inhalte erhalten.'
  [[ -e "$actual" ]] || gt_plan_row create "$path/index.html" 'Landing page in newly created document root' \
    'Startseite im neu angelegten Dokumentenverzeichnis'
  DISKS=() DISK_FREE=() DISK_NEED=(); unset 'FACT[disk.unknown]'
  gt_disk /home 4096; gt_disk /opt "$(gt_opt_need)"; gt_disk "${FACT[database.datadir]}" 2048; gt_disk "$path" 300
  if [[ "${ANSWER[SWAP]:-no}" == yes && "${STATE[step.swap]:-}" != complete ]]; then gt_disk / "$SWAP_MB"; fi
  gt_swap_plan
  [[ "${FACT[disk.unknown]:-no}" != yes ]] || gt_plan_block 'Disk capacity UNKNOWN.' 'Freier Speicherplatz UNKNOWN.'
  for device in "${!DISK_NEED[@]}"; do
    (( DISK_FREE[$device] >= DISK_NEED[$device] )) || gt_plan_block \
      "Device $device: ${DISK_NEED[$device]} MiB required, ${DISK_FREE[$device]} MiB available."
  done
  max=${ANSWER[JAVA_HEAP]##*Xmx}; [[ "$max" != *g ]] || max="$((${max%g}*1024))m"; max=${max%m}
  if [[ "$mem" =~ ^[0-9]+$ ]] && (( max >= mem )); then
    gt_plan_block 'Maximum Java heap must be smaller than RAM.' 'Maximaler Java-Heap muss kleiner als RAM sein.'
  fi
}

gt_dns_required() { [[ -n "${ANSWER[DOMAIN]:-}" && "${ANSWER[TLS_SOURCE]:-}" != proxy ]]; }
gt_dns_tool_available() { command -v dig >/dev/null 2>&1; }

# Plan-time notices about the installer's own DNS steps. The result never keeps them.
gt_dns_tool_notice() {
  gt_text 'dig is installed with the base packages; DNS is verified after the DuckDNS update.' \
    'dig wird mit den Basispaketen installiert; DNS wird nach dem DuckDNS-Update geprüft.'
}

gt_dns_update_notice() {
  gt_text \
    'DNS differs; the installer updates DuckDNS before the build and verifies the records before any certificate.' \
    'DNS weicht ab; der Installer aktualisiert DuckDNS vor dem Build und prüft die Einträge vor jedem Zertifikat.'
}

# The full bootstrap installs dig with its approved package transaction before the DuckDNS step, which updates and
# verifies the records before any certificate. Its certificate names do not depend on the records found now.
gt_dns_tool_deferred() {
  [[ "$MODE" == --bootstrap && "${ANSWER[DUCKDNS_UPDATER]:-no}" == yes && "${STATE[step.duckdns]:-}" != complete &&
     "${PLAN_PACKAGES[bind9-dnsutils]:-}" == install ]]
}

# Inputs are normalized, newline-separated address sets. Every published address
# must be usable, but a host need not publish all of its stable IPv6 addresses.
gt_dns_records_match() {
  local family=$1 records=$2 expected=$3 address
  if [[ "${ANSWER[DNS_FAMILY]}:$family" == ipv4:AAAA || "${ANSWER[DNS_FAMILY]}:$family" == ipv6:A ]]; then
    [[ -z "$records" ]]; return
  fi
  [[ -n "$records" && -n "$expected" &&
     $'\n'"$records"$'\n' != *$'\nunknown\n'* && $'\n'"$expected"$'\n' != *$'\nunknown\n'* ]] || return 1
  if [[ "$family" == A ]]; then [[ "$records" == "$expected" ]]; return; fi
  while IFS= read -r address; do
    [[ $'\n'"$expected"$'\n' == *$'\n'"$address"$'\n'* ]] || return 1
  done <<< "$records"
}

gt_plan_dns() {
  local domain=${ANSWER[DOMAIN]} family name result records expected mismatched=no matched_www=yes en de
  FACT[plan.names]=$domain
  FACT[dns.status]=skipped
  gt_dns_required || return 0
  if ! gt_dns_tool_available; then
    FACT[dns.status]='missing-tool'
    gt_plan_package bind9-dnsutils
    if gt_dns_tool_deferred; then
      FACT[dns.status]=pending-update FACT[plan.names]="$domain www.$domain"
      gt_plan_warn "$(gt_dns_tool_notice)"
      return 0
    fi
    en='Missing DNS check prerequisite: dig (bind9-dnsutils).'
    en+=' Install the confirmed prerequisite, then repeat DNS checks.'
    de='Fehlende DNS-Prüfvoraussetzung: dig (bind9-dnsutils).'
    de+=' Bestätigtes Voraussetzungspaket installieren, danach DNS erneut prüfen.'
    gt_plan_block "$en" "$de"
    return 0
  fi
  for name in "$domain" "www.$domain"; do
    for family in A AAAA; do
      result=$(gt_probe dig +time=2 +tries=1 +noall +answer "$name" "$family") || result=unknown
      records=$(awk -v type="$family" '$4==type {print tolower($5)}' <<< "$result" | LC_ALL=C sort -u)
      if [[ "$family" == A ]]; then expected=${FACT[network.public_ipv4]:-unknown}
      else expected=${FACT[network.global_ipv6]:-unknown}; fi
      if [[ "${ANSWER[DNS_FAMILY]}:$family" == ipv4:AAAA || "${ANSWER[DNS_FAMILY]}:$family" == ipv6:A ]]; then
        expected=''
      fi
      records=$(gt_normalize_addresses <<< "$records")
      expected=$(gt_normalize_addresses <<< "$expected")
      gt_plan_row verify "DNS:$name:$family" "records=${records:-none}; expected=${expected:-none}"
      if [[ "$result" == unknown ]] || ! gt_dns_records_match "$family" "$records" "$expected"; then
        [[ "$name" == "$domain" ]] && mismatched=yes || matched_www=no
      fi
    done
  done
  [[ "$matched_www" != yes ]] || FACT[plan.names]+=" www.$domain"
  # DuckDNS answers every name below the subdomain with the same records. With the installer's own updater the
  # certificate names therefore do not depend on the records found before the first update.
  [[ "${ANSWER[DUCKDNS_UPDATER]:-no}" != yes ]] || FACT[plan.names]="$domain www.$domain"
  FACT[dns.status]=matched
  if [[ "$mismatched" == yes && "${ANSWER[DUCKDNS_UPDATER]:-no}" == yes &&
      "${STATE[step.duckdns]:-}" != complete ]]; then
    FACT[dns.status]=pending-update
    gt_plan_warn "$(gt_dns_update_notice)"
  elif [[ "$mismatched" == yes ]]; then
    FACT[dns.status]=mismatch
    if [[ "${ANSWER[TLS_SOURCE]}" == letsencrypt ]]; then
      en='DNS does not match selected address families or is UNKNOWN; resolve before certbot.'
      en+=' No DNS update was sent.'
      de='DNS passt nicht zu den gewählten Adressfamilien oder ist UNKNOWN; vor certbot klären.'
      de+=' Kein DNS-Update gesendet.'
      gt_plan_block "$en" "$de"
    else gt_plan_warn 'DNS differs or is UNKNOWN; existing-certificate deployment needs a reachability check.' \
      'DNS weicht ab oder ist UNKNOWN; Erreichbarkeit bei Verwendung des vorhandenen Zertifikats prüfen.'; fi
  fi
}

# This package-only plan authorizes no web, application or database changes.
gt_dns_tools_plan() {
  PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=()
  gt_system; gt_packages
  gt_plan_package bind9-dnsutils
  [[ -z "${PACKAGE[bind9-dnsutils]:-}" ]] || gt_plan_block \
    'bind9-dnsutils is installed but dig is missing from PATH; repair the package or PATH before continuing.'
  [[ "${FACT[dpkg.lock]}" == free ]] || gt_plan_block 'DNS prerequisite: package lock is held or UNKNOWN.'
  if [[ "${FACT[apt.age_hours]}" == unknown ]] || (( ${FACT[apt.age_hours]} > 24 )); then
    gt_plan_block 'DNS prerequisite: APT metadata is absent/stale; run sudo apt-get update and re-plan.'
  fi
  gt_plan_packages
  FACT[dns.tools_plan]=$(printf '%s\n' "${PLAN[@]}" | sha256sum)
  (( ${#PLAN_BLOCKERS[@]} == 0 ))
}

gt_verify_dns() {
  gt_dns_required || return 0
  PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=()
  gt_domain_network || { gt_core_error 'DNS check: network inventory failed.'; return 2; }
  gt_plan_dns
  gt_report_rows "$(gt_text 'DNS checks' 'DNS-Prüfungen')" "${PLAN[@]}"
  gt_report_rows "$(gt_text Warnings Hinweise)" "${PLAN_WARNINGS[@]}"
  gt_report_rows "$(gt_text Blockers Blocker)" "${PLAN_BLOCKERS[@]}"
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
}

gt_prepare_dns() {
  local before reply
  gt_dns_required || return 0
  case "$MODE" in --install-core|--install-web) ;; --bootstrap) gt_verify_dns; return $? ;; *) return 2 ;; esac
  if ! gt_dns_tool_available; then
    gt_text 'Missing DNS check prerequisite: dig (bind9-dnsutils). Only its prerequisite transaction can proceed.' \
      'Fehlende DNS-Prüfvoraussetzung: dig (bind9-dnsutils). Zunächst ist nur diese Paketinstallation möglich.'
    if ! gt_dns_tools_plan; then gt_show_plan; return 2; fi
    before=${FACT[dns.tools_plan]}
    if [[ "$FRONTEND" == whiptail && "$CORE_CONFIRM" != yes ]]; then
      gt_plan_report > "$SCRATCH/plan.txt"
      gt_confirm install-dns-tools || return 130
    else
      gt_plan_report
      if [[ "$CORE_CONFIRM" != yes ]]; then
        if [[ -z "$QUESTION_FD" ]]; then
          { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
        fi
        printf '%s: ' "$(gt_text 'Type install-dns-tools to install this prerequisite only' \
          'install-dns-tools eingeben, um nur diese Voraussetzung zu installieren')" >&"$QUESTION_FD"
        IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == install-dns-tools ]] || return 130
      fi
    fi
    gt_dns_tools_plan || { gt_plan_report; return 2; }
    [[ "$before" == "${FACT[dns.tools_plan]}" ]] || {
      gt_core_error 'DNS prerequisite transaction changed; review a fresh plan.'; return 2;
    }
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install bind9-dnsutils || return 2
    gt_dns_tool_available || { gt_core_error 'dig is still unavailable after installing bind9-dnsutils.'; return 2; }
    # Subsequent plan snapshots must include the packages just installed.
    gt_system; gt_packages
  fi
  # Even successful APT does not authorize the domain plan: recollect addresses
  # and resolve DNS before the caller can confirm or execute application changes.
  gt_verify_dns
}
gt_plan_certificate() {
  local cert key permissions public_cert public_key name
  cert=$(gt_path "${ANSWER[TLS_CERT]}"); key=$(gt_path "${ANSWER[TLS_KEY]}")
  permissions=$(stat -Lc %a "$key" 2>/dev/null) || permissions=unknown
  if [[ ! -f "$cert" || ! -r "$cert" || ! -f "$key" || ! -r "$key" || ! "$permissions" =~ ^[0-7]{3,4}$ ]]; then
    gt_plan_block 'Certificate/key missing, unreadable or permissions UNKNOWN.' \
      'Zertifikat/Schlüssel fehlt, unlesbar oder Rechte UNKNOWN.'; return 0
  fi
  (( (8#$permissions & 077) == 0 )) || gt_plan_block 'Private key is group/world accessible.' \
    'Privater Schlüssel ist für Gruppe/Andere zugänglich.'
  public_cert=$(gt_probe openssl x509 -in "$cert" -pubkey -noout) || public_cert=''
  # Empty passphrase refuses encrypted keys without prompting or exposing key contents.
  public_key=$(gt_probe openssl pkey -in "$key" -passin pass: -pubout) || public_key=''
  [[ -n "$public_cert" && "$public_cert" == "$public_key" ]] || gt_plan_block \
    'Certificate and private key do not match or cannot be read.' \
    'Zertifikat und privater Schlüssel passen nicht zusammen oder sind unlesbar.'
  for name in ${FACT[plan.names]}; do
    gt_certificate_covers "$cert" "$name" || gt_plan_block "Certificate does not cover $name" \
      "Zertifikat gilt nicht für $name"
  done
  if ! gt_probe openssl x509 -in "$cert" -noout -ext subjectAltName | grep -q 'DNS:'; then
    gt_plan_block 'Certificate has no DNS subject alternative names.' \
      'Zertifikat hat keine DNS-Namen in subjectAltName.'
  fi
  gt_probe openssl x509 -in "$cert" -noout -checkend 0 >/dev/null || gt_plan_block 'Certificate expired or invalid.' \
    'Zertifikat abgelaufen oder ungültig.'
  gt_probe openssl x509 -in "$cert" -noout -checkend 2592000 >/dev/null || gt_plan_warn \
    'Certificate expires within 30 days.' 'Zertifikat läuft innerhalb von 30 Tagen ab.'
  gt_probe openssl verify -purpose sslserver -untrusted "$cert" "$cert" >/dev/null || gt_plan_block \
    'Certificate chain cannot be verified against system trust.' \
    'Zertifikatskette kann nicht gegen den System-Vertrauensspeicher geprüft werden.'
  if [[ "${1:-existing}" == certbot ]]; then
    gt_plan_row reuse "${ANSWER[TLS_CERT]} / ${ANSWER[TLS_KEY]}" \
      'Validated Certbot certificate; renewal planned separately.'
  else
    gt_plan_row reuse "${ANSWER[TLS_CERT]} / ${ANSWER[TLS_KEY]}" \
      'Reference existing files; owner renews and reloads the web server. No renewal hook installed.' \
      'Vorhandene Dateien referenzieren; Eigentümer erneuert und lädt Webserver neu. Kein Erneuerungs-Hook.'
  fi
}

gt_certbot_plan() {
  local name=${ANSWER[LETSENCRYPT_CERT_NAME]:-} renewal digest permissions
  local saved_cert=${ANSWER[TLS_CERT]:-} saved_key=${ANSWER[TLS_KEY]:-}
  if [[ -z "$name" ]]; then name=${STATE[resource.certbot_selection]:-}; fi
  if [[ -z "$name" && "${STATE[resource.acme_certificate]:-}" == intent ]]; then name=-; fi
  [[ -n "$name" ]] || name=$(gt_certbot_default)
  gt_validate_answer LETSENCRYPT_CERT_NAME "$name" || {
    gt_plan_block 'Invalid Certbot lineage name.'; return 0;
  }
  FACT[tls.selection]=$name FACT[tls.renewal_hash]=''
  if [[ -n "${STATE[resource.certbot_selection]:-}" && "$name" != "${STATE[resource.certbot_selection]}" ]]; then
    gt_plan_block 'Certbot lineage selection changed; restore the confirmed selection.'; return 0
  fi
  if [[ "$name" == - ]]; then
    FACT[tls.lineage]="gt-install-${STATE[run_id]:-<installation-id>}" FACT[tls.reuse]=no
  else
    FACT[tls.lineage]=$name FACT[tls.reuse]=yes
  fi
  FACT[tls.cert]="/etc/letsencrypt/live/${FACT[tls.lineage]}/fullchain.pem"
  FACT[tls.key]="/etc/letsencrypt/live/${FACT[tls.lineage]}/privkey.pem"
  [[ "${FACT[tls.reuse]}" == yes ]] || return 0
  renewal=$(gt_path "/etc/letsencrypt/renewal/$name.conf")
  if ! gt_no_symlinks "$renewal" || [[ ! -f "$renewal" || "$(stat -c %u "$renewal")" != 0 ]]; then
    gt_plan_block 'Existing Certbot renewal configuration must be a root-owned regular file.'; return 0
  fi
  permissions=$(stat -c %a "$renewal")
  if (( (8#$permissions & 022) != 0 )); then
    gt_plan_block 'Existing Certbot renewal configuration is writable by group/others.'; return 0
  fi
  digest=$(sha256sum "$renewal"); FACT[tls.renewal_hash]=${digest%% *}
  if [[ -n "${STATE[resource.certbot_renewal_hash]:-}" &&
      "${STATE[resource.certbot_renewal_hash]}" != "${FACT[tls.renewal_hash]}" ]]; then
    gt_plan_block 'Existing Certbot renewal configuration changed; review it before resuming.'
  fi
  ANSWER[TLS_CERT]=${FACT[tls.cert]} ANSWER[TLS_KEY]=${FACT[tls.key]}
  gt_plan_certificate certbot
  ANSWER[TLS_CERT]=$saved_cert ANSWER[TLS_KEY]=$saved_key
  gt_plan_row preserve "$renewal" 'Keep renewal configuration, certificate names and existing renewal schedule.'
  gt_plan_row create 'Certbot deploy hook' \
    'Additional installer-owned hook, scoped to this lineage; reload selected web server.'
  gt_plan_row verify "certbot renew --cert-name $name --dry-run" \
    'Test the existing renewal configuration; no duplicate issuance.'
}
gt_plan_tls() {
  local web=${ANSWER[WEBSERVER]} en de
  case "${ANSWER[TLS_SOURCE]:-lan}" in
    letsencrypt)
      if [[ "$web" == none ]]; then
        gt_plan_block "Let's Encrypt requires nginx or Apache integration." \
          "Let's Encrypt benötigt nginx- oder Apache-Integration."
      else
        gt_plan_package certbot
        gt_certbot_plan
        if [[ "${FACT[tls.reuse]:-no}" != yes ]]; then
          en='certbot HTTP-01, domain HTTPS redirect, enable renewal and verify renew --dry-run during installation'
          de='certbot HTTP-01, HTTPS-Umleitung für Domain, Erneuerung aktivieren'
          de+=' und renew --dry-run bei Installation prüfen'
          gt_plan_row issue "certificate:${FACT[plan.names]}" "$en" "$de"
        fi
      fi ;;
    existing) gt_plan_certificate ;;
    proxy)
      en="HTTP ${ANSWER[TLS_PROXY_LISTEN]}; allowed source=${ANSWER[TLS_PROXY_FROM]:-any};"
      en+=' preserve Host, X-Forwarded-For, X-Forwarded-Proto'
      gt_plan_row configure proxy "$en"
      [[ -n "${ANSWER[TLS_PROXY_FROM]}" ]] || gt_plan_warn \
        'Proxy source restriction is empty; all sources could supply forwarded headers.' \
        'Proxy-Quellbeschränkung ist leer; alle Quellen könnten Forwarded-Header liefern.' ;;
    lan) gt_plan_row skip tls 'LAN-only HTTP, no certificate' 'HTTP nur im LAN, kein Zertifikat' ;;
  esac
  gt_duckdns_plan
}
gt_plan_application() {
  local file en de
  gt_plan_row create user:grafioschtrader 'Home /home/grafioschtrader; disabled login password' \
    'Home /home/grafioschtrader; Anmeldung per Passwort gesperrt'
  for file in /etc/sudoers.d/grafioschtrader /etc/systemd/system/grafioschtrader.service \
      /etc/logrotate.d/grafioschtrader /home/grafioschtrader/gtvar.sh /home/grafioschtrader/grafioschtrader.sh; do
    gt_plan_file "$file" 'GT configuration; validated before activation' 'GT-Konfiguration; vor Aktivierung prüfen'
  done
  gt_plan_row create /var/log/grafioschtrader.log 'Preserve if present; weekly copytruncate, 8 compressed rotations' \
    'Erhalten falls vorhanden; wöchentlich copytruncate, 8 komprimierte Rotationen'
  gt_plan_file /root/.gt-install/secrets \
    'Only during installation: application secrets, excluding database root password; directory 700, file 600' \
    'Erst bei Installation: Anwendungsgeheimnisse ohne Datenbank-Root-Passwort; Verzeichnis 700, Datei 600'
  gt_plan_row clone /home/grafioschtrader/build/grafioschtrader \
    "master; planned commit=${FACT[source.commit]}; build as grafioschtrader"
  en='Database/mail/JWT credentials encrypted with Jasypt; admin, user limit, connector and mail keys'
  de='Datenbank/Mail/JWT-Zugangsdaten mit Jasypt verschlüsseln;'
  de+=' Administrator, Benutzerlimit, Connector- und Mail-Schlüssel'
  gt_plan_row configure application.properties "$en" "$de"
  gt_plan_row configure application-production.properties \
    'server.address=127.0.0.1; Apache also server.port; survives gtupdate' \
    'server.address=127.0.0.1; Apache zusätzlich server.port; bleibt bei gtupdate erhalten'
  if [[ "${ANSWER[TIMEZONE]}" != "${FACT[timezone]}" ]]; then
    gt_plan_row modify /etc/localtime "${ANSWER[TIMEZONE]}; before first cron setup"
  fi
  gt_plan_row build /home/grafioschtrader/gtupdate.sh \
    "backend from source, frontend=${FACT[frontend.mode]}; verify built commit and requirements"
  gt_plan_row enable grafioschtrader.service \
    'After=mariadb.service; first start runs Flyway; verify /api/gtinfo and schema collations' \
    'After=mariadb.service; erster Start führt Flyway aus; /api/gtinfo und Schema-Kollationen prüfen'
  if [[ "${ANSWER[SMTP_CONFIGURE]}" == yes ]]; then
    en="${ANSWER[SMTP_HOST]}:${ANSWER[SMTP_PORT]}; auth=${ANSWER[SMTP_AUTH]}; security=${ANSWER[SMTP_SECURITY]};"
    en+=" sender=${ANSWER[SMTP_USER]}; test=${ANSWER[SMTP_TEST]}; no message sent"
    gt_plan_row configure mail "$en"
    if [[ "${ANSWER[SMTP_AUTH]}:${ANSWER[SMTP_SECURITY]}" == yes:none ]]; then
      gt_plan_block 'Authenticated SMTP requires STARTTLS or TLS.' 'SMTP mit Anmeldung benötigt STARTTLS oder TLS.'
    fi
  else gt_plan_warn "$(gt_mail_skipped_warning core)"; fi
  gt_firewall_plan
  en='Execution only: atomic progress, planned/built commits, owned resources; re-inventory before execution'
  de='Erst bei Ausführung: atomarer Fortschritt, geplante/gebaute Commits, eigene Ressourcen;'
  de+=' Bestand vorher erneut prüfen'
  gt_plan_row create /var/lib/gt-install/state "$en" "$de"
}
# Immutable answers must describe a route supported by every later stage. This
# contract contains no host-class gate, credentials or mutations, so it also
# applies to an owned installation resumed after its first application start.
gt_stage_contract() {
  local key port row label directive value name before=${#PLAN_BLOCKERS[@]} web=${ANSWER[WEBSERVER]:-} en de
  local -A used=()
  if [[ "${ANSWER[VHOST_INCLUDE]:-no}" == yes ]] && ! gt_vhost_include_mode; then
    gt_plan_block \
      'VHOST_INCLUDE=yes needs a domain and TLS_SOURCE=existing with the certificate of that virtual host.' \
      'VHOST_INCLUDE=yes benötigt eine Domain und TLS_SOURCE=existing mit dem Zertifikat dieses Vhosts.'
  fi
  # The LAN site answers on one of this host's own addresses; later stages check the live host again.
  if [[ "${FACT[network.ipv4_addresses]:-unknown}" != unknown ]]; then
    value=$(gt_lan_planned)
    [[ " ${FACT[network.ipv4_addresses]} " == *" $value "* ]] || gt_plan_block \
      "LAN_ADDRESS $value is not assigned to this host; choose one of: ${FACT[network.ipv4_addresses]:-none}." \
      "LAN_ADDRESS $value gehört nicht zu diesem Host; eine davon wählen: ${FACT[network.ipv4_addresses]:-keine}."
  fi
  # Without an own nginx or Apache nothing answers the HTTP-01 challenge.
  [[ "${ANSWER[WEBSERVER]:-}:${ANSWER[TLS_SOURCE]:-}" != none:letsencrypt ]] || gt_plan_block \
    "WEBSERVER=none needs TLS_SOURCE=existing or proxy; Let's Encrypt requires nginx or Apache." \
    "WEBSERVER=none benötigt TLS_SOURCE=existing oder proxy; Let's Encrypt erfordert nginx oder Apache."
  for key in BACKEND_PORT BACKEND_HTTP_PORT TLS_PROXY_LISTEN; do
    [[ "$key" != TLS_PROXY_LISTEN || "${ANSWER[TLS_SOURCE]:-}" == proxy ]] || continue
    port=${ANSWER[$key]:-}; [[ -n "$port" ]] || continue
    if [[ -n "${used[$port]:-}" ]]; then gt_plan_block "$key duplicates ${used[$port]} on port $port."; fi
    used[$port]=$key
    [[ "$port" != 80 && "$port" != 443 ]] || gt_plan_block "$key=$port overlaps the web listeners; select another port."
  done
  # A separate LAN site still needs port 80 even behind an upstream TLS proxy.
  # Later stages refresh and verify owned listeners in their own preflights.
  if [[ -n "${FACT[listeners]+set}" && "$web" != none ]]; then
    for port in 80 443; do
      [[ "$port" != 443 || -n "${ANSWER[DOMAIN]:-}" && "${ANSWER[TLS_SOURCE]:-}" != proxy ]] || continue
      if ! gt_port_free "$port" && ! awk -v port="$port" -v owner="$web" \
          '$4 ~ (":" port "$") && owner != "" && index($0,owner) {found=1} END {exit !found}' \
          <<< "${FACT[listeners]}"; then
        gt_plan_block \
          "Web port $port belongs to another process or is UNKNOWN; co-resident proxy integration is not implemented."
      fi
    done
  fi
  # The web executor supports owned sites only. Do not persist an include choice
  # merely because the static planner can describe it.
  for row in "${WEB[@]}"; do
    read -r label directive value <<< "$row"
    case "$directive" in server_name|ServerName|ServerAlias)
      if [[ -n "${STATE[resource.web_link]:-}${STATE[resource.web_names]:-}" ]]; then
        case "${label%#*}" in
          /etc/nginx/sites-enabled/grafioschtrader|/etc/nginx/sites-enabled/grafioschtrader-domain|\
          /etc/nginx/sites-enabled/grafioschtrader-http|/etc/apache2/sites-enabled/grafioschtrader.conf|\
          /etc/apache2/sites-enabled/grafioschtrader-domain.conf|/etc/apache2/sites-enabled/grafioschtrader-http.conf)
            continue ;;
        esac
      fi
      for name in ${value//\"/}; do
        # The domain may belong to the existing virtual host the snippet is included into; the LAN address never.
        if [[ -n "${ANSWER[DOMAIN]:-}" && "$name" == "${ANSWER[DOMAIN]}" ]] && ! gt_vhost_include_mode; then
          gt_plan_block \
            "Selected name $name belongs to a foreign vhost; select VHOST_INCLUDE=yes with TLS_SOURCE=existing."
        elif [[ "$name" == "$(gt_lan_planned)" ]]; then
          gt_plan_block "The LAN address $name belongs to a foreign vhost; the LAN site needs it."
        fi
      done ;;
    esac
  done
  if [[ "${ANSWER[TLS_SOURCE]:-}" == proxy ]]; then
    de='Der vorgeschaltete Proxy muss vom Client gelieferte Forwarding-Header ersetzen'
    de+=' und das öffentliche Protokoll setzen.'
    gt_plan_warn 'The upstream proxy must overwrite client-supplied forwarding headers and set the public protocol.' \
      "$de"
    if [[ -z "${ANSWER[TLS_PROXY_FROM]:-}" ]]; then
      en='TLS_PROXY_FROM is empty: login lockout cannot reliably attribute clients'
      en+=' behind an unconfigured upstream proxy.'
      de='TLS_PROXY_FROM ist leer: Die Login-Sperre kann Clients'
      de+=' hinter einem unkonfigurierten Proxy nicht zuverlässig zuordnen.'
      gt_plan_warn "$en" "$de"
    fi
  fi
  [[ "${ANSWER[SMTP_CONFIGURE]:-}:${ANSWER[SMTP_AUTH]:-}:${ANSWER[SMTP_SECURITY]:-}" != yes:yes:none ]] ||
    gt_plan_block 'Authenticated SMTP requires encryption.'
  (( ${#PLAN_BLOCKERS[@]} == before ))
}

gt_stage_preflight() {
  PLAN_BLOCKERS=()
  gt_stage_contract && return 0
  gt_report_rows "$(gt_text Blockers Blocker)" "${PLAN_BLOCKERS[@]}"
  return 2
}

gt_plan() {
  local key
  PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=()
  case "${FACT[host.class]}" in
    classic|completed|docker)
      gt_plan_row reuse "${FACT[host.class]}" 'Existing installation: no bootstrap actions; use its updater.' \
        'Vorhandene Installation: keine Bootstrap-Aktionen; zugehörigen Updater verwenden.'; return 0 ;;
    fresh) ;;
    *) gt_plan_block 'Host is not fresh; resolve existing state or installation pieces first.' \
      'Host ist nicht neu; vorhandenen Zustand oder Installationsteile zuerst klären.'; return 2 ;;
  esac
  gt_question_model
  for key in "${QUESTIONS[@]}"; do
    if gt_question_applies "$key" && [[ "${Q_TYPE[$key]}" != secret ]]; then
      gt_validate_answer "$key" "${ANSWER[$key]:-}" || gt_plan_block "Invalid/missing answer: $key" \
        "Ungültige/fehlende Antwort: $key"
    elif [[ -n "${ANSWER[$key]:-}" ]]; then
      gt_plan_block "Inactive or secret answer must not enter dry-run: $key" \
        "Inaktive oder geheime Antwort darf nicht in den Dry-run gelangen: $key"
    fi
  done
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
  for key in "${!ACTION[@]}"; do
    [[ "$key" == disk || "${ACTION[$key]}" != block ]] || gt_plan_block "$key: ${REASON[$key]}"
  done
  [[ "${FACT[source.requirements]}" == remote ]] || gt_plan_block \
    'Source requirements are provisional; no executable plan until source lookup succeeds.' \
    'Quellcode-Anforderungen sind vorläufig; kein ausführbarer Plan ohne erfolgreiche Quellcode-Abfrage.'
  [[ "${FACT[dpkg.lock]}" == free ]] || gt_plan_block 'Package lock is held or UNKNOWN.' \
    'Paketsperre ist belegt oder UNKNOWN.'
  if [[ "${FACT[apt.age_hours]}" == unknown ]] || (( ${FACT[apt.age_hours]} > 24 )); then
    gt_plan_block 'APT metadata is absent/stale; refresh and re-plan before installation.' \
      'APT-Metadaten fehlen/sind veraltet; vor Installation aktualisieren und neu planen.'
  fi
  gt_stage_contract || true
  gt_plan_toolchains; gt_plan_database; gt_plan_ports; gt_plan_dns; gt_plan_web; gt_plan_storage; gt_plan_tls
  gt_plan_application; gt_plan_packages
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
}
gt_plan_report() {
  local key row de
  gt_text 'Installation plan — no installation changes made' \
    'Installationsplan — keine Installationsänderungen vorgenommen'
  for key in "${QUESTIONS[@]}"; do
    [[ "${Q_TYPE[$key]}" != secret && -n "${ANSWER[$key]+present}" ]] || continue
    printf '%s=%s\n' "$key" "$(gt_safe "${ANSWER[$key]}")"
  done
  if [[ "${FACT[host.class]}" == fresh ]]; then
    gt_text 'Required secrets (values omitted)' 'Erforderliche Geheimnisse (Werte ausgeblendet)'
    for key in "${QUESTIONS[@]}"; do
      [[ "${Q_TYPE[$key]}" == secret ]] || continue
      gt_question_applies "$key" || continue
      printf '%s | %s | %s\n' "$key" "$(gt_text "${Q_EN[$key]}" "${Q_DE[$key]}")" "${SECRET_STATUS[$key]:-required}"
    done
    printf 'JWT_SECRET | g.jwt.secret | %s\n' "${SECRET_STATUS[JWT_SECRET]:-automatic during preparation}"
  fi
  gt_report_rows "$(gt_text 'Planned actions (subject to blockers)' 'Geplante Aktionen (abhängig von Blockern)')" \
    "${PLAN[@]}"
  gt_report_rows "$(gt_text 'Disk requirements' 'Speicherplatzbedarf')" "${DISKS[@]}"
  gt_report_rows "$(gt_text Warnings Hinweise)" "${PLAN_WARNINGS[@]}"
  gt_report_rows "$(gt_text Blockers Blocker)" "${PLAN_BLOCKERS[@]}"
  printf '%s: %s\n' "$(gt_text 'Blocking findings' 'Blockierende Befunde')" "${#PLAN_BLOCKERS[@]}"
  de='Keine Installation ausgeführt.'
  de+=' Zugangsdaten und aktueller Host-Zustand müssen vor Ausführung erneut geprüft werden.'
  gt_text 'No installation was executed. Credentials and live host state must be verified again before execution.' \
    "$de"
}
gt_dry_run() {
  local status=0
  gt_completed || status=$?
  (( status == 3 )) || return "$status"
  status=0
  gt_inventory; gt_compatibility; gt_show_report || return $?
  if [[ "${FACT[host.class]}" == fresh ]]; then
    if ! { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null; then
      gt_text 'Dry-run requires a terminal for questions; use --check for unattended inventory.' \
        'Dry-run benötigt ein Terminal für Fragen; --check für unbeaufsichtigte Bestandsaufnahme verwenden.' >&2
      return 2
    fi
    QUESTION_OUTPUT=$QUESTION_FD
    gt_questions || return $?
  fi
  gt_plan || status=$?
  gt_show_plan
  return "$status"
}

gt_prepare() {
  local status=0 de
  gt_completed || status=$?
  (( status == 3 )) || return "$status"
  status=0
  gt_inventory; gt_compatibility; gt_show_report || return $?
  gt_question_model
  if [[ "${FACT[host.class]}" == fresh ]]; then
    if [[ -n "$ANSWERS_FILE" ]]; then
      gt_answers_file "$ANSWERS_FILE" || return $?
    else
      if ! { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null; then
        gt_text 'Preparation needs a terminal or --answers FILE.' \
          'Vorbereitung benötigt ein Terminal oder --answers DATEI.' >&2
        return 2
      fi
      QUESTION_OUTPUT=$QUESTION_FD
      gt_text 'Preparation only: secrets are discarded on exit; nothing will be installed.' \
        'Nur Vorbereitung: Geheimnisse werden beim Beenden verworfen; es wird nichts installiert.' >&"$QUESTION_OUTPUT"
    fi
    gt_prepare_root || return $?
    if [[ -n "$ANSWERS_FILE" ]]; then
      gt_file_questions || return $?
      gt_prepare_secrets || return $?
    else gt_interactive_answers || return $?; fi
  fi
  gt_plan || status=$?
  gt_show_plan
  de='Vorbereitung endet hier. Erfasste/erzeugte Geheimnisse werden verworfen;'
  de+=' die übergebene Antwortdatei bleibt unverändert.'
  gt_text 'Preparation ends here. Collected/generated secrets are discarded; the supplied answers file is unchanged.' \
    "$de"
  return "$status"
}
