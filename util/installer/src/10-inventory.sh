gt_system() {
  local field value os mem pidone lists newest now
  os=$(gt_path /etc/os-release)
  for field in ID ID_LIKE VERSION_ID VERSION_CODENAME; do
    value=$(gt_literal "$os" "$field") || value=unknown
    FACT[os.$field]=$value
  done
  gt_capture architecture dpkg --print-architecture
  mem=$(gt_path /proc/meminfo)
  for field in MemTotal MemAvailable SwapTotal; do
    value=$(awk -v key="$field:" '$1==key && $2~/^[0-9]+$/ {print int($2/1024)}' "$mem" 2>/dev/null)
    FACT[memory.$field]=${value:-unknown}
  done
  pidone=$(cat "$(gt_path /proc/1/comm)" 2>/dev/null) || pidone=unknown
  FACT[init]=$pidone
  gt_capture timezone timedatectl show --property=Timezone --value
  # fuser returns 1 for a free lock, 127 when unavailable.
  if value=$(gt_probe fuser "$(gt_path /var/lib/dpkg/lock-frontend)"); then FACT[dpkg.lock]=$value
  elif [[ $? == 1 ]]; then FACT[dpkg.lock]=free
  else FACT[dpkg.lock]=unknown; gt_note UNKNOWN unavailable dpkg.lock; fi
  lists=$(gt_path /var/lib/apt/lists)
  newest=$(find "$lists" -maxdepth 1 -type f \( -name '*Packages*' -o -name '*InRelease' -o -name '*Release' \) \
    -printf '%T@\n' 2>/dev/null | sort -nr | head -n 1)
  newest=${newest%%.*}; now=$(date +%s)
  FACT[apt.age_hours]=unknown
  if [[ "$newest" =~ ^[0-9]+$ ]]; then FACT[apt.age_hours]=$(((now-newest)/3600)); fi
  if [[ "${FACT[apt.age_hours]}" == unknown ]] || (( ${FACT[apt.age_hours]} > 24 )); then gt_note WARN stale; fi
  gt_capture listeners ss -Htlnp
  gt_capture firewall.ufw ufw status verbose
  if value=$(gt_probe nft list ruleset); then
    FACT[firewall.nftables]=$([[ -n "$value" ]] && echo present || echo empty)
  else FACT[firewall.nftables]=unknown; fi
  if value=$(gt_probe iptables-save); then
    FACT[firewall.iptables]=$([[ "$value" == *'-A '* ]] && echo present || echo empty)
  else FACT[firewall.iptables]=unknown; fi
}

gt_packages() {
  local name version state value
  PACKAGE=() CANDIDATE=()
  # shellcheck disable=SC2016
  if value=$(gt_probe dpkg-query -W '-f=${binary:Package}\t${Version}\t${db:Status-Abbrev}\n'); then
    while IFS=$'\t' read -r name version state; do
      [[ "$state" == ii* ]] && PACKAGE[${name%%:*}]=$version
    done <<< "$value"
    FACT[packages]=known
  else FACT[packages]=unknown; gt_note UNKNOWN unavailable packages; fi
  for name in "openjdk-$JAVA_REQUIRED-jdk-headless" maven nodejs mariadb-server nginx apache2; do
    # Do not let apt-cache create its binary caches during a read-only run.
    value=$(gt_probe apt-cache -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= policy "$name") || value=
    version=$(awk '/Candidate:/ {print $2; exit}' <<< "$value")
    CANDIDATE[$name]=${version:-unknown}
    FACT[apt.candidate.$name]=${version:-unknown}
  done
}

