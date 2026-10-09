#!/bin/bash
# Prepare a complete frontend before replacing any files served by the web server.
set -Eeuo pipefail
umask 022 # Published static assets must remain readable by the web server.
shopt -s dotglob nullglob

stage=
replacing=0
published=0
service_stopped=0
installed=()

finish() {
  local status=$? entry restore_failed=0
  trap - EXIT
  if [[ -n "$stage" && -d "$stage" ]]; then
    if (( replacing && ! published )); then
      for entry in "${installed[@]}"; do
        rm -rf -- "$entry" || restore_failed=1
      done
      for entry in "$stage/previous/"*; do
        mv -- "$entry" "$target/" || restore_failed=1
      done
    fi
    if (( restore_failed )); then
      echo "ERROR: Frontend recovery failed; saved files are in $stage/previous." >&2
      status=1
    else
      rm -rf -- "$stage" || status=1
    fi
  fi
  if (( service_stopped )); then
    if ! sudo systemctl start grafioschtrader.service; then
      echo "ERROR: Could not restart grafioschtrader.service; inspect its journal." >&2
      status=1
    fi
  fi
  if (( status != 0 )); then
    echo "ERROR: Frontend update failed. Check the build output before retrying." >&2
  fi
  exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'echo "ERROR: Frontend command failed at line $LINENO." >&2' ERR

# shellcheck source=/dev/null
. "$HOME/gtvar.sh"
: "${builddir:?Set builddir in gtvar.sh}"
: "${docroot:?Set docroot in gtvar.sh}"
# Reject traversal and shell patterns before resolving symlinks; the target must be strictly below docroot.
if [[ -z "${basehref:-}" || "$basehref" == /* || "$basehref" == *..* || "$basehref" == *[\*\?\[\]\\]* ]]; then
  echo "ERROR: basehref must be a non-empty relative path without '..' or shell patterns." >&2
  exit 1
fi
root=$(realpath -e -- "$docroot")
target=$(realpath -m -- "$docroot/$basehref")
if [[ "$root" == / || "$target" != "$root/"* || ( -e "$target" && ! -d "$target" ) ]]; then
  echo "ERROR: Frontend target must be a directory strictly beneath docroot." >&2
  exit 1
fi

cd "$builddir/grafioschtrader/frontend"
memorytotal=$(LC_ALL=C free -m | awk '/^Mem:/ { print $2 }')
[[ "$memorytotal" =~ ^[0-9]+$ ]] || { echo "ERROR: Cannot determine RAM size." >&2; exit 1; }
mkdir -p -- "$target"
# Shared document roots may be root-owned. In that case stage within the GT-owned directory.
stage_parent=$(dirname "$target")
[[ -w "$stage_parent" ]] || stage_parent=$target
stage=$(mktemp -d "$stage_parent/.gt-frontend.XXXXXX")
mkdir "$stage/previous"

npm ci
if (( memorytotal >= 3700 && memorytotal <= 4048 )); then
  if [[ "${GT_UPDATE_COORDINATED:-0}" != 1 && "${GT_INSTALL_BUILD_ONLY:-0}" != 1 ]]; then
    sudo systemctl stop grafioschtrader.service
    service_stopped=1
  fi
  export NODE_OPTIONS="--max_old_space_size=4071"
fi
if (( memorytotal < 3700 )); then
  wget -O "$stage/latest.tar.gz" \
    https://github.com/grafioschtrader/grafioschtrader/releases/download/Latest/latest.tar.gz
  tar -xf "$stage/latest.tar.gz" -C "$stage"
  output="$stage/dist/browser"
else
  node scripts/sync-yaml-schemas.mjs
  ng build frontend --configuration production --base-href "/$basehref" --output-path "$stage/dist"
  output="$stage/dist/browser"
fi
if [[ ! -s "$output/index.html" ]]; then
  echo "ERROR: Frontend output has no non-empty index.html." >&2
  exit 1
fi

# Preserve the target directory itself: only it, not its parent, may belong to the GT user.
replacing=1
for entry in "$target/"*; do
  [[ "$entry" == "$stage" ]] && continue
  mv -- "$entry" "$stage/previous/"
done
for entry in "$output/"*; do
  destination="$target/$(basename "$entry")"
  mv -- "$entry" "$destination"
  installed+=("$destination")
done
published=1
