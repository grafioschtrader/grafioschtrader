#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  # setup_check sources the production functions; the probe stub remains defined by this helper.
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
}

@test "sourcing the installer does not run its entry point" {
  run bash -c 'GT_INSTALL_SOURCE_ONLY=1 source "$1"; declare -F gt_check' _ "$REPO/util/installer/gt-install.sh"
  [ "$status" -eq 0 ]
  [ "$output" = gt_check ]
}

@test "unsupported modes never attempt installation" {
  for mode in --answers --install ''; do
    run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" "$mode"
    [ "$status" -eq 2 ]
    [[ "$output" == *'Use --check'* ]]
  done
}

@test "non-root check is refused before any probes" {
  [ "$(id -u)" -ne 0 ] || skip 'requires regular test user'
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --check
  [ "$status" -eq 2 ]
  [[ "$output" == *'as root'* ]]
}

@test "literal parsers never execute configuration or downloaded scripts" {
  printf 'java_required=25\nnode_required="^22.22.3"\nangular_cli_required=22\ntouch %s/pwned\n' "$ROOT" > "$SCRATCH/versions"
  gt_parse_requirements "$SCRATCH/versions"
  [ "$JAVA_REQUIRED" = 25 ]
  [ ! -e "$ROOT/pwned" ]
  printf 'java_required=$(touch %s/pwned)\n' "$ROOT" >> "$SCRATCH/versions"
  run gt_parse_requirements "$SCRATCH/versions"
  [ "$status" -ne 0 ]
  [ ! -e "$ROOT/pwned" ]
}

@test "Node range checks implement caret floors, major ceilings and the open-ended branch" {
  range='^22.22.3 || ^24.15.0 || >=26.0.0'
  for version in 22.22.3 22.99.0 24.15.0 26.0.0 27.1.0; do gt_node_satisfies "$version" "$range"; done
  for version in 22.22.2 23.0.0 24.14.9 25.0.0 26.0.0-rc.1 invalid; do
    run gt_node_satisfies "$version" "$range"
    [ "$status" -ne 0 ]
  done
  gt_node_satisfies 0.2.5 '^0.2.3'
  run gt_node_satisfies 0.3.0 '^0.2.3'
  [ "$status" -ne 0 ]
}

@test "unknown range syntax and duplicate requirement assignments are rejected" {
  run gt_node_range_valid '>=22; touch /tmp/pwned'
  [ "$status" -ne 0 ]
  printf 'java_required=25\njava_required=26\nnode_required="^22.22.3"\nangular_cli_required=22\n' > "$SCRATCH/versions"
  run gt_parse_requirements "$SCRATCH/versions"
  [ "$status" -ne 0 ]
}

@test "offline source lookup retains explicitly provisional floors" {
  gt_source_revision
  [ "${FACT[source.requirements]}" = fallback ]
  [ "${FACT[source.commit]}" = unknown ]
  [[ "${NOTES[*]}" == *'built-in floors are provisional'* ]]
}

@test "source lookup fetches both files at the resolved immutable commit" {
  ONLINE=yes
  gt_source_revision
  [ "${FACT[source.requirements]}" = remote ]
  [ "${FACT[source.collation]}" = yes ]
  [ "$(grep -c '0000000000000000000000000000000000000001/' "$PROBES")" -eq 2 ]
  ! grep -q 'npm install' "$PROBES"
}

@test "source lookup without git reads the same commit from the ref advertisement" {
  ONLINE=yes GIT_MISSING=yes
  gt_source_revision
  [ "${FACT[source.commit]}" = 0000000000000000000000000000000000000001 ]
  [ "${FACT[source.requirements]}" = remote ]
  grep -q 'info/refs?service=git-upload-pack' "$PROBES"
  ONLINE=no
  gt_source_revision
  [ "${FACT[source.requirements]}" = fallback ]
}

@test "system inventory reads memory independently of translated free output" {
  gt_system
  [ "${FACT[memory.MemTotal]}" = 7812 ]
  [ "${FACT[dpkg.lock]}" = free ]
  [[ "${NOTES[*]}" == *'APT metadata'* ]]
  ! grep -q '^free' "$PROBES"
}

