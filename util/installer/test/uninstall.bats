#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  STATE=([run_id]=0123456789abcdef0123456789abcdef)
  UNSTATE=([schema]=1 [run_id]=${STATE[run_id]} [database]=no [packages]=no)
  ANSWER=([WEBSERVER]=nginx [DOMAIN]=example.org [TLS_SOURCE]=existing [VHOST_INCLUDE]=yes [DOCROOT]=/var/www/gt)
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/etc/nginx/sites-available" "$ROOT/etc/nginx/sites-enabled" \
    "$ROOT/etc/nginx/snippets" "$ROOT/etc/sudoers.d"
  chmod 700 "$ROOT/var/lib/gt-install"
}

digest() { local value; value=$(sha256sum < "$1"); printf '%s' "${value%% *}"; }

@test "each installer APT transaction journals the packages its simulation installs, once" {
  gt_core_mark() { STATE[$1]=$2; }
  APT_OUTPUT=$'Inst nginx (1.24.0 Ubuntu:24.04/noble [arm64])\nInst nginx-common (1.24.0 Ubuntu [all])'
  gt_packages_record env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade install nginx
  [ "${STATE[resource.packages]}" = 'nginx nginx-common' ]
  APT_OUTPUT=$'Inst certbot (2.9.0 Ubuntu [all])\nInst nginx-common (1.24.0 Ubuntu [all])'
  gt_packages_record env DEBIAN_FRONTEND=noninteractive apt-get -y install certbot=2.9.0
  [ "${STATE[resource.packages]}" = 'nginx nginx-common certbot' ]
  # A purge is no installation, and a transaction before the journal exists records nothing.
  gt_packages_record env DEBIAN_FRONTEND=noninteractive apt-get -y purge nginx
  STATE=()
  gt_packages_record env DEBIAN_FRONTEND=noninteractive apt-get -y install nginx
  [ -z "${STATE[resource.packages]:-}" ]
}

@test "an installer file goes while unchanged; a changed or foreign file stays and is reported" {
  printf 'grafioschtrader ALL=(root) NOPASSWD: /bin/systemctl start grafioschtrader\n' \
    > "$ROOT/etc/sudoers.d/grafioschtrader"
  STATE[file.app_sudoers]=$(digest "$ROOT/etc/sudoers.d/grafioschtrader")
  gt_uninstall_file app_sudoers "$ROOT/etc/sudoers.d/grafioschtrader"
  [ ! -e "$ROOT/etc/sudoers.d/grafioschtrader" ]
  [ "${UNSTATE[removed.0]}" = /etc/sudoers.d/grafioschtrader ]
  printf 'edited by the administrator\n' > "$ROOT/etc/sudoers.d/grafioschtrader"
  gt_uninstall_file app_sudoers "$ROOT/etc/sudoers.d/grafioschtrader"
  [ -e "$ROOT/etc/sudoers.d/grafioschtrader" ]
  [[ "${UNSTATE[kept.0]}" == '/etc/sudoers.d/grafioschtrader: changed since the installation' ]]
  printf 'x\n' > "$ROOT/etc/foreign.conf"
  gt_uninstall_file never_written "$ROOT/etc/foreign.conf"
  [ -e "$ROOT/etc/foreign.conf" ]
}

@test "a directory goes when the journal owns it or an interrupted step intended it, never otherwise" {
  mkdir -p "$ROOT/opt/nodejs-gt/bin" "$ROOT/opt/gt-build-tools" "$ROOT/opt/.gt-node-partial"
  STATE[resource.node]=intent
  gt_uninstall_toolchains
  [ ! -e "$ROOT/opt/nodejs-gt" ] && [ ! -e "$ROOT/opt/.gt-node-partial" ]
  [ -d "$ROOT/opt/gt-build-tools" ]
  [[ "${UNSTATE[kept.0]}" == "/opt/gt-build-tools: not recorded as the installer's" ]]
}

