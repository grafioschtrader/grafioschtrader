gt_nginx_inventory() {
  local lan=$1
  python3 - "$ROOT" "$lan" "$SCRATCH" "${FACT[web.names]:-}" <<'PY'
# @python nginx-inventory.py
PY
}

gt_nginx_test() {
  # nginx returns zero for some ignored/conflicting server names; warnings block too.
  gt_core_run nginx -t > "$SCRATCH/nginx-test.log" 2>&1 &&
    ! grep -Eq '\[(warn|emerg|alert|crit|error)\]' "$SCRATCH/nginx-test.log"
}

gt_nginx_statuses() {
  local address port scheme name status
  while IFS=$'\t' read -r address port scheme name; do
    [[ -n "$address" ]] || continue
    [[ "$address" != *:* ]] || address="[$address]"
    # Certificate ownership/validity is outside this LAN stage; compare existing
    # HTTPS sites using their real Host/SNI without requiring a public CA chain.
    status=$(curl --disable --noproxy '*' --silent --show-error --insecure --max-time 5 \
      --resolve "$name:$port:$address" -o /dev/null -w '%{http_code}' "$scheme://$name:$port/") || return 2
    [[ "$status" =~ ^[1-5][0-9][0-9]$ ]] || return 2
    printf '%s\t%s\t%s\t%s\t%s\n' "$address" "$port" "$scheme" "$name" "$status"
  done < "$SCRATCH/web-endpoints"
}

