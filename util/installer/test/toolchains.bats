#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  FACT[java.suitable]=absent FACT[maven.path]=absent FACT[architecture]=amd64
  ACTION[java]=install ACTION[maven]=install
  CANDIDATE[openjdk-25-jdk-headless]='25.0.1+8-1' CANDIDATE[maven]='3.8.7-1'
  TEMURIN_SHA=$(printf 'd%.0s' {1..64}) MAVEN_SHA=$(printf 'a%.0s' {1..128})
  RUN_ID=00000000000000000000000000000001
  FORGED=''
}

# Vendor metadata fixtures: only the metadata URLs the planner derives are answered.
vendor_fixture() {
  gt_probe() {
    local arg previous='' url='' output=''
    printf '%s\n' "$*" >> "$PROBES"
    [[ "$1" == curl ]] || return 127
    for arg in "$@"; do
      [[ "$previous" != -o ]] || output=$arg
      [[ "$arg" != https://* ]] || url=$arg
      previous=$arg
    done
    local adoptium='https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture='
    local filter='&image_type=jdk&os=linux&vendor=eclipse' maven=https://downloads.apache.org/maven/maven-3
    case "$url" in
      "${adoptium}x64$filter") temurin_json x64 ;;
      "${adoptium}aarch64$filter") temurin_json aarch64 ;;
      https://api.bell-sw.com/v1/liberica/releases\?*'&bitness=32&os=linux&arch=arm&'*) liberica_json ;;
      "$maven/")
        printf '<a href="3.9.16/">3.9.16/</a>\n<a href="3.10.0-rc-1/">3.10.0-rc-1/</a>\n'
        printf '<a href="3.10.0/">3.10.0/</a>\n' ;;
      "$maven/3.10.0/binaries/apache-maven-3.10.0-bin.tar.gz.sha512") printf '%s\n' "$MAVEN_SHA" ;;
      *) return 1 ;;
    esac > "$output"
  }
}

temurin_json() {
  local name="OpenJDK25U-jdk_$1_linux_hotspot_25.0.4.1_1.tar.gz" checksum=$TEMURIN_SHA
  local link="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.4.1%2B1/$name"
  [[ "$FORGED" != link ]] || link="https://downloads.example.org/$name"
  [[ "$FORGED" != checksum ]] || checksum=not-a-checksum
  printf '[{"release_name":"jdk-25.0.4.1+1","binary":{"os":"linux","architecture":"%s","image_type":"jdk",' "$1"
  printf '"jvm_impl":"hotspot","package":{"name":"%s","checksum":"%s","link":"%s"}}}]\n' "$name" "$checksum" "$link"
}

liberica_entry() {
  local name="bellsoft-jdk$1-linux-arm32-vfp-hflt.tar.gz"
  printf '{"version":"%s","filename":"%s",' "$1" "$name"
  printf '"downloadUrl":"https://github.com/bell-sw/Liberica/releases/download/%s/%s",' "$1" "$name"
  printf '"sha1":"%s","os":"linux","bundleType":"jdk","packageType":"tar.gz",' "$2"
  printf '"GA":true,"FX":false,"featureVersion":25}'
}

# The numerically newest release must win over list order: 25.0.4.1+1 is newer than 25.0.4+9.
liberica_json() {
  printf '[%s,%s]\n' "$(liberica_entry 25.0.4.1+1 "$(printf 'c%.0s' {1..40})")" \
    "$(liberica_entry 25.0.4+9 "$(printf 'b%.0s' {1..40})")"
}

journal_archives() {
  STATE[toolchain.java.package]=archive STATE[toolchain.java.source]=temurin STATE[toolchain.java.version]=25.0.2+10
  STATE[toolchain.java.file]=OpenJDK25U-jdk_x64_linux_hotspot_25.0.2_10.tar.gz
  STATE[toolchain.java.checksum]=sha256:$TEMURIN_SHA
  STATE[toolchain.maven.package]=archive STATE[toolchain.maven.source]=apache STATE[toolchain.maven.version]=3.9.16
  STATE[toolchain.maven.file]=apache-maven-3.9.16-bin.tar.gz STATE[toolchain.maven.checksum]=sha512:$MAVEN_SHA
}

