# The full controller approves one concrete plan. Existing stage preflights still
# run at their execution boundaries; approval never bypasses ownership checks.
gt_bootstrap_apt_parse() {
  local line package version
  while IFS= read -r line; do
    case "$line" in
      'Remv '*) return 2 ;;
      'Inst '*)
        [[ "$line" =~ ^Inst[[:space:]]+([^[:space:]]+)[[:space:]]+\(([^[:space:]]+) ]] || return 2
        package=${BASH_REMATCH[1]} version=${BASH_REMATCH[2]}
        [[ "$package" =~ ^[a-z0-9][a-z0-9+.:_-]*$ && "$version" =~ ^[a-zA-Z0-9.+:~_-]+$ ]] || return 2
        printf '%s\t%s\n' "$package" "$version" ;;
    esac
  done
}

gt_bootstrap_apt_plan() {
  local package transaction records version
  local -a requested=()
  BOOTSTRAP_APT=()
  for package in "${!PLAN_PACKAGES[@]}"; do
    [[ "${PLAN_PACKAGES[$package]}" != install ]] || requested+=("$package")
  done
  if (( ${#requested[@]} )); then
    [[ "${FACT[dpkg.lock]:-}" == free && "${FACT[apt.age_hours]:-unknown}" != unknown ]] || return 2
    (( ${FACT[apt.age_hours]} <= 24 )) || return 2
    transaction=$(gt_probe env LC_ALL=C apt-get --simulate --no-remove --no-upgrade \
      -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= install "${requested[@]}") || return 2
    records=$(gt_bootstrap_apt_parse <<< "$transaction") || return 2
    while IFS=$'\t' read -r package version; do
      [[ -n "$package" ]] || continue
      BOOTSTRAP_APT[$package]=$version
      gt_plan_row install "package:$package" "$version"
    done <<< "$records"
  fi
  FACT[bootstrap.apt]=$(printf '%s\n' "${records:-}" | LC_ALL=C sort | sha256sum)
}

gt_bootstrap_apt_run() {
  local arg found=no transaction records package version
  local -a requested=() pins=()
  for arg in "$@"; do
    if [[ "$found" == yes ]]; then requested+=("$arg")
    elif [[ "$arg" == install ]]; then found=yes; fi
  done
  [[ "$found" == yes && ${#requested[@]} -gt 0 ]] || return 2
  transaction=$(gt_probe env LC_ALL=C apt-get --simulate --no-remove --no-upgrade \
    -o Dir::Cache::pkgcache= -o Dir::Cache::srcpkgcache= install "${requested[@]}") || return 2
  records=$(gt_bootstrap_apt_parse <<< "$transaction") || return 2
  while IFS=$'\t' read -r package version; do
    [[ -n "$package" ]] || continue
    [[ "${BOOTSTRAP_APT[$package]:-}" == "$version" ]] || {
      gt_core_error 'APT transaction differs from the approved full plan; run again to review it.'; return 2;
    }
    pins+=("$package=$version")
  done <<< "$records"
  # Pin dependencies as well as requested packages; never silently resolve a new
  # version after the final simulation. --no-remove/--no-upgrade remain in force.
  "$@" "${pins[@]}"
}

gt_bootstrap_web_review() {
  local web=${ANSWER[WEBSERVER]} executable address certificate='' tls
  executable=$web; [[ "$web" != apache2 ]] || executable=apache2ctl
  address=$(gt_lan_address) || return 2
  FACT[web.lan]=$address
  [[ -z "${STATE[resource.web_lan]:-}" || "${STATE[resource.web_lan]}" == "$address" ]] || return 2
  # Another web server serves the routes; the installer only writes its proposal and verifies the result.
  if [[ "$web" == none ]]; then FACT[bootstrap.web]="none:${FACT[web.lan]}:${FACT[web.names]:-}"; return 0; fi
  if [[ "$web" == nginx && -z "${ANSWER[DOMAIN]:-}" ]]; then gt_nginx_owned || return 2
  else gt_site_owned || return 2; fi
  if command -v "$executable" >/dev/null; then
    if [[ "$web" == nginx ]]; then
      nginx -V 2>&1 | grep -q -- '--conf-path=/etc/nginx/nginx.conf' || return 2
    fi
    gt_site_test || return 2
    FACT[web.snapshot]=$(gt_site_inventory) || return 2
    gt_core_run systemctl is-active --quiet "$web.service" || return 2
    gt_nginx_statuses > "$SCRATCH/bootstrap-web-statuses" || return 2
  else
    [[ ! -e "$(gt_path "/etc/$web")" && ! -L "$(gt_path "/etc/$web")" && -z "${PACKAGE[$web]:-}" ]] || return 2
    FACT[web.snapshot]=absent
  fi
  tls=$(gt_tls_snapshot)
  # The fresh journal receives its random identity only after approval.
  [[ "${FACT[tls.selection]:-}" != - ]] || tls='new-installer-lineage'
  if [[ "${ANSWER[TLS_SOURCE]:-}" == existing || "${FACT[tls.reuse]:-}" == yes ]]; then
    certificate=$(sha256sum "$(gt_path "${FACT[tls.cert]}")") || return 2
  fi
  FACT[bootstrap.web]="${FACT[web.snapshot]}:${FACT[web.lan]}:${FACT[web.names]:-}:$tls:$certificate"
}

gt_bootstrap_app_review() {
  local file path digest raw=https://raw.githubusercontent.com/grafioschtrader/grafioschtrader
  gt_app_targets || return 2
  for file in gtupdate.sh gtupbackend.sh gtupfrontend.sh gtupfrontback.sh checkversion.sh merger.sh \
      gt_to_g_rename.sh gtcronrandom.sh; do
    path="$CORE_HOME/$file"
    gt_no_symlinks "$path" || return 2
    if [[ -e "$path" ]]; then
      [[ -f "$path" && "$(stat -c '%U:%a' "$path")" == grafioschtrader:700 ]] || return 2
      digest=$(sha256sum "$path"); digest=${digest%% *}
      [[ "$digest" == "${STATE[file.app_$file]:-}" ]] || return 2
    fi
  done
  # The first build must never invoke an older helper that also starts/stops services.
  if [[ "${STATE[step.core]:-}" != complete ]]; then
    for file in gtupbackend.sh gtupfrontend.sh; do
      gt_probe curl --disable -fsS --connect-timeout 4 --max-time 10 \
        "$raw/${FACT[source.commit]}/util/shellscripts/$file" \
        > "$SCRATCH/bootstrap-$file" || { gt_core_error "Pinned build helper unavailable: $file"; return 2; }
      grep -q GT_INSTALL_BUILD_ONLY "$SCRATCH/bootstrap-$file" ||
        { gt_core_error "Pinned build helper lacks GT_INSTALL_BUILD_ONLY: $file"; return 2; }
    done
  fi
}

gt_bootstrap_plan() {
  local key id target en
  if [[ "${STATE[step.core]:-}" == complete ]]; then
    PLAN=() PLAN_BLOCKERS=() PLAN_WARNINGS=() PLAN_PACKAGES=()
    # A journaled first start reaches the post-start verifier, never the core's
    # empty-schema planner. Root database credentials are unnecessary here.
    gt_app_preflight || gt_plan_block 'Owned application resources or database cannot be verified.'
    gt_plan_row verify core 'Reuse original installation identity, source pin and secrets; no database bootstrap.'
    for key in platform architecture disk; do
      [[ "${ACTION[$key]:-block}" != block ]] || gt_plan_block "$key: ${REASON[$key]:-unknown}"
    done
  else
    gt_core_plan || :
    [[ -z "${STATE[step.app_start]:-}" ]] || gt_plan_block 'First start is journaled but the core is incomplete.'
  fi
  local -a core_rows=("${PLAN[@]}") core_blocks=("${PLAN_BLOCKERS[@]}") core_warnings=("${PLAN_WARNINGS[@]}")
  gt_domain_plan || gt_plan_block 'Resolve domain/TLS checks before the full bootstrap.'
  PLAN=("${core_rows[@]}" "${PLAN[@]}")
  PLAN_BLOCKERS=("${core_blocks[@]}" "${PLAN_BLOCKERS[@]}")
  PLAN_WARNINGS=("${core_warnings[@]}" "${PLAN_WARNINGS[@]}")
  gt_bootstrap_app_review || gt_plan_block 'Foreign/changed application target or unsupported pinned build helpers.'
  gt_bootstrap_web_review || gt_plan_block 'Web targets, LAN address or shared web configuration cannot be verified.'
  gt_plan_web_runtime
  gt_plan_row configure application \
    'Update helpers, sudoers (start/stop only), systemd unit, weekly log rotation (8 copies), protected build log.'
  for target in /etc/sudoers.d/grafioschtrader /etc/systemd/system/grafioschtrader.service \
      /etc/logrotate.d/grafioschtrader /var/log/grafioschtrader.log; do
    gt_plan_row manage "$target" 'Create or verify installer-owned target; foreign targets block.'
  done
  gt_plan_row configure timezone "${ANSWER[TIMEZONE]}; choose and persist the application cron slot once."
  en="${STATE[planned_commit]:-${FACT[source.commit]}}; backend from source;"
  en+=" frontend=${FACT[frontend.mode]:-from-pinned-helper}"
  gt_plan_row build "$CORE_HOME" "$en"
  gt_plan_row manage "${ANSWER[DOCROOT]}/grafioschtrader" 'Owned frontend directory; shared document root preserved.'
  gt_plan_row enable grafioschtrader.service \
    'Start migrations, verify production database and loopback listeners, then enable boot.'
  for id in lan http domain; do
    [[ "${ANSWER[WEBSERVER]}" != none ]] || break
    [[ "$id" == lan || -n "${ANSWER[DOMAIN]:-}" ]] || continue
    # In include mode the existing virtual host serves the domain; its snippet rows come from gt_domain_plan.
    [[ "$id" == lan ]] || ! gt_vhost_include_mode || continue
    [[ "$id" != http || "${ANSWER[TLS_SOURCE]:-}" == letsencrypt ]] || continue
    target=$(gt_site_path "$id")
    gt_plan_row manage "$target" 'Own vhost and sites-enabled link; validate before reload and compare shared sites.'
  done
  if [[ "${ANSWER[WEBSERVER]}" == apache2 ]]; then
    gt_plan_row configure apache2 \
      'Enable required modules; disable only the distribution default site; preserve rollback records.'
    [[ "${ANSWER[TLS_SOURCE]:-}" != proxy ]] || gt_plan_row manage \
      /etc/apache2/conf-enabled/grafioschtrader-listen.conf "Listen ${ANSWER[TLS_PROXY_LISTEN]}"
  fi
  if [[ "${ANSWER[WEBSERVER]}" == none ]]; then
    gt_plan_row create "$GT_WEB_MANUAL" \
      'Proposed nginx and Apache routes for the own web server; nothing is activated' \
      'Vorgeschlagene nginx- und Apache-Routen für den eigenen Webserver; nichts wird aktiviert'
    gt_plan_row verify web 'LAN and domain routes once they answer; until then the result stays incomplete' \
      'LAN- und Domain-Routen, sobald sie antworten; bis dahin bleibt das Ergebnis unvollständig'
  else
    gt_plan_row enable "${ANSWER[WEBSERVER]}.service" \
      'Start/reload selected web server and enable boot after route verification.'
  fi
  gt_firewall_plan
  if [[ "${ANSWER[TLS_SOURCE]:-}" == letsencrypt ]]; then
    gt_plan_row consent letsencrypt \
      'HTTP-01 issuance/reuse, renewal test and scoped reload hook/timer; terms: https://letsencrypt.org/repository/'
  fi
  if [[ "${ANSWER[SMTP_CONFIGURE]}" == yes ]]; then
    en="${ANSWER[SMTP_HOST]}:${ANSWER[SMTP_PORT]}; auth=${ANSWER[SMTP_AUTH]}; TLS=${ANSWER[SMTP_SECURITY]};"
    en+=" sender=${ANSWER[SMTP_USER]}; recipient=${ANSWER[ADMIN_EMAIL]}; send=${ANSWER[SMTP_TEST]}"
    gt_plan_row verify SMTP "$en"
    [[ "${STATE[step.mail]:-}" != intent ]] ||
      gt_plan_warn 'Previous mail attempt was interrupted; resumption may submit the same Message-ID again.'
    [[ "${STATE[resource.mail_delivery]:-}" != accepted ]] ||
      gt_plan_row reuse SMTP 'Recorded server acceptance; no duplicate test message.'
  else gt_plan_warn "$(gt_mail_skipped_warning bootstrap)"; fi
  gt_plan_row publish /var/lib/gt-install/result \
    'Verify milestones and publish the protected result; completion requires backend, web, TLS and mail evidence.'
  gt_bootstrap_apt_plan || gt_plan_block 'Full APT transaction unavailable, stale, or would upgrade/remove packages.'
  FACT[bootstrap.plan]=$(printf '%s\n' "${PLAN[@]}" | sha256sum)
  (( ${#PLAN_BLOCKERS[@]} == 0 ))
}

gt_execution_plan() {
  if [[ "$MODE" == --bootstrap ]]; then gt_bootstrap_plan; else gt_core_plan; fi
}

gt_install_snapshot() {
  gt_core_snapshot
  [[ "$MODE" == --bootstrap ]] || return 0
  printf '%s\n' "${FACT[bootstrap.plan]}" "${FACT[bootstrap.apt]}" "${FACT[bootstrap.web]}" \
    "${FACT[listeners]:-}" "${FACT[timezone]:-}"
  [[ ! -e "$(gt_path /var/lib/gt-install/state)" ]] || sha256sum "$(gt_path /var/lib/gt-install/state)"
}

gt_bootstrap_execute() {
  local status=0 stage
  local CORE_CONFIRM=yes
  for stage in app web mail; do
    if [[ "$stage" == web ]]; then
      gt_domain_plan && gt_bootstrap_web_review || return 2
      [[ "$BOOTSTRAP_WEB" == "${FACT[bootstrap.web]}" ]] || {
        gt_core_error 'Web/DNS/TLS configuration changed since full-plan approval; run again.'; return 2;
      }
    fi
    status=0
    [[ "$stage" == app ]] || gt_progress "$stage" "$stage"
    case "$stage" in
      app) gt_install_app || status=$? ;;
      web) gt_install_web || status=$? ;;
      mail) gt_check_mail || status=$? ;;
    esac
    if [[ "$stage" == mail ]]; then return "$status"; fi
    if (( status != 10 )); then
      (( status != 0 )) || status=2
      return "$status"
    fi
  done
}
