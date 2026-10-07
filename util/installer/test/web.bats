#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  gt_question_model
  mkdir -p "$ROOT/etc/nginx/sites-available" "$ROOT/etc/nginx/sites-enabled" "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  echo 'events {} http { include /etc/nginx/sites-enabled/*; }' > "$ROOT/etc/nginx/nginx.conf"
  echo 'server { listen 80 default_server; server_name existing.example; location / { return 204; } }' > "$ROOT/etc/nginx/sites-enabled/existing"
  ANSWER[DOCROOT]=/var/www/gt ANSWER[BACKEND_PORT]=9091
  FACT[web.lan]=192.0.2.20
}

@test "nginx inventory expands includes and probes every real host and default" {
  echo 'server_name second.example alias.example;' > "$ROOT/etc/nginx/names.conf"
  echo 'server { listen 8088; include /etc/nginx/names.conf; }' > "$ROOT/etc/nginx/sites-enabled/second"
  gt_nginx_inventory 192.0.2.20
  grep -q $'80\thttp\texisting.example' "$SCRATCH/web-endpoints"
  grep -q gt-install-default.invalid "$SCRATCH/web-endpoints"
  grep -q $'8088\thttp\talias.example' "$SCRATCH/web-endpoints"
  grep -q $'8088\thttp\tsecond.example' "$SCRATCH/web-endpoints"
}

@test "LAN collision hidden in an include is blocked" {
  echo 'server_name 192.0.2.20;' > "$ROOT/etc/nginx/names.conf"
  echo 'server { listen 80; include /etc/nginx/names.conf; }' > "$ROOT/etc/nginx/sites-enabled/second"
  run gt_nginx_inventory 192.0.2.20
  [ "$status" -eq 2 ]
  [[ "$output" == *'already belongs'* ]]
}

@test "ambiguous names bindings includes and implicit defaults are blocked" {
  local config
  for config in 'server { listen 80; server_name other.example; }' \
    'server { listen 80 default_server; server_name *.example; }' \
    'server { listen 192.0.2.20:80; server_name other.example; }' \
    'server { listen 80 default_server; include $variable; }'; do
    echo "$config" > "$ROOT/etc/nginx/sites-enabled/existing"
    run gt_nginx_inventory 192.0.2.20
    [ "$status" -eq 2 ]
  done
}

@test "owned vhost is excluded from foreign comparisons" {
  gt_nginx_render > "$ROOT/etc/nginx/sites-enabled/grafioschtrader"
  gt_nginx_inventory 192.0.2.20
  ! grep -q $'http\t192.0.2.20' "$SCRATCH/web-endpoints"
}

@test "renderer preserves backend port websocket rewrite and frontend fallback" {
  run gt_nginx_render
  [ "$status" -eq 0 ]
  [[ "$output" == *'127.0.0.1:9091/socket;'* ]]
  [[ "$output" == *'proxy_set_header Upgrade $http_upgrade;'* ]]
  [[ "$output" == *'try_files $uri $uri/ /grafioschtrader/index.html;'* ]]
  [[ "$output" == *'client_max_body_size 50m;'* ]]
  [[ "$output" != *default_server* ]]
}

@test "foreign file link and managed configuration drift block publication" {
  [ "$EUID" -eq 0 ] || skip 'Root ownership requires root'
  local target="$ROOT/etc/nginx/sites-available/grafioschtrader"
  local link="$ROOT/etc/nginx/sites-enabled/grafioschtrader"
  gt_nginx_render > "$SCRATCH/site"
  cp "$SCRATCH/site" "$target"
  run gt_nginx_owned
  [ "$status" -eq 2 ]
  rm "$target"
  gt_app_root_file web_nginx "$SCRATCH/site" "$target" 644
  ln -s "$target" "$link"
  run gt_nginx_owned
  [ "$status" -eq 2 ]
  STATE[resource.web_link]=intent
  gt_nginx_owned
  echo '# drift' >> "$target"
  run gt_nginx_owned
  [ "$status" -eq 2 ]
}

@test "APT removals and upgrades are blocked" {
  apt-get() { echo 'Inst shared-runtime [1.0] (2.0 distribution)'; }
  run gt_nginx_package_plan
  [ "$status" -eq 2 ]
  apt-get() { echo 'Remv apache2 [1.0]'; }
  run gt_nginx_package_plan
  [ "$status" -eq 2 ]
  apt-get() { echo 'Inst nginx (1.24 distribution)'; }
  gt_nginx_package_plan
}

@test "nginx warnings count as failed validation even with exit zero" {
  gt_core_run() { echo 'nginx: [warn] conflicting server name'; }
  run gt_nginx_test
  [ "$status" -ne 0 ]
}

@test "CLI accepts the web mode but refuses answer overrides and combined modes" {
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --install-web --answers /root/answers --yes
  [ "$status" -eq 2 ]
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --install-web --install-app
  [ "$status" -eq 2 ]
  run env GT_INSTALL_SOURCE_ONLY=0 bash "$REPO/util/installer/gt-install.sh" --help
  [[ "$output" == *'--install-web [--yes]'* ]]
}

@test "web preflight refuses unfinished applications Apache and domains before probing" {
  STATE[step.app]=running ANSWER[WEBSERVER]=nginx ANSWER[DOMAIN]=''
  run gt_web_preflight
  [ "$status" -eq 2 ]
  STATE[step.app]=complete ANSWER[WEBSERVER]=apache2
  run gt_web_preflight
  [ "$status" -eq 2 ]
  ANSWER[WEBSERVER]=nginx ANSWER[DOMAIN]=example.org
  run gt_web_preflight
  [ "$status" -eq 2 ]
}