@test "invalid completed journal cannot masquerade as a completed installation" {
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/etc/systemd/system"
  printf 'schema=1\nstatus=complete\nbuilt_commit=abc\nsecrets=TOP_SECRET\n' > "$ROOT/var/lib/gt-install/state"
  touch "$ROOT/etc/systemd/system/grafioschtrader.service"
  gt_installation
  [ "${FACT[host.class]}" = invalid-state ]
  [[ "${FACT[*]}" != *TOP_SECRET* ]]
}

@test "running state reports the completed steps without executing the file" {
  mkdir -p "$ROOT/var/lib/gt-install"
  printf 'status=running\nstep.2=complete\nstep.3=pending\ntouch %s/pwned\n' "$ROOT" > "$ROOT/var/lib/gt-install/state"
  gt_installation
  [ "${FACT[host.class]}" = unfinished ]
  [ "${FACT[state.completed_steps]}" = step.2 ]
  [ ! -e "$ROOT/pwned" ]
}

@test "classic and foreign partial installations are distinguished" {
  mkdir -p "$ROOT/etc/systemd/system" "$ROOT/home/grafioschtrader"
  touch "$ROOT/etc/systemd/system/grafioschtrader.service"
  gt_installation
  [ "${FACT[host.class]}" = foreign-partial ]
  touch "$ROOT/home/grafioschtrader/gtvar.sh" "$ROOT/home/grafioschtrader/gtupdate.sh"
  gt_installation
  [ "${FACT[host.class]}" = classic ]
}

@test "stopped Docker installations are detected and unknown state is not fresh" {
  fake_executable docker
  IMAGES=ghcr.io/grafioschtrader/grafioschtrader-backend:latest
  gt_installation
  [ "${FACT[host.class]}" = docker ]
  mkdir -p "$ROOT/var/lib/gt-install"
  printf 'status=invalid\n' > "$ROOT/var/lib/gt-install/state"
  gt_installation
  [ "${FACT[host.class]}" = invalid-state ]
}

@test "JREs are not accepted as JDKs and release files are parsed as data" {
  fake_executable java
  unset JAVA_HOME
  mkdir -p "$ROOT/opt/jre25/bin" "$ROOT/opt/jdk25/bin"
  printf 'JAVA_VERSION="25.0.1"\nIMPLEMENTOR="Test vendor"\n' > "$ROOT/opt/jre25/release"
  cp "$ROOT/opt/jre25/release" "$ROOT/opt/jdk25/release"
  printf '#!/bin/sh\nexit 0\n' > "$ROOT/opt/jdk25/bin/javac"
  cp "$ROOT/opt/jdk25/bin/javac" "$ROOT/opt/jdk25/bin/java"
  chmod +x "$ROOT/opt/jdk25/bin/javac"
  chmod +x "$ROOT/opt/jdk25/bin/java"
  # The interpreter-only Zero VM of Debian's armhf build has a JDK layout but no JIT.
  cp -r "$ROOT/opt/jdk25" "$ROOT/opt/jdk25-zero"
  mkdir -p "$ROOT/opt/jdk25/lib/server" "$ROOT/opt/jdk25-zero/lib/zero"
  touch "$ROOT/opt/jdk25/lib/server/libjvm.so" "$ROOT/opt/jdk25-zero/lib/zero/libjvm.so"
  gt_java
  [ "${FACT[java.suitable]}" = "$ROOT/opt/jdk25" ]
  [ "${#JDKS[@]}" -ge 3 ]
  [[ "${JDKS[*]}" == *"$ROOT/opt/jdk25-zero version=25.0.1 vendor=Test vendor javac=yes jit=no"* ]]
  rm -r "$ROOT/opt/jdk25"
  gt_java
  [ "${FACT[java.suitable]}" = absent ]
}

@test "inactive socket-activated database is never connected to or started" {
  fake_executable mariadb
  FACT[packages]=known
  PACKAGE[mariadb-server]=1:11.8.3-1
  SOCKET_ACTIVE=yes
  gt_database
  [[ "${FACT[database.socket]}" == *'ActiveState=active'* ]]
  [ "${FACT[database.gt_tables]}" = unknown ]
  ! grep -q -- '--protocol=SOCKET' "$PROBES"
  ! grep -q 'systemctl start' "$PROBES"
}

@test "failed database authentication is unknown, never absent" {
  fake_executable mariadb
  FACT[packages]=known
  SERVICE_ACTIVE=yes SQL_FAIL=yes
  PACKAGE[mariadb-server]=1:11.8.3-1
  gt_database
  [ "${FACT[database.query]}" = unknown ]
  [ "${FACT[database.gt_tables]}" = unknown ]
  [ "${FACT[database.gt_user]}" = unknown ]
}