@test "the include leaves the vhost: an unchanged vhost returns to its backup, a changed one loses only the line" {
  local site=/etc/nginx/sites-available/site original
  printf 'server {\n    listen 443 ssl;\n    server_name example.org;\n}\n' > "$ROOT$site.gt-install.20261013T082404Z"
  original=$(cat "$ROOT$site.gt-install.20261013T082404Z")
  { printf 'server {\n'; gt_vhost_include_line; printf '    listen 443 ssl;\n    server_name example.org;\n}\n'; } \
    > "$ROOT$site"
  printf 'location /api {}\n' > "$ROOT/etc/nginx/snippets/grafioschtrader.conf"
  STATE[resource.vhost_target]=$site STATE[resource.vhost_backup]=$site.gt-install.20261013T082404Z
  STATE[file.vhost_target]=$(digest "$ROOT$site")
  STATE[file.vhost_snippet]=$(digest "$ROOT/etc/nginx/snippets/grafioschtrader.conf")
  printf 'old attempt\n' > "$ROOT$site.gt-install.20261013T081000Z"
  gt_uninstall_vhost
  [ "$(cat "$ROOT$site")" = "$original" ]
  [ ! -e "$ROOT/etc/nginx/snippets/grafioschtrader.conf" ]
  [ -z "$(compgen -G "$ROOT$site.gt-install.*")" ]
  # A vhost edited after the installation keeps the administrator's edit.
  { printf 'server {\n'; gt_vhost_include_line; printf '    listen 443 ssl;\n    # edited\n}\n'; } > "$ROOT$site"
  UNSTATE=([schema]=1 [run_id]=${STATE[run_id]} [database]=no [packages]=no)
  gt_uninstall_vhost
  [ "$(cat "$ROOT$site")" = $'server {\n    listen 443 ssl;\n    # edited\n}' ]
  [[ "${UNSTATE[kept.0]}" == *'only the include line was removed' ]]
}

@test "swap removal takes the installer's swap file and its own fstab line only" {
  printf 'UUID=1 / ext4 defaults 0 1\n/swapfile none swap sw 0 0\n' > "$ROOT/etc/fstab"
  printf 'Filename Type Size Used Priority\n' > "$ROOT/proc/swaps"
  : > "$ROOT/swapfile"
  STATE[resource.swapfile]=owned
  gt_uninstall_swap
  [ "$(cat "$ROOT/etc/fstab")" = 'UUID=1 / ext4 defaults 0 1' ]
  [ ! -e "$ROOT/swapfile" ]
  STATE[resource.swapfile]='' ; : > "$ROOT/swapfile"
  gt_uninstall_swap
  [ -e "$ROOT/swapfile" ]
}

@test "an alternative returns to auto only when it was auto and its best candidate is the recorded path" {
  BEST=/usr/lib/jvm/java-25/bin/java
  update-alternatives() {
    case "$1" in
      --query) printf 'Name: %s\nStatus: manual\nBest: %s\nValue: /usr/lib/jvm/java-17/bin/java\n' "$2" "$BEST" ;;
      --auto) printf '%s\n' "$2" >> "$BATS_TEST_TMPDIR/auto" ;;
    esac
  }
  STATE[alternative.java]=/usr/lib/jvm/java-17/bin/java STATE[alternative_mode.java]=auto
  STATE[alternative.javac]=/usr/lib/jvm/java-17/bin/javac STATE[alternative_mode.javac]=manual
  gt_uninstall_alternatives
  [ ! -e "$BATS_TEST_TMPDIR/auto" ]
  BEST=/usr/lib/jvm/java-17/bin/java
  gt_uninstall_alternatives
  [ "$(cat "$BATS_TEST_TMPDIR/auto")" = java ]
}

@test "packages are purged only on request and only when nothing else would go with them" {
  STATE[resource.packages]='nginx nginx-common gone'
  dpkg-query() { [[ "$*" != *gone* ]] && printf installed; }
  apt-get() { printf 'Purg nginx [1.24]\nPurg nginx-common [1.24]\n%s' "${EXTRA:-}"; }
  gt_core_run() { printf '%s\n' "$*" >> "$BATS_TEST_TMPDIR/run"; }
  gt_uninstall_packages
  [ ! -e "$BATS_TEST_TMPDIR/run" ]
  UNSTATE[packages]=yes EXTRA=$'Purg php-fpm [8.3]\n'
  run gt_uninstall_packages
  [ "$status" -ne 0 ] && [[ "$output" == *'would remove other packages'* ]]
  [ ! -e "$BATS_TEST_TMPDIR/run" ]
  EXTRA=''
  gt_uninstall_packages
  grep -qx 'env DEBIAN_FRONTEND=noninteractive apt-get -y purge nginx nginx-common' "$BATS_TEST_TMPDIR/run"
}

@test "only an installation of this installer can be uninstalled, and a resumed run keeps its options" {
  gt_installation() { FACT[host.class]=classic; }
  run gt_uninstall
  [ "$status" -eq 2 ] && [[ "$output" == *'no installation of this installer (classic)'* ]]
  UNSTATE[database]=yes
  gt_uninstall_save
  UNINSTALL_DATABASE=no UNINSTALL_PACKAGES=yes
  run gt_uninstall_begin
  [ "$status" -eq 2 ] && [[ "$output" == *'used other options'* ]]
  UNINSTALL_PACKAGES=no
  gt_uninstall_begin
  [ "${UNSTATE[database]}" = yes ]
  STATE[resource.packages]=''
  unset 'STATE[resource.packages]'
  UNSTATE[packages]=yes
  gt_uninstall_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'predates the package record'* ]]
}
