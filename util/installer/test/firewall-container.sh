#!/bin/bash
# Real ufw acceptance: only a fresh disposable container with NET_ADMIN; its rules live in the container's own
# network namespace.
set -euo pipefail
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install ]]
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq --no-install-recommends ufw iptables python3 openssl > /dev/null
ufw --force enable > /dev/null
ufw allow 80/tcp > /dev/null
export GT_INSTALL_SOURCE_ONLY=1
# shellcheck source=util/installer/gt-install.sh
source /repo/util/installer/gt-install.sh
gt_reset; gt_question_model
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
umask 077
mkdir -m 700 /var/lib/gt-install
STATE=([run_id]=00000000000000000000000000000001)
ANSWER=([FIREWALL_ALLOW]=yes [DOMAIN]=example.org [TLS_SOURCE]=letsencrypt)
gt_web_firewall
added=$(ufw show added)
grep -Fxq 'ufw allow 80/tcp' <<< "$added"
grep -Fxq 'ufw allow 443/tcp' <<< "$added"
[[ "${STATE[$(gt_firewall_key 'allow 80/tcp')]}" == 'preexisting:allow 80/tcp' ]]
[[ "${STATE[$(gt_firewall_key 'allow 443/tcp')]}" == 'owned:allow 443/tcp' ]]
ufw status | grep -Eq '^443/tcp +ALLOW +Anywhere'
# A repeated stage changes nothing.
before=$(ufw show added)
gt_web_firewall
[[ "$(ufw show added)" == "$before" ]]
# The proxy route is limited to the upstream address.
ANSWER=([FIREWALL_ALLOW]=yes [DOMAIN]=example.org [TLS_SOURCE]=proxy [TLS_PROXY_LISTEN]=8081
  [TLS_PROXY_FROM]=192.0.2.5)
gt_web_firewall
grep -Fxq 'ufw allow from 192.0.2.5 to any port 8081 proto tcp' <<< "$(ufw show added)"
ufw status | grep -Eq '^8081/tcp +ALLOW +192\.0\.2\.5'
# A removed installer rule stops the stage.
ufw delete allow 443/tcp > /dev/null
ANSWER=([FIREWALL_ALLOW]=yes [DOMAIN]=example.org [TLS_SOURCE]=letsencrypt)
if gt_web_firewall 2> "$SCRATCH/error"; then echo 'Expected a missing-rule failure' >&2; exit 1; fi
grep -q 'ufw rule disappeared: allow 443/tcp' "$SCRATCH/error"
if ufw show added | grep -q '22'; then echo 'SSH must not be touched' >&2; exit 1; fi
echo 'PASS: real ufw rules for LAN, TLS and a restricted proxy route; preexisting rule unclaimed, repeat unchanged.'
