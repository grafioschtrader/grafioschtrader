#!/bin/bash
# Real nginx/Apache acceptance of the include into an existing HTTPS site, in a disposable container from
# web.Dockerfile. The site keeps its TLS, its PHP and static-file rules; a failed verification restores it.
set -Eeuo pipefail
[[ $EUID == 0 && -f /.dockerenv && ! -e /var/lib/gt-install ]]
web=${1:-nginx}
[[ "$web" == nginx || "$web" == apache2 ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset; gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap 'nginx -s quit 2>/dev/null || :; apache2ctl stop 2>/dev/null || :; if [[ -n ${backend_pid:-} ]]; then kill "$backend_pid" 2>/dev/null || :; fi; gt_cleanup' EXIT
bash /repo/util/installer/test/test-certificates.sh
install -d -m 700 /var/lib/gt-install
install -d -m 755 /var/www/gt /var/www/gt/grafioschtrader /var/www/site
printf '<base href="/grafioschtrader/"><script src="main.js"></script>\n' > /var/www/gt/grafioschtrader/index.html
echo 'console.log("GT test");' > /var/www/gt/grafioschtrader/main.js
echo 'SITE' > /var/www/site/index.html
echo 'console.log("site");' > /var/www/site/main.js
echo 'console.log("site copy");' > /var/www/site/grafioschtrader.js
chmod 644 /var/www/gt/grafioschtrader/* /var/www/site/*
cert=/root/gt-test-certificates/fullchain.pem key=/root/gt-test-certificates/server.key
ANSWER=([WEBSERVER]=$web [DOMAIN]=gt.test [DNS_FAMILY]=ipv4 [TLS_SOURCE]=existing [VHOST_INCLUDE]=yes
  [TLS_CERT]=$cert [TLS_KEY]=$key [DOCROOT]=/var/www/gt [BACKEND_PORT]=9091 [BACKEND_HTTP_PORT]=9091)
[[ "$web" != apache2 ]] || ANSWER[BACKEND_PORT]=9090
FACT[web.names]='' FACT[tls.cert]=$cert FACT[tls.key]=$key
FACT[web.lan]=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<NF;i++) if($i=="src") {print $(i+1); exit}}')
STATE[run_id]=00000000000000000000000000000001
python3 /repo/util/installer/test/web-backend.py > "$SCRATCH/backend.log" 2>&1 &
backend_pid=$!
for ((i=0;i<30;i++)); do
  if curl --noproxy '*' -fsS http://127.0.0.1:9091/api/gtinfo >/dev/null 2>&1; then break; fi
  sleep .1
done
if [[ "$web" == nginx ]]; then
  target=/etc/nginx/sites-available/site
  cat > "$target" <<SITE
server { listen 80; server_name gt.test www.gt.test; return 301 https://\$host\$request_uri; }
server {
    listen 443 ssl;
    server_name gt.test;
    ssl_certificate $cert;
    ssl_certificate_key $key;
    root /var/www/site;
    index index.html;
    location / { try_files \$uri \$uri/ =404; }
    location ~ \.php\$ { return 418; }
    location ~* \.(js|css)\$ { root /var/www/site; expires 30d; }
}
SITE
  ln -s "$target" /etc/nginx/sites-enabled/site
  nginx
  php=418
else
  target=/etc/apache2/sites-available/site.conf
  cat > "$target" <<SITE
<VirtualHost *:80>
    ServerName gt.test
    Redirect permanent / https://gt.test/
</VirtualHost>
<VirtualHost *:443>
    ServerName gt.test
    DocumentRoot /var/www/site
    SSLEngine on
    SSLCertificateFile $cert
    SSLCertificateKeyFile $key
    <Directory /var/www/site>
        Require all granted
    </Directory>
    <FilesMatch "\.php\$">
        Require all denied
    </FilesMatch>
</VirtualHost>
SITE
  printf '<VirtualHost *:80>\n ServerName default.test\n DocumentRoot /var/www/html\n</VirtualHost>\n' \
    > /etc/apache2/sites-available/000-default.conf
  a2enmod -q ssl > /dev/null
  a2ensite -q site > /dev/null
  apache2ctl start
  php=403
fi
cp -p "$target" "$SCRATCH/site.original"
gt_core_run() {
  if [[ "$1" == systemctl ]]; then
    case "$2" in
      reload) if [[ "$3" == nginx.service ]]; then nginx -s reload; else apache2ctl graceful; fi; sleep 1 ;;
      enable|start) : ;;
      *) return 99 ;;
    esac
  else "$@"; fi
}
site() {
  curl --noproxy '*' -sS --resolve gt.test:443:127.0.0.1 -o "$SCRATCH/body" -w '%{http_code}' "https://gt.test$1"
}
site_unchanged() {
  [[ "$(site /)" == 200 && "$(cat "$SCRATCH/body")" == SITE ]]
  [[ "$(site /x.php)" == "$php" ]]
  [[ "$(site /main.js)" == 200 && "$(cat "$SCRATCH/body")" == 'console.log("site");' ]]
  [[ "$(site /grafioschtrader.js)" == 200 && "$(cat "$SCRATCH/body")" == 'console.log("site copy");' ]]
}
site_unchanged
gt_site_inventory
gt_nginx_statuses > "$SCRATCH/web-before"
gt_site_baseline
[[ "$web" != apache2 ]] || gt_apache_modules lan
gt_vhost_plan
(( ${#PLAN_BLOCKERS[@]} == 0 ))

# A failed verification restores the site byte for byte; the site keeps answering as before.
eval "$(declare -f gt_web_verify | sed '1s/gt_web_verify/gt_web_verify_real/')"
gt_web_verify() { return 1; }
if gt_vhost_include 2> "$SCRATCH/error"; then echo 'Expected a failed verification' >&2; exit 1; fi
grep -q 'the backup was restored' "$SCRATCH/error"
cmp "$target" "$SCRATCH/site.original"
site_unchanged
eval "$(declare -f gt_web_verify_real | sed '1s/gt_web_verify_real/gt_web_verify/')"

gt_vhost_include
[[ "${STATE[step.vhost_include]}" == complete ]]
grep -q 'grafioschtrader.conf' "$target"
# GT routes win over the site's regex rules for .js/.css and .php; the site's own paths are unchanged.
[[ "$(site /grafioschtrader/main.js)" == 200 && "$(cat "$SCRATCH/body")" == 'console.log("GT test");' ]]
[[ "$(site /api/gtinfo)" == 200 ]]
python3 - "$SCRATCH/body" <<'PY'
import json, sys
headers = {k.lower(): v for k, v in json.load(open(sys.argv[1]))['headers'].items()}
assert headers.get('x-forwarded-proto') == 'https', headers
assert headers.get('x-forwarded-for') == '127.0.0.1', headers
PY
site_unchanged
gt_vhost_web
echo "PASS: $web include into an existing HTTPS site: own TLS, regex and PHP rules unchanged, rollback restores."
