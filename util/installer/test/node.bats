#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  FACT[node.version]=18.20.0 FACT[node.path]=/usr/bin/node FACT[architecture]=amd64
  NODE_FIXTURE_VERSION=24.15.0 NODE_FIXTURE_ARCH=x64 BAD_SUM=no DUPLICATE_SUM=no
  FIXTURE_INTEGRITY="sha512-$(printf '%086d' 0)=="
  gt_build_metadata() {
    [[ "$1" == semver ]] && printf '7.8.0\t%s\n' "$FIXTURE_INTEGRITY" || printf '22.0.0\t%s\n' "$FIXTURE_INTEGRITY"
  }
  gt_probe() {
    [[ "$1" == curl ]] || return 99
    local checksum
    checksum=$(printf '%064d' 0)
    [[ "$BAD_SUM" != yes ]] || checksum=invalid
    printf '%s  node-v%s-linux-%s.tar.xz\n' "$checksum" "$NODE_FIXTURE_VERSION" "$NODE_FIXTURE_ARCH" > "${*: -1}"
    [[ "$DUPLICATE_SUM" != yes ]] || printf '%s  node-v%s-linux-%s.tar.xz\n' "$checksum" "$NODE_FIXTURE_VERSION" "$NODE_FIXTURE_ARCH" >> "${*: -1}"
    return 0
  }
}

@test "official Node archive resolution maps supported architectures and pins versions and checksums" {
  for arch in amd64 arm64 armhf; do
    FACT[architecture]=$arch
    case "$arch" in amd64) NODE_FIXTURE_ARCH=x64 ;; arm64) NODE_FIXTURE_ARCH=arm64 ;; armhf) NODE_FIXTURE_ARCH=armv7l; NODE_FIXTURE_VERSION=22.22.3 ;; esac
    gt_build_resolve
    [ "${BUILD[mode]}" = archive ]
    [ "${BUILD[node_version]}" = "$NODE_FIXTURE_VERSION" ]
    [ "${BUILD[node_file]}" = "node-v$NODE_FIXTURE_VERSION-linux-$NODE_FIXTURE_ARCH.tar.xz" ]
    [ "${BUILD[prefix]}" = /opt/gt-build-tools ]
    [ "${BUILD[cli_version]}" = 22.0.0 ]
  done
}

@test "invalid duplicate and incompatible archive metadata block resolution" {
  BAD_SUM=yes
  run gt_build_resolve
  [ "$status" -ne 0 ]
  BAD_SUM=no DUPLICATE_SUM=yes
  run gt_build_resolve
  [ "$status" -ne 0 ]
  DUPLICATE_SUM=no NODE_FIXTURE_VERSION=24.1.0
  run gt_build_resolve
  [ "$status" -ne 0 ]
}

@test "resumption uses original build metadata without fetching current releases" {
  gt_build_resolve
  for key in "${!BUILD[@]}"; do STATE[build.$key]=${BUILD[$key]}; done
  gt_probe() { return 99; }
  gt_build_metadata() { return 99; }
  gt_build_resolve
  [ "${BUILD[node_version]}" = 24.15.0 ]
  STATE[build.cli_version]=21.0.0
  run gt_build_resolve
  [ "$status" -ne 0 ]
}

@test "build metadata rejects unknown fields shell fragments and arbitrary destinations" {
  for pair in 'mode|replace' 'prefix|/usr/local' 'cli_version|22.0.0;id' 'unknown|value' 'node_file|../../outside.tar.xz' 'node_sha|bad'; do
    run gt_build_field "${pair%%|*}" "${pair#*|}"
    [ "$status" -ne 0 ]
  done
}

@test "build plan refuses replacement of shared Node" {
  ANSWER[NODE_REPLACE]=yes
  gt_core_build_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'Shared Node replacement'* ]]
  [[ "${PLAN[*]}" == *'system Node unchanged'* ]]
}

@test "managed build tree digest detects contents modes and symlink target changes" {
  mkdir -m 755 "$SCRATCH/tree"
  printf 'one\n' > "$SCRATCH/tree/file"
  chmod 644 "$SCRATCH/tree/file"
  ln -s file "$SCRATCH/tree/link"
  original=$(gt_build_digest "$SCRATCH/tree")
  printf 'two\n' > "$SCRATCH/tree/file"
  [ "$(gt_build_digest "$SCRATCH/tree")" != "$original" ]
  chmod 666 "$SCRATCH/tree/file"
  run gt_build_digest "$SCRATCH/tree"
  [ "$status" -ne 0 ]
}