journal_state() {
  STATE=([schema]=1 [status]=running [scope]=core [run_id]=$RUN_ID
    [planned_commit]=0000000000000000000000000000000000000001 [java_home]=pending [maven]=pending
    [new_database_server]=yes [database_before]=no [account_before]=no [step.toolchains]=pending)
  journal_archives
}

# Destinations below the fixture root; the real ones are absolute paths under /opt.
fixture_homes() {
  gt_toolchain_home() {
    case "$1" in
      java) printf '%s/opt/jdk-%s-%s\n' "$ROOT" "${3/+/_}" "$2" ;;
      maven) printf '%s/opt/apache-maven-%s\n' "$ROOT" "$3" ;;
    esac
  }
}

maven_archive_fixture() {
  local root=${1:-apache-maven-3.10.0}
  mkdir -p "$BATS_TEST_TMPDIR/fixture/$root/bin" "$ROOT/var/lib/gt-install" "$ROOT/opt"
  chmod 700 "$ROOT/var/lib/gt-install"
  printf '#!/bin/sh\necho "Apache Maven 3.10.0"\n' > "$BATS_TEST_TMPDIR/fixture/$root/bin/mvn"
  chmod 755 "$BATS_TEST_TMPDIR/fixture/$root/bin/mvn"
  tar -C "$BATS_TEST_TMPDIR/fixture" -czf "$BATS_TEST_TMPDIR/maven.tar.gz" "$root"
  FIXTURE_SHA=$(sha512sum "$BATS_TEST_TMPDIR/maven.tar.gz"); FIXTURE_SHA=${FIXTURE_SHA%% *}
  STATE[run_id]=$RUN_ID STATE[toolchain.maven.package]=archive STATE[toolchain.maven.source]=apache
  STATE[toolchain.maven.version]=3.10.0 STATE[toolchain.maven.file]=apache-maven-3.10.0-bin.tar.gz
  STATE[toolchain.maven.checksum]=sha512:$FIXTURE_SHA
  fixture_homes
  DOWNLOAD_FAIL=''
  gt_toolchain_download() {
    printf '%s\n' "$1" >> "$SCRATCH/downloads"
    [[ -z "$DOWNLOAD_FAIL" || "$1" != *"$DOWNLOAD_FAIL"* ]] || return 22
    cp "$BATS_TEST_TMPDIR/maven.tar.gz" "$2"
  }
}

@test "APT toolchain plan pins real candidates without assuming distribution availability" {
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${FACT[toolchain.java.path]}" = pending ]
  [ "${PLAN_PACKAGES[openjdk-25-jdk-headless=25.0.1+8-1]}" = install ]
  [ "${PLAN_PACKAGES[maven=3.8.7-1]}" = install ]
  ! grep -E 'apt-get .*install|update-alternatives --set' "$PROBES"
}

@test "unresolvable vendor archives block execution and keep architecture-specific manual instructions" {
  for arch in amd64 arm64 armhf; do
    FACT[architecture]=$arch
    CANDIDATE[openjdk-25-jdk-headless]='(none)' CANDIDATE[maven]='3.6.3-5'
    PLAN_BLOCKERS=() PLAN_PACKAGES=()
    gt_core_toolchain_plan
    [ "${#PLAN_BLOCKERS[@]}" -eq 2 ]
    [[ "${PLAN_BLOCKERS[0]}" == *'Could not resolve a verified java archive'* ]]
    [[ "${PLAN_BLOCKERS[1]}" == *'Could not resolve a verified maven archive'* ]]
    [ "${#PLAN_PACKAGES[@]}" -eq 0 ]
    run gt_toolchain_guidance
    [[ "$output" == *'Install-Java'* && "$output" == *'maven.apache.org/download.cgi'* ]]
    case "$arch" in
      amd64) [[ "$output" == *'Linux amd64'* ]] ;;
      arm64) [[ "$output" == *'Linux aarch64'* ]] ;;
      armhf) [[ "$output" == *'Linux arm32-vfp-hflt'* ]] ;;
    esac
  done
}

