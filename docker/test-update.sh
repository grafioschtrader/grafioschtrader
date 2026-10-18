#!/usr/bin/env bash
# Regression checks of the deployment-file refresh in update.sh, run against
# throw-away git repositories. Docker and the GitHub API are simulated; no
# container, image or remote repository is touched.
set -euo pipefail
docker_dir="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
mkdir -p "$work/bin"
export PATH="$work/bin:$PATH" TEST_LOG="$work/commands" TEST_COMPARE=""
export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.invalid
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.invalid

cat > "$work/bin/docker" <<'EOF'
#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$TEST_LOG"
case "$*" in
  'compose version') exit 0 ;;
  # Only a docker-compose.yml that mounts duckdns-update.sh passes the address family on.
  'compose config duckdns')
    if grep -q gt-duckdns-update.sh docker-compose.yml; then
      printf '    environment:\n      UPDATE_IP: ipv6\n    volumes:\n      - target: /etc/gt-duckdns-update.sh\n'
    else
      printf '    environment:\n      SUBDOMAINS: example\n'
    fi ;;
  # The database backup is the first step after the checks under test: stop there.
  'compose up -d mariadb') printf 'STOPPED-AT-BACKUP\n' >&2; exit 1 ;;
  *) printf 'Unexpected Docker command: %s\n' "$*" >&2; exit 99 ;;
