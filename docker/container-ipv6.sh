# shellcheck shell=bash
#
# Gives the containers IPv6 when this host has it. Sourced by install.sh and
# update.sh; not meant to be run on its own.
#
# Docker's project network carries IPv4 only, so without this the backend cannot
# reach any address that has only an AAAA record: its own domain on a DS-Lite
# line (the first GTNet entry and the identity check of the own entry fail with
# "Network is unreachable"), and GTNet peers that are reachable over IPv6 only.
#
# Docker 27 and newer does the actual work once the network has enable_ipv6: it
# allocates a ULA prefix, masquerades it onto the host's address and switches
# IPv6 forwarding on. The only thing written here is docker-compose.override.yml,
# and only while that file does not exist — an existing override belongs to the
# administrator and is never edited. GT_CONTAINER_IPV6=off in .env (or the
# environment) skips all of this.
#
# Sets CONTAINER_NETWORK_CHANGED=true when the existing network has to be
# recreated, i.e. the caller must run 'docker compose down' once before 'up'.

CONTAINER_IPV6_OVERRIDE=docker-compose.override.yml
CONTAINER_IPV6_NETWORK=grafioschtrader_default
CONTAINER_NETWORK_CHANGED=false

container_ipv6_setting() {
  local value="${GT_CONTAINER_IPV6:-}"
  if [ -z "$value" ] && [ -f .env ]; then
    value="$(sed -n 's/^GT_CONTAINER_IPV6=//p' .env | tail -n 1)"
    value="${value//\"/}"; value="${value//\'/}"
  fi
  printf '%s' "${value:-auto}"
}

# Only a local engine shares this host's network: for a remote one the routes
# checked below say nothing about where the containers run.
container_ipv6_local_engine() {
  case "$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" in
    unix:///var/run/docker.sock|unix:///run/docker.sock) return 0 ;;
    *) return 1 ;;
  esac
}

# With forwarding on, a kernel that processes router advertisements itself
# (accept_ra=1) ignores them, and the host loses the IPv6 route and address it
# learned from them — usually noticed after the next reboot. NetworkManager and
# systemd-networkd normally handle RAs in userspace and leave accept_ra at 0, but
# a NetworkManager connection with ipv6.method=ignore leaves them to the kernel,
# for example the WLAN of a Raspberry Pi OS image written with Raspberry Pi Imager.
container_ipv6_kernel_ra() {
  local routes="$1" uplink
  grep -q ' proto ra ' <<< "$routes " || return 1
  uplink="$(awk '{ for (i = 1; i < NF; i++) if ($i == "dev") { print $(i + 1); exit } }' <<< "$routes")"
  [ -n "$uplink" ] && [ "$(sysctl -n "net/ipv6/conf/$uplink/accept_ra" 2>/dev/null)" = 1 ]
}

setup_container_ipv6() {
  local routes version=""
  CONTAINER_NETWORK_CHANGED=false
  [ "$(container_ipv6_setting)" != off ] || return 0
  command -v ip >/dev/null 2>&1 || return 0
  routes="$(ip -6 route show default 2>/dev/null || true)"
  # No IPv6 on the host: the containers could not use it either.
  [ -n "$routes" ] || return 0

  if [ -f "$CONTAINER_IPV6_OVERRIDE" ]; then
    grep -q 'enable_ipv6' "$CONTAINER_IPV6_OVERRIDE" && return 0
    warn "This host has IPv6, but the containers do not. $CONTAINER_IPV6_OVERRIDE already exists and is"
    warn "left as it is; add the following to it, then run 'docker compose down && docker compose up -d':"
    warn "  networks:"
    warn "    default:"
    warn "      enable_ipv6: true"
    warn "See 'IPv6 for the containers' in README.md. GT_CONTAINER_IPV6=off in .env silences this."
    return 0
  fi
  container_ipv6_local_engine || return 0
  version="$(docker version --format '{{.Server.Version}}' 2>/dev/null || true)"
  if ! [[ "${version%%.*}" =~ ^[0-9]+$ ]] || [ "${version%%.*}" -lt 27 ]; then
    warn "This host has IPv6, but Docker ${version:-of unknown version} is older than 27, which cannot give"
    warn "the containers IPv6 by itself. They stay IPv4-only; see 'IPv6 for the containers' in README.md."
    return 0
  fi
  if container_ipv6_kernel_ra "$routes"; then
    warn "This host has IPv6, but its kernel processes router advertisements itself. Docker switching IPv6"
    warn "forwarding on would make the host lose its IPv6 route, so the containers stay IPv4-only."
    warn "See 'IPv6 for the containers' in README.md (accept_ra = 2)."
    return 0
  fi

  cat > "$CONTAINER_IPV6_OVERRIDE" <<'EOF'
# Written by container-ipv6.sh because this host has IPv6: the containers get it
# too, so the backend reaches its own IPv6-only domain and IPv6-only GTNet peers.
# Docker allocates a ULA prefix and masquerades it onto the host's address.
# To go back to IPv4-only containers, delete this file, set GT_CONTAINER_IPV6=off
# in .env and run 'docker compose down && docker compose up -d'.
networks:
  default:
    enable_ipv6: true
EOF
  info "This host has IPv6 — $CONTAINER_IPV6_OVERRIDE gives it to the containers as well."
  if docker network inspect "$CONTAINER_IPV6_NETWORK" >/dev/null 2>&1; then
    CONTAINER_NETWORK_CHANGED=true
  fi
}

# Docker switches forwarding on when it creates the IPv6 network; if it did not,
# the containers have an IPv6 address but no way out, which is worse than none.
verify_container_ipv6() {
  [ -f "$CONTAINER_IPV6_OVERRIDE" ] && grep -q 'enable_ipv6' "$CONTAINER_IPV6_OVERRIDE" || return 0
  container_ipv6_local_engine || return 0
  [ "$(sysctl -n net.ipv6.conf.all.forwarding 2>/dev/null)" != 0 ] && return 0
  warn "The containers have IPv6, but IPv6 forwarding is off on this host, so they cannot leave it."
  warn "Switch it on (see 'IPv6 for the containers' in README.md) or delete $CONTAINER_IPV6_OVERRIDE."
}
