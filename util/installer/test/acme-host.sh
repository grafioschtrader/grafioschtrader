#!/bin/bash
# Run from the repository root on a Linux Docker host. No published host ports.
set -euo pipefail
web=${1:-nginx}
[[ "$web" == nginx || "$web" == apache2 ]]
image=${GT_WEB_TEST_IMAGE:-gt-installer-domain-test}
name="gt-installer-acme-$$"
network_created=no
cleanup() {
  docker rm -f "$name-client" "$name-ca" >/dev/null 2>&1 || :
  if [[ "$network_created" == yes ]]; then docker network rm "$name" >/dev/null; fi
}
trap cleanup EXIT
docker network create "$name" >/dev/null
network_created=yes
docker run -d --name "$name-ca" --network "$name" --network-alias pebble \
  -e PEBBLE_VA_NOSLEEP=1 -e PEBBLE_AUTHZREUSE=0 -e PEBBLE_WFE_NONCEREJECT=0 \
  -v "$PWD/util/installer/test/pebble.json:/test/installer.json:ro" \
  ghcr.io/letsencrypt/pebble:latest -config /test/installer.json -strict=false >/dev/null
docker run --rm --name "$name-client" --network "$name" --network-alias gt.test --network-alias www.gt.test \
  -v "$PWD:/repo:ro" "$image" bash util/installer/test/domain-container.sh "$web" acme || {
  docker logs "$name-ca"; exit 1;
}
