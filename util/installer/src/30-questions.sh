gt_text() { if [[ "$LANG_CODE" == de ]]; then printf '%s\n' "$2"; else printf '%s\n' "$1"; fi; }
gt_question_model() {
  local key type when choices en de row next
  QUESTIONS=() Q_TYPE=() Q_WHEN=() Q_CHOICES=() Q_EN=() Q_DE=()
  # A row ending in '|' continues on the next line; that line's indentation is not part of the field.
  while IFS= read -r row; do
    while [[ "$row" == *'|' ]] && IFS= read -r next; do row+=${next#"${next%%[! ]*}"}; done
    IFS='|' read -r key type when choices en de <<< "$row"
    QUESTIONS+=("$key"); Q_TYPE[$key]=$type Q_WHEN[$key]=$when Q_CHOICES[$key]=$choices Q_EN[$key]=$en Q_DE[$key]=$de
    [[ "$type" != yesno ]] || Q_CHOICES[$key]='yes no'
  done <<'QUESTIONS'
DOMAIN|domain|always||Public domain; empty for LAN only|Öffentliche Domain; leer für nur LAN
DNS_FAMILY|choice|domain|ipv4 ipv6 both|Reachable address families; choose ipv6 for DS-Lite/CGNAT|
  Erreichbare Adressfamilien; ipv6 bei DS-Lite/CGNAT wählen
DUCKDNS_UPDATER|yesno|duckdns||Run a DuckDNS updater here; no if your router or another client updates it|
  DuckDNS hier aktualisieren; nein bei Aktualisierung durch Router oder anderen Client
DUCKDNS_TOKEN|secret|duck_updater||DuckDNS token required during installation|
  DuckDNS-Token bei der Installation erforderlich
TLS_SOURCE|choice|domain|letsencrypt existing proxy|TLS provider: certbot, existing certificate, or terminating proxy|
  TLS-Anbieter: certbot, vorhandenes Zertifikat oder vorgeschalteter Proxy
LETSENCRYPT_EMAIL|email|letsencrypt||Email for Let's Encrypt|E-Mail für Let's Encrypt
LETSENCRYPT_CERT_NAME|lineage|letsencrypt||
  Reuse this Certbot lineage with its renewal configuration; - requests a new certificate|
  Diese Certbot-Zertifikatsreihe samt Erneuerung verwenden; - fordert ein neues Zertifikat an
TLS_CERT|path|existing_tls||Readable full-chain certificate file; renewal remains your responsibility|
  Lesbare Zertifikatsdatei mit vollständiger Kette; Erneuerung bleibt Ihre Aufgabe
TLS_KEY|path|existing_tls||Private key file, readable only by its owner|
  Privater Schlüssel, nur für den Eigentümer lesbar
TLS_PROXY_LISTEN|port|proxy||Local HTTP port receiving proxy traffic|Lokaler HTTP-Port für Proxy-Anfragen
TLS_PROXY_FROM|address|proxy||Proxy IP address; empty allows every source|
  IP-Adresse des Proxys; leer erlaubt alle Quellen
LAN_ADDRESS|lan|always||IPv4 address of this host for the LAN site http://<address>/|
  IPv4-Adresse dieses Hosts für die LAN-Seite http://<Adresse>/
WEBSERVER|choice|always|nginx apache2 none|Web integration; none leaves manual configuration pending|
  Web-Integration; none lässt die manuelle Konfiguration offen
VHOST_INCLUDE|yesno|vhost||Permit an include in the identified vhost after backup and route checks|
  Include im erkannten Vhost nach Sicherung und Routenprüfung erlauben
BACKEND_PORT|port|always||Primary backend port, bound to loopback|Primärer Backend-Port, an Loopback gebunden
BACKEND_HTTP_PORT|port|apache||Additional loopback HTTP port required by Apache topology|
  Zusätzlicher Loopback-HTTP-Port für die Apache-Topologie
ADMIN_EMAIL|email|always||Email whose registration becomes administrator|
  E-Mail-Adresse, deren Registrierung Administrator wird
ALLOWED_USERS|integer|always||Maximum number of users|Maximale Anzahl Benutzer
SMTP_CONFIGURE|yesno|always||Configure mail; no prevents completed registration|
  Mail konfigurieren; no verhindert abgeschlossene Registrierungen
SMTP_HOST|host|smtp||SMTP host|SMTP-Server
SMTP_PORT|port|smtp||SMTP port|SMTP-Port
SMTP_AUTH|yesno|smtp||SMTP authentication required|SMTP-Anmeldung erforderlich
SMTP_USER|email|smtp||SMTP login and sender email; also required for unauthenticated relays|
  SMTP-Anmeldung und Absender-E-Mail; auch für Relays ohne Anmeldung erforderlich
SMTP_SECURITY|choice|smtp|starttls tls none|SMTP transport security; none only for unauthenticated relays|
  SMTP-Transportverschlüsselung; none nur für Relays ohne Anmeldung
SMTP_PASSWORD|secret|smtp_auth||SMTP password required during installation|
  SMTP-Passwort bei der Installation erforderlich
SMTP_TEST|yesno|smtp||Send a test message during installation|Bei der Installation eine Testnachricht senden
DB_PASSWORD|secret|always||Grafioschtrader database password (spring.datasource.password)|
  Grafioschtrader-Datenbankpasswort (spring.datasource.password)
DB_REUSE_EMPTY|yesno|empty_db||Reuse the existing empty grafioschtrader database|
  Vorhandene leere Datenbank grafioschtrader verwenden
DB_ROOT_PASSWORD|secret|root_password||MariaDB root password; new for a new server, current for an existing server|
  MariaDB-Root-Passwort; neu für neuen Server, aktuell für vorhandenen Server
JASYPT_PASSWORD|secret|always||Encryption password (JASYPT_ENCRYPTOR_PASSWORD)|
  Verschlüsselungspasswort (JASYPT_ENCRYPTOR_PASSWORD)
BUFFER_POOL|yesno|buffer||Set MariaDB buffer pool and restart the server, affecting every database|
  MariaDB-Puffer setzen und Server neu starten; betrifft alle Datenbanken
JAVA_HEAP|heap|always||Java heap: -Xms<size> -Xmx<size>, with m or g units|
  Java-Heap: -Xms<Größe> -Xmx<Größe>, mit m oder g
DOCROOT|path|always||Absolute document root; existing application content must remain untouched|
  Absolutes Dokumentenverzeichnis; vorhandene Anwendungen müssen erhalten bleiben
TIMEZONE|timezone|always||Host time zone used for the first cron setup|Host-Zeitzone für die erste Cron-Einrichtung
SWAP|yesno|swap||Create 2 GiB at /swapfile and add it to /etc/fstab|
  2 GiB unter /swapfile anlegen und in /etc/fstab eintragen
NODE_REPLACE|yesno|node_shared||Replace shared Node.js instead of isolating; affects other consumers|
  Gemeinsames Node.js ersetzen statt isolieren; betrifft andere Anwendungen
FIREWALL_ALLOW|yesno|ufw||Allow the selected web ports in ufw; SSH and existing rules stay unchanged|
  Gewählte Web-Ports in ufw freigeben; SSH und bestehende Regeln bleiben unverändert
QUESTIONS
}

gt_question_applies() {
  case "${Q_WHEN[$1]}" in
    always) return 0 ;;
    domain) [[ -n "${ANSWER[DOMAIN]:-}" ]] ;;
    duckdns) [[ "${ANSWER[DOMAIN]:-}" == *.duckdns.org ]] ;;
    duck_updater) [[ "${ANSWER[DUCKDNS_UPDATER]:-}" == yes ]] ;;
    letsencrypt) [[ "${ANSWER[TLS_SOURCE]:-}" == letsencrypt ]] ;;
    existing_tls) [[ "${ANSWER[TLS_SOURCE]:-}" == existing ]] ;;
    proxy) [[ "${ANSWER[TLS_SOURCE]:-}" == proxy ]] ;;
    apache) [[ "${ANSWER[WEBSERVER]:-}" == apache2 ]] ;;
    vhost) [[ "${ANSWER[WEBSERVER]:-none}" != none ]] && gt_vhost_claims_domain ;;
    smtp) [[ "${ANSWER[SMTP_CONFIGURE]:-}" == yes ]] ;;
    smtp_auth) [[ "${ANSWER[SMTP_CONFIGURE]:-}" == yes && "${ANSWER[SMTP_AUTH]:-}" == yes ]] ;;
    empty_db) [[ "${FACT[database.gt_tables]:-}" == 0 ]] ;;
    root_password) [[ "${FACT[database.vendor]:-unknown}" == absent || "${FACT[database.query]:-unknown}" != ok ||
      "${FACT[database.gt_user]:-unknown}" == unknown || "${FACT[database.schemas]:-unknown}" == unknown ||
      "${FACT[database.password_auth]:-}" == yes ]] ;;
    buffer) [[ -z "${FACT[database.buffer_pool_config]:-}" && "${FACT[database.vendor]:-absent}" != mysql ]] ;;
    swap) [[ "${ACTION[swap]:-}" == install ]] ;;
    node_shared) [[ "${ACTION[node]:-}" == isolate ]] ;;
    ufw) [[ "${FACT[firewall.ufw]:-}" == *'Status: active'* ]] ;;
    *) return 1 ;;
  esac
}

