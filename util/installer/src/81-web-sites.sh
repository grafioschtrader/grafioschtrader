gt_site_path() {
  local suffix='' extension=''
  [[ "$1" == lan ]] || suffix="-$1"
  [[ "${ANSWER[WEBSERVER]}" != apache2 ]] || extension=.conf
  gt_path "/etc/${ANSWER[WEBSERVER]}/sites-available/grafioschtrader$suffix$extension"
}

gt_site_owned() {
  local id target link digest
  for id in lan http domain; do
    target=$(gt_site_path "$id"); link=${target/sites-available/sites-enabled}
    gt_no_symlinks "$target" && gt_no_symlinks "${link%/*}" || return 2
    if [[ -e "$target" ]]; then
      digest=$(sha256sum "$target"); digest=${digest%% *}
      [[ -f "$target" && "$digest" == "${STATE[file.site_$id]:-}" && "$(stat -c '%u:%a' "$target")" == 0:644 ]] || return 2
    fi
    if [[ -e "$link" || -L "$link" ]]; then
      [[ -L "$link" && "$(readlink "$link")" == "$target" && "${STATE[resource.site_$id]:-}" == intent && -f "$target" ]] || return 2
    fi
  done
  if [[ "${ANSWER[WEBSERVER]}" == apache2 ]]; then
    target=$(gt_path /etc/apache2/conf-enabled/grafioschtrader-listen.conf)
    gt_no_symlinks "$target" || return 2
    if [[ -e "$target" ]]; then
      digest=$(sha256sum "$target"); digest=${digest%% *}
      [[ -f "$target" && "$digest" == "${STATE[file.apache_listen]:-}" && "$(stat -c '%u:%a' "$target")" == 0:644 ]] || return 2
    fi
  fi
}

gt_site_link() {
  local target link
  target=$(gt_site_path "$1"); link=${target/sites-available/sites-enabled}
  gt_site_owned || return 2
  if [[ "$2" == on ]]; then
    gt_core_mark "resource.site_$1" intent || return 2
    [[ -L "$link" ]] || ln -s "$target" "$link" || return 2
  elif [[ -L "$link" ]]; then rm -- "$link" || return 2; fi
  sync -f "${link%/*}"
}

gt_site_test() {
  if [[ "${ANSWER[WEBSERVER]}" == nginx ]]; then gt_nginx_test
  else gt_core_run apache2ctl configtest > "$SCRATCH/apache-test.log" 2>&1; fi
}

gt_apache_inventory() {
  gt_core_run apache2ctl -t -D DUMP_VHOSTS > "$SCRATCH/apache-vhosts" 2> "$SCRATCH/apache-dump.log" || return 2
  # Apache itself resolves conditional/includes. DUMP_INCLUDES provides the files
  # actually consumed, so the approval snapshot includes configurations outside /etc.
  gt_core_run apache2ctl -t -D DUMP_INCLUDES > "$SCRATCH/apache-includes" 2>> "$SCRATCH/apache-dump.log" || return 2
  python3 - "$SCRATCH" "${FACT[web.lan]}" "${FACT[web.names]:-}" <<'PY'
# @python apache-inventory.py
PY
}

gt_site_inventory() {
  if [[ "${ANSWER[WEBSERVER]}" == nginx ]]; then gt_nginx_inventory "${FACT[web.lan]}"
  else gt_apache_inventory; fi
}

gt_apache_routes() {
  local scheme=${1:-http} port=${ANSWER[BACKEND_PORT]} http=${ANSWER[BACKEND_HTTP_PORT]}
  cat <<EOF
    DocumentRoot ${ANSWER[DOCROOT]}
    ProxyRequests Off
    ProxyPreserveHost On
    ProxyAddHeaders On
    LimitRequestBody 52428800
    RequestHeader set X-Real-IP "expr=%{REMOTE_ADDR}"
EOF
  [[ "$scheme" == proxy ]] || printf '    RequestHeader set X-Forwarded-For "expr=%%{REMOTE_ADDR}"\n'
  [[ "$scheme" == proxy ]] || printf '    RequestHeader set X-Forwarded-Proto "%s"\n' "$scheme"
  cat <<EOF
    ProxyPass /socket/websocket ws://127.0.0.1:$http/socket
    ProxyPass /ws ws://127.0.0.1:$http/ws
    ProxyPass /api ajp://127.0.0.1:$port/api
    ProxyPass /m2m ajp://127.0.0.1:$port/m2m
    RedirectMatch 302 ^/$ /grafioschtrader/
    RedirectMatch 301 ^/grafioschtrader$ /grafioschtrader/
    <Directory ${ANSWER[DOCROOT]}>
        Options -Indexes
        AllowOverride None
        Require all denied
    </Directory>
    <Directory ${ANSWER[DOCROOT]}/grafioschtrader>
        Require all granted
        DirectoryIndex index.html
        FallbackResource /grafioschtrader/index.html
        AddOutputFilterByType DEFLATE text/plain text/html text/css text/xml application/javascript application/json application/wasm application/xml image/svg+xml
    </Directory>
EOF
}

