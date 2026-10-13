#!/bin/bash
# Creates a new signing key for the APT repository in a throwaway keyring.
# Usage: bash util/installer/package/new-signing-key.sh SECRET_KEY_FILE
# Writes the public key to util/installer/package/grafioschtrader-archive-keyring.asc, which is committed, and the
# armored secret key to SECRET_KEY_FILE outside the repository. Keep that file offline and store it as the repository
# secret read by .github/workflows/apt.yml:  gh secret set APT_SIGNING_KEY < SECRET_KEY_FILE
# The key does not expire: an enrolled host keeps its copy of the public key and would otherwise stop trusting the
# repository on the expiry date. A replaced key therefore means enrolling every host again.
set -euo pipefail
umask 077
(( $# == 1 )) || { echo 'Usage: new-signing-key.sh SECRET_KEY_FILE' >&2; exit 2; }
here=$(cd "$(dirname "$0")" && pwd)
top=$(cd "$here/../../.." && pwd -P)
secret=$(realpath -m -- "$1")
if [[ "$secret" == "$top"/* ]]; then
  echo 'The secret key file must lie outside the repository.' >&2
  exit 2
fi
[[ ! -e "$secret" ]] || { echo "Refusing to overwrite $secret" >&2; exit 2; }

GNUPGHOME=$(mktemp -d)
export GNUPGHOME
trap 'rm -rf -- "$GNUPGHOME"' EXIT
gpg --batch --quiet --passphrase '' --quick-gen-key 'Grafioschtrader APT repository' ed25519 sign never
fingerprint=$(gpg --batch --with-colons --list-secret-keys | awk -F: '$1 == "fpr" && !n++ {print $10}')
gpg --batch --armor --export-secret-keys "$fingerprint" > "$secret"
(umask 022; gpg --batch --armor --export "$fingerprint" > "$here/grafioschtrader-archive-keyring.asc")
echo "Fingerprint: $fingerprint"
echo "Public key:  $here/grafioschtrader-archive-keyring.asc (commit it)"
echo "Secret key:  $secret (gh secret set APT_SIGNING_KEY < \"$secret\", then keep it offline)"