@test "database inventory records schemas, root socket auth and existing users without hashes" {
  fake_executable mariadb
  FACT[packages]=known SERVICE_ACTIVE=yes
  gt_database
  [ "${FACT[database.query]}" = ok ]
  [ "${FACT[database.gt_tables]}" = 0 ]
  [ "${FACT[database.gt_user]}" = present ]
  [ "${FACT[database.root_socket]}" = 1 ]
  [[ "${FACT[database.schemas]}" == *phpmyadmin* ]]
}

@test "disk needs sharing a device are summed" {
  gt_disk /home 4096
  gt_disk /var/lib/mysql 2048
  gt_disk /opt 300
  [ "${#DISK_NEED[@]}" -eq 1 ]
  [ "${DISK_NEED[*]}" -eq 6444 ]
}

@test "disk device IDs follow symlinks to a separate filesystem" {
  rmdir "$ROOT/opt"
  ln -s /dev/shm "$ROOT/opt"
  gt_disk /home 4096
  gt_disk /opt 300
  [ "${#DISK_NEED[@]}" -eq 2 ]
}

@test "version probes bypass Maven startup files and Node preload hooks" {
  fake_executable mvn
  fake_executable node
  gt_runtimes
  [ "${FACT[maven.version]}" = 3.9.9 ]
  [ "${FACT[node.version]}" = 22.22.3 ]
  grep -q 'MAVEN_SKIP_RC=1' "$PROBES"
  grep -q 'NODE_OPTIONS= node --version' "$PROBES"
}

@test "piped scripts are refused before inventory" {
  run bash -c 'unset GT_INSTALL_SOURCE_ONLY; cat "$1" | bash -s -- --check' _ "$REPO/util/installer/gt-install.sh"
  [ "$status" -eq 2 ]
  [[ "$output" == *'piped execution is refused'* ]]
}

@test "compatibility preserves shared Node consumers and refuses a non-empty database" {
  collect_check
  FACT[node.version]=20.19.0 FACT[node.consumers]=yes FACT[database.gt_tables]=20
  gt_compatibility
  [ "${ACTION[node]}" = isolate ]
  [ "${ACTION[database]}" = block ]
}

@test "distribution derivatives and supported architecture combinations are recognized" {
  collect_check
  for distro in debian ubuntu; do
    for arch in amd64 arm64; do
      FACT[os.ID]=$distro FACT[architecture]=$arch
      if [[ "$distro" == debian ]]; then releases='12 13'; else releases='24.04 26.04'; fi
      for release in $releases; do
        FACT[os.VERSION_ID]=$release
        gt_compatibility
        [ "${ACTION[platform]}" = reuse ]
      done
    done
  done
  FACT[os.ID]=raspbian FACT[os.ID_LIKE]=debian FACT[os.VERSION_ID]=12
  gt_compatibility
  [ "${ACTION[platform]}" = reuse ]
}

@test "unsupported systems and MySQL are blocking findings" {
  collect_check
  FACT[os.ID]=fedora FACT[os.VERSION_ID]=44 FACT[os.ID_LIKE]=unknown
  FACT[database.vendor]=mysql FACT[init]=init
  gt_compatibility
  [ "${ACTION[platform]}" = block ]
  [ "${ACTION[mariadb]}" = block ]
  [ "${ACTION[init]}" = block ]
}

@test "occupied backend ports are replaced with distinct free recommendations" {
  FACT[listeners]=$'LISTEN 0 128 127.0.0.1:9090 0.0.0.0:* users:(("other"))\nLISTEN 0 128 [::]:8080 [::]:* users:(("other"))'
  FACT[web.nginx]=absent FACT[web.apache2]=absent
  gt_web_recommendation
  [ "${FACT[ports.backend_primary]}" = 9091 ]
  [ "${FACT[ports.backend_http]}" = 8081 ]
}