# nginx variables and Apache regex backreferences are emitted literally.
# shellcheck disable=SC2016
gt_domain_render() {
  local phase=$1 names=${FACT[web.names]} name=${ANSWER[DOMAIN]} port=80 route target proto='$scheme'
  local cert=${FACT[tls.cert]:-} key=${FACT[tls.key]:-} web=${ANSWER[WEBSERVER]}
  if [[ "$phase" == proxy ]]; then port=${ANSWER[TLS_PROXY_LISTEN]}; proto='$http_x_forwarded_proto'; fi
  if [[ "$web" == nginx ]]; then
    if [[ "$phase" == tls ]]; then
      printf 'server { listen 80; server_name %s;\n' "$names"
      [[ "${ANSWER[DNS_FAMILY]}" == ipv4 ]] || printf '    listen [::]:80;\n'
      printf '    location /.well-known/acme-challenge/ { root /var/lib/gt-install-acme; }\n'
      printf '    location / { return 301 https://%s$request_uri; }\n}\n' "$name"
      port=443
    fi
    printf 'server {\n    listen %s%s;\n    server_name %s;\n' "$port" "$([[ "$phase" != tls ]] || printf ' ssl')" "$names"
    [[ "${ANSWER[DNS_FAMILY]:-ipv4}" == ipv4 || "$phase" == proxy ]] || printf '    listen [::]:%s%s;\n' "$port" "$([[ "$phase" != tls ]] || printf ' ssl')"
    [[ "$phase" != tls ]] || printf '    ssl_certificate %s;\n    ssl_certificate_key %s;\n    ssl_protocols TLSv1.2 TLSv1.3;\n' "$cert" "$key"
    if [[ "$phase" == proxy && -n "${ANSWER[TLS_PROXY_FROM]:-}" ]]; then
      printf '    allow 127.0.0.1;\n    allow %s;\n    deny all;\n' "${ANSWER[TLS_PROXY_FROM]}"
    fi
    printf '    root %s;\n    index index.html;\n    client_max_body_size 50m;\n    gzip on;\n' "${ANSWER[DOCROOT]}"
    printf '    gzip_types text/plain text/css text/xml application/javascript application/json application/wasm application/xml image/svg+xml;\n'
    cat <<'EOF'
    location /.well-known/acme-challenge/ { root /var/lib/gt-install-acme; }
    location = / { return 302 /grafioschtrader/; }
    location = /grafioschtrader { return 301 /grafioschtrader/; }
    location /grafioschtrader/ { try_files $uri $uri/ /grafioschtrader/index.html; }
    location / { return 404; }
EOF
    for route in /api /m2m /socket/websocket /ws; do
      target=$route; [[ "$route" != /socket/websocket ]] || target=/socket
      printf '    location %s {\n        proxy_pass http://127.0.0.1:%s%s;\n' "$route" "${ANSWER[BACKEND_PORT]}" "$target"
      printf '        proxy_set_header Host $host;\n        proxy_set_header X-Real-IP $remote_addr;\n'
      if [[ "$phase" == proxy ]]; then
        printf '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n'
      else
        printf '        proxy_set_header X-Forwarded-For $remote_addr;\n'
      fi
      printf '        proxy_set_header X-Forwarded-Proto %s;\n' "$proto"
      if [[ "$route" == /ws || "$route" == /socket/websocket ]]; then
        printf '        proxy_http_version 1.1;\n        proxy_set_header Upgrade $http_upgrade;\n        proxy_set_header Connection "upgrade";\n'
      fi
      printf '    }\n'
    done
    printf '}\n'
  else
    if [[ "$phase" == tls ]]; then
      printf '<VirtualHost *:80>\n    ServerName %s\n' "$name"
      for name in $names; do [[ "$name" == "${ANSWER[DOMAIN]}" ]] || printf '    ServerAlias %s\n' "$name"; done
      printf '    Alias /.well-known/acme-challenge/ /var/lib/gt-install-acme/.well-known/acme-challenge/\n'
      printf '    <Directory /var/lib/gt-install-acme>\n        Require all granted\n    </Directory>\n'
      printf '    RedirectMatch 301 ^/(?!\.well-known/acme-challenge/)(.*)$ https://%s/$1\n</VirtualHost>\n' "${ANSWER[DOMAIN]}"
      port=443
    fi
    printf '<VirtualHost *:%s>\n    ServerName %s\n' "$port" "${ANSWER[DOMAIN]}"
    for name in $names; do [[ "$name" == "${ANSWER[DOMAIN]}" ]] || printf '    ServerAlias %s\n' "$name"; done
    [[ "$phase" != tls ]] || printf '    SSLEngine on\n    SSLCertificateFile %s\n    SSLCertificateKeyFile %s\n    SSLProtocol -all +TLSv1.2 +TLSv1.3\n' "$cert" "$key"
    if [[ "$phase" == proxy && -n "${ANSWER[TLS_PROXY_FROM]:-}" ]]; then
      printf '    <Location />\n        Require ip 127.0.0.1 %s\n    </Location>\n' "${ANSWER[TLS_PROXY_FROM]}"
    fi
    printf '    Alias /.well-known/acme-challenge/ /var/lib/gt-install-acme/.well-known/acme-challenge/\n'
    printf '    <Directory /var/lib/gt-install-acme>\n        Require all granted\n    </Directory>\n'
    proto=http; [[ "$phase" != tls ]] || proto=https; [[ "$phase" != proxy ]] || proto=proxy
    gt_apache_routes "$proto"
    printf '</VirtualHost>\n'
  fi
}

