#!/bin/bash
# Real login service behind Caddy; no Spring application, credentials or database.
set -euo pipefail
[[ -f /.dockerenv && ${GT_LOGIN_PROXY_TEST:-} == yes ]]
mkdir -p /tmp/login-proxy-libs
cd /tmp/login-proxy-libs
fixture_jar=$(find /repo/backend/grafiosch-test-integration/target -maxdepth 1 \
  -name 'grafiosch-test-integration-*.jar' -print -quit)
[[ -n "$fixture_jar" ]]
jar xf "$fixture_jar" BOOT-INF/lib
exec java -cp '/repo/backend/grafiosch-test-integration/target/test-classes:BOOT-INF/lib/*' \
  grafiosch.service.LoginProxyCheckServer