@test "missing APT JDK and old Maven plan pinned vendor archives on every architecture" {
  vendor_fixture
  for arch in amd64 arm64 armhf; do
    FACT[architecture]=$arch
    CANDIDATE[openjdk-25-jdk-headless]='(none)' CANDIDATE[maven]='3.6.3-5'
    PLAN=() PLAN_BLOCKERS=() PLAN_PACKAGES=()
    gt_core_toolchain_plan
    [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
    [ "${#PLAN_PACKAGES[@]}" -eq 0 ]
    [ "${FACT[toolchain.java.package]}:${FACT[toolchain.maven.package]}" = archive:archive ]
    [ "${FACT[toolchain.java.path]}:${FACT[toolchain.maven.path]}" = pending:pending ]
    [ "${FACT[toolchain.maven.version]}" = 3.10.0 ]
    [ "${FACT[toolchain.maven.checksum]}" = "sha512:$MAVEN_SHA" ]
    row="isolate | /opt/apache-maven-3.10.0 | Apache Maven 3.10.0; apache-maven-3.10.0-bin.tar.gz; sha512:$MAVEN_SHA"
    [[ "${PLAN[*]}" == *"$row"* ]]
    # Archives never touch alternatives, so the plan does not announce their recovery.
    [[ "${PLAN[*]}" != *java-alternatives* ]]
    case "$arch" in
      amd64)
        [ "${FACT[toolchain.java.file]}" = OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz ]
        [ "${FACT[toolchain.java.checksum]}" = "sha256:$TEMURIN_SHA" ]
        [[ "${PLAN[*]}" == *'isolate | /opt/jdk-25.0.4.1_1-temurin | Eclipse Temurin 25.0.4.1+1'* ]] ;;
      arm64)
        [ "${FACT[toolchain.java.file]}" = OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.4.1_1.tar.gz ] ;;
      armhf)
        [ "${FACT[toolchain.java.version]}" = 25.0.4.1+1 ]
        [ "${FACT[toolchain.java.checksum]}" = "sha1:$(printf 'c%.0s' {1..40})" ]
        [[ "${PLAN[*]}" == *'isolate | /opt/jdk-25.0.4.1_1-liberica | BellSoft Liberica 25.0.4.1+1'* ]] ;;
    esac
  done
  ! grep -E 'apt-get .*install|update-alternatives --set' "$PROBES"
}

@test "armhf ignores the APT JDK, which Debian builds as the Zero VM, and plans Liberica" {
  vendor_fixture
  FACT[architecture]=armhf
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${FACT[toolchain.java.package]}" = archive ]
  [[ "${PLAN[*]}" == *'BellSoft Liberica 25.0.4.1+1'* ]]
  [ -z "${PLAN_PACKAGES[openjdk-25-jdk-headless=25.0.1+8-1]:-}" ]
  [ "${PLAN_PACKAGES[maven=3.8.7-1]}" = install ]
}

@test "a journal that recorded a Zero VM JDK blocks resumption instead of building with it" {
  mkdir -p "$ROOT/usr/lib/jvm/java-25-openjdk-armhf/lib/zero"
  STATE[scope]=bootstrap STATE[step.toolchains]=complete STATE[maven]=/usr/bin/mvn
  STATE[java_home]=$ROOT/usr/lib/jvm/java-25-openjdk-armhf FACT[maven.path]=/usr/bin/mvn
  gt_core_toolchain_plan || true
  [[ "${PLAN_BLOCKERS[*]}" == *'is the interpreter-only Zero VM'* ]]
  mkdir -p "${STATE[java_home]}/lib/server" && touch "${STATE[java_home]}/lib/server/libjvm.so"
  FACT[java.suitable]=${STATE[java_home]} PLAN_BLOCKERS=()
  gt_core_toolchain_plan || true
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
}

