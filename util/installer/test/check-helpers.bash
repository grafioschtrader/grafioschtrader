setup_check() {
  REPO=$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)
  export GT_INSTALL_SOURCE_ONLY=1
  # shellcheck source=util/installer/gt-install.sh
  source "$REPO/util/installer/gt-install.sh"
  gt_reset
  ROOT="$BATS_TEST_TMPDIR/host"
  SCRATCH="$BATS_TEST_TMPDIR/scratch"
  PROBES="$BATS_TEST_TMPDIR/probes"
  mkdir -p "$ROOT"/{etc/apt,proc/1,home,var/www,opt,var/lib/mysql,var/lib/apt/lists} "$SCRATCH"
  printf 'ID=debian\nVERSION_ID="13"\nVERSION_CODENAME=trixie\n' > "$ROOT/etc/os-release"
  printf 'MemTotal: 8000000 kB\nMemAvailable: 7000000 kB\nSwapTotal: 1000000 kB\n' > "$ROOT/proc/meminfo"
  echo systemd > "$ROOT/proc/1/comm"
  : > "$PROBES"
  ARCH=amd64
  IMAGES='' INSTALLED='' LISTENERS='' GT_USER=no SERVICE_ACTIVE=no SOCKET_ACTIVE=no SQL_FAIL=no ONLINE=no
  APT_OUTPUT='' APT_FAIL=no DNS_A=198.51.100.12 DNS_AAAA='' DNS_FAIL=no OPENSSL_REAL=no
  DNS_TOOL_AVAILABLE=yes LAN_ADDRESSES='192.168.1.2 10.0.0.2'
  SQL_PASSWORD_ONLY=no SQL_GT_LOGIN=grafioschtrader@localhost
}

gt_dns_tool_available() { [[ "$DNS_TOOL_AVAILABLE" == yes ]]; }

