#!/bin/bash
# Development only; installed hosts download the generated gt-install.sh alone.
set -euo pipefail
exec python3 "$(dirname "$0")/build.py" "$@"
