#!/bin/bash
# Disposable Compose project; mounts the repo read-only and publishes no host ports.
set -Eeuo pipefail
cd "$(dirname "$0")/../../.."
scratch=$(mktemp -d)
project="gt-login-proxy-$$"
compose=(docker compose -p "$project" -f "$scratch/compose.json")
cleanup() {
  result=$?
  if [[ -f "$scratch/compose.json" ]]; then
    if ((result != 0)); then "${compose[@]}" logs --tail 80 || :; fi
    "${compose[@]}" down --volumes --remove-orphans >/dev/null || :
  fi
  rm -rf -- "$scratch"
}
trap cleanup EXIT
# All inputs are disposable. Never read a host's deployment .env or start its database.
cat > "$scratch/fixture.env" <<'ENV'
DB_ROOT_PASSWORD=fixture-root
DB_PASSWORD=fixture-database
JWT_SECRET=fixture-jwt-not-used
ADMIN_EMAIL=fixture@example.test
ENV
export GT_PROXY_NETWORK_PREFIX=${GT_TEST_PROXY_NETWORK_PREFIX:-172.30.232}
docker compose --env-file "$scratch/fixture.env" -f docker/docker-compose.yml config --format json \
  | python3 util/installer/test/login-proxy-compose.py "$PWD" > "$scratch/compose.json"
# Start dynamic peers first to detect a pool that can allocate Caddy's reserved address.
"${compose[@]}" up -d backend client1 client2
"${compose[@]}" up -d web
probe() {
  "${compose[@]}" exec -T "$1" python3 - "$2" "${3:-status}" "${4:-203.0.113.77}" <<'PY'
import sys
import urllib.request
request = urllib.request.Request(f"http://{sys.argv[1]}/api/{sys.argv[2]}",
                                 headers={"X-Forwarded-For": sys.argv[3]})
print(urllib.request.urlopen(request, timeout=3).read().decode())
PY
}
ready=no
for ((i=0; i<60; i++)); do
  if initial=$(probe client1 web 2>/dev/null); then ready=yes; break; fi
  sleep 1
done
[[ $ready == yes && $initial == allowed:* ]]
attempts=${initial#*:}
for ((i=0; i<attempts; i++)); do probe client1 web fail "203.0.113.$i" >/dev/null; done
[[ $(probe client1 web) == blocked:* ]]
[[ $(probe client2 web) == allowed:* ]]
probe client2 web succeed >/dev/null
[[ $(probe client1 web) == blocked:* ]]
probe client1 web succeed >/dev/null
[[ $(probe client1 web) == allowed:* ]]
# A neighboring container bypassing Caddy cannot rotate its counter through forged headers.
for ((i=0; i<attempts; i++)); do probe client2 backend:8080 fail "198.51.100.$i" >/dev/null; done
[[ $(probe client2 backend:8080) == blocked:* ]]
[[ $(probe client2 web) == blocked:* ]]
[[ $(probe client1 web) == allowed:* ]]
echo 'PASS: Compose proxy trust, real Caddy, separate login counters, forged headers and direct container peers.'
