#!/bin/bash
# Run only in a disposable fresh container. Credentials below are test fixtures.
set -euo pipefail
cd "$(dirname "$0")/../../.."
test "$(id -u)" -eq 0
smoke_dir=$(mktemp -d)
trap 'rm -rf -- "$smoke_dir"' EXIT
before=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
umask 077
cat > "$smoke_dir/answers" <<'ANSWERS'
ADMIN_EMAIL=admin@example.org
SMTP_CONFIGURE=no
TIMEZONE=UTC
JAVA_HEAP=-Xms128m -Xmx896m
DB_ROOT_PASSWORD=FIXTURE_ROOT_ONLY
DB_PASSWORD=FIXTURE_DB_ONLY
JASYPT_PASSWORD=FIXTURE_JASYPT_ONLY
ANSWERS
status=0
LC_ALL=C bash util/installer/gt-install.sh --prepare --answers "$smoke_dir/answers" \
  > "$smoke_dir/report" 2> "$smoke_dir/errors" || status=$?
[[ "$status" == 0 || "$status" == 2 ]]
test ! -s "$smoke_dir/errors"
grep -q 'Preparation ends here' "$smoke_dir/report"
grep -q 'DB_ROOT_PASSWORD.*collected' "$smoke_dir/report"
grep -q 'JWT_SECRET.*generated' "$smoke_dir/report"
if grep -qE 'FIXTURE_(ROOT|DB|JASYPT)_ONLY' "$smoke_dir/report"; then exit 1; fi
after=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
test "$before" = "$after"
printf 'PASS: unattended preparation, no credentials in report, scratch cleaned (exit %s).\n' "$status"
