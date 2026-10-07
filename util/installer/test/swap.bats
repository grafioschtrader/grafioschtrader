#!/usr/bin/env bats
load check-helpers

setup() {
  setup_check
  source "$BATS_TEST_DIRNAME/check-helpers.bash"
  gt_question_model
  # A small swap file keeps the real dd/mkswap/blkid path fast; activation is simulated.
  SWAP_MB=8 RUN_ID=00000000000000000000000000000001 FILESYSTEM=ext2/ext3 MKSWAPFILE=no
  mkdir -p "$ROOT/var/lib/gt-install" "$ROOT/etc"
  chmod 700 "$ROOT/var/lib/gt-install"
  printf 'UUID=0000 / ext4 defaults 0 1\n' > "$ROOT/etc/fstab"
  : > "$SCRATCH/active"
  STATE=([run_id]=$RUN_ID) ANSWER=([SWAP]=yes)
  stat() {
    if [[ "$1 $2 $3" == '-Lf -c %T' ]]; then printf '%s\n' "$FILESYSTEM"; else command stat "$@"; fi
  }
  gt_probe() {
    printf '%s\n' "$*" >> "$PROBES"
    [[ "$*" == 'btrfs filesystem mkswapfile --help' && "$MKSWAPFILE" == yes ]]
  }
  swapon() {
    printf 'swapon %s\n' "$*" >> "$SCRATCH/commands"
    if [[ "$1" == --show=NAME ]]; then cat "$SCRATCH/active"; else readlink -f -- "${*: -1}" >> "$SCRATCH/active"; fi
  }
}

low_memory() {
  FACT[memory.MemTotal]=1900 FACT[memory.MemAvailable]=1800 FACT[memory.SwapTotal]=0
}

@test "a small host without swap is offered a swap file only on a supported root filesystem" {
  local filesystem
  collect_check
  low_memory
  for filesystem in ext2/ext3 xfs; do
    FILESYSTEM=$filesystem
    gt_compatibility
    [ "${ACTION[swap]}" = install ]
    [ "${FACT[swap.method]}" = mkswap ]
    gt_question_applies SWAP
    [ "$(gt_default SWAP)" = yes ]
  done
  FILESYSTEM=btrfs MKSWAPFILE=yes
  gt_compatibility
  [ "${ACTION[swap]}:${FACT[swap.method]}" = install:btrfs ]
  # Without mkswapfile, or on a filesystem swapon cannot map, the question is not asked.
  for FILESYSTEM in btrfs tmpfs; do
    MKSWAPFILE=no NOTES=()
    gt_compatibility
    [ "${ACTION[swap]}" = reuse ]
    run gt_question_applies SWAP
    [ "$status" -ne 0 ]
    [[ "${NOTES[*]}" == *'no swap file can be created'* ]]
  done
  FILESYSTEM=ext2/ext3 FACT[memory.SwapTotal]=1048572
  gt_compatibility
  [ "${ACTION[swap]}" = reuse ]
}

@test "planning refuses a foreign swap file or fstab entry and recognizes the journaled one" {
  low_memory
  ACTION[swap]=install
  gt_swap_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'create | /swapfile | 8 MiB swap file (mkswap)'* ]]
  [[ "${PLAN[*]}" == *"modify | /etc/fstab | Append '/swapfile none swap sw 0 0'"* ]]
  : > "$ROOT/swapfile"
  PLAN_BLOCKERS=()
  gt_swap_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'A foreign /swapfile exists'* ]]
  rm -f "$ROOT/swapfile"
  printf '/swapfile none swap sw 0 0\n' >> "$ROOT/etc/fstab"
  PLAN_BLOCKERS=()
  gt_swap_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'/etc/fstab already lists /swapfile'* ]]
  # An interrupted run owns both; its swap may already be active, so the offer no longer applies.
  ACTION[swap]=reuse STATE[resource.swapfile]=intent STATE[resource.fstab_backup]="$ROOT/etc/fstab.gt-install.x"
  PLAN_BLOCKERS=()
  gt_swap_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  STATE=() PLAN_BLOCKERS=()
  gt_swap_plan
  [[ "${PLAN_BLOCKERS[*]}" == *'less than 4000 MB RAM and no swap'* ]]
  STATE[step.swap]=complete PLAN=() PLAN_BLOCKERS=()
  gt_swap_plan
  [ "${#PLAN_BLOCKERS[@]}" -eq 0 ]
  [[ "${PLAN[*]}" == *'reuse | /swapfile'* ]]
  ANSWER[SWAP]=no PLAN=()
  gt_swap_plan
  [ "${#PLAN[@]}" -eq 0 ]
}