@test "listening web server wins when both are installed and foreign proxies are retained" {
  FACT[web.nginx]=1 FACT[web.apache2]=1
  FACT[listeners]='LISTEN 0 128 0.0.0.0:80 0.0.0.0:* users:(("apache2",pid=12,fd=3))'
  gt_web_recommendation
  [ "${REASON[web]}" = apache2 ]
  FACT[listeners]='LISTEN 0 128 0.0.0.0:80 0.0.0.0:* users:(("caddy",pid=12,fd=3))'
  gt_web_recommendation
  [[ "${REASON[web]}" == *'foreign proxy'* ]]
  [ "${FACT[ports.proxy_http]}" = 8081 ]
}

@test "DuckDNS inventory reports locations without exposing tokens" {
  mkdir -p "$ROOT/etc/cron.d"
  echo '* * * * * root curl https://duckdns.org/update?token=TOP_SECRET' > "$ROOT/etc/cron.d/duckdns"
  gt_dynamic_dns
  [ "${FACT[dns.duckdns]}" = yes ]
  [[ "${FACT[*]}" != *TOP_SECRET* ]]
}

@test "stopped Node services and wrapper commands count as consumers without leaking arguments" {
  mkdir -p "$ROOT/etc/systemd/system"
  printf '[Service]\nExecStart=/bin/sh -c "node /opt/homebridge/server.js --token TOP_SECRET"\n' > "$ROOT/etc/systemd/system/homebridge.service"
  printf '[Service]\nExecStart=/usr/bin/kmod static-nodes\n' > "$ROOT/etc/systemd/system/kmod-static-nodes.service"
  gt_consumers
  [ "${FACT[node.consumers]}" = yes ]
  [[ "${CONSUMERS[*]}" == *homebridge.service* && "${CONSUMERS[*]}" != *TOP_SECRET* ]]
  [[ "${CONSUMERS[*]}" != *kmod-static-nodes* ]]
}

@test "compact nginx blocks retain their own host names, ports and routes" {
  mkdir -p "$ROOT/etc/nginx/sites-enabled"
  printf '%s\n' 'server { listen 80; server_name one.example; location /api { proxy_pass http://localhost:8080; } }' \
    'server { listen 443 ssl; server_name two.example; root /var/www/two; }' > "$ROOT/etc/nginx/sites-enabled/sites"
  gt_web
  [[ "${WEB[*]}" == *'name=one.example port=80 scheme=http'* ]]
  [[ "${WEB[*]}" == *'name=two.example port=443 scheme=https'* ]]
  [[ "${WEB[*]}" != *'name=one.example port=443'* ]]
  [[ "${WEB[*]}" == *'location /api'* ]]
}

@test "Apache enabled includes are inventoried without executing configtest" {
  mkdir -p "$ROOT/etc/apache2/sites-enabled"
  printf 'IncludeOptional sites-enabled/*.conf\n' > "$ROOT/etc/apache2/apache2.conf"
  printf '<VirtualHost *:8085>\nServerName apache.example\nDocumentRoot /var/www/app\nProxyPass /api http://user:TOP_SECRET@localhost:9090/\n</VirtualHost>\n' > "$ROOT/etc/apache2/sites-enabled/app.conf"
  gt_web
  [[ "${WEB[*]}" == *'name=apache.example port=8085 scheme=http'* ]]
  [[ "${WEB[*]}" != *TOP_SECRET* ]]
  ! grep -q apache2ctl "$PROBES"
}

@test "public address and release probes exclude ULA addresses and signed URLs" {
  ONLINE=yes
  gt_network
  [ "${FACT[network.lan_ipv4]}" = 192.168.1.2 ]
  [ "${FACT[network.global_ipv6]}" = 2001:db8::12 ]
  [[ "${NETWORK[*]}" != *SECRET* ]]
}

@test "offline full fixture check is read-only and reports all sections" {
  before=$(find "$ROOT" -type f -exec sha256sum {} + | sort)
  run gt_check
  [[ "$status" == 0 || "$status" == 2 ]]
  [[ "$output" == *host.class=fresh* && "$output" == *'[certificates]'* && "$output" == *'[network]'* ]]
  [ "$(find "$ROOT" -type f -exec sha256sum {} + | sort)" = "$before" ]
  [ ! -e "$ROOT/var/lib/gt-install" ]
  ! grep -E 'apt-get|npm install|systemctl (start|stop|restart)|update-alternatives --set|nginx -T|apache2ctl' "$PROBES"
}

@test "German locale changes headings while stable fact keys remain English" {
  LANG_CODE=de
  run gt_check
  [[ "$output" == *Bestandsaufnahme* && "$output" == *host.class=fresh* ]]
}
