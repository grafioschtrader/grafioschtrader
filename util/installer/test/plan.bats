#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  collect_check
  gt_source_revision
  FACT[source.requirements]=remote FACT[source.commit]=0000000000000000000000000000000000000001
  FACT[source.collation]=yes FACT[apt.age_hours]=0
  FACT[java.suitable]=/opt/jdk-25 FACT[java.version]=25
  FACT[maven.version]=3.9.9 FACT[maven.path]=/opt/maven/bin/mvn
  FACT[node.version]=24.15.0 FACT[node.path]=/usr/bin/node
  FACT[network.public_ipv4]=198.51.100.12 FACT[network.global_ipv6]='' FACT[network.lan_ipv4]=192.168.1.2
  gt_compatibility
  gt_question_model
  mkdir -p "$ROOT/usr/share/zoneinfo/Europe"
  : > "$ROOT/usr/share/zoneinfo/Europe/Zurich"
  defaults
}

defaults() {
  local key
  ANSWER=()
  for key in "${QUESTIONS[@]}"; do
    gt_question_applies "$key" || continue
    [[ "${Q_TYPE[$key]}" != secret ]] || continue
    if [[ "$key" == ADMIN_EMAIL ]]; then ANSWER[$key]=admin@example.org
    elif [[ "$key" == SMTP_CONFIGURE ]]; then ANSWER[$key]=no
    else ANSWER[$key]=$(gt_default "$key"); fi
  done
}

domain_answers() {
  ANSWER[DOMAIN]=example.org ANSWER[DNS_FAMILY]=ipv4 ANSWER[TLS_SOURCE]=letsencrypt
  ANSWER[LETSENCRYPT_EMAIL]=admin@example.org
}

@test "dry-run and core both plan every executable base package" {
  gt_core_toolchain_plan() { :; }
  gt_core_build_plan() { :; }
  local package
  gt_plan
  for package in git curl wget ca-certificates gnupg sudo openssl logrotate whiptail tzdata; do
    [ -n "${PLAN_PACKAGES[$package]:-}" ]
  done
  gt_core_plan
  for package in git curl wget ca-certificates gnupg sudo openssl logrotate whiptail tzdata; do
    [ -n "${PLAN_PACKAGES[$package]:-}" ]
  done
}

@test "web plans select Certbot webroot without server plugins and list actual Apache modules" {
  domain_answers
  ANSWER[WEBSERVER]=apache2 ANSWER[BACKEND_HTTP_PORT]=8080
  gt_plan_web_runtime
  gt_plan_tls
  [ "$(printf '%s\n' "${!PLAN_PACKAGES[@]}" | sort)" = $'apache2\ncertbot' ]
  [ "$(gt_web_package_names | sort)" = $'apache2\ncertbot' ]
  [[ "${PLAN[*]}" == *'proxy proxy_ajp proxy_http proxy_wstunnel headers alias dir mime authz_core deflate filter'* ]]
  [[ "${PLAN[*]}" == *apache-tls-modules* ]]
  [[ "${PLAN[*]}" != *rewrite* ]]
  ANSWER[TLS_SOURCE]=proxy
  PLAN=() PLAN_PACKAGES=()
  gt_plan_web_runtime
  [ "${!PLAN_PACKAGES[*]}" = apache2 ]
  [[ "${PLAN[*]}" != *apache-tls-modules* ]]
  ANSWER[WEBSERVER]=nginx
  PLAN=() PLAN_PACKAGES=()
  gt_plan_web_runtime
  [[ "${PLAN[*]}" != *apache-modules* ]]
}