gt_memory_defaults() {
  local mem=${FACT[memory.MemTotal]:-0} row=0 pool=384 max
  [[ "$mem" =~ ^[0-9]+$ ]] || mem=0
  if (( mem >= 6000 )); then row=2; pool=2048
  elif (( mem >= 3000 )); then row=1; pool=1024; fi
  local -a heaps=('-Xms128m -Xmx896m' '-Xms256m -Xmx1792m' '-Xms512m -Xmx2048m') sizes=(896 1792 2048)
  max=${sizes[row]}
  [[ "${ANSWER[BUFFER_POOL]:-no}" == yes ]] || pool=0
  if (( row > 0 && (max+pool)*100 > mem*60 )); then row=$((row-1)); fi
  printf '%s' "${heaps[row]}"
}

gt_certificate_covers() {
  # x509 -checkhost prints a mismatch but still exits zero on OpenSSL 3.0. Verification
  # returns a meaningful status. Trusting the leaf here checks names only; deployment trust
  # is checked separately against the system store in gt_plan_certificate.
  gt_probe openssl x509 -in "$1" -noout -ext subjectAltName | grep -q 'DNS:' || return 1
  gt_probe openssl verify -trusted "$1" -partial_chain -no_check_time -verify_hostname "$2" "$1" >/dev/null
}
# An existing site of this host, not one of the installer's own, already serves the domain.
gt_vhost_claims_domain() {
  local row label directive value name
  [[ -n "${ANSWER[DOMAIN]:-}" ]] || return 1
  for row in "${WEB[@]}"; do
    read -r label directive value <<< "$row"
    case "$directive" in server_name|ServerName|ServerAlias) ;; *) continue ;; esac
    case "${label%#*}" in */sites-enabled/grafioschtrader*) continue ;; esac
    for name in ${value//\"/}; do [[ "$name" != "${ANSWER[DOMAIN]}" ]] || return 0; done
  done
  return 1
}

