#!/bin/bash
set -euo pipefail
# Run from the directory containing the installation's gtvar.sh, as before.
# shellcheck source=/dev/null
. ./gtvar.sh
: "${docroot:?Set docroot in gtvar.sh}"
if [[ -z "${basehref:-}" || "$basehref" == /* || "$basehref" == *..* || "$basehref" == *[\*\?\[\]\\]* ]]; then
  echo "ERROR: basehref must be a non-empty relative path without '..' or shell patterns." >&2
  exit 1
fi
root=$(realpath -e -- "$docroot")
target=$(realpath -m -- "$docroot/$basehref")
if [[ "$root" == / || "$target" != "$root/"* ]]; then
  echo "ERROR: Frontend target must be strictly beneath docroot." >&2
  exit 1
fi
mkdir -p -- "$target"
touch /var/log/grafioschtrader.log
chown grafioschtrader:grafioschtrader /var/log/grafioschtrader.log
chown -R grafioschtrader:grafioschtrader "$target"