esac
EOF
cat > "$work/bin/curl" <<'EOF'
#!/usr/bin/env bash
printf 'curl %s\n' "$*" >> "$TEST_LOG"
case "$*" in
  *api.github.com/repos/grafioschtrader/grafioschtrader/compare/*)
    [ -n "$TEST_COMPARE" ] || exit 22
    printf '{"url": "https://api.github.invalid", "status": "%s", "ahead_by": 1}\n' "$TEST_COMPARE" ;;
  *) exit 22 ;;
esac
EOF
chmod +x "$work/bin/"*

fail() { printf 'FAIL: %s\n' "$*" >&2; cat "$work/output" >&2 2>/dev/null || true; exit 1; }

# A checkout holding the files update.sh refreshes. Each commit changes only
# docker-compose.yml, whose content names the commit.
new_repo() {
  repo="$work/$1"
  mkdir -p "$repo/docker" "$repo/util/shellscripts"
  cp "$docker_dir/update.sh" "$repo/docker/update.sh"
  printf '#!/usr/bin/env bash\n' > "$repo/util/shellscripts/gt_to_g_rename.sh"
  printf '#!/usr/bin/env bash\n' > "$repo/util/shellscripts/gtcronrandom.sh"
  git -C "$repo" init -q -b master
}
commit_compose() {
  printf '%s\n' "$1" > "$repo/docker/docker-compose.yml"
  git -C "$repo" add -A
  git -C "$repo" commit -qm "$1"
}
write_env() {
  printf 'DB_ROOT_PASSWORD=test-root\nGT_VERSION=latest\n%s' "${2:-}" > "$1/docker/.env"
}
compose_is() { [ "$(cat "$1/docker/docker-compose.yml")" = "$2" ] || fail "docker-compose.yml is '$(cat "$1/docker/docker-compose.yml")', expected '$2'"; }
run_update() {
  local dir="$1"; shift
  : > "$TEST_LOG"
  if bash "$dir/docker/update.sh" "$@" > "$work/output" 2>&1; then fail 'update.sh did not stop at the simulated backup'; fi
}
expect_output() { grep -qF -- "$1" "$work/output" || fail "missing output '$1'"; }
expect_stopped_at_backup() { expect_output STOPPED-AT-BACKUP; }
expect_not_started() {
  if grep -q '^docker compose up' "$TEST_LOG"; then fail 'continued to the backup and restart'; fi
}

# The observed fault: install.sh from master, then update.sh replaced the newer
# docker-compose.yml with the release's, whose DuckDNS updater sends IPv4.
new_repo ahead
commit_compose release
git -C "$repo" tag V1.0.0
commit_compose newer
write_env "$repo"
run_update "$repo" 1.0.0
compose_is "$repo" newer
expect_output 'is newer than release V1.0.0 (ahead)'
expect_stopped_at_backup
ahead_repo="$repo"

new_repo diverged
commit_compose base
git -C "$repo" checkout -q -b local
commit_compose local
git -C "$repo" checkout -q master
commit_compose release
git -C "$repo" tag V1.0.0
git -C "$repo" checkout -q local
write_env "$repo"
run_update "$repo" 1.0.0
compose_is "$repo" local
expect_output '(diverged)'

new_repo behind
commit_compose older
commit_compose release
git -C "$repo" tag V1.0.0
git -C "$repo" checkout -q HEAD~1
write_env "$repo"
run_update "$repo" 1.0.0
compose_is "$repo" release
expect_output 'updated: docker-compose.yml'
expect_stopped_at_backup

# An earlier update recorded other hashes, but the file is still the checkout's:
# it was delivered by git, not edited here, and an older checkout is updated.
new_repo recorded-elsewhere
commit_compose older
commit_compose release
git -C "$repo" tag V1.0.0
git -C "$repo" checkout -q HEAD~1
write_env "$repo"
printf '%s  docker/docker-compose.yml\n' "$(printf 'ancient\n' | sha256sum | cut -d' ' -f1)" \
  > "$repo/docker/.gt-deployment.sha256"
run_update "$repo" 1.0.0
compose_is "$repo" release
expect_stopped_at_backup

# A real local edit still stops the update.
new_repo edited
commit_compose older
commit_compose release
git -C "$repo" tag V1.0.0
git -C "$repo" checkout -q HEAD~1
printf 'edited here\n' > "$repo/docker/docker-compose.yml"
write_env "$repo"
run_update "$repo" 1.0.0
compose_is "$repo" 'edited here'
expect_output 'changed on this host'
expect_not_started

# A shallow clone (git clone --depth 1, as the README describes) has no history:
# GitHub decides, and an undetermined position changes nothing.
shallow="$work/shallow"
git clone -q --depth 1 "file://$ahead_repo" "$shallow"
write_env "$shallow"
for status in ahead diverged; do
  TEST_COMPARE="$status" run_update "$shallow" 1.0.0
  compose_is "$shallow" newer
  expect_output "is newer than release V1.0.0 ($status)"
  grep -q "compare/V1.0.0...$(git -C "$shallow" rev-parse HEAD)" "$TEST_LOG" || fail 'did not ask GitHub to compare'
done
TEST_COMPARE="" run_update "$shallow" 1.0.0
compose_is "$shallow" newer
expect_output 'cannot tell whether this checkout'
expect_not_started
TEST_COMPARE=behind run_update "$shallow" 1.0.0
compose_is "$shallow" release

# The address family in .env must reach duckdns-update.sh, or the stack is not restarted.
new_repo duckdns
commit_compose 'duckdns without the address family'
write_env "$repo" $'COMPOSE_PROFILES=duckdns\nDUCKDNS_UPDATE_IP="ipv6"\n'
run_update "$repo" --skip-files
expect_output 'does not hand DUCKDNS_UPDATE_IP=ipv6 to duckdns-update.sh'
expect_not_started
commit_compose 'duckdns with - ./duckdns-update.sh:/etc/gt-duckdns-update.sh:ro'
run_update "$repo" --skip-files
expect_stopped_at_backup
write_env "$repo" $'COMPOSE_PROFILES=\nDUCKDNS_UPDATE_IP=ipv6\n'
commit_compose 'no duckdns service in use'
run_update "$repo" --skip-files
expect_stopped_at_backup

printf 'PASS: newer, diverged, older, recorded, edited and shallow checkouts; DuckDNS wiring\n'