@test "unsupported saved choices block dry-run and fresh or resumed core before execution" {
  local entry mode
  gt_core_toolchain_plan() { :; }
  gt_core_build_plan() { :; }
  for entry in DUCKDNS_UPDATER SWAP FIREWALL_ALLOW VHOST_INCLUDE; do
    defaults
    [[ "$entry" != DUCKDNS_UPDATER ]] || { domain_answers; ANSWER[DOMAIN]=demo.duckdns.org; }
    ANSWER[$entry]=yes
    for mode in fresh unfinished; do
      FACT[host.class]=$mode STATE[scope]=core
      gt_core_plan || true
      [[ "${PLAN_BLOCKERS[*]}" == *"$entry"* ]]
    done
    FACT[host.class]=fresh
    gt_plan || true
    [[ "${PLAN_BLOCKERS[*]}" == *"$entry"* ]]
  done
  defaults
  ANSWER[WEBSERVER]=none
  gt_core_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'WEBSERVER=none'* ]]
}

@test "proxy port defaults avoid both backend ports and unsupported web ports" {
  FACT[listeners]='LISTEN 0 128 0.0.0.0:8080 0.0.0.0:*'
  gt_web_recommendation
  ANSWER[BACKEND_PORT]=9090 ANSWER[BACKEND_HTTP_PORT]=8081
  [ "$(gt_default TLS_PROXY_LISTEN)" = 8082 ]
  domain_answers
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_FROM]=127.0.0.1
  for port in 80 443 8081 9090; do
    ANSWER[TLS_PROXY_LISTEN]=$port
    PLAN_BLOCKERS=()
    run gt_stage_contract
    [ "$status" -ne 0 ]
    gt_stage_contract || true
    [[ "${PLAN_BLOCKERS[*]}" == *TLS_PROXY_LISTEN* ]]
  done
}

@test "safe optional defaults and resumed choices pass the same stage contract" {
  FACT[dns.duckdns]=no
  [ "$(gt_default DUCKDNS_UPDATER)" = no ]
  [ "$(gt_default SWAP)" = no ]
  FACT[host.class]=unfinished STATE[scope]=core
  gt_stage_contract
}

@test "matching foreign vhost blocks core even with include consent" {
  domain_answers
  ANSWER[VHOST_INCLUDE]=yes
  WEB=('foreign#1 server_kind nginx' 'foreign#1 server_name example.org')
  PLAN_BLOCKERS=()
  gt_stage_contract || true
  [[ "${PLAN_BLOCKERS[*]}" == *'foreign vhost'* ]]
}

@test "upstream proxy occupying the separate LAN port blocks before core changes" {
  domain_answers
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_LISTEN]=8081 ANSWER[TLS_PROXY_FROM]=127.0.0.1
  FACT[listeners]='LISTEN 0 128 0.0.0.0:80 0.0.0.0:* users:(("caddy",pid=42,fd=1))'
  gt_core_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Web port 80'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'co-resident proxy integration is not implemented'* ]]
}

@test "question model conditions omit irrelevant TLS SMTP and secrets from LAN questions" {
  ! gt_question_applies TLS_SOURCE
  ! gt_question_applies SMTP_PASSWORD
  gt_question_applies DB_ROOT_PASSWORD
  domain_answers
  gt_question_applies TLS_SOURCE
  gt_question_applies LETSENCRYPT_EMAIL
  ! gt_question_applies TLS_KEY
  ANSWER[TLS_SOURCE]=proxy
  gt_question_applies TLS_PROXY_FROM
  ! gt_question_applies LETSENCRYPT_EMAIL
  [ "${Q_TYPE[DB_PASSWORD]}" = secret ]
}