@test "publication resumes an interrupted rename but never adopts foreign matching trees" {
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  mkdir -m 755 "$SCRATCH/stage"
  printf 'payload\n' > "$SCRATCH/stage/file"
  chmod 644 "$SCRATCH/stage/file"
  STATE[resource.node]=intent
  gt_core_mark() { return 2; }
  if gt_build_publish node "$SCRATCH/stage" "$SCRATCH/published"; then return 1; fi
  [ -f "$SCRATCH/published/file" ]
  [ ! -e "$SCRATCH/stage" ]
  gt_core_mark() { STATE[$1]=$2; gt_state_save; }
  gt_build_publish node "$SCRATCH/stage" "$SCRATCH/published"
  [ "${STATE[resource.node]}" = owned ]
  unset 'STATE[resource.node]'
  run gt_build_publish node "$SCRATCH/stage" "$SCRATCH/published"
  [ "$status" -ne 0 ]
}

@test "archive validation rejects traversal links and writing through links before extraction" {
  python3 - "$SCRATCH" <<'PY'
import io, sys, tarfile
from pathlib import Path
root = Path(sys.argv[1])
for kind in ('valid', 'traversal', 'absolute', 'link', 'through-link'):
    with tarfile.open(root / (kind + '.tar.xz'), 'w:xz') as t:
        name = 'node/bin/node'
        if kind == 'traversal': name = 'node/../../outside'
        if kind == 'absolute': name = '/outside'
        if kind in ('link', 'through-link'):
            m = tarfile.TarInfo('node/bin'); m.type = tarfile.SYMTYPE
            m.linkname = '../../outside' if kind == 'link' else 'lib'; t.addfile(m)
        m = tarfile.TarInfo(name); m.size = 4; t.addfile(m, io.BytesIO(b'test'))
PY
  gt_build_archive_safe "$SCRATCH/valid.tar.xz" node
  for kind in traversal absolute link through-link; do
    run gt_build_archive_safe "$SCRATCH/$kind.tar.xz" node
    [ "$status" -ne 0 ]
  done
  [ ! -e "$SCRATCH/outside" ]
}

@test "unfinished owned staging may restart but foreign staging is preserved" {
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  mkdir -m 755 "$SCRATCH/stage"
  printf 'foreign\n' > "$SCRATCH/stage/file"
  run gt_build_stage node "$SCRATCH/stage"
  [ "$status" -ne 0 ]
  [ -f "$SCRATCH/stage/file" ]
  STATE[resource.node]=intent
  gt_build_stage node "$SCRATCH/stage"
  [ ! -e "$SCRATCH/stage/file" ]
  [ "$(stat -c %a "$SCRATCH/stage")" = 700 ]
}

@test "older completed core configuration gains only the managed build environment without reencrypting secrets" {
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/home/grafioschtrader"
  chmod 700 "$ROOT/var/lib/gt-install"
  CORE_HOME="$ROOT/home/grafioschtrader"
  printf 'export JAVA_HOME=/opt/jdk-25\n' > "$CORE_HOME/gtvar.sh"
  chmod 600 "$CORE_HOME/gtvar.sh"
  original=$(sha256sum "$CORE_HOME/gtvar.sh")
  STATE[file.variables]=${original%% *} STATE[step.configuration]=complete
  STATE[java_home]=/opt/jdk-25 STATE[maven]=/opt/apache-maven-3.9.9/bin/mvn
  STATE[build.mode]=archive STATE[build.node_home]=/opt/nodejs-gt STATE[build.prefix]=/opt/gt-build-tools
  ANSWER[DOCROOT]=/var/www/gt
  gt_core_config_valid() { :; }
  gt_encrypt_secret() { touch "$SCRATCH/reencrypted"; return 99; }
  chown() { :; }
  gt_core_configure
  grep -qx 'export npm_config_prefix=/opt/gt-build-tools' "$CORE_HOME/gtvar.sh"
  grep -q '/opt/nodejs-gt/bin:/opt/gt-build-tools/bin:' "$CORE_HOME/gtvar.sh"
  [ ! -e "$SCRATCH/reencrypted" ]
  [ "${STATE[file.variables]}" != "${original%% *}" ]
}
