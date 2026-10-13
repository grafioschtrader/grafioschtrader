#!/usr/bin/with-contenv bash
# Called at startup and periodically by the LinuxServer DuckDNS image.
# Never send an empty address: DuckDNS would interpret it as IPv4 autodetection,
# including on installations that explicitly selected IPv6 only.
set -euo pipefail

fail() { printf 'DuckDNS: %s; no DNS update sent.\n' "$*" >&2; exit 1; }

case "${UPDATE_IP:-}" in
  ipv4|ipv6|both) ;;
  *) fail "set DUCKDNS_UPDATE_IP to ipv4, ipv6 or both in .env (or rerun install.sh)" ;;
esac
[ -n "${SUBDOMAINS:-}" ] && [ -n "${TOKEN:-}" ] || fail "subdomain or token missing"

# Query the same public-address service as the upstream image. Host networking
# makes IPv6 use the server's outbound address rather than a container address.
detect_address() {
  local family="$1" resolver="$2" address
  address="$(dig +short +time=5 +tries=1 ch txt whoami.cloudflare "-$family" "@$resolver")" \
    || fail "IPv$family address detection failed"
  address="${address//\"/}"
  if [ "$family" = 6 ]; then
    [[ "$address" == *:* && "$address" =~ ^[23][[:xdigit:]:]+$ ]] \
      || fail "no public IPv6 address detected"
  else
    local octet
    local -a octets
    [[ "$address" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || fail "no IPv4 address detected"
    IFS=. read -r -a octets <<< "$address"
    for octet in "${octets[@]}"; do
      (( 10#$octet <= 255 )) || fail "invalid IPv4 address detected"
    done
  fi
  printf '%s' "$address"
}

args=(--data-urlencode "domains=$SUBDOMAINS" --data-urlencode "token=$TOKEN")
if [ "$UPDATE_IP" != ipv6 ]; then
  ipv4="$(detect_address 4 1.1.1.1)"
  args+=(--data-urlencode "ip=$ipv4")
fi
if [ "$UPDATE_IP" != ipv4 ]; then
  ipv6="$(detect_address 6 2606:4700:4700::1111)"
  args+=(--data-urlencode "ipv6=$ipv6")
fi

# Do not print the request URL or a raw response: they may contain credentials.
if ! response="$(curl --fail --silent --show-error --max-time 60 --get \
    "${args[@]}" https://www.duckdns.org/update)"; then
  printf 'DuckDNS: update request failed.\n' >&2
  exit 1
fi
if [ "${response//$'\r'/}" != OK ]; then
  printf 'DuckDNS: update rejected; check subdomain and token.\n' >&2
  exit 1
fi
printf 'DuckDNS: %s update successful%s%s.\n' "$UPDATE_IP" "${ipv4:+ IPv4=$ipv4}" "${ipv6:+ IPv6=$ipv6}"
