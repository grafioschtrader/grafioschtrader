#!/bin/bash
# Mutating integration: only a fresh dedicated container built from toolchains-archive.Dockerfile.
# Debian 12 has no APT JDK 25, so Java always comes from the vendor archive. GT_TEST_MAVEN=apt keeps the
# Debian 12 APT Maven; GT_TEST_MAVEN=archive treats it as too old, as Ubuntu 22.04's Maven 3.6 is, so the
# Apache Maven archive is downloaded and verified as well.
set -euo pipefail
cd "$(dirname "$0")/../../.."
[[ -f /.dockerenv && $EUID == 0 && ! -e /var/lib/gt-install && ! -e /home/grafioschtrader ]]
maven_mode=${GT_TEST_MAVEN:-apt}
[[ "$maven_mode" == apt || "$maven_mode" == archive ]]
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
apt-get update -qq
gt_system; gt_packages; gt_java; gt_runtimes
[[ "${FACT[java.suitable]}" == absent && "${FACT[maven.path]}" == absent ]]
[[ "${CANDIDATE[openjdk-25-jdk-headless]}" == unknown || "${CANDIDATE[openjdk-25-jdk-headless]}" == '(none)' ]]
ACTION[java]=isolate ACTION[maven]=install
[[ "$maven_mode" == apt ]] || CANDIDATE[maven]='3.6.3-5'
gt_core_toolchain_plan; gt_plan_packages
if (( ${#PLAN_BLOCKERS[@]} )); then gt_plan_report; exit 1; fi
gt_bootstrap_apt_plan
gt_report_rows 'Toolchain plan' "${PLAN[@]}"
[[ "${FACT[toolchain.java.package]}:${FACT[toolchain.java.source]}" == archive:temurin ]]
[[ "${FACT[toolchain.java.checksum]}" =~ ^sha256:[a-f0-9]{64}$ ]]
if [[ "$maven_mode" == apt ]]; then [[ "${FACT[toolchain.maven.package]}" == maven ]]
else [[ "${FACT[toolchain.maven.package]}:${FACT[toolchain.maven.checksum]}" =~ ^archive:sha512:[a-f0-9]{128}$ ]]; fi
FACT[source.commit]=0000000000000000000000000000000000000001
FACT[database.vendor]=absent FACT[database.gt_tables]=absent FACT[database.gt_user]=absent
ANSWER[ADMIN_EMAIL]=admin@example.org ANSWER[SMTP_CONFIGURE]=no ANSWER[DOMAIN]=''
SECRET[DB_PASSWORD]=fixture-db SECRET[JASYPT_PASSWORD]=fixture-key
SECRET[JWT_SECRET]=012345678901234567890123456789012345678901234567
gt_core_begin
saved_secrets=$(cat /root/.gt-install/secrets)
java_home=$(gt_toolchain_home java temurin "${STATE[toolchain.java.version]}")
stage="/opt/.gt-jdk-${STATE[run_id]}"

# The real download and checksum succeed; the extraction is interrupted after writing part of the tree.
tar() {
  [[ "$1" == --extract && "$*" == *"$stage/.gt-download.tar.gz"* ]] || { command tar "$@"; return; }
  command tar "$@" --wildcards '*/release'
  return 1
}
if gt_core_toolchains; then echo 'Expected interrupted toolchain step' >&2; exit 1; fi
unset -f tar
[[ -f "$stage/release" && ! -e "$java_home" && "${STATE[step.toolchains]}" == running ]]
[[ "$(readlink -f /usr/bin/java)" == "$original_java" && "$(readlink -f /usr/bin/javac)" == "$original_javac" ]]

STATE=() ANSWER=() SECRET=()
gt_state_load; gt_secrets_load
gt_core_toolchains
[[ "${STATE[step.toolchains]}" == complete && "${STATE[java_home]}" == "$java_home" ]]
[[ "${STATE[resource.jdk]}" == owned && ! -e "$stage" && "$(stat -c '%U:%a' "$java_home")" == root:755 ]]
[[ ! -e "$java_home/.gt-download.tar.gz" ]]
"$java_home/bin/java" --version | grep -q '^openjdk 25'
if [[ "$maven_mode" == apt ]]; then [[ "${STATE[maven]}" == /usr/bin/mvn ]]
else [[ "${STATE[maven]}" == /opt/apache-maven-*/bin/mvn && "${STATE[resource.maven_archive]}" == owned ]]; fi
# The archive is isolated: no alternatives entry, the shared Java 17 stays selected.
if update-alternatives --list java | grep -qF "$java_home"; then echo 'Archive JDK was registered' >&2; exit 1; fi
[[ "$(readlink -f /usr/bin/java)" == "$original_java" && "$(readlink -f /usr/bin/javac)" == "$original_javac" ]]
[[ "$(cat /root/.gt-install/secrets)" == "$saved_secrets" ]]
gt_core_user
version=$(gt_as_app "${STATE[maven]}" -v)
[[ "$version" == *'Apache Maven '* && "$version" == *'Java version: 25'* ]]

gt_toolchain_download() { echo 'Completed toolchains must not download again' >&2; return 99; }
gt_core_run() { echo 'Completed toolchains must not invoke APT or alternatives' >&2; return 99; }
gt_core_toolchains
printf 'PASS: Temurin JDK 25 archive with %s Maven: pinned vendor checksum, ' "$maven_mode"
printf 'interrupted extraction recovered, '
printf 'alternatives and shared Java 17 unchanged, service-user Maven on the archive JDK.\n'
