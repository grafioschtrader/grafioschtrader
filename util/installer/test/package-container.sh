#!/bin/bash
# Debian package acceptance in a fresh disposable Debian or Ubuntu container: build the package twice, publish both
# through a signed repository, enroll it the documented way, then install, upgrade, run the read-only check and remove
# the package next to an installed Grafioschtrader, which must stay byte for byte unchanged.
set -euo pipefail
trap 'echo "FAIL: package-container.sh line $LINENO" >&2' ERR
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install ]]
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends apt-utils gnupg ca-certificates >/dev/null

work=$(mktemp -d)
# apt fetches file: repositories as its unprivileged _apt user, which needs read access to the repository.
chmod 0755 "$work"
export GNUPGHOME="$work/gnupg"
install -d -m 700 "$GNUPGHOME"
gpg --batch --quiet --passphrase '' --quick-gen-key 'Grafioschtrader test repository' ed25519 sign never
key=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" && !n++ {print $10}')
version=$(sed -n 's:^  <version>\(.*\)</version>$:\1:p' /repo/backend/pom.xml | head -n 1)
package=/repo/util/installer/package
old=$(bash "$package/build-deb.sh" 0.0.1 "$work/debs")
new=$(bash "$package/build-deb.sh" "$version" "$work/debs")

# Exactly the installer and its copyright file; the dependencies exist on every supported release.
contents=$(dpkg-deb --fsys-tarfile "$new" | tar -t | grep -v '/$' | LC_ALL=C sort)
[[ "$contents" == $'./usr/sbin/gt-install\n./usr/share/doc/grafioschtrader-installer/copyright' ]]
[[ $(dpkg-deb --field "$new" Depends) == 'bash, curl, git, sudo, logrotate, ca-certificates' ]]
[[ $(dpkg-deb --field "$new" Architecture) == all ]]
listing=$(dpkg-deb -c "$new")
[[ ${listing%%$'\n'*} == 'drwxr-xr-x root/root '* ]]
cmp <(dpkg-deb --fsys-tarfile "$new" | tar -xO ./usr/sbin/gt-install) /repo/util/installer/gt-install.sh

# Enrollment as documented: armored key in /etc/apt/keyrings, deb822 source restricted to that key.
publish() {
  rm -rf "$work/apt"
  bash "$package/build-repo.sh" "$work/apt" "$key" "$@"
  chmod -R a+rX "$work/apt"
}
publish "$old"
install -d -m 0755 /etc/apt/keyrings
install -m 0644 "$work/apt/grafioschtrader-archive-keyring.asc" /etc/apt/keyrings/grafioschtrader.asc
cat > /etc/apt/sources.list.d/grafioschtrader.sources <<SOURCES
Types: deb
URIs: file://$work/apt
Suites: stable
Components: main
Signed-By: /etc/apt/keyrings/grafioschtrader.asc
SOURCES
apt-get update -qq
first=$(apt-get install -y --no-install-recommends grafioschtrader-installer 2>&1)
grep -qF 'Start the Grafioschtrader installation with: sudo gt-install' <<< "$first"
[[ $(dpkg-query -W -f='${Version}' grafioschtrader-installer) == 0.0.1 ]]
[[ $(stat -c '%U:%G %a' /usr/sbin/gt-install) == 'root:root 755' ]]
[[ $(command -v gt-install) == /usr/sbin/gt-install ]]
[[ $(gt-install --help) == 'Usage: sudo gt-install [--answers FILE]'* ]]

# A host with a completed installation: everything the installer owns outside the package.
install -d -m 0700 /var/lib/gt-install /root/.gt-install
printf 'status=complete\n' > /var/lib/gt-install/state
printf 'secret=kept\n' > /root/.gt-install/secrets
install -d /home/grafioschtrader /etc/sudoers.d /etc/logrotate.d /etc/systemd/system
for file in /home/grafioschtrader/gtvar.sh /home/grafioschtrader/gtupdate.sh /etc/sudoers.d/grafioschtrader \
    /etc/logrotate.d/grafioschtrader /etc/systemd/system/grafioschtrader.service /var/log/grafioschtrader.log; do
  printf 'installed by gt-install: %s\n' "$file" > "$file"
done
owned=(/var/lib/gt-install /root/.gt-install /home/grafioschtrader /etc/sudoers.d/grafioschtrader
  /etc/logrotate.d/grafioschtrader /etc/systemd/system/grafioschtrader.service /var/log/grafioschtrader.log)
fingerprint() {
  find "${owned[@]}" -printf '%p %y %m %U %G %s %T@\n' | LC_ALL=C sort
  find "${owned[@]}" -type f -exec sha256sum {} + | LC_ALL=C sort
}
before=$(fingerprint)

# An upgrade replaces the installer and runs nothing else; the hint is reserved for the first installation.
publish "$old" "$new"
apt-get update -qq
upgrade=$(apt-get install -y --only-upgrade grafioschtrader-installer 2>&1)
[[ $(dpkg-query -W -f='${Version}' grafioschtrader-installer) == "$version" ]]
if grep -qE 'Start the|already installed' <<< "$upgrade"; then exit 1; fi
cmp /usr/sbin/gt-install /repo/util/installer/gt-install.sh
[[ "$(fingerprint)" == "$before" ]]

# The packaged command runs the read-only inventory; without the fixture journal it inspects the whole host.
rm /var/lib/gt-install/state
before=$(fingerprint)
status=0
check=$(gt-install --check --plain 2>&1) || status=$?
(( status == 0 || status == 2 )) || { printf '%s\n' "$check"; exit 1; }
grep -q '^source.commit=' <<< "$check"
[[ "$(fingerprint)" == "$before" ]]
printf 'status=complete\n' > /var/lib/gt-install/state
before=$(fingerprint)

apt-get remove -y -qq grafioschtrader-installer >/dev/null
[[ ! -e /usr/sbin/gt-install && "$(fingerprint)" == "$before" ]]
apt-get install -y -qq grafioschtrader-installer >/dev/null
apt-get purge -y -qq grafioschtrader-installer >/dev/null
[[ ! -e /usr/sbin/gt-install && ! -e /usr/share/doc/grafioschtrader-installer && "$(fingerprint)" == "$before" ]]
[[ $(dpkg-query -W -f='${db:Status-Status}' grafioschtrader-installer 2>/dev/null || true) =~ ^(not-installed)?$ ]]

# A repository signed by another key is refused.
gpg --batch --quiet --passphrase '' --quick-gen-key 'Foreign key' ed25519 sign never
foreign=$(gpg --batch --with-colons --list-secret-keys 'Foreign key' | awk -F: '$1 == "fpr" && !n++ {print $10}')
bash "$package/build-repo.sh" "$work/apt" "$foreign" "$new"
chmod -R a+rX "$work/apt"
# With its previous lists still present apt only warns and keeps them; without them the refusal is an error.
rm -f /var/lib/apt/lists/"${work//\//_}"_apt_*
if refused=$(apt-get update 2>&1); then printf '%s\n' "$refused"; exit 1; fi
grep -qE 'NO_PUBKEY|not signed|[Ss]ignature' <<< "$refused"
echo "PASS: package $version installs, upgrades, checks and is removed without touching the installation."