@test "validators reject code paths control characters invalid hosts ports and heaps" {
  for entry in 'DOMAIN|$(touch /tmp/pwned)' 'DOMAIN|foo..org' 'DOMAIN|-foo.org' 'DOMAIN|*.example.org' \
      'DOMAIN|127.0.0.1' 'ADMIN_EMAIL|a@@example.org' 'DOCROOT|/var/www/../etc' 'DOCROOT|/var/www//gt' \
      'DOCROOT|/var/www/gt;rm' 'DOCROOT|relative/path' 'BACKEND_PORT|0' 'BACKEND_PORT|65536' \
      'BACKEND_PORT|080' 'JAVA_HEAP|-Xms2g -Xmx1g' 'JAVA_HEAP|-Xms128m -Xmx2g -javaagent:evil' \
      'SWAP|true' 'WEBSERVER|nginx apache2' 'TLS_PROXY_FROM|999.1.1.1' 'TLS_PROXY_FROM|1::2::3' \
      'TLS_PROXY_FROM|::::' 'TIMEZONE|../etc/passwd'; do
    run gt_validate_answer "${entry%%|*}" "${entry#*|}"
    [ "$status" -ne 0 ]
  done
  run gt_validate_answer SMTP_USER $'name\nINJECTED=yes'
  [ "$status" -ne 0 ]
  gt_validate_answer DOMAIN example.org
  gt_validate_answer DOMAIN ''
  gt_validate_answer ADMIN_EMAIL a+b@example.org
  gt_validate_answer JAVA_HEAP '-Xms128m -Xmx2g'
  gt_validate_answer TLS_PROXY_FROM 2001:db8::1
  gt_validate_answer TLS_PROXY_FROM ::1
  gt_validate_answer TLS_PROXY_FROM 127.0.0.1
  gt_validate_answer TIMEZONE Europe/Zurich
}

@test "defaults preserve existing DuckDNS client and shared database resources" {
  ANSWER[DOMAIN]=demo.duckdns.org
  FACT[dns.duckdns]=yes
  [ "$(gt_default DUCKDNS_UPDATER)" = no ]
  FACT[database.vendor]=mariadb FACT[database.schemas]=$'mysql\t20\t0\nwordpress\t12\t0'
  [ "$(gt_default BUFFER_POOL)" = no ]
  FACT[memory.MemTotal]=3000 ANSWER[BUFFER_POOL]=yes
  [ "$(gt_default JAVA_HEAP)" = '-Xms128m -Xmx896m' ]
}

@test "plain questions validate retry defaults and cancellation without requesting passwords" {
  QUESTION_OUTPUT=2
  exec {QUESTION_FD}<<<$'invalid\nadmin@example.org'
  run gt_ask ADMIN_EMAIL
  [ "$status" -eq 0 ]
  [[ "$output" == *'Invalid value'* ]]
  exec {QUESTION_FD}<<<'!quit'
  run gt_ask ADMIN_EMAIL
  [ "$status" -eq 130 ]
  exec {QUESTION_FD}</dev/null
  run gt_ask ADMIN_EMAIL
  [ "$status" -eq 130 ]
  LANG_CODE=de
  exec {QUESTION_FD}<<<'admin@example.org'
  run gt_ask ADMIN_EMAIL
  [[ "$output" == *'Administrator'* ]]
}

@test "complete plain LAN questionnaire and plan are read-only" {
  local key before after
  before=$(find "$ROOT" -printf '%P %m %s\n' | sort)
  : > "$SCRATCH/answers"
  for key in "${QUESTIONS[@]}"; do
    [[ "${Q_TYPE[$key]}" != secret ]] || continue
    gt_question_applies "$key" || continue
    printf '%s\n' "${ANSWER[$key]}" >> "$SCRATCH/answers"
  done
  exec {QUESTION_FD}<"$SCRATCH/answers"
  gt_questions
  gt_plan
  gt_plan_report > "$SCRATCH/plan"
  after=$(find "$ROOT" -printf '%P %m %s\n' | sort)
  [ "$before" = "$after" ]
  [ "${ANSWER[ADMIN_EMAIL]}" = admin@example.org ]
  [ -z "${ANSWER[DB_PASSWORD]:-}" ]
  grep -q 'No installation was executed' "$SCRATCH/plan"
  ! grep -E 'apt-get (update|install)|systemctl (start|restart)|openssl rand|certbot ' "$PROBES"
}

@test "LAN plan lists concrete resources and mail incompleteness" {
  gt_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'/etc/systemd/system/grafioschtrader.service'* ]]
  [[ "${PLAN[*]}" == *'account:grafioschtrader@localhost'* ]]
  [[ "${PLAN[*]}" == *'/home/grafioschtrader/gtupdate.sh'* ]]
  [[ "${PLAN_WARNINGS[*]}" == *'nobody can complete registration'* ]]
  ! grep -q dig "$PROBES"
}

