#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/etc/nginx/sites-available" "$ROOT/etc/nginx/sites-enabled" "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  touch "$ROOT/var/lib/gt-install/lock"
  ANSWER[WEBSERVER]=nginx ANSWER[DOMAIN]=gt.test ANSWER[DOCROOT]=/var/www/gt ANSWER[BACKEND_PORT]=9091
  FACT[web.lan]=192.0.2.20 FACT[web.names]='gt.test www.gt.test'
  STATE[run_id]=00000000000000000000000000000001
}

@test "domain name collision inside nginx include blocks site ownership" {
  echo 'events {} http { include /etc/nginx/sites-enabled/*; }' > "$ROOT/etc/nginx/nginx.conf"
  echo 'server_name www.gt.test;' > "$ROOT/etc/nginx/names.conf"
  echo 'server { listen 80 default_server; include /etc/nginx/names.conf; }' > "$ROOT/etc/nginx/sites-enabled/foreign"
  run gt_site_inventory
  [ "$status" -eq 2 ]
  [[ "$output" == *'already belongs'* ]]
}

@test "edge sites replace forged forwarding headers while proxy sites retain the chain" {
  ANSWER[BACKEND_HTTP_PORT]=8080 ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_PROXY_LISTEN]=8081
  run gt_nginx_render
  [[ "$output" == *'X-Forwarded-For $remote_addr;'* ]]
  [[ "$output" != *proxy_add_x_forwarded_for* ]]
  for phase in http tls; do
    run gt_domain_render "$phase"
    [[ "$output" == *'X-Forwarded-For $remote_addr;'* ]]
    [[ "$output" != *proxy_add_x_forwarded_for* ]]
    run gt_apache_routes "$phase"
    [[ "$output" == *'RequestHeader set X-Forwarded-For "expr=%{REMOTE_ADDR}"'* ]]
  done
  run gt_domain_render proxy
  [[ "$output" == *proxy_add_x_forwarded_for* ]]
  run gt_apache_routes proxy
  [[ "$output" != *'RequestHeader set X-Forwarded-For'* ]]
}

@test "LAN-only inventory does not adopt foreign domain-named site files" {
  FACT[web.names]=''
  echo 'events {} http { include /etc/nginx/sites-enabled/*; }' > "$ROOT/etc/nginx/nginx.conf"
  echo 'server { listen 80 default_server; server_name foreign.example; }' > "$ROOT/etc/nginx/sites-enabled/grafioschtrader-domain"
  gt_site_inventory
  grep -q $'http\tforeign.example' "$SCRATCH/web-endpoints"
}

@test "foreign domain file and Apache Listen file block before activation" {
  echo foreign > "$(gt_site_path domain)"
  run gt_site_owned
  [ "$status" -eq 2 ]
  ANSWER[WEBSERVER]=apache2
  mkdir -p "$ROOT/etc/apache2/conf-enabled"
  echo 'Listen 8081' > "$ROOT/etc/apache2/conf-enabled/grafioschtrader-listen.conf"
  run gt_site_owned
  [ "$status" -eq 2 ]
}

@test "proxy ports cannot overlap LAN HTTPS or either backend connector" {
  ANSWER[TLS_SOURCE]=proxy ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_PROXY_FROM]=''
  ANSWER[BACKEND_HTTP_PORT]=8080
  local port
  for port in 80 443 9091 8080; do
    ANSWER[TLS_PROXY_LISTEN]=$port
    run gt_domain_plan
    [ "$status" -ne 0 ]
  done
  ANSWER[TLS_PROXY_LISTEN]=8081
  gt_domain_plan
}

@test "DNS certificate names cannot silently change on recovery" {
  ANSWER[TLS_SOURCE]=proxy ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_PROXY_FROM]='' ANSWER[TLS_PROXY_LISTEN]=8081
  STATE[resource.web_names]='gt.test www.gt.test'
  run gt_domain_plan
  [ "$status" -ne 0 ]
  [[ "$output" == *'name set changed'* ]]
}

@test "failed ACME issuance keeps HTTP and records resumable failure" {
  [ "$EUID" -eq 0 ] || skip 'Root ownership requires root'
  ANSWER[LETSENCRYPT_EMAIL]=admin@example.test
  gt_tls_certbot() { printf '%s\n' "$@" > "$SCRATCH/certbot-args"; return 1; }
  if gt_tls_issue; then false; else [ "$?" -eq 2 ]; fi
  [ "${STATE[step.tls]}" = failed ]
  [ "${STATE[resource.acme_certificate]}" = intent ]
  [ "$(stat -c %a "$ROOT/var/lib/gt-install/tls.log")" = 600 ]
  grep -qx -- --webroot "$SCRATCH/certbot-args"
  grep -qx www.gt.test "$SCRATCH/certbot-args"
  ! grep -q -- --nginx "$SCRATCH/certbot-args"
  grep -qx 'systemctl reload nginx.service' "$SCRATCH/certbot-args"
  ! grep -q '/usr/bin/systemctl' "$SCRATCH/certbot-args"
}

