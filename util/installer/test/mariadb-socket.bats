#!/usr/bin/env bats
load check-helpers

# Debian's mariadb-server enables mariadb.socket, which listens on every address of port 3306.
setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  STATE=([new_database_server]=yes [resource.mariadb]=owned)
  SOCKET=enabled LISTEN='LISTEN 0 4096 *:3306 *:*'
  dpkg-query() { printf 'installed'; }
  gt_core_mark() { STATE[$1]=$2; }
  systemctl() {
    case "$*" in
      'is-enabled --quiet mariadb.socket') [[ "$SOCKET" == enabled ]] ;;
      'is-active --quiet mariadb.socket') [[ "$SOCKET" == enabled ]] ;;
      *) return 1 ;;
    esac
  }
  gt_core_run() {
    printf '%s\n' "$*" >> "$SCRATCH/commands"
    case "$*" in
      'systemctl disable --now mariadb.socket') SOCKET=disabled ;;
      'systemctl restart mariadb.service') LISTEN='LISTEN 0 80 127.0.0.1:3306 0.0.0.0:*' ;;
    esac
  }
  ss() { printf '%s\n' "$LISTEN"; }
}

@test "a newly installed server drops the package's socket activation and listens on loopback only" {
  gt_core_packages
  grep -qx 'systemctl disable --now mariadb.socket' "$SCRATCH/commands"
  grep -qx 'systemctl restart mariadb.service' "$SCRATCH/commands"
  : > "$SCRATCH/commands"
  # Resumption finds the socket already disabled and changes nothing more.
  gt_core_packages
  run grep -q 'mariadb.socket' "$SCRATCH/commands"
  [ "$status" -ne 0 ]
}

@test "a new server still listening beyond loopback stops the core, and a shared server is never touched" {
  SOCKET=disabled LISTEN='LISTEN 0 80 0.0.0.0:3306 0.0.0.0:*'
  run gt_core_packages
  [ "$status" -eq 2 ]
  [[ "$output" == *'listens beyond loopback on port 3306'* ]]
  LISTEN='LISTEN 0 80 [::1]:3306 [::]:*'
  gt_core_packages
  STATE[new_database_server]=no SOCKET=enabled LISTEN='LISTEN 0 4096 *:3306 *:*'
  : > "$SCRATCH/commands"
  gt_core_packages
  [ ! -s "$SCRATCH/commands" ]
}
