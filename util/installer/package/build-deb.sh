#!/bin/bash
# Builds grafioschtrader-installer_VERSION_all.deb from the generated gt-install.sh into OUT_DIR.
# Usage: bash util/installer/package/build-deb.sh VERSION OUT_DIR
# The package installs the bundle as /usr/sbin/gt-install and carries only its copyright file beside it. Building
# needs dpkg-deb alone, so it runs on any Debian or Ubuntu host and on the GitHub runner.
set -euo pipefail
umask 022
(( $# == 2 )) || { echo 'Usage: build-deb.sh VERSION OUT_DIR' >&2; exit 2; }
version=$1 out=$2
[[ "$version" =~ ^[0-9]+(\.[0-9]+)+$ ]] || { echo "Version must be numeric, e.g. 0.38.2: $version" >&2; exit 2; }
here=$(cd "$(dirname "$0")" && pwd)
installer="$here/../gt-install.sh"
[[ -f "$installer" ]] || { echo "Missing $installer" >&2; exit 1; }

stage=$(mktemp -d)
trap 'rm -rf -- "$stage"' EXIT
# mktemp creates 0700; the archive's root entry must not carry that mode onto the target's /.
chmod 0755 "$stage"
install -D -m 0755 "$installer" "$stage/usr/sbin/gt-install"
install -D -m 0644 "$here/copyright" "$stage/usr/share/doc/grafioschtrader-installer/copyright"
install -d -m 0755 "$stage/DEBIAN"
install -m 0755 "$here/postinst" "$stage/DEBIAN/postinst"
size=$(du -sk --exclude=DEBIAN "$stage" | cut -f1)
sed -e "s/@VERSION@/$version/" -e "s/@INSTALLED_SIZE@/$size/" "$here/control.in" > "$stage/DEBIAN/control"
chmod 0644 "$stage/DEBIAN/control"

# Rebuilding the same commit yields the same archive: timestamps follow the installer's last commit.
if [[ -z "${SOURCE_DATE_EPOCH:-}" ]]; then
  SOURCE_DATE_EPOCH=$(git -C "$here" log -1 --format=%ct -- ../gt-install.sh 2>/dev/null || true)
  [[ -n "$SOURCE_DATE_EPOCH" ]] || SOURCE_DATE_EPOCH=$(date +%s)
fi
export SOURCE_DATE_EPOCH
find "$stage" -exec touch -h -d "@$SOURCE_DATE_EPOCH" {} +

mkdir -p "$out"
deb="$out/grafioschtrader-installer_${version}_all.deb"
dpkg-deb --root-owner-group -Zxz --build "$stage" "$deb" >/dev/null
echo "$deb"
