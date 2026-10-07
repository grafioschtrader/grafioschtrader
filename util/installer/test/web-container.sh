#!/bin/bash
# Real nginx/HTTP acceptance with a protocol-recording backend fixture. No host ports.
# Functions imported from gt-install.sh are replaced later to inject failures.
# shellcheck disable=SC2218
set -Eeuo pipefail
[[ $EUID == 0 && -f /.dockerenv && ! -e /var/lib/gt-install ]]
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset
gt_question_model
umask 077
SCRATCH=$(mktemp -d)
trap 'nginx -s quit 2>/dev/null || :; if [[ -n ${backend_pid:-} ]]; then kill "$backend_pid" 2>/dev/null || :; fi; gt_cleanup' EXIT
install -d -m 700 /var/lib/gt-install
install -d -m 755 /var/www/gt /var/www/gt/grafioschtrader
printf '<base href="/grafioschtrader/"><script src="main.js"></script>\n' > /var/www/gt/grafioschtrader/index.html
printf 'console.log("installer acceptance");\n' > /var/www/gt/grafioschtrader/main.js
chmod 644 /var/www/gt/grafioschtrader/*
ANSWER[DOCROOT]=/var/www/gt ANSWER[BACKEND_PORT]=9091
FACT[web.lan]=$(ip -4 route get 1.1.1.1 | awk '{for(i=1;i<NF;i++) if($i=="src") {print $(i+1); exit}}')
cat > /etc/nginx/sites-enabled/other <<'SITE'
server { listen 80; server_name other.example; location / { return 204; } }
SITE
sha256sum /etc/nginx/nginx.conf /etc/nginx/sites-enabled/default /etc/nginx/sites-enabled/other > "$SCRATCH/foreign.sha256"
python3 -u - > "$SCRATCH/backend.log" 2>&1 <<'PY' &
import http.server, json
class Backend(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def do_GET(self):
        if self.path == '/ws/upgrade' and self.headers.get('Upgrade') == 'websocket':
            import base64, hashlib
            accept = base64.b64encode(hashlib.sha1((self.headers['Sec-WebSocket-Key'] +
                '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            self.send_response(101)
            self.send_header('Upgrade', 'websocket'); self.send_header('Connection', 'Upgrade')
            self.send_header('Sec-WebSocket-Accept', accept); self.end_headers()
            self.wfile.write(b'\x81\x02OK'); self.wfile.flush()
            self.close_connection = True
            return
        data = {'path': self.path, 'headers': dict(self.headers)}
        if self.path == '/api/gtinfo': data.update(databaseName='grafioschtrader', activeProfile='production')
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers(); self.wfile.write(body)
http.server.ThreadingHTTPServer(('127.0.0.1', 9091), Backend).serve_forever()
PY
backend_pid=$!
for ((i=0;i<20;i++)); do
  if curl --noproxy '*' -fsS http://127.0.0.1:9091/api/gtinfo >/dev/null 2>&1; then break; fi
  sleep .1
done
nginx
# Only systemd is absent in this container. Configuration, reloads, routing,
# permissions, curl and publication all use their real implementations.
gt_core_run() {
  if [[ "$1" == systemctl ]]; then
    case "$2" in reload) nginx -s reload ;; enable) : ;; *) return 99 ;; esac
  else "$@"; fi
}
gt_nginx_test
gt_nginx_inventory "${FACT[web.lan]}"
gt_nginx_statuses > "$SCRATCH/web-before"
gt_web_activate
[[ ${STATE[step.web]} == complete ]]
sha256sum --check "$SCRATCH/foreign.sha256"
gt_nginx_owned
gt_web_verify
for route in /api/probe /m2m/probe /ws /socket/websocket; do
  curl --noproxy '*' -fsS -H 'Upgrade: websocket' -H 'Connection: upgrade' -H 'X-Forwarded-For: 203.0.113.99' \
    "http://${FACT[web.lan]}$route" > "$SCRATCH/route.json"
  python3 - "$SCRATCH/route.json" "$route" "${FACT[web.lan]}" <<'PY'
import json, sys
d = json.load(open(sys.argv[1])); route, lan = sys.argv[2:]
assert d['path'] == ('/socket' if route == '/socket/websocket' else route)
h = d['headers']
assert h['Host'] == lan and h['X-Forwarded-Proto'] == 'http'
assert h['X-Real-IP'] == lan and h['X-Forwarded-For'] == lan
if route in ('/ws', '/socket/websocket'):
    assert h['Upgrade'] == 'websocket' and h['Connection'] == 'upgrade'
PY
done
python3 - "${FACT[web.lan]}" <<'PY'
import socket, sys
lan = sys.argv[1]
with socket.create_connection((lan, 80), timeout=5) as connection:
    connection.sendall(('GET /ws/upgrade HTTP/1.1\r\nHost: '+lan+'\r\nUpgrade: websocket\r\n'
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
echo 'PASS: real nginx, API/m2m/WebSocket headers and rewrite, SPA, JS and shared sites.'

# Simulate interruption after publication: original comparisons survive recovery.
gt_core_mark step.web running
gt_nginx_inventory "${FACT[web.lan]}"
gt_nginx_statuses > "$SCRATCH/web-before"
gt_web_activate
sha256sum /etc/nginx/sites-available/grafioschtrader > "$SCRATCH/owned.sha256"
gt_web_activate
sha256sum --check "$SCRATCH/owned.sha256"

# A failed candidate validation must remove only our link and reload the baseline.
eval "$(declare -f gt_nginx_test | sed '1s/gt_nginx_test/real_nginx_test/')"
gt_nginx_test() { [[ ! -L /etc/nginx/sites-enabled/grafioschtrader ]] && real_nginx_test; }
if gt_web_activate; then echo 'Expected validation failure' >&2; exit 1; fi
[[ ! -L /etc/nginx/sites-enabled/grafioschtrader && ${STATE[step.web]} == failed ]]
sha256sum --check "$SCRATCH/foreign.sha256"
unset -f gt_nginx_test
gt_nginx_test() { real_nginx_test; }
gt_web_activate

# A changed existing website after reload also rolls back enablement. Injection
# changes only the observed result; the foreign configuration remains untouched.
eval "$(declare -f gt_nginx_statuses | sed '1s/gt_nginx_statuses/real_nginx_statuses/')"
gt_nginx_statuses() { real_nginx_statuses; echo 'changed-status'; }
if gt_web_activate; then echo 'Expected shared-site mismatch' >&2; exit 1; fi
[[ ! -L /etc/nginx/sites-enabled/grafioschtrader && ${STATE[step.web]} == failed ]]
sha256sum --check "$SCRATCH/foreign.sha256"
echo 'PASS: interruption, repeated publication, config-test and shared-site rollback.'