@test "vendor metadata with a foreign download link or a malformed checksum blocks planning" {
  vendor_fixture
  CANDIDATE[openjdk-25-jdk-headless]='(none)'
  for FORGED in link checksum; do
    PLAN=() PLAN_BLOCKERS=() PLAN_PACKAGES=()
    gt_core_toolchain_plan
    [ "${#PLAN_BLOCKERS[@]}" -eq 1 ]
    [[ "${PLAN_BLOCKERS[0]}" == *'Could not resolve a verified java archive'* ]]
    [ "${FACT[toolchain.java.package]}" = none ]
  done
}

@test "a resumed plan keeps the journaled archive offline and refuses foreign destinations or architectures" {
  gt_probe() { printf '%s\n' "$*" >> "$PROBES"; return 1; }
  fixture_homes
  STATE[scope]=core STATE[step.toolchains]=running
  journal_archives
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${FACT[toolchain.java.version]}:${FACT[toolchain.maven.version]}" = 25.0.2+10:3.9.16 ]
  [ "${FACT[toolchain.java.checksum]}" = "sha256:$TEMURIN_SHA" ]
  ! grep -q curl "$PROBES"
  mkdir -p "$ROOT/opt/apache-maven-3.9.16"
  PLAN_BLOCKERS=()
  gt_core_toolchain_plan
  [[ "${PLAN_BLOCKERS[*]}" == *"Foreign maven destination exists: $ROOT/opt/apache-maven-3.9.16"* ]]
  # The installer's own interrupted publication is not foreign.
  STATE[resource.maven_archive]=intent PLAN_BLOCKERS=()
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  FACT[architecture]=arm64 PLAN_BLOCKERS=()
  gt_core_toolchain_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'does not match architecture arm64'* ]]
}

