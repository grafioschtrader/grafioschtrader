#!/bin/bash
# Builds the signed APT repository that GitHub Pages serves under
# https://grafioschtrader.github.io/grafioschtrader/apt (suite "stable", component "main").
# Usage: bash util/installer/package/build-repo.sh REPO_DIR KEY_FINGERPRINT DEB...
# The secret key must already be in the gpg keyring (GNUPGHOME). Needs apt-ftparchive (apt-utils) and gpg.
# The package is Architecture: all; it is listed in the index of every architecture the installer supports, so apt
# finds it without relying on a binary-all index.
set -euo pipefail
umask 022
(( $# >= 3 )) || { echo 'Usage: build-repo.sh REPO_DIR KEY_FINGERPRINT DEB...' >&2; exit 2; }
repo=$1 key=$2
shift 2
pool=pool/main/g/grafioschtrader-installer
architectures='amd64 arm64 armhf'

mkdir -p "$repo/$pool"
for deb; do cp -- "$deb" "$repo/$pool/"; done
cd "$repo"
rm -rf dists
# The Release file must not list itself, so it is written outside dists/ first.
release=$(mktemp)
for arch in $architectures; do
  dir=dists/stable/main/binary-$arch
  mkdir -p "$dir"
  apt-ftparchive --arch "$arch" packages pool > "$dir/Packages"
  gzip -9nk "$dir/Packages"
done
apt-ftparchive \
  -o APT::FTPArchive::Release::Origin=Grafioschtrader \
  -o APT::FTPArchive::Release::Label=Grafioschtrader \
  -o APT::FTPArchive::Release::Suite=stable \
  -o APT::FTPArchive::Release::Codename=stable \
  -o "APT::FTPArchive::Release::Architectures=$architectures" \
  -o APT::FTPArchive::Release::Components=main \
  -o 'APT::FTPArchive::Release::Description=Grafioschtrader installer' \
  release dists/stable > "$release"
install -m 0644 "$release" dists/stable/Release
rm -f -- "$release"
gpg --batch --yes --local-user "$key" --digest-algo SHA512 --clearsign -o dists/stable/InRelease dists/stable/Release
gpg --batch --yes --local-user "$key" --digest-algo SHA512 --detach-sign --armor -o dists/stable/Release.gpg \
  dists/stable/Release
# The public key beside the repository is what a host enrolls. apt reads the armored file directly when its name ends
# in .asc, so a host needs no gnupg.
gpg --batch --yes --armor --export "$key" > grafioschtrader-archive-keyring.asc