@test "Apache module execution uses the same LAN and TLS selections as the plan" {
  mkdir -p "$ROOT/etc/apache2/mods-enabled"
  gt_app_root_file() { :; }
  gt_core_run() { printf '%s\n' "$*" >> "$SCRATCH/module-commands"; }
  gt_apache_modules lan
  gt_apache_modules tls
  local module
  for module in proxy proxy_ajp proxy_http proxy_wstunnel headers alias dir mime authz_core deflate filter ssl; do
    grep -qx "a2enmod $module" "$SCRATCH/module-commands"
  done
  [ "$(wc -l < "$SCRATCH/module-commands")" -eq 12 ]
  run gt_apache_modules invalid
  [ "$status" -eq 2 ]
}

mail_ready() {
  CORE_CONFIRM=yes STATE[step.app]=complete
  ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_HOST]=mail.example.test ANSWER[SMTP_PORT]=587
  ANSWER[SMTP_USER]=sender@example.test ANSWER[ADMIN_EMAIL]=admin@example.test
  ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_TEST]=yes
  SECRET[SMTP_PASSWORD]='fixture'
  gt_state_load() { :; }
  gt_secrets_load() { :; }
  gt_core_config_valid() { :; }
  gt_app_artifacts() { :; }
  gt_app_verify() { :; }
  gt_core_mark() { STATE[$1]=$2; }
  gt_mail_probe() { echo 'unexpected SMTP connection'; return 99; }
}

@test "mail never connects before application success or with plaintext authentication" {
  mail_ready
  STATE[step.app]=running
  run gt_check_mail
  [ "$status" -eq 2 ]
  [[ "$output" != *'unexpected SMTP'* ]]
  STATE[step.app]=complete ANSWER[SMTP_SECURITY]=none
  run gt_check_mail
  [ "$status" -eq 2 ]
  [[ "$output" != *'unexpected SMTP'* ]]
}

@test "persisted mail acceptance recovers without duplicate even before step completion" {
  mail_ready
  STATE[step.mail]=intent STATE[resource.mail_delivery]=accepted STATE[resource.mail_configuration]=application-v1
  if gt_check_mail; then false; else [ "$?" -eq 10 ]; fi
  [ "${STATE[step.mail]}" = complete ]
}

@test "old mail milestones require application configuration verification without another message" {
  mail_ready
  STATE[step.mail]=complete STATE[resource.mail_delivery]=accepted
  echo accepted > "$SCRATCH/mail-result"
  gt_mail_probe() { [[ "$1" == no ]] || return 99; return 2; }
  run gt_check_mail
  [ "$status" -eq 2 ]
  gt_mail_probe() { [[ "$1" == no ]] || return 99; echo connected > "$SCRATCH/mail-result"; }
  if gt_check_mail; then false; else [ "$?" -eq 10 ]; fi
  [ "${STATE[resource.mail_configuration]}" = application-v1 ]
  [ "${STATE[resource.mail_delivery]}" = accepted ]
}

@test "mail cannot connect when application configuration resolution fails or disagrees with answers" {
  ANSWER[SMTP_TEST]=yes
  gt_mail_resolve() { return 2; }
  run gt_mail_probe
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/mail-result" ]
  gt_mail_resolve() { printf '%s\0' other.example 587 sender secret yes starttls admin > "$SCRATCH/mail-configuration"; }
  run gt_mail_probe
  [ "$status" -eq 2 ]
  [[ "$output" == *'differs from the confirmed answer'* ]]
  [ ! -e "$SCRATCH/mail-result" ]
}

@test "explicit mail skip never connects and remains incomplete" {
  mail_ready
  ANSWER[SMTP_CONFIGURE]=no
  if gt_check_mail; then false; else [ "$?" -eq 10 ]; fi
  [ "${STATE[step.mail]}" = skipped ]
}

@test "SMTP transport failure records incomplete result while keeping mail resumable" {
  mail_ready
  gt_mail_probe() { return 1; }
  run gt_check_mail
  [ "$status" -eq 10 ]
  grep -qx 'mail=failed' "$ROOT/var/lib/gt-install/result"
  grep -qx 'status=incomplete' "$ROOT/var/lib/gt-install/result"
  grep -qx 'step.mail=failed' "$ROOT/var/lib/gt-install/state"
}

@test "mail command refuses combined installation modes" {
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --check-mail --install-web
  [ "$status" -eq 2 ]
}
