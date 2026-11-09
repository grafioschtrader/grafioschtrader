#!/usr/bin/env bash
#
# Prepares a fresh Debian, Ubuntu or Raspberry Pi OS host and starts install.sh:
# installs Docker Engine with the Compose plugin, creates the user grafioschtrader
# and runs the installer as this user, which needs neither a password nor sudo.
#
# This is the only step that needs root rights:
#   sudo bash bootstrap.sh
#
# Safe to re-run: a working Docker, an existing user and an existing checkout are
# kept. install.sh then offers to keep its configuration as well.
#
set -euo pipefail

GT_USER=grafioschtrader
GT_REPOSITORY=https://github.com/grafioschtrader/grafioschtrader.git
DOCKER_GUIDE=https://docs.docker.com/engine/install/

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
info()  { printf '  %s\n' "$*"; }
warn()  { printf '\033[33m  ! %s\033[0m\n' "$*"; }
fail()  { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

# The releases the Linux installer accepts, see util/installer/src/20-compat.sh. The containers would start on an
# older host as well, but its kernel and Docker Engine get no security updates while it serves ports 80 and 443.
check_release() {
  case "${OS_ID}:${VERSION_ID:-}" in
    debian:12|debian:13|ubuntu:24.04|ubuntu:26.04) ;;
    ubuntu:22.04) warn "Ubuntu 22.04 standard support ends in April 2027. Plan the upgrade to Ubuntu 24.04 or 26.04." ;;
    *) fail "this script supports Debian 12 and 13 and Ubuntu 22.04, 24.04 and 26.04, but this host runs ${PRETTY_NAME:-${OS_ID}}. Releases out of support receive no security updates: upgrade this host first." ;;
  esac
}

check_host() {
  [ "$(id -u)" = 0 ] || fail "run this script with root rights: sudo bash bootstrap.sh"
  [ -r /etc/os-release ] && command -v apt-get >/dev/null 2>&1 \
    || fail "this script needs a Debian or Ubuntu based system. Install Docker by hand: ${DOCKER_GUIDE}"
  # shellcheck source=/dev/null
  . /etc/os-release
  OS_ID="${ID:-}"
  # Ubuntu derivatives carry the codename of their Ubuntu base here.
  OS_CODENAME="${UBUNTU_CODENAME:-${VERSION_CODENAME:-}}"
  case "$OS_ID" in
    debian|ubuntu) ;;
    *) fail "'${OS_ID}' is not supported by this script. Install Docker by hand: ${DOCKER_GUIDE}" ;;
  esac
  check_release
  [ -n "$OS_CODENAME" ] || fail "cannot determine the release name of this system."
  ARCHITECTURE="$(dpkg --print-architecture)"
  case "$ARCHITECTURE" in
    amd64|arm64) ;;
    *) fail "this is a '${ARCHITECTURE}' system. Grafioschtrader publishes images for 64-bit systems only (amd64, arm64)." ;;
  esac
}

docker_is_ready() {
  command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1
}

package_installed() {
  dpkg-query -W -f='${Status}' "$1" 2>/dev/null | grep -q 'ok installed'
}

# These packages conflict with Docker's own. They may carry the containers of
# another application, so removing them is the administrator's decision.
check_conflicts() {
  local package found=""
  for package in docker.io docker-compose docker-doc docker-buildx podman-docker containerd runc; do
    if package_installed "$package"; then found="${found} ${package}"; fi
  done
  [ -z "$found" ] || fail "these packages conflict with Docker Engine:${found}. Remove them with 'sudo apt remove${found}' and run this script again."
}

add_docker_repository() {
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL "https://download.docker.com/linux/${OS_ID}/gpg" -o /etc/apt/keyrings/docker.asc \
    || fail "could not download Docker's signing key."
  chmod a+r /etc/apt/keyrings/docker.asc
  cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/${OS_ID}
Suites: ${OS_CODENAME}
Components: stable
Architectures: ${ARCHITECTURE}
Signed-By: /etc/apt/keyrings/docker.asc
EOF
}

install_packages() {
  export DEBIAN_FRONTEND=noninteractive
  # Without </dev/null, apt and dpkg swallow whatever was typed ahead for install.sh.
  apt-get update -qq </dev/null || fail "'apt-get update' failed."
  apt-get install -y -qq ca-certificates curl git </dev/null >/dev/null || fail "could not install curl and git."
  if docker_is_ready; then
    info "Docker is already installed: $(docker --version)"
    return 0
  fi
  check_conflicts
  info "Installing Docker Engine and the Compose plugin from Docker's package repository"
  add_docker_repository
  apt-get update -qq </dev/null \
    || fail "Docker's package repository has no packages for '${OS_CODENAME}'. Install Docker by hand: ${DOCKER_GUIDE}"
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin \
    </dev/null >/dev/null || fail "could not install Docker."
  info "$(docker --version)"
}

create_user() {
  if id "$GT_USER" >/dev/null 2>&1; then
    info "User ${GT_USER} already exists"
    usermod -aG docker "$GT_USER"
  else
    info "Creating user ${GT_USER}"
    useradd -m -s /bin/bash -G docker "$GT_USER"
  fi
  GT_HOME="$(getent passwd "$GT_USER" | cut -d: -f6)"
  [ -d "$GT_HOME" ] || fail "the home directory ${GT_HOME} of ${GT_USER} does not exist."
}

# Runs a command as GT_USER in this terminal. Unlike su or a login shell it starts
# no new session, so the installer's questions and Ctrl+C keep working.
as_gt_user() {
  (
    cd "$GT_HOME"
    exec setpriv --reuid="$(id -u "$GT_USER")" --regid="$(id -g "$GT_USER")" --init-groups \
      env HOME="$GT_HOME" USER="$GT_USER" LOGNAME="$GT_USER" "$@"
  )
}

get_sources() {
  GT_DOCKER_DIR="${GT_HOME}/grafioschtrader/docker"
  if [ -f "${GT_DOCKER_DIR}/install.sh" ]; then
    info "Keeping the existing checkout in ${GT_HOME}/grafioschtrader"
  elif [ -e "${GT_HOME}/grafioschtrader" ]; then
    fail "${GT_HOME}/grafioschtrader exists but is no Grafioschtrader checkout."
  else
    info "Downloading Grafioschtrader's installation files"
    as_gt_user git clone -q --depth 1 "$GT_REPOSITORY" || fail "'git clone' failed."
  fi
}

main() {
  bold "=== Prepare this host for Grafioschtrader ==="
  check_host
  install_packages
  systemctl enable --now docker.service >/dev/null 2>&1 \
    || fail "could not start Docker. Check 'systemctl status docker.service'."
  create_user
  get_sources
  echo
  as_gt_user bash "${GT_DOCKER_DIR}/install.sh"
  echo
  bold "Later updates and maintenance run as user ${GT_USER}:"
  info "sudo -iu ${GT_USER}"
  info "cd ~/grafioschtrader/docker"
  info "./update.sh"
}

main "$@"
