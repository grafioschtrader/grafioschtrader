#!/bin/bash
set -Eeuo pipefail
[[ $EUID == 0 && -f /.dockerenv && ! -e /var/lib/gt-install ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset; gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap 'nginx -s quit 2>/dev/null || :; apache2ctl stop 2>/dev/null || :; if [[ -n ${backend_pid:-} ]]; then kill "$backend_pid" 2>/dev/null || :; fi; gt_cleanup' EXIT
bash /repo/util/installer/test/test-certificates.sh
install -d -m 700 /var/lib/gt-install
install -d -m 755 /var/www/gt /var/www/gt/grafioschtrader
printf '<base href="/grafioschtrader/"><script src="main.js"></script>\n' > /var/www/gt/grafioschtrader/index.html
echo 'console.log("GT test");' > /var/www/gt/grafioschtrader/main.js
chmod 644 /var/www/gt/grafioschtrader/*
ANSWER[WEBSERVER]=${1:-nginx} ANSWER[DOMAIN]=gt.test ANSWER[DNS_FAMILY]=ipv4
ANSWER[TLS_SOURCE]=existing ANSWER[DOCROOT]=/var/www/gt ANSWER[BACKEND_PORT]=9091 ANSWER[BACKEND_HTTP_PORT]=9091
[[ ${ANSWER[WEBSERVER]} != apache2 ]] || ANSWER[BACKEND_PORT]=9090
ANSWER[TLS_CERT]=/root/gt-test-certificates/fullchain.pem ANSWER[TLS_KEY]=/root/gt-test-certificates/server.key
FACT[tls.cert]=${ANSWER[TLS_CERT]} FACT[tls.key]=${ANSWER[TLS_KEY]} FACT[web.names]='gt.test www.gt.test'
test_mode=${2:-existing}
[[ "$test_mode" == existing || "$test_mode" == proxy || "$test_mode" == acme ]]
if [[ "$test_mode" == proxy ]]; then
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_LISTEN]=8081 ANSWER[TLS_PROXY_FROM]=192.0.2.1
fi
FACT[web.lan]=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<NF;i++) if($i=="src") {print $(i+1); exit}}')
STATE[run_id]=00000000000000000000000000000001
python3 /repo/util/installer/test/web-backend.py > "$SCRATCH/backend.log" 2>&1 &
backend_pid=$!
for ((i=0;i<30;i++)); do
  if curl --noproxy '*' -fsS http://127.0.0.1:9091/api/gtinfo >/dev/null 2>&1; then break; fi
  sleep .1
done
if [[ ${ANSWER[WEBSERVER]} == nginx ]]; then
  echo 'server { listen 80; server_name other.test; location / { return 204; } }' > /etc/nginx/sites-enabled/other
  nginx
else
  printf '<VirtualHost *:80>\n ServerName default.test\n DocumentRoot /var/www/html\n</VirtualHost>\n' > /etc/apache2/sites-available/000-default.conf
  printf '<VirtualHost *:80>\n ServerName other.test\n Redirect 302 / /elsewhere\n</VirtualHost>\n' > /etc/apache2/sites-enabled/other.conf
  apache2ctl start
fi
gt_core_run() {
  if [[ "$1" == systemctl ]]; then
    case "$2" in
      reload) if [[ "$3" == nginx.service ]]; then nginx -s reload; else apache2ctl graceful; fi ;;
      enable|start) : ;;
      *) return 99 ;;
    esac
  else "$@"; fi
}
gt_site_inventory
gt_nginx_statuses > "$SCRATCH/web-before"
gt_site_baseline
if [[ ${ANSWER[WEBSERVER]} == apache2 ]]; then
  gt_apache_modules lan
fi
gt_site_render_lan > "$SCRATCH/site-lan"
gt_site_activate lan "http://${FACT[web.lan]}"
gt_web_verify
if [[ "$test_mode" == proxy ]]; then
  if [[ ${ANSWER[WEBSERVER]} == apache2 ]]; then
    echo 'Listen 8081' > "$SCRATCH/listen"
    gt_app_root_file apache_listen "$SCRATCH/listen" /etc/apache2/conf-enabled/grafioschtrader-listen.conf 644
  fi
  gt_domain_render proxy > "$SCRATCH/site-domain"
  gt_site_activate domain http://gt.test:8081 gt.test:8081:127.0.0.1
  curl --noproxy '*' -fsS --resolve gt.test:8081:127.0.0.1 -H 'X-Forwarded-Proto: https' http://gt.test:8081/api/gtinfo > "$SCRATCH/proxy.json"
  python3 - "$SCRATCH/proxy.json" <<'PY'
import json, sys
headers = {k.lower(): v for k, v in json.load(open(sys.argv[1]))['headers'].items()}
assert headers['x-forwarded-proto'] == 'https'
assert headers['host'].split(':')[0] == 'gt.test'
PY
  [[ $(curl --noproxy '*' -sS -H 'Host: gt.test' -o /dev/null -w '%{http_code}' "http://${FACT[web.lan]}:8081/api/gtinfo") == 403 ]]
  gt_web_verify
  gt_site_compare
  echo "PASS: ${ANSWER[WEBSERVER]} external proxy headers, source restriction and independent LAN access."
  exit 0
fi
gt_domain_render http > "$SCRATCH/site-http"
gt_site_activate http http://gt.test gt.test:80:127.0.0.1
curl --noproxy '*' -fsS --resolve gt.test:80:127.0.0.1 -H 'X-Forwarded-For: 203.0.113.99' \
  http://gt.test/api/gtinfo > "$SCRATCH/edge.json"
python3 - "$SCRATCH/edge.json" <<'PY'
import json, sys
headers = {k.lower(): v for k, v in json.load(open(sys.argv[1]))['headers'].items()}
assert all(ip.strip() == '127.0.0.1' for ip in headers['x-forwarded-for'].split(',')), headers
PY
if [[ "$test_mode" == acme ]]; then
  # This override exists only in the disposable test: real HTTP-01 validation by
  # Pebble, no Let's Encrypt accounts, public certificates or host trust changes.
  ANSWER[TLS_SOURCE]=letsencrypt ANSWER[LETSENCRYPT_EMAIL]=admin@example.test
  FACT[plan.names]=${FACT[web.names]}
  FACT[tls.cert]="/etc/letsencrypt/live/gt-install-${STATE[run_id]}/fullchain.pem"
  FACT[tls.key]="/etc/letsencrypt/live/gt-install-${STATE[run_id]}/privkey.pem"
  curl --noproxy '*' -kfsS https://pebble:15000/roots/0 > /usr/local/share/ca-certificates/pebble-issued.crt
  update-ca-certificates >/dev/null
  gt_tls_certbot() { certbot "$@" --server https://pebble:14000/dir --no-verify-ssl; }
  # A container has no systemd PID 1. Exercise the saved deployment hook by
  # translating only its exact reload command to the real web-server reload.
  # shellcheck disable=SC2016
  printf '#!/bin/bash\n[[ "$1" == reload && "$2" == %s.service ]] || exit 2\n' "${ANSWER[WEBSERVER]}" > /usr/bin/systemctl
  if [[ ${ANSWER[WEBSERVER]} == nginx ]]; then echo 'exec nginx -s reload' >> /usr/bin/systemctl
  else echo 'exec apache2ctl graceful' >> /usr/bin/systemctl; fi
  chmod 755 /usr/bin/systemctl
  if ! gt_tls_issue; then cat /var/lib/gt-install/tls.log; exit 1; fi
fi
if [[ ${ANSWER[WEBSERVER]} == apache2 ]]; then gt_apache_modules tls; fi
gt_domain_render tls > "$SCRATCH/site-domain"
gt_site_activate domain https://gt.test gt.test:443:127.0.0.1
gt_tls_verify
gt_web_verify
if [[ "$test_mode" == acme ]]; then
  if ! gt_tls_certbot renew --cert-name "gt-install-${STATE[run_id]}" --dry-run --non-interactive --no-random-sleep-on-renew --run-deploy-hooks > "$SCRATCH/renew.log" 2>&1; then
    cat "$SCRATCH/renew.log"; exit 1
  fi
  gt_tls_verify
  # Adopt the existing valid lineage without issuing a second certificate or
  # changing its authenticator, webroot, deploy hook or other renewal settings.
  ANSWER[LETSENCRYPT_CERT_NAME]="gt-install-${STATE[run_id]}"
  renewal="/etc/letsencrypt/renewal/${ANSWER[LETSENCRYPT_CERT_NAME]}.conf"
  sha256sum "$renewal" > "$SCRATCH/renewal.sha256"
  find /etc/letsencrypt/renewal -name '*.conf' -printf '%f\n' | sort > "$SCRATCH/lineages-before"
  PLAN_BLOCKERS=()
  gt_certbot_plan
  [[ ${#PLAN_BLOCKERS[@]} == 0 && ${FACT[tls.reuse]} == yes ]]
  gt_tls_certbot() {
    printf '%s\n' "$1" >> "$SCRATCH/reuse-commands"
    certbot "$@" --server https://pebble:14000/dir --no-verify-ssl
  }
  gt_tls_issue
  [[ ! -e "$SCRATCH/reuse-commands" ]]
  gt_tls_reuse_hook
  if ! gt_tls_renew; then cat /var/lib/gt-install/tls.log; exit 1; fi
  [[ $(cat "$SCRATCH/reuse-commands") == renew ]]
  sha256sum -c "$SCRATCH/renewal.sha256"
  find /etc/letsencrypt/renewal -name '*.conf' -printf '%f\n' | sort > "$SCRATCH/lineages-after"
  cmp "$SCRATCH/lineages-before" "$SCRATCH/lineages-after"
  RENEWED_LINEAGE="/etc/letsencrypt/live/${FACT[tls.lineage]}" \
    "/etc/letsencrypt/renewal-hooks/deploy/gt-install-${STATE[run_id]}"
  gt_tls_verify
fi
[[ $(curl --noproxy '*' -sS --resolve gt.test:80:127.0.0.1 -o /dev/null -w '%{http_code}' http://gt.test/api/gtinfo) == 301 ]]
gt_site_owned
gt_site_compare

# Repeat and recover a journaled activation without changing any configuration.
target=$(gt_site_path domain)
sha256sum "$target" > "$SCRATCH/site.sha256"
gt_core_mark step.site_domain running
gt_site_activate domain https://gt.test gt.test:443:127.0.0.1
sha256sum --check "$SCRATCH/site.sha256"
gt_tls_verify

# Wrong certificate or a missing JS file must never pass verification.
cp /var/www/gt/grafioschtrader/main.js "$SCRATCH/main.js"
rm /var/www/gt/grafioschtrader/main.js
if gt_web_verify https://gt.test gt.test:443:127.0.0.1; then exit 1; fi
install -m 644 "$SCRATCH/main.js" /var/www/gt/grafioschtrader/main.js

# Actual WebSocket upgrade and frame through TLS, for both proxy implementations.
python3 - <<'PY'
import socket, ssl
for route in ('/ws', '/socket/websocket'):
    with ssl.create_default_context().wrap_socket(socket.create_connection(('127.0.0.1', 443), timeout=5), server_hostname='gt.test') as connection:
        connection.sendall(('GET '+route+' HTTP/1.1\r\nHost: gt.test\r\nUpgrade: websocket\r\n'
            'Connection: Upgrade\r\nSec-WebSocket-Version: 13\r\n'
            'Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n\r\n').encode())
        response = b''
        while True:
            part = connection.recv(4096)
            if not part: break
            response += part
        headers, frame = response.split(b'\r\n\r\n', 1)
        assert headers.startswith(b'HTTP/1.1 101 ')
        assert b's3pPLMBiTxaQ9kYGzzhZRbK+xOo=' in headers
        assert frame == b'\x81\x02OK'
PY

# A failed domain validation restores bootstrap HTTP and keeps the LAN working.
eval "$(declare -f gt_site_test | sed '1s/gt_site_test/real_site_test/')"
gt_site_test() {
  local target
  target=$(gt_site_path domain)
  [[ ! -L "${target/sites-available/sites-enabled}" ]] && real_site_test
}
if gt_site_activate domain https://gt.test gt.test:443:127.0.0.1; then exit 1; fi
[[ ${STATE[step.site_domain]} == failed ]]
gt_web_verify
for ((attempt=0; attempt<10; attempt++)); do
  if gt_web_verify http://gt.test gt.test:80:127.0.0.1 2>/dev/null; then break; fi
  sleep 1
done
gt_web_verify http://gt.test gt.test:80:127.0.0.1
gt_site_compare
gt_site_test() { real_site_test; }
gt_site_activate domain https://gt.test gt.test:443:127.0.0.1
gt_tls_verify
echo "PASS: ${ANSWER[WEBSERVER]} LAN, domain HTTP, TLS/SNI/certificate, redirects, SPA, shared sites and resume."
