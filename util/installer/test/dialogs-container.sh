#!/bin/bash
# Disposable container only: installs whiptail, Python, the German locale and the tools every real host has
# (ss for port defaults, openssl for generated secrets), then drives the real dialogs.
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends whiptail python3 locales iproute2 openssl > /dev/null
sed -i 's/^# *de_CH.UTF-8/de_CH.UTF-8/' /etc/locale.gen
locale-gen > /dev/null
python3 "$(dirname "$0")/smoke-dialogs.py"
