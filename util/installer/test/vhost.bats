#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  ANSWER=([WEBSERVER]=nginx [DOMAIN]=example.org [TLS_SOURCE]=existing [VHOST_INCLUDE]=yes
    [TLS_CERT]=/etc/letsencrypt/live/example.org/fullchain.pem [TLS_KEY]=/etc/letsencrypt/live/example.org/privkey.pem
    [DOCROOT]=/var/www/gt [BACKEND_PORT]=9090 [BACKEND_HTTP_PORT]=8080)
  STATE=([run_id]=00000000000000000000000000000001)
  mkdir -p "$ROOT/etc/nginx/sites-available" "$ROOT/etc/nginx/sites-enabled" "$ROOT/etc/letsencrypt" \
    "$ROOT/etc/apache2/sites-enabled" "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  printf 'ssl_session_cache shared:le_nginx_SSL:10m;\nssl_protocols TLSv1.2 TLSv1.3;\n' \
    > "$ROOT/etc/letsencrypt/options-ssl-nginx.conf"
  nginx_site
}

# A site as Certbot leaves it: an HTTP redirect block and the HTTPS block with Certbot's include.
nginx_site() {
  cat > "$ROOT/etc/nginx/sites-available/site" <<'SITE'
server {
    listen 80;
    server_name example.org www.example.org;
    return 301 https://$host$request_uri;
}
server {
    listen 443 ssl http2;
    server_name example.org;
    ssl_certificate /etc/letsencrypt/live/example.org/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/example.org/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    root /var/www/site;
    location / { try_files $uri $uri/ =404; }
    location ~ \.php$ { fastcgi_pass unix:/run/php/php-fpm.sock; }
    location ~* \.(js|css)$ { expires 30d; }
}
SITE
  ln -sf "$ROOT/etc/nginx/sites-available/site" "$ROOT/etc/nginx/sites-enabled/site"
  cp "$ROOT/etc/nginx/sites-available/site" "$BATS_TEST_TMPDIR/site.original"
}

@test "the HTTPS block of the domain is the target, not its redirect block, and its certificate is reported" {
  gt_vhost_target
  [ "${FACT[vhost.file]}" = /etc/nginx/sites-available/site ]
  [ "${FACT[vhost.line]}" = 6 ]
  [ "${FACT[vhost.cert]}" = /etc/letsencrypt/live/example.org/fullchain.pem ]
  [ "${FACT[vhost.key]}" = /etc/letsencrypt/live/example.org/privkey.pem ]
  [ "${FACT[vhost.state]}" = ready ]
}

@test "overlapping locations, a second HTTPS block, dynamic includes and an unusual opening line are refused" {
  local variant
  for variant in 'location /api/ { return 404; }' 'location ^~ /grafioschtrader/ { return 404; }' \
      'location = /ws { return 404; }' 'include /etc/nginx/$host.conf;'; do
    nginx_site
    sed -i "s|    root /var/www/site;|    root /var/www/site;\n    $variant|" "$ROOT/etc/nginx/sites-available/site"
    run gt_vhost_target
    [ "$status" -ne 0 ]
  done
  nginx_site
  printf 'server {\n    listen 443 ssl;\n    server_name example.org;\n}\n' >> "$ROOT/etc/nginx/sites-available/site"
  run gt_vhost_target
  [[ "$output" == *'exactly one HTTPS server block for example.org, found 2'* ]]
  nginx_site
  sed -i '6s/.*/server { listen 443 ssl;/; 7d' "$ROOT/etc/nginx/sites-available/site"
  run gt_vhost_target
  [[ "$output" == *'must open with "{" ending its line'* ]]
}

@test "the snippet scopes every route with ^~ so regex locations of the site cannot capture them" {
  gt_vhost_snippet_render > "$SCRATCH/snippet"
  [ "$(grep -c '^location ^~ ' "$SCRATCH/snippet")" -eq 5 ]
  grep -qx 'location = /grafioschtrader { return 301 /grafioschtrader/; }' "$SCRATCH/snippet"
  grep -q 'proxy_pass http://127.0.0.1:9090/socket;' "$SCRATCH/snippet"
  run grep -E '^(server|listen|ssl_|root)' "$SCRATCH/snippet"
  [ "$status" -ne 0 ]
}

@test "Apache routes live in Location blocks; the site's own requests keep their headers" {
  ANSWER[WEBSERVER]=apache2
  gt_vhost_snippet_render > "$SCRATCH/snippet"
  [ "$(grep -c '^<Location ' "$SCRATCH/snippet")" -eq 4 ]
  grep -q 'ProxyPass ajp://127.0.0.1:9090/api' "$SCRATCH/snippet"
  grep -q 'ProxyPass ws://127.0.0.1:8080/socket' "$SCRATCH/snippet"
  # Header and proxy settings appear only inside Location blocks, never at virtual host level.
  run awk '/^<Location /{inside=1} /^<\/Location>/{inside=0; next}
    !inside && /RequestHeader|ProxyPreserveHost|ProxyPass/' "$SCRATCH/snippet"
  [ -z "$output" ]
}

include_fixture() {
  gt_app_root_file() { cp "$2" "$3"; printf '%s\n' "$1" >> "$SCRATCH/published"; }
  gt_site_test() { [[ "${SITE_TEST:-ok}" == ok ]]; }
  gt_core_run() { printf '%s\n' "$*" >> "$SCRATCH/commands"; }
  VERIFY=ok
  gt_web_verify() { printf '%s\n' "$*" >> "$SCRATCH/verified"; [[ "$VERIFY" == ok ]]; }
  gt_site_compare() { :; }
  mkdir -p "$ROOT/etc/nginx/snippets"
}