@test "archive journal fields round trip and inconsistent records are rejected" {
  local change
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  journal_state
  gt_state_save
  gt_state_load
  [ "${STATE[toolchain.java.checksum]}" = "sha256:$TEMURIN_SHA" ]
  [ "${STATE[toolchain.maven.file]}" = apache-maven-3.9.16-bin.tar.gz ]
  for change in toolchain.java.file=OpenJDK25U-jdk_x64_linux_hotspot_25.0.3_1.tar.gz \
      "toolchain.maven.checksum=sha256:$TEMURIN_SHA" toolchain.java.source=liberica \
      toolchain.maven.version=4.0.0 toolchain.java.file=../../etc/passwd.tar.gz; do
    journal_state
    STATE[${change%%=*}]=${change#*=}
    gt_state_save
    run gt_state_load
    [ "$status" -ne 0 ]
  done
  # Archive fields beside an APT selection are not silently ignored.
  journal_state
  STATE[toolchain.maven.package]=maven STATE[toolchain.maven.version]=3.8.7-1
  gt_state_save
  run gt_state_load
  [ "$status" -ne 0 ]
}

@test "a checksum mismatch never extracts and resumption replaces a partial stage" {
  local stage target
  maven_archive_fixture
  target="$ROOT/opt/apache-maven-3.10.0" stage="$ROOT/opt/.gt-maven_archive-$RUN_ID"
  STATE[toolchain.maven.checksum]=sha512:$MAVEN_SHA
  if gt_toolchain_archive_install maven; then return 1; fi
  [ ! -e "$target" ]
  # Only the rejected download is in the stage; nothing was extracted.
  [ "$(ls -A "$stage")" = .gt-download.tar.gz ]
  [ "${STATE[resource.maven_archive]}" = intent ]
  # An interruption during extraction leaves a populated stage behind.
  touch "$stage/partial"
  STATE[toolchain.maven.checksum]=sha512:$FIXTURE_SHA
  DOWNLOAD_FAIL=dlcdn.apache.org
  gt_toolchain_archive_install maven
  [ -x "$target/bin/mvn" ]
  [ ! -e "$target/partial" ]
  [ ! -e "$stage" ]
  [ ! -e "$target/.gt-download.tar.gz" ]
  [ "$(stat -c '%a' "$target")" = 755 ]
  [ "${STATE[resource.maven_archive]}" = owned ]
  [[ "${STATE[file.maven_archive]}" =~ ^[a-f0-9]{64}$ ]]
  # The CDN only serves current releases; the archive host keeps the journaled version.
  [ "$(tail -n 1 "$SCRATCH/downloads")" = \
    https://archive.apache.org/dist/maven/maven-3/3.10.0/binaries/apache-maven-3.10.0-bin.tar.gz ]
  [ "$(wc -l < "$SCRATCH/downloads")" -eq 3 ]
  gt_toolchain_archive_install maven
  [ "$(wc -l < "$SCRATCH/downloads")" -eq 3 ]
  printf 'changed\n' >> "$target/bin/mvn"
  if gt_toolchain_archive_install maven; then return 1; fi
}

@test "an archive with an unexpected top-level directory is refused before extraction" {
  maven_archive_fixture evil
  STATE[toolchain.maven.checksum]=sha512:$FIXTURE_SHA
  if gt_toolchain_archive_install maven; then return 1; fi
  [ ! -e "$ROOT/opt/apache-maven-3.10.0" ]
  [ "$(ls -A "$ROOT/opt/.gt-maven_archive-$RUN_ID")" = .gt-download.tar.gz ]
}

@test "confirmed archives install without APT or alternatives and select their own destinations" {
  alternatives_fixture
  journal_state
  gt_toolchain_archive_install() { printf '%s\n' "$1" >> "$SCRATCH/archives"; }
  gt_core_run() { touch "$SCRATCH/apt"; return 99; }
  # Discovery may rank another JDK first; the confirmed archive must still be selected.
  gt_java() { FACT[java.suitable]=/usr/lib/jvm/other-25; }
  gt_runtimes() { FACT[maven.path]=/usr/bin/mvn; }
  gt_toolchain_verify() { printf '%s %s\n' "$1" "$2" > "$SCRATCH/verified"; }
  gt_core_toolchains
  [ "$(cat "$SCRATCH/archives")" = $'java\nmaven' ]
  [ ! -e "$SCRATCH/apt" ]
  [ ! -e "$SCRATCH/restores" ]
  [ -z "${STATE[toolchain.alternatives]:-}" ]
  [ "$(cat "$SCRATCH/verified")" = '/opt/jdk-25.0.2_10-temurin /opt/apache-maven-3.9.16/bin/mvn' ]
  [ "${STATE[java_home]}:${STATE[maven]}" = /opt/jdk-25.0.2_10-temurin:/opt/apache-maven-3.9.16/bin/mvn ]
  [ "${STATE[step.toolchains]}" = complete ]
  gt_state_load
  [ "${STATE[java_home]}" = /opt/jdk-25.0.2_10-temurin ]
}

@test "existing suitable toolchains bypass APT and keep their paths" {
  ACTION[java]=reuse ACTION[maven]=reuse
  FACT[java.suitable]=/usr/local/jdk-25 FACT[maven.path]=/opt/apache-maven-3.9.9/bin/mvn
  gt_core_toolchain_plan
  [ "${#PLAN_PACKAGES[@]}" -eq 0 ]
  [ "${FACT[toolchain.java.path]}" = /usr/local/jdk-25 ]
  [ "${FACT[toolchain.maven.path]}" = /opt/apache-maven-3.9.9/bin/mvn ]
}

@test "pending toolchain recovery keeps recorded candidates and blocks a silently changed version" {
  STATE[scope]=core STATE[step.toolchains]=running
  STATE[toolchain.java.package]=openjdk-25-jdk-headless STATE[toolchain.java.version]='25.0.1+8-1'
  STATE[toolchain.maven.package]=maven STATE[toolchain.maven.version]='3.8.7-1'
  CANDIDATE[openjdk-25-jdk-headless]='25.0.2+1-1'
  gt_core_toolchain_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'Recorded candidate openjdk-25-jdk-headless=25.0.1+8-1 is unavailable'* ]]
  [ "${FACT[toolchain.java.version]}" = '25.0.1+8-1' ]
  PACKAGE[openjdk-25-jdk-headless]='25.0.1+8-1'
  PLAN_BLOCKERS=() PLAN_PACKAGES=()
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
}

