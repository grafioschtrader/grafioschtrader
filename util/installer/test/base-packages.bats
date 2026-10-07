#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  INSTALLS=0
  gt_packages() { FACT[packages]=known; }
  gt_core_mark() { STATE[$1]=$2; }
  gt_core_run() {
    [[ "$*" == 'env DEBIAN_FRONTEND=noninteractive apt-get -y --no-remove --no-upgrade '* ]] || return 99
    printf '%s\n' "$@" > "$SCRATCH/installed-args"
    INSTALLS=$((INSTALLS+1))
    local package
    while IFS= read -r package; do PACKAGE[$package]=1.0; done < <(gt_base_package_names)
  }
}

@test "base package execution installs the planned missing packages and is repeatable" {
  PACKAGE[git]=1.0 PACKAGE[curl]=1.0
  gt_plan_base_packages
  gt_core_base_packages
  [ "$INSTALLS" -eq 1 ]
  [ "${STATE[step.base_packages]}" = complete ]
  local package
  for package in "${!PLAN_PACKAGES[@]}"; do
    if [[ "${PLAN_PACKAGES[$package]}" == install ]]; then
      grep -qx "$package" "$SCRATCH/installed-args"
    else
      ! grep -qx "$package" "$SCRATCH/installed-args"
    fi
  done
  gt_core_base_packages
  [ "$INSTALLS" -eq 1 ]
  ! grep -q 'update-alternatives\|systemctl' "$SCRATCH/installed-args"
}

@test "base package removal upgrade and failed simulation never reach APT execution" {
  for APT_OUTPUT in 'Remv shared-service [1.0]' 'Inst shared-library [1.0] (2.0 Debian)'; do
    run gt_core_base_packages
    [ "$status" -eq 2 ]
    [ ! -e "$SCRATCH/installed-args" ]
  done
  APT_FAIL=yes
  run gt_core_base_packages
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/installed-args" ]
}

@test "failed base package installation remains resumable and is verified after APT" {
  gt_core_run() { return 1; }
  if gt_core_base_packages; then false; else [ "$?" -eq 2 ]; fi
  [ "${STATE[step.base_packages]}" = running ]
  gt_core_run() { :; }
  run gt_core_base_packages
  [ "$status" -eq 2 ]
  [[ "$output" == *'still missing after APT'* ]]
  local package
  while IFS= read -r package; do PACKAGE[$package]=1.0; done < <(gt_base_package_names)
  gt_core_base_packages
  [ "${STATE[step.base_packages]}" = complete ]
}

@test "unknown inventory and removal of a completed base package block resumption" {
  gt_packages() { FACT[packages]=unknown; }
  run gt_core_base_packages
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/installed-args" ]
  gt_packages() { FACT[packages]=known; }
  STATE[step.base_packages]=complete
  gt_plan_base_packages
  [[ "${PLAN_BLOCKERS[*]}" == *'Previously verified base package is missing'* ]]
  run gt_core_base_packages
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/installed-args" ]
}

@test "base package failure stops the core before toolchains users and database changes" {
  gt_core_base_packages() { return 2; }
  gt_core_toolchains() { touch "$SCRATCH/toolchains-started"; }
  run gt_core_execute
  [ "$status" -eq 2 ]
  [ ! -e "$SCRATCH/toolchains-started" ]
}

@test "reused base packages do not require a new APT transaction" {
  local package
  while IFS= read -r package; do PACKAGE[$package]=1.0; done < <(gt_base_package_names)
  gt_plan_base_packages
  ! gt_plan_needs_apt
  unset 'PACKAGE[whiptail]'
  PLAN_PACKAGES=()
  gt_plan_base_packages
  gt_plan_needs_apt
}
