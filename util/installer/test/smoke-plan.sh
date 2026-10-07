#!/bin/bash
# Disposable fresh container only: drive the real CLI through a pseudo-terminal.
set -euo pipefail
cd "$(dirname "$0")/../../.."
test "$(id -u)" -eq 0
command -v script >/dev/null
smoke_dir=$(mktemp -d)
trap 'rm -rf -- "$smoke_dir"' EXIT
before=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
# Fresh-container questions: LAN, nginx, backend, admin, limit, no mail, buffer, heap, docroot, zone.
# A trailing yes supplies SWAP on a small host; it is harmless if that question is absent.
status=0
LC_ALL=C script -q -e -c 'bash util/installer/gt-install.sh --dry-run --plain' /dev/null \
  > "$smoke_dir/report" 2> "$smoke_dir/errors" <<'ANSWERS' || status=$?

nginx
9090
admin@example.org
20
no
yes
-Xms128m -Xmx896m
/var/www/gt
UTC
yes
ANSWERS
cat "$smoke_dir/report"
cat "$smoke_dir/errors" >&2
[[ "$status" == 0 || "$status" == 2 ]]
test ! -s "$smoke_dir/errors"
grep -q 'Installation plan' "$smoke_dir/report"
grep -q 'No installation was executed' "$smoke_dir/report"
after=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
test "$before" = "$after"
printf 'PASS: terminal dry-run completed with no leaked scratch directory (exit %s).\n' "$status"