gt_tls_default() {
  local row file existing=no
  # The existing site keeps terminating TLS when Grafioschtrader is included into it.
  if gt_vhost_claims_domain; then echo existing; return; fi
  for row in "${CERTS[@]}"; do
    file=${row%%: *}
    if [[ -r "$file" ]] && gt_certificate_covers "$file" "${ANSWER[DOMAIN]}"; then
      [[ "$file" == */etc/letsencrypt/live/* ]] && { echo letsencrypt; return; }
      existing=yes
    fi
  done
  [[ "$existing" != yes ]] || { echo existing; return; }
  if [[ "${REASON[web]:-}" == 'foreign proxy'* || "${FACT[web.cloudflared]:-}" == *'LoadState=loaded'* ]]; then
    echo proxy
  else echo letsencrypt; fi
}
gt_certificate_default() {
  local row web_row file label directive value wanted_label=''
  for row in "${CERTS[@]}"; do
    file=${row%%: *}
    if [[ -r "$file" ]] && gt_certificate_covers "$file" "${ANSWER[DOMAIN]}"; then
      if [[ "$1" == TLS_CERT ]]; then printf '%s' "${file#"$ROOT"}"; return; fi
      for web_row in "${WEB[@]}"; do
        read -r label directive value <<< "$web_row"
        if [[ "$directive" == ssl_certificate || "$directive" == SSLCertificateFile ]]; then
          [[ "$(gt_path "$value")" != "$file" ]] || wanted_label=$label
        fi
      done
      for web_row in "${WEB[@]}"; do
        read -r label directive value <<< "$web_row"
        if [[ -n "$wanted_label" && "$label" == "$wanted_label" &&
            ( "$directive" == ssl_certificate_key || "$directive" == SSLCertificateKeyFile ) ]]; then
          printf '%s' "$value"; return
        fi
      done
      value=${file#"$ROOT"}
      [[ ! -r "${file%/*}/privkey.pem" ]] || printf '%s/privkey.pem' "${value%/*}"
      return
    fi
  done
}
gt_certbot_default() {
  local row file name
  for row in "${CERTS[@]}"; do
    file=${row%%: *}
    [[ "$file" == "$(gt_path /etc/letsencrypt/live)/"*/fullchain.pem ]] || continue
    name=${file%/fullchain.pem}; name=${name##*/}
    gt_validate_answer LETSENCRYPT_CERT_NAME "$name" || continue
    if gt_certificate_covers "$file" "${ANSWER[DOMAIN]}"; then printf '%s' "$name"; return; fi
  done
  printf '-'
}
gt_vhost_root_default() {
  local row label directive value name match
  local -A matches=()
  for row in "${WEB[@]}"; do
    read -r label directive value <<< "$row"
    case "$directive" in server_name|ServerName|ServerAlias)
      for name in ${value//\"/}; do
        if [[ -n "${ANSWER[DOMAIN]:-}" && "$name" == "${ANSWER[DOMAIN]}" || "$name" == "$(gt_lan_planned)" ]]; then
          matches[$label]=1
        fi
      done ;;
    esac
  done
  (( ${#matches[@]} == 1 )) || return 1
  for match in "${!matches[@]}"; do :; done
  for row in "${WEB[@]}"; do
    read -r label directive value <<< "$row"
    if [[ "$label" == "$match" && ( "$directive" == root || "$directive" == DocumentRoot ) ]]; then
      value=${value//\"/}; gt_valid_path "$value" || return 1
      printf '%s' "$value"; return 0
    fi
  done
  return 1
}
gt_default() {
  local value
  case "$1" in
    DNS_FAMILY)
      if [[ "${FACT[network.global_ipv6]:-unknown}" != unknown && -n "${FACT[network.global_ipv6]:-}" ]]; then
        [[ "${FACT[network.public_ipv4]:-unknown}" == unknown ]] && echo ipv6 || echo both
      else echo ipv4; fi ;;
    # An updater found on this host keeps the records; a second one would fight it over the same record.
    DUCKDNS_UPDATER) [[ "${FACT[dns.duckdns]:-no}" == yes ]] && echo no || echo yes ;;
    TLS_SOURCE) gt_tls_default ;;
    LETSENCRYPT_CERT_NAME) gt_certbot_default ;;
    TLS_CERT|TLS_KEY) gt_certificate_default "$1" ;;
    TLS_PROXY_LISTEN) gt_proxy_port_default ;;
    # The source address towards the internet; a host with a separate intranet interface chooses that one.
    LAN_ADDRESS) if gt_lan_candidate "${FACT[network.lan_ipv4]:-}"; then echo "${FACT[network.lan_ipv4]}"; fi ;;
    WEBSERVER)
      case "${REASON[web]:-}" in nginx|apache2) echo "${REASON[web]}" ;;
        'foreign proxy'*) [[ "${ANSWER[TLS_SOURCE]:-}" == proxy ]] && echo nginx || echo none ;;
        *) echo nginx ;; esac ;;
    BACKEND_PORT) echo "${FACT[ports.backend_primary]:-9090}" ;;
    BACKEND_HTTP_PORT) echo "${FACT[ports.backend_http]:-8080}" ;;
    ALLOWED_USERS) echo 20 ;;
    SMTP_PORT) echo 587 ;;
    SMTP_CONFIGURE|SMTP_AUTH) echo yes ;;
    SMTP_SECURITY) case "${ANSWER[SMTP_PORT]:-}" in 587) echo starttls ;; 465) echo tls ;; esac ;;
    SMTP_TEST) echo yes ;;
    # Only offered when RAM is below 4000 MB, no swap exists and the root filesystem supports a swap file.
    SWAP) echo yes ;;
    DB_REUSE_EMPTY|NODE_REPLACE|VHOST_INCLUDE) echo no ;;
    # Only asked while ufw is active; without the rules the LAN and domain routes would stay unreachable.
    FIREWALL_ALLOW) echo yes ;;
    BUFFER_POOL)
      value=${FACT[database.schemas]:-unknown}
      if [[ "${FACT[database.vendor]:-}" == absent ]]; then echo yes
      elif [[ "$value" == unknown ]] || awk -F '\t' '
          $1 !~ /^(mysql|information_schema|performance_schema|sys|grafioschtrader)$/ {found=1}
          END {exit !found}' <<< "$value"; then echo no
      else echo yes; fi ;;
    JAVA_HEAP) gt_memory_defaults ;;
    DOCROOT) gt_vhost_root_default || echo "/var/www/${ANSWER[DOMAIN]:-gt}" ;;
    TIMEZONE) value=${FACT[timezone]:-unknown}; [[ "$value" != unknown ]] && echo "$value" ;;
    *) printf '' ;;
  esac
}