gt_service() {
  local unit=$1 value
  value=$(gt_probe systemctl show "$unit" --property=LoadState --property=ActiveState --property=SubState --property=UnitFileState) || value=unknown
  printf '%s' "$value"
}
gt_installed_unit() { [[ "$(gt_service "$1")" == *'LoadState=loaded'* ]]; }
gt_installation() {
  local state_file status key unit=no user=no vars=no updater=no jar=no sudoers=no file images
  state_file=$(gt_path /var/lib/gt-install/state)
  FACT[host.class]=fresh
  FACT[containers]=absent
  if command -v docker >/dev/null; then
    # -a also detects stopped installations. Never inspect container environment variables.
    if images=$(gt_probe docker ps -a --format '{{.Image}}'); then FACT[containers]=$images
    else FACT[containers]=unknown; gt_note UNKNOWN unavailable 'Docker container inventory'; fi
  fi
  gt_installed_unit grafioschtrader.service && unit=yes
  [[ -f "$(gt_path /etc/systemd/system/grafioschtrader.service)" ]] && unit=yes
  gt_probe getent passwd grafioschtrader >/dev/null && user=yes
  [[ -f "$(gt_path /home/grafioschtrader/gtvar.sh)" ]] && vars=yes
  [[ -f "$(gt_path /home/grafioschtrader/gtupdate.sh)" ]] && updater=yes
  [[ -f "$(gt_path /etc/sudoers.d/grafioschtrader)" ]] && sudoers=yes
  for file in "$(gt_path /home/grafioschtrader)"/grafioschtrader-server-*.jar; do [[ -f "$file" ]] && jar=yes; done
  FACT[host.pieces]="user=$user unit=$unit gtvar=$vars gtupdate=$updater jar=$jar sudoers=$sudoers"
  if [[ -e "$state_file" ]]; then
    status=$(gt_literal "$state_file" status) || status=unknown
    FACT[state.status]=$status
    for key in schema installer_commit planned_commit built_commit completed_at; do
      FACT[state.$key]=$(gt_literal "$state_file" "$key") || FACT[state.$key]=unknown
    done
    # Only completion markers are shown, never arbitrary state values or the secrets file.
    FACT[state.completed_steps]=$(awk -F= '$1~/^step[._][a-zA-Z0-9_.-]+$/ && $2~/^(complete|completed|ok|1)$/ {print $1}' "$state_file" 2>/dev/null)
    case "$status" in complete) FACT[host.class]=completed ;; running) FACT[host.class]=unfinished ;; *) FACT[host.class]='invalid-state' ;; esac
  elif [[ "$unit$vars$updater" == yesyesyes ]]; then FACT[host.class]=classic
  elif [[ "$user$unit$vars$updater$jar$sudoers" == *yes* ]]; then FACT[host.class]=foreign-partial
  elif [[ "${FACT[containers]}" == *ghcr.io/grafioschtrader/grafioschtrader-* ]]; then FACT[host.class]=docker
  elif [[ "${FACT[containers]}" == unknown ]]; then FACT[host.class]=unknown
  fi
}