# All external probes in fixture tests go through this allow-list. Unexpected commands fail loudly.
gt_probe() {
  printf '%s\n' "$*" >> "$PROBES"
  case "$1" in
    git) [[ "${GIT_MISSING:-no}" == no ]] || return 127
      [[ "$ONLINE" == yes ]] || return 1; printf '%040d\trefs/heads/master\n' 1 ;;
    curl)
      [[ "$ONLINE" == yes ]] || return 1
      case "$*" in
        *info/refs*) printf '001e# service=git-upload-pack\n00000151%040d HEAD\0multi_ack\n003f%040d refs/heads/master\n0046%040d refs/heads/master-old\n0000' 1 1 2 ;;
        *checkversion.sh*) printf 'java_required=25\nnode_required="^22.22.3 || ^24.15.0 || >=26.0.0"\nangular_cli_required=22\n' ;;
        *application.properties*) printf "spring.datasource.hikari.connection-init-sql=SET time_zone = '+00:00' /*M!110200 , character_set_collations = 'utf8mb3=utf8mb3_general_ci,utf8mb4=utf8mb4_general_ci' */\n" ;;
        *url_effective*) echo 'https://release-assets.githubusercontent.com/test?signature=SECRET' ;;
        *ifconfig.co*) echo 198.51.100.12 ;;
        *) echo 200 ;;
      esac ;;
    dpkg) echo "$ARCH" ;;
    dpkg-query) printf '%s' "$INSTALLED" ;;
    apt-cache)
      case "$*" in *rdepends*) printf 'nodejs\nReverse Depends:\n' ;; *) echo '  Candidate: (none)' ;; esac ;;
    fuser|pgrep) return 1 ;;
    timedatectl) echo Europe/Zurich ;;
    ss) printf '%s' "$LISTENERS" ;;
    systemctl)
      [[ "$2" == show ]] || return 99
      if [[ "$3" == mariadb.service && "$SERVICE_ACTIVE" == yes ]]; then
        printf 'LoadState=loaded\nActiveState=active\n'
      elif [[ "$3" == mariadb.socket && "$SOCKET_ACTIVE" == yes ]]; then
        printf 'LoadState=loaded\nActiveState=active\n'
      else printf 'LoadState=not-found\nActiveState=inactive\n'; fi ;;
    docker) printf '%s' "$IMAGES" ;;
    getent) [[ "$GT_USER" == yes ]] || return 1; echo 'grafioschtrader:x:1001:1001::/home/grafioschtrader:/bin/bash' ;;
    update-alternatives) return 1 ;;
    env)
      case "$*" in
        *'apt-get --simulate '*) [[ "$APT_FAIL" == no ]] || return 1; printf '%s\n' "$APT_OUTPUT" ;;
        *'node --version') echo v22.22.3 ;;
        *'/mvn -v') echo 'Apache Maven 3.9.9' ;;
        *) return 127 ;;
      esac ;;
    ufw|nft|iptables-save) return 127 ;;
    ip)
      case "$*" in
        *'-4 route'*) echo '1.1.1.1 via 192.168.1.1 dev eth0 src 192.168.1.2 uid 0' ;;
        *'route show default'*) echo 'default via fe80::1 dev eth0' ;;
        *'-4 -o addr show'*)
          local address index=2
          for address in $LAN_ADDRESSES; do
            printf '%s: eth%s    inet %s/24 scope global eth%s\\       valid_lft forever\n' \
              "$index" "$index" "$address" "$index"
            index=$((index+1))
          done ;;
        *'addr show'*) printf '    inet6 2001:db8::12/64 scope global\n    inet6 fd12::12/64 scope global\n' ;;
      esac ;;
    */mvn) echo 'Apache Maven 3.9.9' ;;
    node) echo v22.22.3 ;;
    */mariadb|*/mysql)
      [[ "$SQL_FAIL" != yes ]] || return 1
      [[ "$SQL_PASSWORD_ONLY" != yes || "$2" == --defaults-file=* ]] || return 1
      case "$*" in
        *'SELECT CURRENT_USER()'*) printf '%s\n' "$SQL_GT_LOGIN" ;;
        *'SELECT VERSION()'*) printf '11.8.3-MariaDB\t/var/lib/mysql/\tutf8mb4_uca1400_ai_ci\n' ;;
        *'information_schema.SCHEMATA'*) printf 'mysql\t20\t0\ngrafioschtrader\t0\t0\nphpmyadmin\t12\t0\n' ;;
        *'SELECT User,Host,plugin'*) printf 'root\tlocalhost\tmysql_native_password\ngrafioschtrader\tlocalhost\tmysql_native_password\n' ;;
        *global_priv*) echo 1 ;;
        *'SHOW SESSION VARIABLES'*) echo 'character_set_collations utf8mb4=utf8mb4_uca1400_ai_ci' ;;
      esac ;;
    ps) printf 'PID COMMAND RSS\n1 homeassistant 600000\n' ;;
    dig)
      [[ "$DNS_FAIL" == no ]] || return 1
      local dns_records dns_address
      case "${*: -1}" in
        A) dns_records=$DNS_A ;;
        AAAA) dns_records=$DNS_AAAA
          [[ "${*: -2:1}" != www.* ]] || dns_records=${DNS_WWW_AAAA-$DNS_AAAA} ;;
      esac
      while IFS= read -r dns_address; do
        [[ -z "$dns_address" ]] || printf 'example.org. 60 IN %s %s\n' "${*: -1}" "$dns_address"
      done <<< "$dns_records"
      return 0 ;;
    openssl)
      if [[ "$OPENSSL_REAL" == yes ]]; then command "$@" 2>/dev/null
      else printf 'subject=CN=example.org\nnotAfter=Jan 1 00:00:00 2028 GMT\nDNS:example.org\n'; fi ;;
    *) echo "Unexpected probe: $*" >&2; return 99 ;;
  esac
}

collect_check() {
  gt_system
  gt_packages
  gt_installation
  gt_java
  gt_runtimes
  gt_consumers
  gt_database
  gt_web
  gt_dynamic_dns
}

fake_executable() {
  mkdir -p "$SCRATCH/bin"
  printf '#!/bin/bash\nexit 99\n' > "$SCRATCH/bin/$1"
  chmod +x "$SCRATCH/bin/$1"
  export PATH="$SCRATCH/bin:$PATH"
}