@test "existing installations return no bootstrap plan even with missing answers" {
  for class in classic completed docker; do
    FACT[host.class]=$class ANSWER=()
    gt_plan
    [ "${#PLAN[@]}" -eq 1 ]
    [[ "${PLAN[0]}" == reuse* ]]
  done
  for class in foreign-partial unfinished unknown invalid-state; do
    FACT[host.class]=$class
    run gt_plan
    [ "$status" -eq 2 ]
  done
}

@test "planner revalidates answers and rejects inactive or secret values" {
  ANSWER[BACKEND_PORT]='9090; touch /tmp/pwned'
  run gt_plan
  [ "$status" -eq 2 ]
  defaults
  ANSWER[DB_PASSWORD]='TOP_SECRET_VALUE'
  gt_plan || true
  [ "${#PLAN[@]}" -eq 0 ]
  gt_plan_report > "$SCRATCH/report"
  ! grep -q TOP_SECRET_VALUE "$SCRATCH/report"
}

@test "source fallback stale metadata and occupied lock block a plan" {
  FACT[source.requirements]=fallback FACT[apt.age_hours]=48 FACT[dpkg.lock]=1234
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'provisional'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'stale'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'lock'* ]]
}

@test "ports cannot overlap each other or a foreign listener" {
  ANSWER[WEBSERVER]=apache2 ANSWER[BACKEND_HTTP_PORT]=9090
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'duplicated'* ]]
  ANSWER[BACKEND_HTTP_PORT]=8080
  FACT[listeners]='LISTEN 0 128 0.0.0.0:9090 0.0.0.0:* users:(("other",pid=42,fd=1))'
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'9090'* ]]
  FACT[listeners]='' ANSWER[BACKEND_PORT]=80
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'overlaps a planned web listener'* ]]
}

@test "nonempty database and unverified existing accounts remain blockers" {
  FACT[database.vendor]=mariadb FACT[database.version]=11.8 FACT[database.gt_tables]=8
  FACT[database.gt_user]=present
  gt_compatibility
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'non-empty'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'credentials remain unverified'* ]]
}

@test "empty database requires consent and buffer pool restart is explicit" {
  FACT[database.gt_tables]=0
  ANSWER[DB_REUSE_EMPTY]=no
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'not accepted'* ]]
  ANSWER[DB_REUSE_EMPTY]=yes
  gt_plan
  [[ "${PLAN[*]}" == *'restart | mariadb'* ]]
  ANSWER[BUFFER_POOL]=no
  gt_plan
  [[ "${PLAN[*]}" != *'restart | mariadb'* ]]
}

@test "shared Node is isolated unless replacement explicitly selected" {
  ACTION[node]=isolate
  ANSWER[NODE_REPLACE]=no
  gt_plan
  [[ "${PLAN[*]}" == *'/opt/nodejs-gt'* ]]
  [[ "${PLAN[*]}" != *'nodesource.sources'* ]]
  ANSWER[NODE_REPLACE]=yes
  gt_plan || true
  [[ "${PLAN[*]}" == *'replace | package:nodejs'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'NodeSource'* ]]
}

@test "APT simulation reports removals upgrades and failures as blockers" {
  APT_OUTPUT=$'Inst curl [7.0] (8.0 Debian)\nRemv homeassistant [1.0]\nConf curl (8.0 Debian)'
  gt_plan || true
  [[ "${PLAN[*]}" == *'Remv homeassistant'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'upgrade an existing package'* ]]
  [[ "${PLAN_BLOCKERS[*]}" == *'remove packages'* ]]
  grep -q 'apt-get --simulate ' "$PROBES"
  ! grep -q -- '--no-download' "$PROBES"
  APT_FAIL=yes
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'simulation failed'* ]]
}

@test "APT architecture brackets on new packages are not interpreted as upgrades" {
  APT_OUTPUT=$'Inst nginx (1.24.0 Ubuntu:24.04/noble [amd64])\nInst libguava-java (32.0.1 Ubuntu [all]) []\nConf nginx (1.24.0 Ubuntu [amd64])'
  gt_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'Inst nginx'* ]]
}

