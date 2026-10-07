#!/bin/bash
# Mutating integration: only a fresh dedicated container built from toolchains.Dockerfile.
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install && ! -e /home/grafioschtrader ]]
export GT_INSTALL_SOURCE_ONLY=1
source util/installer/gt-install.sh
gt_reset; gt_question_model
SCRATCH=$(mktemp -d)
trap gt_cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
umask 077
original_java=$(readlink -f /usr/bin/java)
original_javac=$(readlink -f /usr/bin/javac)
[[ "$original_java" == */java-17-* ]]
gt_packages; gt_java; gt_runtimes
[[ "${FACT[java.suitable]}" == absent && "${FACT[maven.path]}" == absent ]]
ACTION[java]=install ACTION[maven]=install
gt_core_toolchain_plan; gt_plan_packages
if (( ${#PLAN_BLOCKERS[@]} )); then gt_plan_report; exit 1; fi
[[ "${FACT[toolchain.java.package]}" == openjdk-25-jdk-headless && "${FACT[toolchain.maven.package]}" == maven ]]
FACT[source.commit]=0000000000000000000000000000000000000001
FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent
ANSWER[ADMIN_EMAIL]=admin@example.org ANSWER[SMTP_CONFIGURE]=no ANSWER[DOMAIN]=''
SECRET[DB_PASSWORD]=fixture-db SECRET[JASYPT_PASSWORD]=fixture-key
SECRET[JWT_SECRET]=012345678901234567890123456789012345678901234567
gt_core_begin
saved_secrets=$(cat /root/.gt-install/secrets)

# The real APT transaction completes; inject a failing return before finalizing the step.
gt_core_run() {
  "$@" || return $?
  [[ "$1" != env ]]
}
if gt_core_toolchains; then echo 'Expected interrupted toolchain step' >&2; exit 1; fi
[[ "$(readlink -f /usr/bin/java)" == "$original_java" && "$(readlink -f /usr/bin/javac)" == "$original_javac" ]]
[[ "${STATE[step.toolchains]}" == running ]]
STATE=() ANSWER=() SECRET=()
gt_state_load; gt_secrets_load
gt_core_run() { "$@"; }
gt_core_toolchains
[[ "${STATE[step.toolchains]}" == complete && "${STATE[java_home]}" == */java-25-* ]]
[[ "$(readlink -f /usr/bin/java)" == "$original_java" && "$(readlink -f /usr/bin/javac)" == "$original_javac" ]]
[[ "$(cat /root/.gt-install/secrets)" == "$saved_secrets" ]]
gt_core_user
version=$(gt_as_app "${STATE[maven]}" -v)
[[ "$version" == *'Apache Maven '* && "$version" == *'Java version: 25'* ]]
gt_core_run() { echo 'Completed toolchains must not invoke APT or alternatives' >&2; return 99; }
gt_core_toolchains
printf 'PASS: real pinned APT Java 25/Maven installation, interrupted recovery, original Java 17 selections and service-user Maven on Java 25.\n'
