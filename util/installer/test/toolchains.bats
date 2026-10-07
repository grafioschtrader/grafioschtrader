#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  FACT[java.suitable]=absent FACT[maven.path]=absent FACT[architecture]=amd64
  ACTION[java]=install ACTION[maven]=install
  CANDIDATE[openjdk-25-jdk-headless]='25.0.1+8-1' CANDIDATE[maven]='3.8.7-1'
}

@test "APT toolchain plan pins real candidates without assuming distribution availability" {
  gt_core_toolchain_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [ "${FACT[toolchain.java.path]}" = pending ]
  [ "${PLAN_PACKAGES[openjdk-25-jdk-headless=25.0.1+8-1]}" = install ]
  [ "${PLAN_PACKAGES[maven=3.8.7-1]}" = install ]
  ! grep -E 'apt-get .*install|update-alternatives --set' "$PROBES"
}

@test "unavailable JDK and old Maven give architecture-specific alternatives without authorizing execution" {
  for arch in amd64 arm64 armhf; do
    FACT[architecture]=$arch
    CANDIDATE[openjdk-25-jdk-headless]='(none)' CANDIDATE[maven]='3.6.3-5'
    PLAN_BLOCKERS=() PLAN_PACKAGES=()
    gt_core_toolchain_plan
    [ "${#PLAN_BLOCKERS[@]}" -eq 2 ]
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
  mkdir -p "$ROOT/usr/local/jdk-25/bin" "$ROOT/opt/apache-maven-3.9.9/bin"
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
