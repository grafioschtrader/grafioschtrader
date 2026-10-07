#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../../.."
bash util/installer/build.sh --check
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s util/installer/test -p test_build.py
scripts=(util/installer/gt-install.sh util/installer/build.sh util/shellscripts/gtupbackend.sh util/shellscripts/gtupfrontend.sh
  util/shellscripts/gtupfrontback.sh util/shellscripts/installroot.sh
  util/shellscripts/grafioschtrader.sh util/shellscripts/checkversion.sh)
for script in "${scripts[@]}" util/installer/src/*.sh util/installer/test/*.sh util/installer/test/*.bash; do
  bash -n "$script"
done
shellcheck "${scripts[@]}" util/installer/test/*.sh util/installer/test/*.bash
# Modules share globals, associative-array declarations and optional-argument
# callers across files. The complete bundle above retains all these checks.
shellcheck --shell=bash -e SC2034,SC2154,SC2004,SC2119,SC2120 util/installer/src/*.sh
bats util/installer/test/*.bats