gt_site_render_lan() {
  if [[ "${ANSWER[WEBSERVER]}" == nginx ]]; then gt_nginx_render
  else
    printf '<VirtualHost *:80>\n    ServerName %s\n' "${FACT[web.lan]}"
    gt_apache_routes http
    printf '</VirtualHost>\n'
  fi
}

gt_extended_packages() {
  local line transaction
  local -a packages=()
  read -r -a packages <<< "${FACT[web.packages]}"
  transaction=$(LC_ALL=C apt-get --simulate --no-remove --no-upgrade install "${packages[@]}" 2>/dev/null) || return 2
  while IFS= read -r line; do [[ "$line" != 'Remv '* && ! "$line" =~ ^Inst\ [^\ ]+\ \[ ]] || return 2; done <<< "$transaction"
  printf '%s\n' "$transaction" | sha256sum
}

gt_domain_plan() {
  local field
  PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=()
  gt_stage_preflight || return 2
  FACT[web.names]=${ANSWER[DOMAIN]:-}
  [[ -n "${ANSWER[DOMAIN]:-}" ]] || return 0
  for field in DOMAIN DNS_FAMILY TLS_SOURCE; do
    gt_validate_answer "$field" "${ANSWER[$field]:-}" || { gt_core_error "Invalid/missing answer: $field"; return 2; }
  done
  if [[ "${ANSWER[TLS_SOURCE]}" != proxy ]]; then
    gt_domain_network || { gt_core_error 'DNS check: network inventory failed.'; return 2; }
    gt_plan_dns
    FACT[web.names]=${FACT[plan.names]}
  fi
  if [[ -n "${STATE[resource.web_names]:-}" ]]; then
    [[ "${STATE[resource.web_names]}" == "${FACT[web.names]}" ]] || { gt_core_error 'DNS certificate-name set changed; restore the recorded DNS names before resuming.'; return 2; }
  fi
  case "${ANSWER[TLS_SOURCE]}" in
    existing)
      for field in TLS_CERT TLS_KEY; do
        gt_validate_answer "$field" "${ANSWER[$field]:-}" || { gt_core_error "Invalid/missing answer: $field"; return 2; }
      done
      FACT[tls.cert]=${ANSWER[TLS_CERT]} FACT[tls.key]=${ANSWER[TLS_KEY]}
      gt_plan_certificate ;;
    letsencrypt)
      gt_validate_answer LETSENCRYPT_EMAIL "${ANSWER[LETSENCRYPT_EMAIL]:-}" || {
        gt_core_error 'Invalid/missing answer: LETSENCRYPT_EMAIL'; return 2;
      }
      gt_certbot_plan ;;
    proxy)
      for field in TLS_PROXY_LISTEN TLS_PROXY_FROM; do
        gt_validate_answer "$field" "${ANSWER[$field]:-}" || { gt_core_error "Invalid/missing answer: $field"; return 2; }
      done
      ;;
  esac
  gt_report_rows "$(gt_text 'Domain/TLS checks' 'Domain-/TLS-Prüfungen')" "${PLAN[@]}"
  gt_report_rows "$(gt_text Warnings Hinweise)" "${PLAN_WARNINGS[@]}"
  gt_report_rows "$(gt_text Blockers Blocker)" "${PLAN_BLOCKERS[@]}"
  (( ${#PLAN_BLOCKERS[@]} == 0 ))
}

gt_domain_network() {
  local value interface
  value=$(gt_probe curl --disable -4 -fsS --connect-timeout 4 --max-time 8 https://ifconfig.co/ip) || value=unknown
  [[ "$value" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || value=unknown
  FACT[network.public_ipv4]=$value
  value=$(gt_probe ip -6 route show default) || value=''
  interface=$(awk '{for(i=1;i<NF;i++) if($i=="dev") {print $(i+1); exit}}' <<< "$value")
  FACT[network.global_ipv6]=unknown
  if [[ "$interface" =~ ^[a-zA-Z0-9_.:-]+$ ]]; then
    value=$(gt_probe ip -6 addr show dev "$interface" scope global -temporary -deprecated) || return 2
    FACT[network.global_ipv6]=$(awk '$1=="inet6" && $2!~/^(f[cd]|fe[89ab])/ {sub(/\/.*/,"",$2); print $2}' <<< "$value")
  fi
}

gt_apache_modules() {
  local phase=$1 file before after
  local -a modules=()
  mapfile -t modules < <(gt_apache_module_names "$phase")
  (( ${#modules[@]} )) || return 2
  before=$(gt_path "/var/lib/gt-install/apache-$phase-before")
  after=$(gt_path "/var/lib/gt-install/apache-$phase-after")
  gt_no_symlinks "$(gt_path /etc/apache2/mods-enabled)" || return 2
  find "$(gt_path /etc/apache2/mods-enabled)" -maxdepth 1 -type l -printf '%f\t%l\n' | LC_ALL=C sort > "$SCRATCH/modules"
  if [[ ! -e "$before" ]]; then gt_app_root_file "modules_${phase}_before" "$SCRATCH/modules" "$before" 600 || return 2
  else gt_app_root_file "modules_${phase}_before" "$before" "$before" 600 || return 2; fi
  for file in "${modules[@]}"; do gt_core_run a2enmod "$file" >/dev/null || return 2; done
  find "$(gt_path /etc/apache2/mods-enabled)" -maxdepth 1 -type l -printf '%f\t%l\n' | LC_ALL=C sort > "$SCRATCH/modules"
  gt_app_root_file "modules_${phase}_after" "$SCRATCH/modules" "$after" 600
}

gt_apache_modules_restore() {
  local phase=$1 before after
  before=$(gt_path "/var/lib/gt-install/apache-$phase-before")
  after=$(gt_path "/var/lib/gt-install/apache-$phase-after")
  gt_app_root_file "modules_${phase}_before" "$before" "$before" 600 &&
    gt_app_root_file "modules_${phase}_after" "$after" "$after" 600 || return 2
  python3 - "$(gt_path /etc/apache2/mods-enabled)" "$before" "$after" <<'PY'
# @python apache-modules-restore.py
PY
}

gt_extended_preflight() {
  local command address line ports='80' web=${ANSWER[WEBSERVER]} executable
  executable=$web
  [[ "${STATE[step.app]:-}" == complete && ( "$web" == nginx || "$web" == apache2 ) ]] || return 2
  [[ "$(cat "$(gt_path /proc/1/comm)")" == systemd ]] || return 2
  for command in curl python3 ip ss systemctl openssl; do command -v "$command" >/dev/null || return 2; done
  gt_core_config_valid && gt_app_artifacts && gt_app_verify || return 2
  gt_validate_answer DOCROOT "${ANSWER[DOCROOT]}" && gt_validate_answer BACKEND_PORT "${ANSWER[BACKEND_PORT]}" || return 2
  [[ "$web" != apache2 ]] || gt_validate_answer BACKEND_HTTP_PORT "${ANSWER[BACKEND_HTTP_PORT]:-}" || return 2
  address=$(ip -4 route get 1.1.1.1) || return 2
  address=$(awk '{for(i=1;i<NF;i++) if($i=="src") {print $(i+1); exit}}' <<< "$address")
  [[ "$address" == *.* && "$address" != 127.* && "$address" != 0.* ]] && gt_valid_address "$address" || return 2
  FACT[web.lan]=$address
  [[ -z "${STATE[resource.web_lan]:-}" || "${STATE[resource.web_lan]}" == "$address" ]] || return 2
  gt_domain_plan && gt_site_owned || return 2
  if [[ -n "${ANSWER[DOMAIN]:-}" ]]; then
    if [[ "${ANSWER[TLS_SOURCE]}" == proxy ]]; then ports+=" ${ANSWER[TLS_PROXY_LISTEN]}"; else ports+=' 443'; fi
  fi
  address=$(ss -Htlnp) || return 2
  while IFS= read -r line; do [[ "$line" == *"\"$web\""* ]] || { gt_core_error 'A required web port belongs to another process.'; return 2; }; done < <(
    awk -v ports="$ports" 'BEGIN {split(ports,p," ")} {for(i in p) if($4 ~ (":" p[i] "$")) print}' <<< "$address")
  [[ "$web" != apache2 ]] || executable=apache2ctl
  FACT[web.packages]=$(gt_web_package_names | paste -sd ' ')
  FACT[web.transaction]=$(gt_extended_packages) || return 2
  if command -v "$executable" >/dev/null; then
    gt_site_test || return 2
    FACT[web.snapshot]=$(gt_site_inventory) || return 2
    gt_core_run systemctl is-active --quiet "$web.service" || { gt_core_error 'Existing web server must be running for shared-site checks.'; return 2; }
    gt_nginx_statuses > "$SCRATCH/web-before" || return 2
  else
    [[ ! -e "$(gt_path "/etc/$web")" ]] || return 2
    FACT[web.snapshot]=absent
  fi
}

# Save shared-site evidence once per installation, before any link is enabled.
gt_site_baseline() {
  local file target
  for file in web-endpoints web-before; do
    target=$(gt_path "/var/lib/gt-install/extended-$file")
    if [[ -e "$target" ]]; then
      gt_app_root_file "extended_$file" "$target" "$target" 600 || return 2
      [[ "$file" != web-endpoints ]] || cmp -s "$target" "$SCRATCH/$file" || return 2
      cp "$target" "$SCRATCH/$file" || return 2
    else gt_app_root_file "extended_$file" "$SCRATCH/$file" "$target" 600 || return 2; fi
  done
}

gt_site_compare() {
  local snapshot
  cp "$SCRATCH/web-endpoints" "$SCRATCH/expected-endpoints" || return 2
  snapshot=$(gt_site_inventory) || return 2
  [[ -n "$snapshot" ]] && cmp -s "$SCRATCH/expected-endpoints" "$SCRATCH/web-endpoints" || return 2
  gt_nginx_statuses > "$SCRATCH/web-after" && cmp -s "$SCRATCH/web-before" "$SCRATCH/web-after"
}

gt_site_activate() {
  local id=$1 base=$2 host_resolution=${3:-} target link attempt old_http=no
  target=$(gt_site_path "$id")
  gt_app_root_file "site_$id" "$SCRATCH/site-$id" "$target" 644 || return 2
  gt_core_mark "step.site_$id" running || return 2
  if [[ "$id" == domain ]]; then
    link=$(gt_site_path http); link=${link/sites-available/sites-enabled}
    [[ ! -L "$link" ]] || old_http=yes
    # Persist the restoration choice before removing the HTTP bootstrap link.
    [[ -n "${STATE[resource.domain_previous_http]:-}" ]] || gt_core_mark resource.domain_previous_http "$old_http" || return 2
    gt_site_link http off || return 2
  fi
  gt_site_link "$id" on || return 2
  if gt_site_test && gt_core_run systemctl reload "${ANSWER[WEBSERVER]}.service"; then
    for ((attempt=0; attempt<10; attempt++)); do
      if gt_web_verify "$base" "$host_resolution"; then
        if gt_site_compare; then gt_core_mark "step.site_$id" complete; return $?; fi
        break
      fi
      sleep 1
    done
  fi
  gt_site_link "$id" off || return 2
  if [[ "$id" == domain && "${STATE[resource.domain_previous_http]:-}" == yes ]]; then gt_site_link http on || return 2; fi
  if ! gt_site_test || ! gt_core_run systemctl reload "${ANSWER[WEBSERVER]}.service"; then
    gt_core_error 'Web rollback reload failed; inspect the web server log.'; return 2
  fi
  gt_core_mark "step.site_$id" failed || return 2
  gt_core_error "Site $id failed verification; previous site links restored. Resume --install-web."
  return 2
}

gt_tls_certbot() { gt_core_run certbot "$@"; }

gt_tls_issue() {
  local path name cert_name="gt-install-${STATE[run_id]}" log
  local -a names=()
  if [[ "${FACT[tls.reuse]:-no}" == yes ]]; then
    gt_core_mark step.tls reused
    return $?
  fi
  for name in ${FACT[web.names]}; do names+=(-d "$name"); done
  path=$(gt_path /var/lib/gt-install-acme)
  gt_no_symlinks "$path" || return 2
  if [[ -e "$path" ]]; then [[ "${STATE[resource.acme_root]:-}" == intent && "$(stat -c '%u:%a' "$path")" == 0:755 ]] || return 2
  else
    gt_core_mark resource.acme_root intent || return 2
    install -d -o root -g root -m 755 "$path" || return 2
  fi
  if [[ -e "/etc/letsencrypt/renewal/$cert_name.conf" && "${STATE[resource.acme_certificate]:-}" != intent ]]; then return 2; fi
  gt_core_mark resource.acme_certificate intent || return 2
  log=$(gt_path /var/lib/gt-install/tls.log)
  gt_no_symlinks "$log" || return 2
  (umask 077; touch "$log") && chmod 600 "$log" || return 2
  # Webroot HTTP-01 keeps certbot from rewriting any owned or foreign vhost.
  gt_tls_certbot certonly --non-interactive --agree-tos --email "${ANSWER[LETSENCRYPT_EMAIL]}" \
    --webroot -w "$path" --cert-name "$cert_name" "${names[@]}" --keep-until-expiring \
    --deploy-hook "systemctl reload ${ANSWER[WEBSERVER]}.service" >> "$log" 2>&1 || {
    gt_core_mark step.tls failed || return 2
    gt_core_error "Certificate request failed; HTTP remains available. See $log and rerun --install-web after DNS/router repair."; return 2;
  }
  local saved_cert=${ANSWER[TLS_CERT]:-} saved_key=${ANSWER[TLS_KEY]:-}
  ANSWER[TLS_CERT]=${FACT[tls.cert]} ANSWER[TLS_KEY]=${FACT[tls.key]}
  PLAN_BLOCKERS=(); gt_plan_certificate certbot
  ANSWER[TLS_CERT]=$saved_cert ANSWER[TLS_KEY]=$saved_key
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
  gt_core_mark step.tls issued
}

gt_tls_reuse_hook() {
  local target
  [[ "${FACT[tls.reuse]:-no}" == yes ]] || return 0
  target=$(gt_path "/etc/letsencrypt/renewal-hooks/deploy/gt-install-${STATE[run_id]}")
  gt_no_symlinks "${target%/*}" || return 2
  [[ -d "${target%/*}" ]] || install -d -o root -g root -m 755 "${target%/*}" || return 2
  {
    printf '#!/bin/sh\n'
    # shellcheck disable=SC2016
    printf '[ "${RENEWED_LINEAGE:-}" = "/etc/letsencrypt/live/%s" ] || exit 0\n' "${FACT[tls.lineage]}"
    printf 'systemctl reload %s.service\n' "${ANSWER[WEBSERVER]}"
  } > "$SCRATCH/certbot-hook"
  gt_app_root_file certbot_hook "$SCRATCH/certbot-hook" "$target" 755
}

gt_tls_snapshot() {
  printf '%s:%s:%s' "${FACT[tls.selection]:-}" "${FACT[tls.lineage]:-}" "${FACT[tls.renewal_hash]:-}"
}

gt_tls_renew() {
  local log digest
  log=$(gt_path /var/lib/gt-install/tls.log)
  gt_no_symlinks "$log" || return 2
  (umask 077; touch "$log") && chmod 600 "$log" || return 2
  gt_tls_certbot renew --cert-name "${FACT[tls.lineage]}" --dry-run --non-interactive \
    --no-random-sleep-on-renew >> "$log" 2>&1 || return 2
  if [[ "${FACT[tls.reuse]:-no}" == yes ]]; then
    digest=$(sha256sum "$(gt_path "/etc/letsencrypt/renewal/${FACT[tls.lineage]}.conf")") || return 2
    [[ "${digest%% *}" == "${FACT[tls.renewal_hash]}" ]] || {
      gt_core_error 'Certbot renewal configuration changed during verification; inspect it before resuming.'; return 2;
    }
  fi
  gt_core_mark step.tls_renewal complete
}

gt_tls_verify() {
  local name served expected
  for name in ${FACT[web.names]}; do
    gt_web_verify "https://$name" "$name:443:127.0.0.1" || return 2
    served=$(timeout 10 openssl s_client -connect 127.0.0.1:443 -servername "$name" </dev/null 2>/dev/null |
      openssl x509 -noout -fingerprint -sha256) || return 2
    expected=$(openssl x509 -in "${FACT[tls.cert]}" -noout -fingerprint -sha256) || return 2
    [[ "$served" == "$expected" ]] || return 2
  done
}

gt_install_extended_web() {
  local before after reply id port name web=${ANSWER[WEBSERVER]} package missing=no link
  local -a packages=()
  gt_stage_preflight || return 2
  [[ "${STATE[step.app]:-}" == complete ]] && gt_core_config_valid && gt_app_artifacts && gt_app_verify || return 2
  gt_prepare_dns || return $?
  gt_extended_preflight || { gt_core_error 'Domain/Apache preflight failed; no web changes made.'; return 2; }
  PLAN=() PLAN_PACKAGES=()
  gt_packages
  [[ "${FACT[packages]}" == known ]] || { gt_core_error 'Web package inventory failed.'; return 2; }
  gt_plan_web_runtime
  gt_plan_packages
  gt_report_rows "$(gt_text 'Web packages and modules' 'Web-Pakete und Module')" "${PLAN[@]}"
  gt_report_rows "$(gt_text Blockers Blocker)" "${PLAN_BLOCKERS[@]}"
  (( ${#PLAN_BLOCKERS[@]} == 0 )) || return 2
  before="${FACT[web.snapshot]}:${FACT[web.transaction]}:${FACT[web.names]}:${FACT[web.lan]}:$(gt_tls_snapshot):$(sha256sum "$(gt_path /var/lib/gt-install/state)")"
  printf 'Web: %s; LAN: http://%s/grafioschtrader/; domain: %s; TLS: %s\n' "$web" "${FACT[web.lan]}" "${ANSWER[DOMAIN]:-none}" "${ANSWER[TLS_SOURCE]:-none}"
  gt_text 'Install required web packages, own sites and Apache modules; verify routes and shared sites. Certbot uses HTTP-01 and a scoped renewal test. Firewall unchanged.' \
    'Benötigte Web-Pakete, eigene Sites und Apache-Module installieren; Routen und bestehende Sites prüfen. Certbot nutzt HTTP-01 und einen begrenzten Erneuerungstest. Firewall unverändert.'
  if [[ "${ANSWER[TLS_SOURCE]:-}" == letsencrypt && "${FACT[tls.reuse]:-no}" != yes ]]; then
    gt_text "Confirmation also accepts the Let's Encrypt terms: https://letsencrypt.org/repository/" \
      "Die Bestätigung akzeptiert auch die Bedingungen von Let's Encrypt: https://letsencrypt.org/repository/"
  fi
  if [[ "$CORE_CONFIRM" != yes ]]; then
    { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
    printf 'install-web: ' >&"$QUESTION_FD"
    IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == install-web ]] || return 130
  fi
  gt_extended_preflight || return 2
  after="${FACT[web.snapshot]}:${FACT[web.transaction]}:${FACT[web.names]}:${FACT[web.lan]}:$(gt_tls_snapshot):$(sha256sum "$(gt_path /var/lib/gt-install/state)")"
  [[ "$before" == "$after" ]] || { gt_core_error 'Web plan changed; run again.'; return 2; }
  gt_core_mark resource.web_lan "${FACT[web.lan]}" && gt_core_mark resource.web_names "${FACT[web.names]}" || return 2
  if [[ "${ANSWER[TLS_SOURCE]:-}" == letsencrypt ]]; then
    gt_core_mark resource.certbot_selection "${FACT[tls.selection]}" || return 2
    if [[ "${FACT[tls.reuse]}" == yes ]]; then
      gt_core_mark resource.certbot_renewal_hash "${FACT[tls.renewal_hash]}" || return 2
    fi
  fi
  read -r -a packages <<< "${FACT[web.packages]}"
  for package in "${packages[@]}"; do
    [[ "$(dpkg-query -W -f='${db:Status-Status}' "$package" 2>/dev/null)" == installed ]] || missing=yes
  done
  if [[ "$missing" == yes ]]; then
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install "${packages[@]}" || return 2
  fi
  gt_core_run systemctl start "$web.service" || return 2
  gt_site_test && gt_site_inventory > "$SCRATCH/web-inventory" && gt_nginx_statuses > "$SCRATCH/web-before" && gt_site_baseline || return 2
  if [[ "$web" == apache2 ]]; then
    gt_apache_default_disable || return 2
    if [[ "${STATE[step.site_lan]:-}" != complete ]]; then
      gt_apache_modules lan || return 2
    fi
    if [[ "${ANSWER[TLS_SOURCE]:-}" == proxy ]]; then
      # Additional Listen is installer-owned; ports.conf stays untouched.
      printf 'Listen %s\n' "${ANSWER[TLS_PROXY_LISTEN]}" > "$SCRATCH/apache-listen"
      gt_app_root_file apache_listen "$SCRATCH/apache-listen" "$(gt_path /etc/apache2/conf-enabled/grafioschtrader-listen.conf)" 644 || return 2
    fi
  fi
  gt_site_render_lan > "$SCRATCH/site-lan"
  if [[ "${STATE[step.site_lan]:-}" != complete ]]; then
    if ! gt_site_activate lan "http://${FACT[web.lan]}"; then
      if [[ "$web" == apache2 ]]; then
        gt_apache_default_restore && gt_apache_modules_restore lan && gt_site_test && gt_core_run systemctl reload apache2.service || return 2
      fi
      return 2
    fi
  else
    link=$(gt_site_path lan); [[ -L "${link/sites-available/sites-enabled}" ]] && gt_web_verify || return 2
  fi
  if [[ -n "${ANSWER[DOMAIN]:-}" ]]; then
    if [[ "${ANSWER[TLS_SOURCE]}" == letsencrypt && "${STATE[step.site_domain]:-}" != complete ]]; then
      if [[ "${FACT[tls.reuse]:-no}" != yes && -z "${STATE[file.site_domain]:-}" ]]; then
        gt_domain_render http > "$SCRATCH/site-http"
        gt_site_activate http "http://${ANSWER[DOMAIN]}" "${ANSWER[DOMAIN]}:80:127.0.0.1" || return 2
      fi
      gt_tls_issue || return 2
    fi
    id=tls; port=443
    if [[ "${ANSWER[TLS_SOURCE]}" == proxy ]]; then id=proxy; port=${ANSWER[TLS_PROXY_LISTEN]}; fi
    gt_domain_render "$id" > "$SCRATCH/site-domain"
    name="https://${ANSWER[DOMAIN]}"; [[ "$id" != proxy ]] || name="http://${ANSWER[DOMAIN]}:$port"
    if [[ "${STATE[step.site_domain]:-}" != complete ]]; then
      if [[ "$web" == apache2 && "$id" == tls ]]; then gt_apache_modules tls || return 2; fi
      if ! gt_site_activate domain "$name" "${ANSWER[DOMAIN]}:$port:127.0.0.1"; then
        if [[ "$web" == apache2 && "$id" == tls ]]; then gt_apache_modules_restore tls && gt_site_test && gt_core_run systemctl reload apache2.service || return 2; fi
        return 2
      fi
    else
      link=$(gt_site_path domain)
      [[ -L "${link/sites-available/sites-enabled}" ]] && gt_web_verify "$name" "${ANSWER[DOMAIN]}:$port:127.0.0.1" || return 2
    fi
    if [[ "$id" == tls ]]; then
      gt_tls_verify || return 2
      if [[ "${ANSWER[TLS_SOURCE]}" == letsencrypt ]]; then
        if [[ "${FACT[tls.reuse]:-no}" == yes ]]; then
          gt_tls_reuse_hook || return 2
        else gt_core_run systemctl enable --now certbot.timer || return 2; fi
        if [[ "${STATE[step.tls_renewal]:-}" != complete ]]; then
          gt_tls_renew || return 2
        fi
      fi
      gt_core_mark step.tls complete || return 2
    elif gt_web_verify "https://${ANSWER[DOMAIN]}"; then gt_core_mark step.tls complete || return 2
    else
      gt_core_mark step.tls unverified || return 2
      gt_text 'External proxy HTTPS not verified from this host; verify from outside the LAN.' 'HTTPS am vorgeschalteten Proxy hier nicht verifiziert; von außerhalb des LAN prüfen.'
    fi
  else gt_core_mark step.tls skipped || return 2; fi
  gt_web_verify && gt_site_compare && gt_core_run systemctl enable "$web.service" && gt_core_mark step.web complete || return 2
  gt_text 'Web routes verified. Run --check-mail for the mail milestone; final hand-over remains pending.' 'Web-Routen geprüft. --check-mail führt die Mail-Prüfung aus; abschließende Übergabe bleibt offen.'
  return 10
}

gt_apache_default_disable() {
  local link target
  [[ -e "$SCRATCH/apache-stock-default" ]] || return 0
  link=$(gt_path /etc/apache2/sites-enabled/000-default.conf)
  [[ -L "$link" ]] || return 2
  target=$(readlink "$link") || return 2
  [[ "$target" == ../sites-available/000-default.conf || "$target" == /etc/apache2/sites-available/000-default.conf ]] || return 2
  gt_core_mark resource.apache_default "$target" || return 2
  rm -- "$link" && sync -f "${link%/*}"
}

gt_apache_default_restore() {
  local link target=${STATE[resource.apache_default]:-}
  [[ -n "$target" ]] || return 0
  [[ "$target" == ../sites-available/000-default.conf || "$target" == /etc/apache2/sites-available/000-default.conf ]] || return 2
  link=$(gt_path /etc/apache2/sites-enabled/000-default.conf)
  gt_no_symlinks "${link%/*}" || return 2
  [[ ! -e "$link" && ! -L "$link" ]] || return 2
  ln -s "$target" "$link" && sync -f "${link%/*}"
}