gt_nginx_render() {
  local route target
  cat <<EOF
# Managed by gt-install; LAN HTTP. Update through a reviewed installer stage.
server {
    listen 80;
    server_name ${FACT[web.lan]};
    root ${ANSWER[DOCROOT]};
    index index.html;
    client_max_body_size 50m;
    gzip on;
    gzip_types text/plain text/css text/xml application/javascript application/json application/wasm application/xml image/svg+xml;
    location = / { return 302 /grafioschtrader/; }
    location = /grafioschtrader { return 301 /grafioschtrader/; }
    location /grafioschtrader/ {
        try_files \$uri \$uri/ /grafioschtrader/index.html;
    }
    location / { return 404; }
EOF
  for route in /api /m2m /socket/websocket /ws; do
    target=$route; [[ "$route" != /socket/websocket ]] || target=/socket
    cat <<EOF
    location $route {
        proxy_pass http://127.0.0.1:${ANSWER[BACKEND_PORT]}$target;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$remote_addr;
        proxy_set_header X-Forwarded-Proto \$scheme;
EOF
    if [[ "$route" == /ws || "$route" == /socket/websocket ]]; then
      cat <<'EOF'
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
EOF
    fi
    printf '    }\n'
  done
  printf '}\n'
}

gt_nginx_owned() {
  local target link digest
  target=$(gt_path /etc/nginx/sites-available/grafioschtrader)
  link=$(gt_path /etc/nginx/sites-enabled/grafioschtrader)
  gt_no_symlinks "$target" && gt_no_symlinks "${link%/*}" || return 2
  if [[ -e "$target" ]]; then
    digest=$(sha256sum "$target"); digest=${digest%% *}
    [[ -f "$target" && "$digest" == "${STATE[file.web_nginx]:-}" && "$(stat -c '%u:%a' "$target")" == 0:644 ]] || return 2
  fi
  if [[ -e "$link" || -L "$link" ]]; then
    [[ -L "$link" && "$(readlink "$link")" == "$target" && "${STATE[resource.web_link]:-}" == intent && -f "$target" ]] || return 2
  fi
}

gt_nginx_package_plan() {
  local transaction line
  transaction=$(LC_ALL=C apt-get --simulate --no-remove --no-upgrade install nginx 2>/dev/null) || return 2
  while IFS= read -r line; do
    [[ "$line" != 'Remv '* && ! "$line" =~ ^Inst\ [^\ ]+\ \[ ]] || return 2
  done <<< "$transaction"
  printf '%s\n' "$transaction" | sha256sum
}

gt_web_preflight() {
  local command address listeners line
  [[ "${STATE[step.app]:-}" == complete && "${ANSWER[WEBSERVER]:-}" == nginx && -z "${ANSWER[DOMAIN]:-}" ]] || {
    gt_core_error 'Complete --install-app first; --install-web currently requires nginx and LAN-only answers.'; return 2;
  }
  for command in nginx curl python3 ip ss systemctl; do
    [[ "$command" != nginx ]] || continue
    command -v "$command" >/dev/null || return 2
  done
  [[ "$(cat "$(gt_path /proc/1/comm)")" == systemd ]] || return 2
  gt_core_config_valid && gt_app_artifacts && gt_app_verify || return 2
  gt_validate_answer DOCROOT "${ANSWER[DOCROOT]}" && gt_validate_answer BACKEND_PORT "${ANSWER[BACKEND_PORT]}" || return 2
  address=$(gt_lan_address) || return 2
  [[ -z "${STATE[resource.web_lan]:-}" || "${STATE[resource.web_lan]}" == "$address" ]] || {
    gt_core_error 'LAN address changed; review the owned vhost before continuing.'; return 2;
  }
  FACT[web.lan]=$address
  gt_nginx_owned || { gt_core_error 'Foreign or changed nginx target/link.'; return 2; }
  listeners=$(ss -Htlnp) || return 2
  while IFS= read -r line; do
    [[ "$line" == *'"nginx"'* ]] || { gt_core_error 'Port 80 belongs to another process.'; return 2; }
  done < <(awk '$4 ~ /:80$/ {print}' <<< "$listeners")
  if command -v nginx >/dev/null; then
    # Use the distribution service/configuration contract, not a custom nginx prefix.
    nginx -V 2>&1 | grep -q -- '--conf-path=/etc/nginx/nginx.conf' || return 2
    gt_nginx_test || { gt_core_error 'nginx -t failed or warned; inspect nginx configuration.'; return 2; }
    FACT[web.snapshot]=$(gt_nginx_inventory "$address") || return 2
    gt_core_run systemctl is-active --quiet nginx.service || {
      gt_core_error 'Existing nginx must be running before shared-site comparisons.'; return 2;
    }
    gt_nginx_statuses > "$SCRATCH/web-before" || return 2
    FACT[web.package]=present
  else
    [[ ! -e "$(gt_path /etc/nginx)" ]] || return 2
    FACT[web.package]=$(gt_nginx_package_plan) || return 2
    FACT[web.snapshot]=absent
  fi
}

gt_web_verify() {
  local base=${1:-http://${FACT[web.lan]}} asset type
  local -a resolve=()
  [[ -z "${2:-}" ]] || resolve=(--resolve "$2")
  if [[ "${ANSWER[TLS_SOURCE]:-}" == proxy && "$base" == "http://${ANSWER[DOMAIN]}:"* ]]; then
    resolve+=(-H 'X-Forwarded-Proto: https')
  fi
  curl --disable --noproxy '*' -fsS --max-time 10 "${resolve[@]}" "$base/api/gtinfo" > "$SCRATCH/web-info" || return 2
  python3 -c '@python-inline web-health.py@' "$SCRATCH/web-info" || return 2
  curl --disable --noproxy '*' -fsS --max-time 10 "${resolve[@]}" "$base/grafioschtrader/" > "$SCRATCH/web-index" || return 2
  cmp -s "$SCRATCH/web-index" "$(gt_path "${ANSWER[DOCROOT]}/grafioschtrader/index.html")" || return 2
  asset=$(python3 - "$SCRATCH/web-index" <<'PY'
# @python frontend-index.py
PY
  ) || return 2
  type=$(curl --disable --noproxy '*' -fsS --max-time 10 "${resolve[@]}" -o "$SCRATCH/web-script" -w '%{content_type}' "$base$asset") || return 2
  [[ "$type" == application/javascript* || "$type" == text/javascript* ]] && [[ -s "$SCRATCH/web-script" ]] || return 2
  cmp -s "$SCRATCH/web-script" "$(gt_path "${ANSWER[DOCROOT]}$asset")" || return 2
  curl --disable --noproxy '*' -fsS --max-time 10 "${resolve[@]}" "$base/grafioschtrader/login" > "$SCRATCH/web-nested" || return 2
  cmp -s "$SCRATCH/web-index" "$SCRATCH/web-nested"
}

gt_web_rollback() {
  local link
  gt_nginx_owned || return 2
  link=$(gt_path /etc/nginx/sites-enabled/grafioschtrader)
  [[ ! -L "$link" ]] || rm -- "$link" || return 2
  gt_nginx_test && gt_core_run systemctl reload nginx.service || return 2
  gt_core_mark step.web failed
}

gt_web_activate() {
  local target link attempt baseline endpoints
  target=$(gt_path /etc/nginx/sites-available/grafioschtrader)
  link=$(gt_path /etc/nginx/sites-enabled/grafioschtrader)
  baseline=$(gt_path /var/lib/gt-install/web-before)
  endpoints=$(gt_path /var/lib/gt-install/web-endpoints)
  # Persist the original comparison before enabling the site. A restart must not
  # accept an already changed foreign-site response as its new baseline.
  gt_app_root_file web_endpoints "$SCRATCH/web-endpoints" "$endpoints" 600 || return 2
  if [[ ! -e "$baseline" ]]; then
    gt_app_root_file web_before "$SCRATCH/web-before" "$baseline" 600 || return 2
  else
    gt_app_root_file web_before "$baseline" "$baseline" 600 || return 2
    cp "$baseline" "$SCRATCH/web-before" || return 2
  fi
  gt_core_mark step.web running && gt_core_mark resource.web_lan "${FACT[web.lan]}" || return 2
  gt_nginx_render > "$SCRATCH/nginx-site"
  gt_app_root_file web_nginx "$SCRATCH/nginx-site" "$target" 644 || return 2
  gt_core_mark resource.web_link intent || return 2
  if [[ ! -L "$link" ]]; then ln -s "$target" "$link" || return 2; fi
  if gt_nginx_test && gt_core_run systemctl reload nginx.service; then
    # Reload is asynchronous: wait for new workers before testing the new host.
    for ((attempt=0; attempt<10; attempt++)); do
      if gt_web_verify; then
        if gt_nginx_statuses > "$SCRATCH/web-after" && cmp -s "$SCRATCH/web-before" "$SCRATCH/web-after"; then
          gt_core_run systemctl enable nginx.service && gt_core_mark step.web complete && return 0
        fi
        break
      fi
      sleep 1
    done
  fi
  gt_web_rollback || { gt_core_error 'nginx recovery failed; inspect nginx -t and the owned enablement link.'; return 2; }
  gt_core_error 'Web verification failed; own vhost disabled and previous configuration reloaded. Resume --install-web after diagnosis.'
  return 2
}

# ufw rules for the selected web routes, one ufw argument list per line. The LAN site always listens on 80, which
# Let's Encrypt's HTTP-01 also needs; SSH and every existing rule stay as they are.
gt_firewall_rules() {
  [[ "${ANSWER[FIREWALL_ALLOW]:-no}" == yes ]] || return 0
  printf 'allow 80/tcp\n'
  [[ -n "${ANSWER[DOMAIN]:-}" ]] || return 0
  case "${ANSWER[TLS_SOURCE]:-}" in
    letsencrypt|existing) printf 'allow 443/tcp\n' ;;
    proxy)
      if [[ -n "${ANSWER[TLS_PROXY_FROM]:-}" ]]; then
        printf 'allow from %s to any port %s proto tcp\n' "${ANSWER[TLS_PROXY_FROM]}" "${ANSWER[TLS_PROXY_LISTEN]}"
      else printf 'allow %s/tcp\n' "${ANSWER[TLS_PROXY_LISTEN]}"; fi ;;
  esac
}

gt_firewall_plan() {
  local rule
  [[ "${ANSWER[FIREWALL_ALLOW]:-no}" == yes ]] || return 0
  [[ "${FACT[firewall.ufw]:-}" == *'Status: active'* ]] ||
    gt_plan_block 'FIREWALL_ALLOW requires an active ufw.' 'FIREWALL_ALLOW benötigt ein aktives ufw.'
  while IFS= read -r rule; do
    gt_plan_row modify ufw "ufw $rule; SSH and existing rules unchanged" \
      "ufw $rule; SSH und bestehende Regeln unverändert"
  done < <(gt_firewall_rules)
}

gt_firewall_key() {
  local digest
  digest=$(printf '%s' "$1" | sha256sum) || return 2
  printf 'resource.ufw.%s\n' "${digest:0:16}"
}

# Rules already present before this installer are recorded as preexisting and never claimed. An interrupted run's
# intent becomes ownership once ufw lists the rule.
gt_web_firewall() {
  local rule key added
  local -a words
  [[ "${ANSWER[FIREWALL_ALLOW]:-no}" == yes ]] || return 0
  added=$(gt_core_run ufw show added) || { gt_core_error 'ufw rules cannot be listed.'; return 2; }
  while IFS= read -r rule; do
    key=$(gt_firewall_key "$rule") || return 2
    if grep -Fxq -- "ufw $rule" <<< "$added"; then
      case "${STATE[$key]:-}" in
        '') gt_core_mark "$key" "preexisting:$rule" || return 2 ;;
        intent:*) gt_core_mark "$key" "owned:$rule" || return 2 ;;
      esac
      continue
    fi
    [[ "${STATE[$key]:-intent:}" == intent:* ]] || { gt_core_error "ufw rule disappeared: $rule"; return 2; }
    gt_core_mark "$key" "intent:$rule" || return 2
    read -r -a words <<< "$rule"
    gt_core_run ufw "${words[@]}" < /dev/null > /dev/null || { gt_core_error "ufw $rule failed."; return 2; }
    gt_core_mark "$key" "owned:$rule" || return 2
  done < <(gt_firewall_rules)
  added=$(gt_core_run ufw show added) || return 2
  while IFS= read -r rule; do
    grep -Fxq -- "ufw $rule" <<< "$added" || { gt_core_error "ufw does not list: $rule"; return 2; }
  done < <(gt_firewall_rules)
  [[ "${STATE[step.firewall]:-}" == complete ]] || gt_core_mark step.firewall complete
}