gt_java() {
  local dir executable version vendor javac value selected
  local -A seen=()
  # update-alternatives queries do not change the selection or its auto/manual mode.
  FACT[java.alternatives]=$(gt_probe update-alternatives --query java) || FACT[java.alternatives]=unknown
  FACT[javac.alternatives]=$(gt_probe update-alternatives --query javac) || FACT[javac.alternatives]=unknown
  selected=$(command -v java) || selected=
  FACT[java.path]=${selected:-absent} FACT[java.suitable]=absent FACT[java.version]=absent
  JDKS=()
  local -a homes=()
  for dir in "${STATE[java_home]:-}" "${JAVA_HOME:-}"; do
    [[ -d "$dir" ]] && homes+=("$dir")
  done
  for dir in "$(gt_path /usr/lib/jvm)"/* "$(gt_path /usr/local)"/jdk-* "$(gt_path /opt)"/*; do
    [[ -d "$dir" ]] && homes+=("$dir")
  done
  value=$(gt_probe update-alternatives --list java) || value=
  while IFS= read -r executable; do
    [[ -n "$executable" ]] && homes+=("$(dirname "$(dirname "$executable")")")
  done <<< "$value"
  if [[ -n "$selected" ]]; then
    executable=$(readlink -f "$selected")
    homes+=("$(dirname "$(dirname "$executable")")")
  fi
  for dir in "${homes[@]}"; do
    dir=$(readlink -f "$dir") || continue
    [[ -z "${seen[$dir]:-}" && -r "$dir/release" ]] || continue
    seen[$dir]=1
    version=$(gt_literal "$dir/release" JAVA_VERSION) || version=unknown
    vendor=$(gt_literal "$dir/release" IMPLEMENTOR) || vendor=unknown
    javac=no; [[ -x "$dir/bin/java" && -x "$dir/bin/javac" ]] && javac=yes
    JDKS+=("$dir version=$version vendor=$vendor javac=$javac")
    if [[ "$version" =~ ^([0-9]+)(\.|$) && "$javac" == yes ]] && (( BASH_REMATCH[1] >= JAVA_REQUIRED )); then
      if [[ "${FACT[java.suitable]}" == absent || ( "${FACT[java.version]%%.*}" != "$JAVA_REQUIRED" && "${version%%.*}" == "$JAVA_REQUIRED" ) ]]; then
        FACT[java.suitable]=$dir FACT[java.version]=$version
      fi
    fi
  done
  return 0
}
gt_maven_probe() {
  local executable=$1 home=$2
  local -a environment=(-u JAVA_HOME -u _JAVA_OPTIONS MAVEN_SKIP_RC=1 MAVEN_OPTS="-Djava.io.tmpdir=$SCRATCH -Dstyle.color=never"
    MAVEN_ARGS= JAVA_TOOL_OPTIONS= JDK_JAVA_OPTIONS=)
  [[ "$home" == absent || "$home" == pending ]] || environment+=("JAVA_HOME=$home" "PATH=$home/bin:$PATH")
  (cd "$SCRATCH" && gt_probe env "${environment[@]}" "$executable" -v)
}

gt_maven_version() {
  sed -E $'s/\033\\[[0-9;]*[mK]//g' <<< "$1" | sed -nE 's/^Apache Maven ([0-9]+\.[0-9]+\.[0-9]+).*/\1/p' | head -n 1
}