@test "document root preserves foreign files and detects existing GT symlinks" {
  mkdir -p "$ROOT/var/www/gt"
  echo foreign > "$ROOT/var/www/gt/index.html"
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Non-empty document root'* ]]
  ln -s /does/not/exist "$ROOT/var/www/gt/grafioschtrader"
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'GT content already exists'* ]]
  [ "$(cat "$ROOT/var/www/gt/index.html")" = foreign ]
}

@test "matching vhost requires consent and cannot hide conflicting GT routes" {
  domain_answers
  WEB=('/etc/nginx/sites-enabled/foreign#1 server_kind nginx' '/etc/nginx/sites-enabled/foreign#1 server_name example.org' '/etc/nginx/sites-enabled/foreign#1 root /var/www/foreign')
  ANSWER[DOCROOT]=$(gt_default DOCROOT)
  [ "${ANSWER[DOCROOT]}" = /var/www/foreign ]
  ANSWER[VHOST_INCLUDE]=no
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'explicit consent'* ]]
  ANSWER[VHOST_INCLUDE]=yes
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'foreign vhost'* ]]
  [[ "${PLAN[*]}" == *'/etc/nginx/sites-enabled/foreign.gt-install.<timestamp>'* ]]
  ANSWER[DOCROOT]=/var/www/elsewhere
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Snippet document root must match'* ]]
  ANSWER[DOCROOT]=/var/www/foreign
  WEB+=('/etc/nginx/sites-enabled/foreign#1 location /api {')
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'conflict-free'* ]]
}

@test "unknown include context blocks web edits despite consent" {
  domain_answers
  NOTES+=('UNKNOWN: included vhost context: /etc/nginx/sites-enabled/foreign')
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Effective web configuration'* ]]
}

@test "an include for one web server cannot be inserted into another server's vhost" {
  domain_answers
  ANSWER[WEBSERVER]=apache2 ANSWER[BACKEND_HTTP_PORT]=8080 ANSWER[VHOST_INCLUDE]=yes
  WEB=('/etc/nginx/sites-enabled/foreign#1 server_kind nginx' '/etc/nginx/sites-enabled/foreign#1 server_name example.org')
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Effective web configuration'* ]]
  [[ "${PLAN[*]}" != *'modify | /etc/nginx/sites-enabled/foreign'* ]]
}

@test "DNS plan checks both families and excludes mismatched www names" {
  domain_answers
  gt_plan
  [ "${FACT[plan.names]}" = 'example.org www.example.org' ]
  DNS_AAAA=2001:db8::999
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
  [ "${FACT[plan.names]}" = example.org ]
  DNS_FAIL=yes
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'UNKNOWN'* ]]
}

@test "missing dig is a prerequisite blocker and plans bind9-dnsutils without DNS queries" {
  domain_answers
  DNS_TOOL_AVAILABLE=no
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Missing DNS check prerequisite: dig (bind9-dnsutils)'* ]]
  [[ "${PLAN_BLOCKERS[*]}" != *'DNS does not match'* ]]
  [ "${PLAN_PACKAGES[bind9-dnsutils]}" = install ]
  ! grep -q '^dig ' "$PROBES"
  [ "${FACT[plan.names]}" = example.org ]
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_LISTEN]=8081 ANSWER[TLS_PROXY_FROM]=''
  PLAN=() PLAN_BLOCKERS=() PLAN_PACKAGES=()
  gt_plan_dns
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${#PLAN_PACKAGES[@]}" -eq 0 ]
}