@test "an installed Maven is usable after adding Java even when its initial version probe cannot run" {
  PACKAGE[maven]='3.8.7-1' FACT[maven.version]=unknown
  mkdir -p "$ROOT/usr/bin"
  printf '#!/bin/sh\nexit 0\n' > "$ROOT/usr/bin/mvn"
  chmod +x "$ROOT/usr/bin/mvn"
  gt_core_toolchain_plan
  [ "${FACT[toolchain.maven.package]}" = none ]
  [ "${FACT[toolchain.maven.path]}" = "$ROOT/usr/bin/mvn" ]
}

@test "Maven probe supplies the selected JDK and parses colored version output" {
  gt_maven_probe /opt/apache-maven-3.9.9/bin/mvn /usr/local/jdk-25 >/dev/null
  grep -q 'JAVA_HOME=/usr/local/jdk-25' "$PROBES"
  grep -q 'MAVEN_SKIP_RC=1' "$PROBES"
  [ "$(gt_maven_version $'\033[1mApache Maven 3.9.9\033[m')" = 3.9.9 ]
}

@test "manual JDK and versioned Maven archives are discovered without changing global paths" {
  fake_executable java
  fake_executable mvn
  unset JAVA_HOME
  mkdir -p "$ROOT/usr/local/jdk-25/bin" "$ROOT/usr/local/jdk-25/lib/server" "$ROOT/opt/apache-maven-3.9.9/bin"
  touch "$ROOT/usr/local/jdk-25/lib/server/libjvm.so"
  printf 'JAVA_VERSION="25.0.1"\nIMPLEMENTOR="Manual fixture"\n' > "$ROOT/usr/local/jdk-25/release"
  for executable in "$ROOT/usr/local/jdk-25/bin/java" "$ROOT/usr/local/jdk-25/bin/javac" "$ROOT/opt/apache-maven-3.9.9/bin/mvn"; do
    printf '#!/bin/sh\nexit 99\n' > "$executable"
    chmod +x "$executable"
  done
  # Make the PATH Maven unsuitable so discovery must continue into /opt.
  gt_probe() {
    printf '%s\n' "$*" >> "$PROBES"
    case "$*" in
      *"$SCRATCH/bin/mvn -v") echo 'Apache Maven 3.6.3' ;;
      *'/bin/mvn -v') echo 'Apache Maven 3.9.9' ;;
      *) return 127 ;;
    esac
  }
  gt_java; gt_runtimes
  [ "${FACT[java.suitable]}" = "$ROOT/usr/local/jdk-25" ]
  [ "${FACT[maven.path]}" = "$ROOT/opt/apache-maven-3.9.9/bin/mvn" ]
  grep -q "JAVA_HOME=$ROOT/usr/local/jdk-25" "$PROBES"
}