@test "the swap file is created, activated and listed in fstab once, with a backup" {
  local backup
  printf 'UUID=0000 / ext4 defaults 0 1' > "$ROOT/etc/fstab"
  gt_core_swap
  [ "$(stat -c '%u:%a:%s' "$ROOT/swapfile")" = "$EUID:600:8388608" ]
  [ "$(blkid -p -s TYPE -o value -- "$ROOT/swapfile")" = swap ]
  [ "$(cat "$SCRATCH/active")" = "$(readlink -f "$ROOT/swapfile")" ]
  # The last fstab line had no newline; the swap entry must still get its own line.
  [ "$(tail -n 2 "$ROOT/etc/fstab")" = $'UUID=0000 / ext4 defaults 0 1\n/swapfile none swap sw 0 0' ]
  backup=${STATE[resource.fstab_backup]}
  [[ "$backup" == "$ROOT/etc/fstab.gt-install."* ]]
  [ "$(cat "$backup")" = 'UUID=0000 / ext4 defaults 0 1' ]
  [ "${STATE[resource.swapfile]}:${STATE[step.swap]}" = owned:complete ]
  [[ "${STATE[file.fstab]}" =~ ^[a-f0-9]{64}$ ]]
  [ ! -e "$ROOT/.gt-swapfile-$RUN_ID" ]
  # A completed swap step only verifies; after a reboot fstab has activated it again.
  gt_swap_create() { touch "$SCRATCH/recreated"; return 2; }
  gt_core_swap
  [ ! -e "$SCRATCH/recreated" ]
  [ "$(grep -c '^/swapfile' "$ROOT/etc/fstab")" -eq 1 ]
  [ "$(grep -c '^swapon -- ' "$SCRATCH/commands")" -eq 1 ]
  : > "$SCRATCH/active"
  gt_core_swap
  [ "$(grep -c '^swapon -- ' "$SCRATCH/commands")" -eq 2 ]
  # A changed file is not the installer's swap file any more.
  truncate -s 4M "$ROOT/swapfile"
  run gt_core_swap
  [ "$status" -ne 0 ]
}

@test "an interrupted creation is discarded and repeated; foreign resources are never adopted" {
  printf 'partial' > "$ROOT/.gt-swapfile-$RUN_ID"
  STATE[resource.swapfile]=intent
  gt_core_swap
  [ "$(stat -c '%s' "$ROOT/swapfile")" = 8388608 ]
  [ "${STATE[step.swap]}" = complete ]

  STATE=([run_id]=$RUN_ID)
  rm -f "$ROOT/swapfile"
  printf 'UUID=0000 / ext4 defaults 0 1\n' > "$ROOT/etc/fstab"
  printf 'foreign' > "$ROOT/swapfile"
  run gt_core_swap
  [ "$status" -ne 0 ]
  [[ "$output" == *'A foreign /swapfile exists'* ]]
  [ "$(cat "$ROOT/swapfile")" = foreign ]

  rm -f "$ROOT/swapfile"
  printf '/swapfile none swap sw 0 0\n' >> "$ROOT/etc/fstab"
  cp "$ROOT/etc/fstab" "$SCRATCH/fstab-before"
  run gt_core_swap
  [ "$status" -ne 0 ]
  [[ "$output" == *'/etc/fstab already lists /swapfile'* ]]
  cmp "$ROOT/etc/fstab" "$SCRATCH/fstab-before"
}

@test "btrfs swap files come from mkswapfile, not from dd" {
  FILESYSTEM=btrfs MKSWAPFILE=yes
  gt_core_run() {
    printf '%s\n' "$*" >> "$SCRATCH/commands"
    if [[ "$1 $2 $3" == 'btrfs filesystem mkswapfile' ]]; then
      command dd if=/dev/zero of="${*: -1}" bs=1M count="$SWAP_MB" status=none && mkswap "${*: -1}" >/dev/null
    else "$@"; fi
  }
  gt_core_swap
  grep -q "^btrfs filesystem mkswapfile --size 8m $ROOT/.gt-swapfile-$RUN_ID\$" "$SCRATCH/commands"
  run grep -q '^dd ' "$SCRATCH/commands"
  [ "$status" -ne 0 ]
  [ "${STATE[step.swap]}" = complete ]
}

@test "swap is the first core step after the base packages and is no longer a stage-contract block" {
  local -a order=()
  for step in base_packages swap toolchains user buildtools clone database configure; do
    eval "gt_core_$step() { order+=($step); }"
  done
  gt_core_mark() { :; }
  gt_core_execute || true
  [ "${order[*]}" = 'base_packages swap toolchains user buildtools clone database configure' ]
  PLAN_BLOCKERS=()
  gt_stage_contract || true
  [[ "${PLAN_BLOCKERS[*]}" != *SWAP* ]]
}