@test "AAAA records can be a nonempty normalized subset of stable host addresses" {
  domain_answers
  ANSWER[DNS_FAMILY]=ipv6 DNS_A=''
  FACT[network.global_ipv6]=$'2001:db8::1\n2001:db8::2'
  DNS_AAAA=2001:0db8:0:0:0:0:0:2
  gt_plan
  [ "${FACT[plan.names]}" = 'example.org www.example.org' ]
  DNS_AAAA=$'2001:db8::1\n2001:db8::2'
  gt_plan
  DNS_WWW_AAAA=2001:db8::999
  gt_plan
  [ "${FACT[plan.names]}" = example.org ]
  unset DNS_WWW_AAAA
  DNS_AAAA=$'2001:db8::1\n2001:db8::999'
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
  for DNS_AAAA in '' 2001:db8::999; do
    gt_plan || true
    [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
  done
  FACT[network.global_ipv6]=''
  DNS_AAAA=''
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
}

@test "DNS comparison rejects one foreign AAAA among valid addresses and unknown observations" {
  ANSWER[DNS_FAMILY]=both
  gt_dns_records_match AAAA $'2001:db8::1\n2001:db8::2' $'2001:db8::1\n2001:db8::2\n2001:db8::3'
  ! gt_dns_records_match AAAA $'2001:db8::1\n2001:db8::99' $'2001:db8::1\n2001:db8::2'
  ! gt_dns_records_match AAAA 'unknown' 'unknown'
  ! gt_dns_records_match AAAA '2001:db8::1' $'2001:db8::1\nunknown'
  ! gt_dns_records_match A '' ''
  ANSWER[DNS_FAMILY]=ipv4
  gt_dns_records_match AAAA '' 'unknown'
  ! gt_dns_records_match AAAA '2001:db8::1' '2001:db8::1'
}

@test "external proxy skips local DNS checks and uses selected HTTP port" {
  domain_answers
  ANSWER[TLS_SOURCE]=proxy ANSWER[TLS_PROXY_LISTEN]=8081 ANSWER[TLS_PROXY_FROM]=127.0.0.1
  unset 'ANSWER[LETSENCRYPT_EMAIL]'
  FACT[listeners]='LISTEN 0 128 0.0.0.0:443 0.0.0.0:* users:(("caddy",pid=42,fd=1))'
  gt_plan
  [[ "${PLAN[*]}" == *'HTTP 8081; allowed source=127.0.0.1'* ]]
  ! grep -q dig "$PROBES"
  [[ "${PLAN_PACKAGES[*]}" != *certbot* ]]
}

@test "DuckDNS plan never sends updates or collects token and rejects duplicate updater" {
  domain_answers
  ANSWER[DOMAIN]=demo.duckdns.org ANSWER[DUCKDNS_UPDATER]=yes
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'DUCKDNS_UPDATER=yes is not implemented'* ]]
  [[ "${PLAN[*]}" == *'/home/grafioschtrader/duckdns/duck.sh'* ]]
  ! grep -q 'duckdns.org/update' "$PROBES"
  FACT[dns.duckdns]=yes
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'must not be duplicated'* ]]
}

@test "certificate validation uses real OpenSSL and rejects untrusted chain and unsafe key permissions" {
  OPENSSL_REAL=yes
  domain_answers
  ANSWER[TLS_SOURCE]=existing ANSWER[TLS_CERT]=/cert.pem ANSWER[TLS_KEY]=/key.pem
  unset 'ANSWER[LETSENCRYPT_EMAIL]'
  openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj /CN=example.org \
    -addext 'subjectAltName=DNS:example.org,DNS:www.example.org' -keyout "$ROOT/key.pem" -out "$ROOT/cert.pem" 2>/dev/null
  chmod 600 "$ROOT/key.pem"
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'chain cannot be verified'* ]]
  [[ "${PLAN_BLOCKERS[*]}" != *'do not match'* ]]
  [[ "${PLAN_WARNINGS[*]}" == *'30 days'* ]]
  chmod 644 "$ROOT/key.pem"
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'group/world accessible'* ]]
  gt_plan_report > "$SCRATCH/report"
  ! grep -q 'BEGIN PRIVATE KEY' "$SCRATCH/report"
  chmod 600 "$ROOT/key.pem"
  ANSWER[DOMAIN]=wrong.example.org
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'does not cover wrong.example.org'* ]]
}

