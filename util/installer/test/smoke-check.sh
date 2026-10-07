#!/bin/bash
# Run inside a disposable container. It may lack systemd/tools, so a completed report can exit 2.
set -euo pipefail
cd "$(dirname "$0")/../../.."
test "$(id -u)" -eq 0
smoke_dir=$(mktemp -d)
trap 'rm -rf -- "$smoke_dir"' EXIT
before=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
status=0
bash util/installer/gt-install.sh --check > "$smoke_dir/report" 2> "$smoke_dir/errors" || status=$?
cat "$smoke_dir/report"
cat "$smoke_dir/errors" >&2
[[ "$status" == 0 || "$status" == 2 ]]
test ! -s "$smoke_dir/errors"
grep -q '^host.class=' "$smoke_dir/report"
grep -q 'Check finished:' "$smoke_dir/report"
after=$(find /tmp -maxdepth 1 -name 'tmp.*' -printf '%f\n' | sort)
test "$before" = "$after"
printf 'PASS: complete CLI report and no leaked scratch directory (exit %s).\n' "$status"