alternatives_fixture() {
  mkdir -p "$ROOT/var/lib/gt-install"
  chmod 700 "$ROOT/var/lib/gt-install"
  printf '/opt/shared17/bin/java\n' > "$SCRATCH/current-java"
  printf '/opt/shared17/bin/javac\n' > "$SCRATCH/current-javac"
  update-alternatives() {
    case "$1" in
      --get-selections) printf 'java auto /opt/shared17/bin/java\njavac manual /opt/shared17/bin/javac\n' ;;
      --query) printf 'Value: %s\n' "$(cat "$SCRATCH/current-$2")" ;;
      --set) printf '%s\n' "$3" > "$SCRATCH/current-$2"; printf '%s\n' "$*" >> "$SCRATCH/restores" ;;
      *) return 99 ;;
    esac
  }
}

@test "alternatives recovery preserves the original snapshot across an interrupted transaction" {
  alternatives_fixture
  gt_alternatives_save
  printf '/opt/new25/bin/java\n' > "$SCRATCH/current-java"
  gt_alternatives_save
  [ "${STATE[alternative.java]}" = /opt/shared17/bin/java ]
  gt_alternatives_restore
  [ "$(cat "$SCRATCH/current-java")" = /opt/shared17/bin/java ]
  [ "$(wc -l < "$SCRATCH/restores")" -eq 1 ]
  gt_alternatives_restore
  [ "$(wc -l < "$SCRATCH/restores")" -eq 1 ]
}

@test "failed APT execution restores alternatives and never completes the toolchain step" {
  alternatives_fixture
  STATE[step.toolchains]=pending
  STATE[toolchain.java.package]=openjdk-25-jdk-headless STATE[toolchain.java.version]='25.0.1+8-1'
  STATE[toolchain.maven.package]=none STATE[toolchain.maven.version]=none
  apt-get() { printf 'Inst openjdk-25-jdk-headless (25.0.1+8-1 test [amd64])\n'; }
  gt_core_run() {
    if [[ "$1" == env ]]; then
      printf '/opt/new25/bin/java\n' > "$SCRATCH/current-java"
      return 1
    fi
    "$@"
  }
  if gt_core_toolchains; then return 1; fi
  [ "$(cat "$SCRATCH/current-java")" = /opt/shared17/bin/java ]
  [ "${STATE[step.toolchains]}" = running ]
}

@test "package removals and upgrades block toolchain execution before alternatives or APT writes" {
  alternatives_fixture
  STATE[step.toolchains]=pending
  STATE[toolchain.java.package]=openjdk-25-jdk-headless STATE[toolchain.java.version]='25.0.1+8-1'
  STATE[toolchain.maven.package]=none STATE[toolchain.maven.version]=none
  apt-get() { printf '%s\n' "$TRANSACTION"; }
  gt_core_run() { touch "$SCRATCH/mutation"; }
  for TRANSACTION in 'Remv shared-service [1.0]' 'Inst shared-service [1.0] (2.0 test [amd64])'; do
    run gt_core_toolchains
    [ "$status" -ne 0 ]
    [ ! -e "$SCRATCH/mutation" ]
    [ ! -e "$ROOT/var/lib/gt-install/state" ]
  done
}

@test "pending paths and alternative records round trip through the strict journal parser" {
  alternatives_fixture
  STATE=([schema]=1 [status]=running [scope]=core [run_id]=00000000000000000000000000000001
    [planned_commit]=0000000000000000000000000000000000000001 [java_home]=pending [maven]=pending
    [new_database_server]=yes [database_before]=no [account_before]=no [step.toolchains]=running
    [toolchain.java.package]=openjdk-25-jdk-headless [toolchain.java.version]=25.0.1+8-1
    [toolchain.maven.package]=maven [toolchain.maven.version]=3.8.7-1)
  gt_alternatives_save
  gt_state_load
  [ "${STATE[java_home]}" = pending ]
  [ "${STATE[alternative.java]}" = /opt/shared17/bin/java ]
  STATE[step.toolchains]=complete
  gt_state_save
  run gt_state_load
  [ "$status" -ne 0 ]
}