gt_valid_hostname() {
  local name=$1 label
  [[ ${#name} -le 253 && "$name" != *..* && "$name" != .* && "$name" != *. ]] || return 1
  local -a labels
  IFS=. read -r -a labels <<< "$name"
  (( ${#labels[@]} > 0 )) || return 1
  for label in "${labels[@]}"; do
    [[ ${#label} -le 63 && "$label" =~ ^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$ ]] || return 1
  done
}
gt_valid_address() {
  local value=$1 octet part left right count=0
  local -a parts
  if [[ "$value" == *:* ]]; then
    [[ "$value" =~ ^[0-9a-fA-F:]+$ && "$value" != *:::* ]] || return 1
    if [[ "$value" == *::* ]]; then
      left=${value%%::*}; right=${value#*::}; [[ "$right" != *::* ]] || return 1
      value="$left:$right"
      IFS=: read -r -a parts <<< "$value"
      for part in "${parts[@]}"; do
        [[ -z "$part" || ${#part} -le 4 ]] || return 1; [[ -z "$part" ]] || count=$((count+1))
      done
      (( count < 8 ))
    else
      [[ "$value" != :* && "$value" != *: ]] || return 1
      IFS=: read -r -a parts <<< "$value"
      (( ${#parts[@]} == 8 )) || return 1
      for part in "${parts[@]}"; do [[ ${#part} -le 4 ]] || return 1; done
    fi
  else
    [[ "$value" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
    IFS=. read -r -a parts <<< "$value"
    for octet in "${parts[@]}"; do [[ ${#octet} -le 3 ]] && (( 10#$octet <= 255 )) || return 1; done
  fi
}
gt_lan_candidate() {
  [[ "$1" == *.* && "$1" != 127.* && "$1" != 0.* ]] && gt_valid_address "$1"
}
# Space-separated IPv4 addresses from `ip -4 -o addr show` on stdin, in interface order.
gt_ipv4_addresses() {
  awk '$3 == "inet" {sub(/\/.*/, "", $4); if (!seen[$4]++) out = out (out == "" ? "" : " ") $4} END {print out}'
}
# The LAN address the plan works with before anything is installed.
gt_lan_planned() { printf '%s' "${ANSWER[LAN_ADDRESS]:-${FACT[network.lan_ipv4]:-unknown}}"; }
# The LAN address the web stages serve, checked against the live host: the LAN_ADDRESS answer, or the default
# route's source address for journals written before that question existed.
gt_lan_address() {
  local address=${ANSWER[LAN_ADDRESS]:-} assigned
  if [[ -z "$address" ]]; then
    address=$(gt_probe ip -4 route get 1.1.1.1) || return 2
    address=$(awk '{for(i=1;i<NF;i++) if($i=="src") {print $(i+1); exit}}' <<< "$address")
  fi
  gt_lan_candidate "$address" || return 2
  assigned=$(gt_probe ip -4 -o addr show scope global) || return 2
  [[ " $(gt_ipv4_addresses <<< "$assigned") " == *" $address "* ]] || {
    gt_core_error "LAN address $address is not assigned to an interface of this host."; return 2;
  }
  printf '%s\n' "$address"
}
gt_normalize_addresses() {
  local address left right part count fill i
  local -a pieces
  while IFS= read -r address; do
    [[ -n "$address" ]] || continue
    gt_valid_address "$address" || { printf 'unknown\n'; continue; }
    if [[ "$address" != *:* ]]; then printf '%s\n' "$address"; continue; fi
    if [[ "$address" == *::* ]]; then
      left=${address%%::*}; right=${address#*::}; count=0
      IFS=: read -r -a pieces <<< "$left:$right"
      for part in "${pieces[@]}"; do [[ -z "$part" ]] || count=$((count+1)); done
      fill=''
      for ((i=count; i<8; i++)); do fill+='0:'; done
      address="${left:+$left:}$fill$right"; address=${address%:}
    fi
    IFS=: read -r -a pieces <<< "$address"
    for ((i=0; i<8; i++)); do
      (( i == 0 )) || printf ':'
      printf '%x' "$((16#${pieces[i]}))"
    done
    printf '\n'
  done | LC_ALL=C sort -u
}
gt_valid_path() {
  # Restrict to literal, normalized paths usable in all target configuration formats.
  [[ "$1" =~ ^/([a-zA-Z0-9_.-]+/)*[a-zA-Z0-9_.-]+$ && "/$1/" != */../* && "/$1/" != */./* ]]
}
gt_validate_answer() {
  local key=$1 value=$2 min max
  [[ -n "${Q_TYPE[$key]:-}" && "$value" != *[$'\001'-$'\037'$'\177']* ]] || return 1
  case "${Q_TYPE[$key]}" in
    domain) [[ -z "$value" ]] || { [[ "$value" == *.* && ! "$value" =~ ^[0-9.]+$ ]] && gt_valid_hostname "$value"; } ;;
    host) gt_valid_hostname "$value" ;;
    email) [[ "$value" =~ ^[a-zA-Z0-9._%+-]+@[^@]+$ ]] && gt_valid_hostname "${value#*@}" &&
      [[ "${value#*@}" == *.* ]] ;;
    yesno) [[ "$value" == yes || "$value" == no ]] ;;
    choice) [[ "$value" =~ ^[a-zA-Z0-9_-]+$ && " ${Q_CHOICES[$key]} " == *" $value "* ]] ;;
    port) [[ "$value" =~ ^[1-9][0-9]{0,4}$ ]] && (( value <= 65535 )) ;;
    integer) [[ "$value" =~ ^[1-9][0-9]{0,8}$ ]] ;;
    path) gt_valid_path "$value" ;;
    lineage) [[ -z "$value" || "$value" == - || "$value" =~ ^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$ ]] ;;
    timezone) gt_valid_path "/$value" && [[ "$value" == UTC || -f "$(gt_path "/usr/share/zoneinfo/$value")" ]] ;;
    address) [[ -z "$value" ]] || gt_valid_address "$value" ;;
    # Empty keeps journals written before LAN_ADDRESS existed valid; they use the default route's source address.
    lan) [[ -z "$value" ]] || gt_lan_candidate "$value" ;;
    heap)
      [[ "$value" =~ ^-Xms([1-9][0-9]{0,5})([mg])\ -Xmx([1-9][0-9]{0,5})([mg])$ ]] || return 1
      min=${BASH_REMATCH[1]}; max=${BASH_REMATCH[3]}
      [[ "${BASH_REMATCH[2]}" != g ]] || min=$((min*1024))
      [[ "${BASH_REMATCH[4]}" != g ]] || max=$((max*1024))
      (( min <= max )) ;;
    text) [[ "$value" != *[$'\n\r']* && ${#value} -le 512 ]] ;;
    secret) return 1 ;; # Dry-run neither collects nor generates secrets.
    *) return 1 ;;
  esac
}

gt_ask() {
  local key=$1 value default prompt choices=${Q_CHOICES[$1]}
  default=$(gt_default "$key")
  prompt=$(gt_text "${Q_EN[$key]}" "${Q_DE[$key]}")
  # The host's own addresses are the sensible answers, but any of them may be typed.
  [[ "$key" != LAN_ADDRESS || "${FACT[network.ipv4_addresses]:-unknown}" == unknown ]] ||
    choices=${FACT[network.ipv4_addresses]}
  while :; do
    printf '%s (%s)%s [%s]: ' "$prompt" "$key" "${choices:+ $choices}" "$default" >&"$QUESTION_OUTPUT"
    if ! IFS= read -r -u "$QUESTION_FD" value || [[ "$value" == '!quit' ]]; then return 130; fi
    [[ -n "$value" ]] || value=$default
    [[ "$key" != DOMAIN ]] || value=${value,,}
    if gt_validate_answer "$key" "$value"; then ANSWER[$key]=$value; return 0; fi
    gt_text 'Invalid value; try again. !quit cancels.' 'Ungültiger Wert; erneut eingeben. !quit bricht ab.' \
      >&"$QUESTION_OUTPUT"
    gt_validation_hint "$key" >&"$QUESTION_OUTPUT"
  done
}
gt_dns_checklist() {
  local en de
  [[ -n "${ANSWER[DOMAIN]:-}" ]] || return 0
  if [[ "${ANSWER[TLS_SOURCE]:-}" == proxy ]]; then
    en='Proxy: preserve Host; overwrite client-supplied X-Forwarded-For / X-Forwarded-Proto;'
    en+=' forward HTTPS to the local HTTP port.'
    de='Proxy: Host erhalten; Client-Header X-Forwarded-For / X-Forwarded-Proto ersetzen;'
    de+=' HTTPS an den lokalen HTTP-Port weiterleiten.'
    gt_text "$en" "$de"
  else
    en='Domain checklist: configure A/AAAA records for the selected reachable families'
    en+=' and admit TCP 80/443 at the router/firewall.'
    de='Domain-Checkliste: A/AAAA für die gewählten erreichbaren Familien setzen'
    de+=' und TCP 80/443 an Router/Firewall freigeben.'
    gt_text "$en" "$de"
    printf 'IPv4=%s IPv6=%s LAN=%s\n' "${FACT[network.public_ipv4]:-unknown}" "${FACT[network.global_ipv6]:-unknown}" \
      "$(gt_lan_planned)"
    if [[ "${ANSWER[DOMAIN]}" == *.duckdns.org ]]; then
      gt_text 'DuckDNS: create the account and subdomain yourself; keep the token for installation.' \
        'DuckDNS: Konto und Subdomain selbst anlegen; Token für die Installation bereithalten.'
    fi
  fi
}
# Asks the applicable questions in the selected front end. The whiptail front end accepts "last" to re-enter at the
# last question after Back from the first credential; every other call starts with empty answers.
gt_questions() {
  local key from=${1:-}
  if [[ "$from" != last ]]; then
    ANSWER=() ANSWER_DEFAULTED=()
    gt_question_model
  fi
  if [[ "$FRONTEND" == whiptail ]]; then gt_wt_questions "$from"; return $?; fi
  if [[ "$MODE" == --install-core ]]; then
    gt_text 'Core configuration: the plan will name the changes before execution. !quit cancels.' \
      'Kernkonfiguration: Der Plan benennt die Änderungen vor der Ausführung. !quit bricht ab.' >&"$QUESTION_OUTPUT"
  elif [[ "$MODE" == --prepare ]]; then
    gt_text 'Preparation only; no installation. Secrets follow the configuration questions. !quit cancels.' \
      'Nur Vorbereitung; keine Installation. Geheimnisse folgen den Konfigurationsfragen. !quit bricht ab.' \
      >&"$QUESTION_OUTPUT"
  else
    gt_text 'Dry-run: no passwords are requested or generated. !quit cancels; Enter accepts the default.' \
      'Dry-run: Passwörter werden weder abgefragt noch erzeugt. !quit bricht ab; Enter übernimmt den Vorschlag.' \
      >&"$QUESTION_OUTPUT"
  fi
  for key in "${QUESTIONS[@]}"; do
    gt_question_applies "$key" || continue
    [[ "${Q_TYPE[$key]}" != secret ]] || continue
    if [[ "$key" == WEBSERVER && ( "${REASON[web]:-}" == nginx || "${REASON[web]:-}" == apache2 ) ]]; then
      ANSWER[$key]=${REASON[web]}
    else gt_ask "$key" || return $?; fi
    if [[ "$key" == DOMAIN || "$key" == TLS_SOURCE ]]; then gt_dns_checklist >&"$QUESTION_OUTPUT"; fi
  done
}

# Secret values live outside ANSWER, which is printable. No caller may enable tracing while
# handling them. Preparation uses only private temporary files and read-only SQL.
gt_secret_error() {
  gt_text 'Credentials could not be read or verified; no installation was performed.' \
    'Zugangsdaten konnten nicht gelesen oder geprüft werden; keine Installation ausgeführt.' >&2
  return 2
}

gt_valid_secret() {
  [[ -n "$1" && "$1" != *[$'\001'-$'\037'$'\177']* ]]
}

# Key-specific formats on top of gt_valid_secret. A DuckDNS token is a UUID; the check catches typing errors before
# the first update and keeps the token free of characters that would need URL or curl-configuration escaping.
gt_secret_valid_for() {
  gt_valid_secret "$2" || return 1
  [[ "$1" != DUCKDNS_TOKEN ]] || gt_duckdns_valid_token "$2"
}

gt_restore_terminal() {
  if [[ -n "$TTY_STATE" ]]; then
    stty "$TTY_STATE" <&"$QUESTION_FD"
    TTY_STATE=''
  fi
}

gt_read_secret() {
  # read normally discards NUL. Read one byte with a NUL delimiter to reject it explicitly. Ctrl-C arrives as a
  # byte because gt_ask_secret clears isig; it cancels like EOF.
  local char invalid=no LC_ALL=C
  SECRET_INPUT=''
  while IFS= read -r -n 1 -d '' -u "$QUESTION_FD" char; do
    [[ "$char" != $'\003' ]] || break
    [[ "$char" != $'\n' ]] || { [[ "$invalid" == no ]] && gt_valid_secret "$SECRET_INPUT"; return $?; }
    if [[ -z "$char" || "$char" == *[$'\001'-$'\037'$'\177']* ]]; then
      if [[ -t "$QUESTION_FD" && ( "$char" == $'\177' || "$char" == $'\010' ) ]]; then
        SECRET_INPUT=${SECRET_INPUT%?}
      else invalid=yes; fi
    else SECRET_INPUT+=$char; fi
  done
  SECRET_INPUT=''
  return 130
}

gt_ask_secret() {
  set +vx
  local key=$1 twice=${2:-no} first status prompt
  prompt=$(gt_text "${Q_EN[$key]}" "${Q_DE[$key]}")
  # Only a new password may be generated, and only by typing the explicit request; empty input never selects it.
  [[ "$twice" != yes ]] || prompt+=$(gt_text '; !generate creates one' '; !generate erzeugt eines')
  if [[ -t "$QUESTION_FD" ]]; then
    TTY_STATE=$(stty -g <&"$QUESTION_FD") || return 2
    # -isig turns Ctrl-C into an input byte that gt_read_secret cancels on: a terminal SIGINT racing the
    # character-wise read could otherwise leave the prompt waiting for another key.
    stty -echo -isig <&"$QUESTION_FD" || { gt_restore_terminal; return 2; }
  fi
  while :; do
    printf '%s (%s): ' "$prompt" "$key" >&"$QUESTION_OUTPUT"
    status=0; gt_read_secret || status=$?
    printf '\n' >&"$QUESTION_OUTPUT"
    if (( status == 130 )); then gt_restore_terminal; return 130; fi
    if (( status == 0 )) && [[ "$twice" == yes && "$SECRET_INPUT" == '!generate' ]]; then
      SECRET_INPUT=''
      gt_restore_terminal
      gt_secret_generate "$key" || return 2
      gt_secret_show_generated "$key"; return $?
    fi
    (( status != 0 )) || gt_secret_valid_for "$key" "$SECRET_INPUT" || status=1
    if (( status == 0 )); then
      first=$SECRET_INPUT
      if [[ "$twice" == yes ]]; then
        printf '%s (%s): ' "$(gt_text 'Repeat password' 'Passwort wiederholen')" "$key" >&"$QUESTION_OUTPUT"
        gt_read_secret || status=$?
        printf '\n' >&"$QUESTION_OUTPUT"
        if (( status == 130 )); then gt_restore_terminal; return 130; fi
        [[ "$first" == "$SECRET_INPUT" ]] || status=1
      fi
      if (( status == 0 )); then
        SECRET[$key]=$first SECRET_STATUS[$key]=collected SECRET_INPUT=''
        gt_restore_terminal; return 0
      fi
    fi
    SECRET_INPUT=''
    gt_text 'Empty, invalid or mismatched input; try again. Ctrl-C cancels.' \
      'Leere, ungültige oder abweichende Eingabe; erneut eingeben. Strg-C bricht ab.' >&"$QUESTION_OUTPUT"
  done
}

gt_answers_file() {
  set +vx
  local file=$1 fd metadata opened contents line key value
  FILE_ANSWERS=()
  [[ -f "$file" && ! -L "$file" ]] || { gt_secret_error; return 2; }
  metadata=$(stat -c '%u:%a:%d:%i' -- "$file") || return 2
  [[ "$metadata" == 0:600:* ]] || { gt_secret_error; return 2; }
  exec {fd}<"$file" || return 2
  opened=$(stat -Lc '%u:%a:%d:%i' "/dev/fd/$fd")
  if [[ "$metadata" != "$opened" || -L "$file" ]] || IFS= read -r -d '' contents <&"$fd"; then
    exec {fd}<&-; gt_secret_error; return 2
  fi
  exec {fd}<&-
  # Every byte after the first '=' is literal data, including quotes and whitespace.
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ -n "$line" && "$line" != \#* ]] || continue
    key=${line%%=*}; value=${line#*=}
    if [[ "$line" != *=* || ! "$key" =~ ^[A-Z_]+$ || -z "${Q_TYPE[$key]:-}" || -n "${FILE_ANSWERS[$key]+present}" ||
        "$value" == *[$'\001'-$'\037'$'\177']* ]]; then
      FILE_ANSWERS=(); gt_secret_error; return 2
    fi
    FILE_ANSWERS[$key]=$value
  done <<< "$contents"
}

gt_file_questions() {
  local key
  ANSWER=()
  for key in "${QUESTIONS[@]}"; do
    gt_question_applies "$key" || continue
    [[ "${Q_TYPE[$key]}" != secret ]] || continue
    if [[ -n "${FILE_ANSWERS[$key]+present}" ]]; then ANSWER[$key]=${FILE_ANSWERS[$key]}
    else ANSWER[$key]=$(gt_default "$key"); fi
    if ! gt_validate_answer "$key" "${ANSWER[$key]}"; then
      printf '%s: %s\n' "$(gt_text 'Invalid/missing answer' 'Ungültige/fehlende Antwort')" "$key" >&2; return 2
    fi
    if [[ "$key" == WEBSERVER && ( "${REASON[web]:-}" == nginx || "${REASON[web]:-}" == apache2 ) &&
        "${ANSWER[$key]}" != "${REASON[web]}" ]]; then
      gt_secret_error; return 2
    fi
  done
  for key in "${!FILE_ANSWERS[@]}"; do
    gt_question_applies "$key" || { gt_secret_error; return 2; }
  done
}

# twice=yes marks a new password: entered twice, or generated on explicit request. back=no turns Back of the
# whiptail dialog into the abort question, for a credential that has no previous step.
gt_collect_secret() {
  local key=$1 twice=${2:-no} back=${3:-yes}
  if [[ -n "$ANSWERS_FILE" ]]; then
    gt_secret_valid_for "$key" "${FILE_ANSWERS[$key]:-}" || { gt_secret_error; return 2; }
    SECRET[$key]=${FILE_ANSWERS[$key]} SECRET_STATUS[$key]=collected
  elif [[ "$FRONTEND" == whiptail ]]; then gt_wt_secret "$key" "$twice" "$back"
  else gt_ask_secret "$key" "$twice"; fi
}

gt_db_options() {
  # The output is a pathname only. Escape MariaDB option-file syntax, never shell syntax.
  local key=$1 user=$2 file value=${SECRET[$1]:-}
  value=${value//\\/\\\\}; value=${value//\"/\\\"}
  file=$(umask 077; mktemp "$SCRATCH/db.XXXXXX") || return 2
  printf '[client]\nuser=%s\npassword="%s"\n' "$user" "$value" > "$file"
  printf '%s' "$file"
}

gt_prepare_root() {
  local file
  gt_question_applies DB_ROOT_PASSWORD || return 0
  if [[ "${FACT[database.vendor]}" == absent ]]; then
    gt_collect_secret DB_ROOT_PASSWORD yes no; return $?
  fi
  [[ "${FACT[database.vendor]}" == mariadb && "${FACT[database.active]}" == yes ]] || { gt_secret_error; return 2; }
  gt_collect_secret DB_ROOT_PASSWORD no no || return $?
  file=$(gt_db_options DB_ROOT_PASSWORD root) || return 2
  gt_database "$file"
  rm -f -- "$file"
  [[ "${FACT[database.query]}" == ok && "${FACT[database.gt_user]}" != unknown &&
    "${FACT[database.schemas]}" != unknown ]] || { gt_secret_error; return 2; }
  FACT[database.password_auth]=yes SECRET_STATUS[DB_ROOT_PASSWORD]=verified
  # Recompute database recommendations with authenticated facts before asking about reuse.
  ACTION=() REASON=() DISKS=() DISK_FREE=() DISK_NEED=()
  gt_compatibility
}

# Returns GT_BACK when Back leaves the first credential, so the caller can return to the questions.
gt_prepare_secrets() {
  set +vx
  local key twice file client value i=0 status
  local -a keys=()
  unset 'FACT[database.gt_auth]'
  [[ "${FACT[database.vendor]}" == absent || "${FACT[database.gt_user]}" == absent ||
    "${FACT[database.gt_user]}" == present ]] || { gt_secret_error; return 2; }
  for key in "${QUESTIONS[@]}"; do
    [[ "${Q_TYPE[$key]}" == secret && "$key" != DB_ROOT_PASSWORD ]] || continue
    gt_question_applies "$key" && keys+=("$key")
  done
  while (( i < ${#keys[@]} )); do
    key=${keys[i]} twice=no
    [[ "$key" != JASYPT_PASSWORD ]] || twice=yes
    [[ "$key" != DB_PASSWORD || "${FACT[database.gt_user]}" == present ]] || twice=yes
    status=0; gt_collect_secret "$key" "$twice" || status=$?
    case "$status" in
      0) i=$((i+1)) ;;
      1) (( i > 0 )) || return "$GT_BACK"; i=$((i-1)) ;;
      *) return "$status" ;;
    esac
  done
  if [[ "${FACT[database.gt_user]}" == present ]]; then
    [[ "${FACT[database.active]}" == yes ]] || { gt_secret_error; return 2; }
    client=$(command -v mariadb || command -v mysql) || { gt_secret_error; return 2; }
    file=$(gt_db_options DB_PASSWORD grafioschtrader) || return 2
    value=$(gt_probe "$client" "--defaults-file=$file" --protocol=TCP --host=127.0.0.1 --port=3306 \
      --user=grafioschtrader --batch --skip-column-names --connect-timeout=4 -e 'SELECT CURRENT_USER();') || value=''
    rm -f -- "$file"
    [[ "$value" == grafioschtrader@localhost ]] || { gt_secret_error; return 2; }
    FACT[database.gt_auth]=ok SECRET_STATUS[DB_PASSWORD]=verified
  fi
  if [[ -z "${SECRET[JWT_SECRET]:-}" ]]; then
    value=$(gt_probe openssl rand -base64 72) || { gt_secret_error; return 2; }
    value=${value//[$'/+=\n']/}; value=${value:0:48}
    [[ "$value" =~ ^[a-zA-Z0-9]{48}$ ]] || { gt_secret_error; return 2; }
    SECRET[JWT_SECRET]=$value SECRET_STATUS[JWT_SECRET]=generated
  fi
}

# A plan consists of display data, never executable command strings. Even blocked plans remain
# inspectable. Revalidate every answer here so future front ends cannot bypass the model.