gt_firewall_summary() {
  if [[ "${ANSWER[FIREWALL_ALLOW]:-no}" == yes ]]; then
    gt_text "ufw: $(gt_firewall_rules | paste -sd ';' -); SSH and existing rules unchanged." \
      "ufw: $(gt_firewall_rules | paste -sd ';' -); SSH und bestehende Regeln unverändert."
  else gt_text 'Firewall unchanged.' 'Firewall unverändert.'; fi
}

gt_install_web() {
  local state_dir before after reply package
  local completed_status
  completed_status=0
  gt_completed || completed_status=$?
  (( completed_status == 3 )) || return "$completed_status"
  state_dir=$(gt_path /var/lib/gt-install)
  gt_question_model
  gt_state_load && gt_secrets_load || return 2
  gt_stage_preflight || return 2
  gt_no_symlinks "$state_dir/lock" || return 2
  if [[ -z "$LOCK_FD" ]]; then exec {LOCK_FD}<"$state_dir/lock" || return 2; fi
  flock -n "$LOCK_FD" || return 2
  if [[ "${ANSWER[WEBSERVER]:-}" == none ]]; then gt_install_manual_web; return $?; fi
  if [[ "${ANSWER[WEBSERVER]:-}" == apache2 || -n "${ANSWER[DOMAIN]:-}" ]]; then
    gt_install_extended_web; return $?
  fi
  gt_web_preflight || { gt_core_error 'Web preflight failed; no web changes made.'; return 2; }
  before="$(sha256sum "$state_dir/state"):${FACT[web.snapshot]}:${FACT[web.package]}:${FACT[web.lan]}"
  gt_text 'LAN stage: install nginx if absent; add an owned HTTP vhost, verify frontend/API and existing sites, enable nginx at boot.' \
    'LAN-Stufe: nginx bei Bedarf installieren; eigenen HTTP-Vhost ergänzen, Frontend/API und bestehende Sites prüfen, nginx beim Boot aktivieren.'
  gt_firewall_summary
  printf 'URL: http://%s/grafioschtrader/\nDocument root: %s\n' "${FACT[web.lan]}" "${ANSWER[DOCROOT]}"
  if [[ "$CORE_CONFIRM" != yes ]]; then
    { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || return 2
    printf 'install-web: ' >&"$QUESTION_FD"
    IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == install-web ]] || return 130
  fi
  gt_web_preflight || return 2
  after="$(sha256sum "$state_dir/state"):${FACT[web.snapshot]}:${FACT[web.package]}:${FACT[web.lan]}"
  [[ "$before" == "$after" ]] || { gt_core_error 'Web inventory changed; review a fresh plan.'; return 2; }
  if [[ "${FACT[web.package]}" != present ]]; then
    package=$(gt_nginx_package_plan) || return 2
    [[ "$package" == "${FACT[web.package]}" ]] || return 2
    gt_core_mark resource.web_package intent || return 2
    gt_core_run env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade \
      -o DPkg::Lock::Timeout=600 install nginx || return 2
    gt_core_run systemctl start nginx.service || return 2
    gt_web_preflight || return 2
  fi
  gt_web_firewall || return 2
  if [[ "${STATE[step.web]:-}" == complete ]]; then
    [[ -L "$(gt_path /etc/nginx/sites-enabled/grafioschtrader)" ]] && gt_web_verify || return 2
    gt_core_run systemctl is-enabled --quiet nginx.service || return 2
  else gt_web_activate || return 2; fi
  gt_text 'LAN web access verified. Installation remains unfinished: mail verification and final hand-over are pending.' \
    'LAN-Webzugriff geprüft. Installation bleibt unvollständig: Mail-Prüfung und abschließende Übergabe stehen aus.'
  return 10
}