@test "certificate chain accepts a trusted full chain but rejects missing intermediate and mismatching key" {
  OPENSSL_REAL=yes
  domain_answers
  ANSWER[TLS_SOURCE]=existing ANSWER[TLS_CERT]=/fullchain.pem ANSWER[TLS_KEY]=/leaf.key
  unset 'ANSWER[LETSENCRYPT_EMAIL]'
  openssl req -x509 -newkey rsa:2048 -nodes -days 40 -subj /CN=TestRoot \
    -addext 'basicConstraints=critical,CA:TRUE' -keyout "$ROOT/ca.key" -out "$ROOT/ca.pem" 2>/dev/null
  openssl req -new -newkey rsa:2048 -nodes -subj /CN=TestIntermediate -keyout "$ROOT/inter.key" -out "$ROOT/inter.csr" 2>/dev/null
  printf 'basicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n' > "$ROOT/inter.ext"
  openssl x509 -req -in "$ROOT/inter.csr" -CA "$ROOT/ca.pem" -CAkey "$ROOT/ca.key" -set_serial 2 \
    -days 40 -extfile "$ROOT/inter.ext" -out "$ROOT/inter.pem" 2>/dev/null
  openssl req -new -newkey rsa:2048 -nodes -subj /CN=example.org -keyout "$ROOT/leaf.key" -out "$ROOT/leaf.csr" 2>/dev/null
  printf 'basicConstraints=critical,CA:FALSE\nsubjectAltName=DNS:example.org,DNS:www.example.org\nextendedKeyUsage=serverAuth\n' > "$ROOT/leaf.ext"
  openssl x509 -req -in "$ROOT/leaf.csr" -CA "$ROOT/inter.pem" -CAkey "$ROOT/inter.key" -set_serial 3 \
    -days 40 -extfile "$ROOT/leaf.ext" -out "$ROOT/leaf.pem" 2>/dev/null
  cat "$ROOT/leaf.pem" "$ROOT/inter.pem" > "$ROOT/fullchain.pem"
  chmod 600 "$ROOT/leaf.key"
  export SSL_CERT_FILE="$ROOT/ca.pem"
  gt_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  ANSWER[TLS_CERT]=/leaf.pem
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'chain cannot be verified'* ]]
  ANSWER[TLS_CERT]=/fullchain.pem ANSWER[TLS_KEY]=/ca.key
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'do not match'* ]]
}

@test "IPv6 DNS comparisons normalize compressed and expanded forms and reject unavailable family" {
  domain_answers
  ANSWER[DNS_FAMILY]=ipv6
  FACT[network.global_ipv6]=2001:db8::1 DNS_A='' DNS_AAAA=2001:0db8:0:0:0:0:0:1
  gt_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  FACT[network.global_ipv6]=''
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'DNS does not match'* ]]
}

@test "mail settings and German plan are rendered without sending mail" {
  ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_HOST]=smtp.example.org ANSWER[SMTP_PORT]=587
  ANSWER[SMTP_USER]=sender@example.org ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=starttls ANSWER[SMTP_TEST]=yes
  LANG_CODE=de
  gt_plan
  run gt_plan_report
  [[ "$output" == *'Installationsplan'* ]]
  [[ "$output" == *'Keine Installation ausgeführt'* ]]
  ! grep -q -- '--mail-rcpt' "$PROBES"
}

@test "contradictory CLI modes are refused before inventory" {
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --check --dry-run
  [ "$status" -eq 2 ]
  [[ "$output" == *'Use --check'* ]]
}

@test "SMTP authentication requires encryption while explicit unauthenticated relay is accepted" {
  ANSWER[SMTP_CONFIGURE]=yes ANSWER[SMTP_HOST]=smtp.example.org ANSWER[SMTP_PORT]=2525
  ANSWER[SMTP_USER]=sender@example.org ANSWER[SMTP_AUTH]=yes ANSWER[SMTP_SECURITY]=none ANSWER[SMTP_TEST]=no
  gt_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'Authenticated SMTP requires'* ]]
  ANSWER[SMTP_AUTH]=no
  gt_plan
  [[ "${PLAN[*]}" == *'auth=no; security=none; sender=sender@example.org'* ]]
}