@test "the include is inserted after a backup, verified through the domain and journaled" {
  include_fixture
  gt_vhost_include
  [ "$(sed -n 7p "$ROOT/etc/nginx/sites-available/site")" = '    include /etc/nginx/snippets/grafioschtrader.conf;' ]
  [ "$(sed '7d' "$ROOT/etc/nginx/sites-available/site")" = "$(cat "$BATS_TEST_TMPDIR/site.original")" ]
  cmp "$ROOT${STATE[resource.vhost_backup]}" "$BATS_TEST_TMPDIR/site.original"
  [ "${STATE[resource.vhost_target]}" = /etc/nginx/sites-available/site ]
  [ "${STATE[step.vhost_include]}" = complete ]
  grep -qx 'https://example.org example.org:443:127.0.0.1' "$SCRATCH/verified"
  grep -qx 'systemctl reload nginx.service' "$SCRATCH/commands"
  [ -s "$ROOT/etc/nginx/snippets/grafioschtrader.conf" ]
  # A completed include is only verified; a changed target is not adopted.
  gt_vhost_web
  [ "$(grep -c 'include /etc/nginx/snippets/grafioschtrader.conf;' "$ROOT/etc/nginx/sites-available/site")" -eq 1 ]
  [ "${STATE[step.tls]}" = complete ]
  STATE[step.vhost_include]=running
  printf '# edited\n' >> "$ROOT/etc/nginx/sites-available/site"
  run gt_vhost_include
  [[ "$output" == *'changed after the installer edited it'* ]]
}

@test "a failed verification restores the backup, and resumption inserts the include again" {
  include_fixture
  VERIFY=fail
  run gt_vhost_include
  [ "$status" -ne 0 ]
  [[ "$output" == *'failed verification; the backup was restored'* ]]
  gt_vhost_include || true
  cmp "$ROOT/etc/nginx/sites-available/site" "$BATS_TEST_TMPDIR/site.original"
  [ "${STATE[step.vhost_include]}" = failed ]
  [ -z "${STATE[file.vhost_target]}" ]
  VERIFY=ok
  gt_vhost_include
  [ "${STATE[step.vhost_include]}" = complete ]
  [ "$(grep -c 'grafioschtrader.conf;' "$ROOT/etc/nginx/sites-available/site")" -eq 1 ]
}

@test "the plan requires the virtual host's own certificate and announces backup and insertion" {
  gt_vhost_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'backup | /etc/nginx/sites-available/site.gt-install.<timestamp>'* ]]
  [[ "${PLAN[*]}" == *"Insert 'include /etc/nginx/snippets/grafioschtrader.conf;' after line 6"* ]]
  ANSWER[TLS_CERT]=/etc/ssl/other.pem PLAN_BLOCKERS=()
  gt_vhost_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'must be the certificate of the existing virtual host'* ]]
  ANSWER[TLS_CERT]=/etc/letsencrypt/live/example.org/fullchain.pem PLAN_BLOCKERS=()
  sed -i '6a\    include /etc/nginx/snippets/grafioschtrader.conf;' "$ROOT/etc/nginx/sites-available/site"
  gt_vhost_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'already includes the snippet without a journal record'* ]]
}

@test "Apache: the 443 virtual host from the vhost dump is the target; RewriteRule and overlapping routes refuse" {
  ANSWER[WEBSERVER]=apache2
  cat > "$ROOT/etc/apache2/sites-enabled/site-le-ssl.conf" <<'SITE'
<IfModule mod_ssl.c>
<VirtualHost *:443>
    ServerName example.org
    DocumentRoot /var/www/site
    SSLCertificateFile /etc/letsencrypt/live/example.org/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/example.org/privkey.pem
</VirtualHost>
</IfModule>
SITE
  gt_core_run() {
    printf 'VirtualHost configuration:\n'
    printf '*:80                   example.org (/etc/apache2/sites-enabled/site.conf:1)\n'
    printf '*:443                  example.org (/etc/apache2/sites-enabled/site-le-ssl.conf:2)\n'
  }
  gt_vhost_target
  [ "${FACT[vhost.file]}:${FACT[vhost.line]}" = /etc/apache2/sites-enabled/site-le-ssl.conf:2 ]
  [ "${FACT[vhost.cert]}" = /etc/letsencrypt/live/example.org/fullchain.pem ]
  for variant in 'RewriteRule ^(.*)$ /index.php [L]' 'ProxyPass /api http://127.0.0.1:3000/api'; do
    sed -i "3a\    $variant" "$ROOT/etc/apache2/sites-enabled/site-le-ssl.conf"
    run gt_vhost_target
    [ "$status" -ne 0 ]
    sed -i '4d' "$ROOT/etc/apache2/sites-enabled/site-le-ssl.conf"
  done
}

@test "the question and the TLS default follow an existing site that serves the domain" {
  WEB=('/etc/nginx/sites-enabled/site#2 server_kind nginx' '/etc/nginx/sites-enabled/site#2 server_name example.org')
  unset 'ANSWER[TLS_SOURCE]'
  gt_question_applies VHOST_INCLUDE
  [ "$(gt_default TLS_SOURCE)" = existing ]
  WEB=('/etc/nginx/sites-enabled/grafioschtrader-domain#1 server_name example.org')
  run gt_question_applies VHOST_INCLUDE
  [ "$status" -ne 0 ]
}