gt_runtimes() {
  local executable value version dir
  FACT[maven.version]=absent FACT[maven.path]=absent
  local -a commands=()
  [[ ! -x "${STATE[maven]:-}" ]] || commands+=("${STATE[maven]}")
  executable=$(command -v mvn) && commands+=("$executable")
  for dir in "$(gt_path /opt)"/maven "$(gt_path /opt)"/apache-maven-*; do
    [[ -x "$dir/bin/mvn" ]] && commands+=("$dir/bin/mvn")
  done
  for executable in "${commands[@]}"; do
    # Version checks must not source mavenrc files or project-local JVM options.
    if value=$(gt_maven_probe "$executable" "${FACT[java.suitable]}"); then
      version=$(gt_maven_version "$value")
      if [[ -n "$version" ]]; then
        FACT[maven.version]=$version FACT[maven.path]=$executable
        gt_version_at_least "$version" 3.8 && break
      fi
    else FACT[maven.version]=unknown; fi
  done
  executable=$(command -v node) || executable=absent
  FACT[node.path]=$executable FACT[node.version]=absent FACT[node.origin]=absent
  if [[ "$executable" != absent ]]; then
    value=$(gt_probe env NODE_OPTIONS= node --version) || value=unknown
    FACT[node.version]=${value#v}
    case "$executable" in
      */.nvm/*) FACT[node.origin]=nvm ;;
      /usr/local/*) FACT[node.origin]=local-tarball ;;
      /opt/*) FACT[node.origin]=isolated-tarball ;;
      *)
        FACT[node.origin]=unknown
        if [[ -n "${PACKAGE[nodejs]:-}" ]]; then
          FACT[node.origin]=distribution
          [[ "${PACKAGE[nodejs]}" == *nodesource* ]] && FACT[node.origin]=nodesource
        fi ;;
    esac
  fi
  FACT[npm.packages]=unknown FACT[angular.version]=unknown FACT[semver.version]=unknown
  # npm ls is read-only except its cache/logs, which must remain inside our disposable directory.
  if value=$(gt_probe env NODE_OPTIONS= npm_config_cache="$SCRATCH/npm" npm_config_update_notifier=false \
      npm_config_logs_max=0 npm ls --global --depth=0 --parseable); then
    FACT[npm.packages]=$value
    while IFS= read -r dir; do
      case "$dir" in
        */@angular/cli|*/semver)
          version=$(sed -nE 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"([0-9]+\.[0-9]+\.[0-9]+)".*/\1/p' "$dir/package.json" 2>/dev/null | head -n 1)
          [[ "$dir" == */semver ]] && FACT[semver.version]=${version:-unknown} || FACT[angular.version]=${version:-unknown}
          ;;
      esac
    done <<< "$value"
  fi
}

gt_consumers() {
  local kind value file executable command_line
  for kind in node java; do
    FACT[$kind.consumers]=no
    # Avoid printing process arguments, which frequently contain passwords and API tokens.
    if value=$(gt_probe pgrep -x "$kind"); then
      while IFS= read -r value; do
        [[ "$value" =~ ^[0-9]+$ ]] || continue
        if [[ "$kind" == java ]]; then
          command_line=$(tr '\0' ' ' < "$(gt_path "/proc/$value/cmdline")" 2>/dev/null) || command_line=''
          [[ "$command_line" != *grafioschtrader-server-*.jar* ]] || continue
        fi
        CONSUMERS+=("$kind pid=$value")
        FACT[$kind.consumers]=yes
      done <<< "$value"
    elif [[ $? != 1 ]]; then FACT[$kind.consumers]=unknown; fi
  done
  value=$(gt_probe apt-cache -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= rdepends --installed nodejs) || value=unknown
  FACT[node.reverse_dependencies]=$value
  if [[ "$value" == unknown ]]; then FACT[node.consumers]=unknown
  elif [[ -n "$(sed '1,2d;/^[[:space:]]*$/d' <<< "$value")" ]]; then FACT[node.consumers]=yes; fi
  while IFS= read -r -d '' file; do
    [[ "$file" != */grafioschtrader.service ]] || continue
    while IFS= read -r command_line; do
      executable=${command_line%% *}
      # Inspect wrappers too, but keep arguments and Environment values out of the report.
      command_line=${command_line//\"/ }
      command_line=${command_line//\'/ }
      if [[ "$command_line" =~ (^|[/[:space:]])(node|nodejs|npm)([[:space:]]|$) ]]; then
        CONSUMERS+=("node unit=$file executable=$executable"); FACT[node.consumers]=yes
      fi
      if [[ "$command_line" =~ (^|[/[:space:]])java([[:space:]]|$) ]]; then
        CONSUMERS+=("java unit=$file executable=$executable"); FACT[java.consumers]=yes
      fi
    done < <(sed -nE 's/^[[:space:]]*ExecStart[[:space:]]*=[-+!:@]*(.*)/\1/p' "$file" 2>/dev/null)
  done < <(find "$(gt_path /etc/systemd/system)" "$(gt_path /lib/systemd/system)" "$(gt_path /usr/lib/systemd/system)" \
    -type f \( -name '*.service' -o -name '*.conf' \) -print0 2>/dev/null)
}

gt_database() {
  local client value package version collation
  local -a options=(--no-defaults --protocol=SOCKET --user=root --batch --skip-column-names --connect-timeout=4)
  [[ -z "${1:-}" ]] || options[0]="--defaults-file=$1"
  FACT[database.vendor]=absent FACT[database.version]=absent FACT[database.query]=unknown
  FACT[database.schemas]=unknown FACT[database.users]=unknown FACT[database.gt_tables]=unknown FACT[database.gt_user]=unknown
  FACT[database.datadir]=/var/lib/mysql FACT[database.root_socket]=unknown FACT[database.active]=no
  FACT[database.service]=$(gt_service mariadb.service)
  FACT[database.socket]=$(gt_service mariadb.socket)
  for package in mariadb-server mysql-server mysql-community-server; do
    if [[ -n "${PACKAGE[$package]:-}" ]]; then
      FACT[database.vendor]=${package%%-server*}
      version=${PACKAGE[$package]#*:}; FACT[database.version]=${version%%-*}
    fi
  done
  [[ "${FACT[packages]}" == unknown ]] && FACT[database.vendor]=unknown
  FACT[database.buffer_pool_config]=$(grep -rlE '^[[:space:]]*innodb[-_]buffer[-_]pool[-_]size[[:space:]]*=' \
    "$(gt_path /etc/mysql)" 2>/dev/null || true)
  # Connecting to an inactive socket-activated server would START it, violating --check.
  if [[ "${FACT[database.service]}" != *'ActiveState=active'* ]] && ! gt_probe pgrep -x mariadbd >/dev/null &&
      ! gt_probe pgrep -x mysqld >/dev/null; then
    gt_note UNKNOWN unavailable 'database SQL (server inactive; socket activation deliberately avoided)'
    return 0
  fi
  FACT[database.active]=yes
  [[ "${FACT[database.vendor]}" != absent ]] || FACT[database.vendor]=unknown
  client=$(command -v mariadb || command -v mysql) || { gt_note UNKNOWN unavailable 'database client'; return 0; }
  # Preparation may supply a private options file; inventory uses no option files.
  if ! value=$(gt_probe "$client" "${options[@]}" -e "SELECT VERSION(), @@datadir, @@collation_server;"); then
    gt_note UNKNOWN unavailable 'database SQL (root socket login)'
    return 0
  fi
  FACT[database.query]=ok
  IFS=$'\t' read -r version value collation <<< "$value"
  FACT[database.version]=${version%%-*} FACT[database.datadir]=$value FACT[database.collation]=$collation
  [[ "$version" == *MariaDB* ]] && FACT[database.vendor]=mariadb || FACT[database.vendor]=mysql
  local sql='SELECT s.SCHEMA_NAME, COUNT(t.TABLE_NAME), COALESCE(SUM(t.TABLE_NAME="flyway_schema_history"),0) FROM information_schema.SCHEMATA s LEFT JOIN information_schema.TABLES t ON t.TABLE_SCHEMA=s.SCHEMA_NAME GROUP BY s.SCHEMA_NAME;'
  if value=$(gt_probe "$client" "${options[@]}" -e "$sql"); then
    FACT[database.schemas]=$value
    FACT[database.gt_tables]=$(awk -F '\t' '$1=="grafioschtrader" {print $2; found=1} END {if(!found) print "absent"}' <<< "$value")
  fi
  if value=$(gt_probe "$client" "${options[@]}" \
      -e 'SELECT User,Host,plugin FROM mysql.user;'); then
    FACT[database.users]=$value FACT[database.gt_user]=absent
    [[ "$(awk -F '\t' '$1=="grafioschtrader" && $2=="localhost" {print "yes"}' <<< "$value")" == yes ]] && FACT[database.gt_user]=present
  fi
  if value=$(gt_probe "$client" "${options[@]}" \
      -e "SELECT COUNT(*) FROM mysql.global_priv WHERE User='root' AND Host='localhost' AND priv LIKE '%unix_socket%';"); then
    FACT[database.root_socket]=$value
  fi
  if value=$(gt_probe "$client" "${options[@]}" \
      -e "SHOW SESSION VARIABLES LIKE 'character_set_collations';"); then FACT[database.character_set_collations]=${value:-unavailable-before-11.2}; fi
  if awk '$4 ~ /:3306$/ && $4 !~ /^(127\.|\[?::1\]?:)/ {found=1} END {exit !found}' <<< "${FACT[listeners]:-unknown}"; then
    gt_note WARN unavailable 'MariaDB listens beyond loopback; leave shared server networking unchanged'
  fi
}

gt_disk() {
  local requested=$1 need=$2 actual device available filesystem value
  actual=$(gt_path "$requested")
  while [[ ! -d "$actual" && "$actual" != / ]]; do actual=$(dirname "$actual"); done
  device=$(stat -Lc %d "$actual" 2>/dev/null) || device=unknown
  value=$(df -Pk "$actual" 2>/dev/null) || value=
  available=$(awk 'NR==2 {print int($4/1024)}' <<< "$value")
  filesystem=$(stat -Lf -c %T "$actual" 2>/dev/null) || filesystem=unknown
  DISKS+=("$requested device=$device free_MiB=${available:-unknown} filesystem=$filesystem required_MiB=$need")
  if [[ "$device" =~ ^[0-9]+$ && "$available" =~ ^[0-9]+$ ]]; then
    DISK_FREE[$device]=$available DISK_NEED[$device]=$((${DISK_NEED[$device]:-0}+need))
  else FACT[disk.unknown]=yes; gt_note UNKNOWN unavailable "disk $requested"; fi
}

gt_network() {
  local value interface host url status file
  gt_capture network.ipv4_route ip -4 route get 1.1.1.1
  FACT[network.lan_ipv4]=$(awk '{for(i=1;i<NF;i++) if($i=="src") print $(i+1)}' <<< "${FACT[network.ipv4_route]}")
  gt_capture network.ipv6_route ip -6 route show default
  interface=$(awk '{for(i=1;i<NF;i++) if($i=="dev") {print $(i+1); exit}}' <<< "${FACT[network.ipv6_route]}")
  FACT[network.global_ipv6]=unknown
  if [[ "$interface" =~ ^[a-zA-Z0-9_.:-]+$ ]]; then
    value=$(gt_probe ip -6 addr show dev "$interface" scope global -temporary -deprecated) || value=
    FACT[network.global_ipv6]=$(awk '$1=="inet6" && $2!~/^(f[cd]|fe[89ab])/ {sub(/\/.*/,"",$2); print $2}' <<< "$value")
  fi
  value=$(gt_probe curl --disable -4 -fsS --connect-timeout 4 --max-time 8 https://ifconfig.co/ip) || value=unknown
  [[ "$value" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]] || value=unknown
  FACT[network.public_ipv4]=$value
  local -A hosts=([github.com]=1 [raw.githubusercontent.com]=1 [registry.npmjs.org]=1 [repo.maven.apache.org]=1
    [deb.nodesource.com]=1 [nodejs.org]=1 [packages.adoptium.net]=1 [download.bell-sw.com]=1 [dlcdn.apache.org]=1)
  while IFS= read -r -d '' file; do
    while IFS= read -r url; do
      host=${url#*://}; host=${host%%/*}; host=${host##*@}
      [[ "$host" =~ ^[a-zA-Z0-9.-]+(:[0-9]+)?$ ]] && hosts[$host]=1
    done < <(awk '/^[[:space:]]*#/ {next} /^[[:space:]]*(URIs:|deb(-src)?[[:space:]])/ {
      for(i=1;i<=NF;i++) if($i~/^https?:\/\//) print $i
    }' "$file" 2>/dev/null)
  done < <(find "$(gt_path /etc/apt)" -maxdepth 2 -type f \( -name '*.list' -o -name '*.sources' \) -print0 2>/dev/null)
  for host in "${!hosts[@]}"; do
    if status=$(gt_probe curl --disable -sSI -o /dev/null --connect-timeout 3 --max-time 6 \
        -w '%{http_code}' "https://$host/"); then NETWORK+=("$host HTTP=$status"); else NETWORK+=("$host UNKNOWN"); fi
  done
  url=https://github.com/grafioschtrader/grafioschtrader/releases/download/Latest/latest.tar.gz
  if value=$(gt_probe curl --disable -fsSIL -o /dev/null --connect-timeout 3 --max-time 10 -w '%{url_effective}' "$url"); then
    # Redirects to release assets contain signed query parameters. Report only the host.
    host=${value#*://}; host=${host%%/*}; host=${host%%\?*}
    NETWORK+=("frontend-release host=$host reachable=yes")
  else NETWORK+=('frontend-release UNKNOWN'); fi
}

gt_web_directives() {
  # Tokenize nginx outside quotes/comments; Apache directives end at the physical newline.
  # This reads text only, including compact "server { listen 80; ... }" configurations.
  awk -v kind="$2" '
    function emit() {gsub(/^[ \t\r\n]+|[ \t\r\n]+$/, "", text); if(length(text)) print text; text=""}
    {
      for(i=1;i<=length($0);i++) {
        c=substr($0,i,1)
        if(escaped) {text=text c; escaped=0; continue}
        if(c=="\\") {text=text c; escaped=1; continue}
        if(quote!="") {text=text c; if(c==quote) quote=""; continue}
        if(c=="\042" || c=="\047") {quote=c; text=text c; continue}
        if(c=="#") break
        if(kind=="nginx" && (c==";" || c=="{" || c=="}")) {
          if(c=="{") text=text " {"
          emit()
          if(c=="}") print "}"
        } else text=text c
      }
      if(kind=="apache") emit(); else text=text " "
    }
    END {emit()}
  ' "$1"
}

gt_web_status() {
  local label=$1 names=$2 listeners=$3 entry name port scheme result
  [[ -n "$listeners" ]] || listeners=80:http
  [[ -n "$names" ]] || names=127.0.0.1
  for entry in $listeners; do
    port=${entry%:*}; scheme=${entry#*:}
    for name in $names; do
      if [[ ! "$name" =~ ^[a-zA-Z0-9][a-zA-Z0-9.-]*$ ]]; then
        gt_note UNKNOWN unavailable "non-literal vhost name: $label"
        continue
      fi
      if result=$(gt_probe curl --disable --noproxy '*' -sS -o /dev/null --connect-timeout 2 --max-time 4 \
          --resolve "$name:$port:127.0.0.1" -w '%{http_code}' "$scheme://$name:$port/"); then :
      else result=unknown; fi
      WEB+=("$label probe name=$name port=$port scheme=$scheme HTTP=$result")
    done
  done
}

gt_web() {
  local file line key value names port certificate result kind include label listeners
  local index=0 depth block_depth block in_block include_file
  local -A visited=() kinds=()
  FACT[web.apache2]=${PACKAGE[apache2]:-absent} FACT[web.nginx]=${PACKAGE[nginx]:-absent}
  FACT[web.certbot]=${PACKAGE[certbot]:-absent}
  FACT[web.certbot_plugins]="nginx=${PACKAGE[python3-certbot-nginx]:-absent} apache=${PACKAGE[python3-certbot-apache]:-absent}"
  FACT[web.certbot_timer]=$(gt_service certbot.timer)
  FACT[web.cloudflared]=$(gt_service cloudflared.service)
  FACT[web.apache_modules]=$(find "$(gt_path /etc/apache2/mods-enabled)" -maxdepth 1 -name '*.load' -printf '%f\n' 2>/dev/null || true)
  local -a files=()
  for file in "$(gt_path /etc/nginx/nginx.conf)" "$(gt_path /etc/nginx/sites-enabled)"/* "$(gt_path /etc/nginx/conf.d)"/*.conf \
      "$(gt_path /etc/apache2/apache2.conf)" "$(gt_path /etc/apache2/sites-enabled)"/*; do
    if [[ -f "$file" ]]; then
      files+=("$file")
      [[ "$file" == */apache2/* ]] && kinds[$file]=apache || kinds[$file]=nginx
    fi
  done
  while (( index < ${#files[@]} )); do
    file=${files[index]}; index=$((index+1)); kind=${kinds[$file]}
    result=$(readlink -f "$file") || continue
    [[ -z "${visited[$result]:-}" ]] || continue
    visited[$result]=1
    WEB+=("config=$file")
    names='' listeners='' depth=0 block_depth=0 block=0 in_block=0 label=$file
    # Read only selected directives. Never run nginx -T/apachectl: they can open logs on disk.
    while IFS= read -r line; do
      read -r key value <<< "$line"
      if [[ "$key" == server && "$value" == '{' || "$key" == '<VirtualHost' ]]; then
        block=$((block+1)); label="$file#$block"; in_block=1; names='' listeners=''; block_depth=$((depth+1))
        WEB+=("$label server_kind $kind")
        if [[ "$key" == '<VirtualHost' ]]; then
          for result in ${value%>}; do
            port=${result##*:}
            if [[ "$port" =~ ^[0-9]+$ ]]; then
              [[ "$port" == 443 ]] && listeners+=" $port:https" || listeners+=" $port:http"
            fi
          done
        fi
      fi
      if [[ "$line" == '}' && "$depth" == "$block_depth" && "$in_block" == 1 || "$key" == '</VirtualHost>' ]]; then
        gt_web_status "$label" "$names" "$listeners"
        in_block=0
      fi
      case "$key" in
        server_name|ServerName|ServerAlias) names+=" ${value//\"/}"; WEB+=("$label $key $value") ;;
        listen)
          WEB+=("$label listen $value")
          result=${value%% *}; port=${result##*:}
          if [[ "$port" =~ ^[0-9]+$ ]]; then
            [[ "$value" == *ssl* ]] && listeners+=" $port:https" || listeners+=" $port:http"
          else gt_note UNKNOWN unavailable "non-numeric web listen: $file"; fi ;;
        '<VirtualHost') WEB+=("$label $line") ;;
        SSLEngine) [[ "$value" != on ]] || listeners=${listeners//:http/:https} ;;
        root|DocumentRoot|location) WEB+=("$label $key $value") ;;
        ProxyPass|ProxyPassMatch) WEB+=("$label $key ${value%% *}") ;;
        ssl_certificate_key|SSLCertificateKeyFile) WEB+=("$label $key ${value//\"/}") ;;
        ssl_certificate|SSLCertificateFile)
          certificate=${value//\"/}; WEB+=("$label $key $certificate"); gt_certificate "$(gt_path "$certificate")" ;;
        include|Include|IncludeOptional)
          include=${value//\"/}
          if [[ "$include" == *'$'* || "$include" == *' '* ]]; then
            gt_note UNKNOWN unavailable "dynamic web include: $file"
          else
            if [[ "$include" != /* ]]; then
              [[ "$kind" == nginx ]] && include="/etc/nginx/$include" || include="/etc/apache2/$include"
            fi
            result=no
            while IFS= read -r include_file; do
              [[ -f "$include_file" ]] || continue
              files+=("$include_file"); kinds[$include_file]=$kind; result=yes
            done < <(compgen -G "$(gt_path "$include")" | LC_ALL=C sort)
            if [[ "$result" == no && "$key" != IncludeOptional ]]; then gt_note UNKNOWN unavailable "web include: $file"; fi
            # The file is inventoried, but assigning included directives to a parent vhost requires expansion.
            if (( in_block )); then gt_note UNKNOWN unavailable "included vhost context: $file"; fi
          fi ;;
      esac
      [[ "$line" != *'{' ]] || depth=$((depth+1))
      [[ "$line" != '}' ]] || depth=$((depth-1))
    done < <(gt_web_directives "$file" "$kind")
    if (( in_block )); then gt_note UNKNOWN unavailable "unterminated vhost: $file"; fi
  done
  for certificate in "$(gt_path /etc/letsencrypt/live)"/*/fullchain.pem; do
    [[ -f "$certificate" ]] && gt_certificate "$certificate"
  done
  FACT[tls.other_clients]=
  for file in "$(gt_path /root/.acme.sh)" "$(gt_path /root/.lego)" "$(gt_path /etc/dehydrated)" \
      "$(gt_path /var/lib/dehydrated)"; do
    [[ -d "$file" ]] && FACT[tls.other_clients]+="$file "
  done
  return 0
}
gt_certificate() {
  local value
  if value=$(gt_probe openssl x509 -in "$1" -noout -subject -enddate -ext subjectAltName); then
    CERTS+=("$1: $value")
  else CERTS+=("$1: UNKNOWN"); fi
}
gt_dynamic_dns() {
  local file
  FACT[dns.duckdns]=no FACT[dns.ddclient]=no
  [[ "${FACT[containers]:-}" == *duckdns* ]] && FACT[dns.duckdns]=yes
  FACT[dns.updater_files]=
  while IFS= read -r -d '' file; do
    # Only file names leave the collector; cron lines and ddclient files can contain credentials.
    if grep -qiE 'duckdns\.org(/update)?|duckdns' "$file" 2>/dev/null; then
      FACT[dns.duckdns]=yes FACT[dns.updater_files]+="$file "
    fi
  done < <(find "$(gt_path /var/spool/cron/crontabs)" "$(gt_path /etc/cron.d)" \
    "$(gt_path /etc/systemd/system)" "$(gt_path /etc/crontab)" "$(gt_path /etc/ddclient.conf)" \
    -type f -print0 2>/dev/null)
  [[ -f "$(gt_path /etc/ddclient.conf)" ]] && FACT[dns.ddclient]=yes
  return 0
}
