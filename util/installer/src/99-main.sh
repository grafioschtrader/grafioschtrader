gt_install_core() {
  local key resume=no before after reply file state_dir status=0 confirmation=install-core
  [[ "$MODE" != --bootstrap ]] || confirmation=install
  local completed_status
  completed_status=0
  gt_completed || completed_status=$?
  (( completed_status == 3 )) || return "$completed_status"
  state_dir=$(gt_path /var/lib/gt-install)
  gt_question_model
  if [[ -e "$state_dir/state" || -L "$state_dir/state" ]]; then
    if ! gt_state_load || ! gt_secrets_load; then
      gt_core_error 'Invalid journal or missing/invalid original secrets; recover them before resuming.'; return 2
    fi
    resume=yes
    [[ "$MODE" == --bootstrap || -z "${STATE[step.app]:-}" ]] || { gt_core_error 'Application stage has begun; resume without a mode or with --install-app.'; return 2; }
  fi
  gt_inventory; gt_compatibility; gt_report
  if [[ "$resume" == no && "${FACT[host.class]}" != fresh ]]; then gt_core_error 'Host is not fresh.'; return 2; fi
  if [[ -n "$ANSWERS_FILE" ]]; then gt_answers_file "$ANSWERS_FILE" || return 2; fi
  if [[ "$CORE_CONFIRM" != yes || "$resume" == no && -z "$ANSWERS_FILE" ]]; then
    { exec {QUESTION_FD}<>/dev/tty; } 2>/dev/null || { gt_core_error 'Terminal required, or use --answers FILE --yes.'; return 2; }
    QUESTION_OUTPUT=$QUESTION_FD
  fi
  if [[ "$resume" == no ]]; then
    gt_prepare_root || return $?
    if [[ -n "$ANSWERS_FILE" ]]; then gt_file_questions || return 2; else gt_questions || return $?; fi
    gt_prepare_secrets || return $?
  else
    # Configuration and application secrets are immutable in this core stage. Never rotate them.
    for key in "${!FILE_ANSWERS[@]}"; do
      [[ "$key" != DB_ROOT_PASSWORD ]] || continue
      if [[ "${Q_TYPE[$key]}" == secret ]]; then [[ "${FILE_ANSWERS[$key]}" == "${SECRET[$key]:-}" ]] || return 2
      else [[ "${FILE_ANSWERS[$key]}" == "${ANSWER[$key]:-}" ]] || return 2; fi
    done
    if [[ "$MODE" != --bootstrap || "${STATE[step.core]:-}" != complete ]]; then
      if [[ "${STATE[new_database_server]}" == yes || "${FACT[database.query]}" != ok ]]; then
        gt_collect_secret DB_ROOT_PASSWORD || return $?
      fi
      if [[ "${FACT[database.vendor]}" != absent ]]; then
        file=$(gt_db_options DB_ROOT_PASSWORD root) || return 2
        gt_database "$file"; rm -f -- "$file"
        gt_compatibility
      fi
    fi
  fi
  if ! gt_execution_plan; then gt_plan_report; return 2; fi
  if [[ "$MODE" != --bootstrap ]]; then gt_prepare_dns || return $?; fi
  gt_execution_plan; local plan_status=$?
  gt_plan_report
  (( plan_status == 0 )) || return 2
  before=$(gt_install_snapshot | LC_ALL=C sort | sha256sum)
  if [[ "$CORE_CONFIRM" != yes ]]; then
    printf '%s: %s: ' "$(gt_text 'Type the following to execute this plan' 'Zur Ausführung dieses Plans Folgendes eingeben')" "$confirmation" >&"$QUESTION_OUTPUT"
    IFS= read -r -u "$QUESTION_FD" reply && [[ "$reply" == "$confirmation" ]] || return 130
  fi
  gt_inventory
  if [[ "${FACT[database.vendor]}" != absent && -n "${SECRET[DB_ROOT_PASSWORD]:-}" ]]; then
    file=$(gt_db_options DB_ROOT_PASSWORD root) || return 2; gt_database "$file"; rm -f -- "$file"
  fi
  gt_compatibility
  if [[ "$MODE" != --bootstrap ]]; then gt_verify_dns || return 2; fi
  if ! gt_execution_plan; then gt_plan_report; return 2; fi
  after=$(gt_install_snapshot | LC_ALL=C sort | sha256sum)
  [[ "$before" == "$after" ]] || { gt_core_error 'Inventory changed after planning; run again to review a fresh plan.'; return 2; }
  gt_private_dir "$state_dir" || return 2
  gt_no_symlinks "$state_dir/lock" || return 2
  if [[ -z "$LOCK_FD" ]]; then exec {LOCK_FD}>"$state_dir/lock" || return 2; fi
  flock -n "$LOCK_FD" || { gt_message lock >&2; return 2; }
  if [[ "$resume" == no ]]; then gt_core_begin || return 2; fi
  if [[ "$MODE" == --bootstrap ]]; then
    STATE[scope]=bootstrap STATE[resource.bootstrap_plan]=${before%% *}
    gt_state_save || return 2
    BOOTSTRAP_APPROVED=yes BOOTSTRAP_WEB=${FACT[bootstrap.web]}
  fi
  if [[ "$MODE" != --bootstrap || "${STATE[step.core]:-}" != complete ]]; then
    gt_build_record || return 2
    gt_core_execute || status=$?
    (( status == 10 )) || return "$status"
  fi
  if [[ "$MODE" == --bootstrap ]]; then gt_bootstrap_execute; else return 10; fi
}

gt_cleanup() {
  set +vx
  gt_restore_terminal
  if [[ "${STATE[step.toolchains]:-}" == running ]]; then gt_alternatives_restore || :; fi
  SECRET=() SECRET_STATUS=() FILE_ANSWERS=() SECRET_INPUT=''
  PRIVATE_CONTENT=''
  local dir
  for dir in "${PRIVATE_FILES[@]}"; do rm -f -- "$dir"; done
  for dir in "${PRIVATE_DIRS[@]}"; do rm -rf -- "$dir"; done
  [[ -z "$SCRATCH" ]] || rm -rf -- "$SCRATCH"
}
main() {
  set +vx
  gt_reset
  local arg
  while (( $# )); do
    arg=$1; shift
    case "$arg" in
      --check|--dry-run|--prepare|--install-core|--install-app|--install-web|--check-mail) [[ -z "$MODE" || "$MODE" == "$arg" ]] || { gt_message mode >&2; return 2; }; MODE=$arg ;;
      --answers) [[ $# -gt 0 && -z "$ANSWERS_FILE" && -n "$1" && "$1" != --* ]] || { gt_message mode >&2; return 2; }; ANSWERS_FILE=$1; shift ;;
      --plain) ;;
      --yes) CORE_CONFIRM=yes ;;
      --help|-h)
        cat <<'USAGE'
Usage: sudo bash gt-install.sh [--answers FILE] [--yes] [--plain]
Without a mode: review and confirm the full bootstrap, or resume its journal.
Optional stages (all accept --plain):
  --check | --dry-run | --prepare [--answers FILE]
  --install-core [--answers FILE] [--yes]
  --install-app [--yes] | --install-web [--yes]
  --check-mail [--answers FILE] [--yes]   FILE may correct SMTP_PASSWORD until mail is verified
USAGE
        return 0 ;;
      *) gt_message mode >&2; return 2 ;;
    esac
  done
  [[ -n "$MODE" ]] || MODE=--bootstrap
  [[ ( -z "$ANSWERS_FILE" || "$MODE" == --bootstrap || "$MODE" == --prepare || "$MODE" == --install-core ||
      "$MODE" == --check-mail ) &&
    ( "$CORE_CONFIRM" == no || "$MODE" == --bootstrap || "$MODE" == --install-core || "$MODE" == --install-app || "$MODE" == --install-web || "$MODE" == --check-mail ) ]] || { gt_message mode >&2; return 2; }
  [[ -n "${BASH_SOURCE[0]:-}" && -f "${BASH_SOURCE[0]}" ]] || { gt_message pipe >&2; return 2; }
  (( EUID == 0 )) || { gt_message root >&2; return 2; }
  command -v timeout >/dev/null || { gt_message unavailable timeout >&2; return 1; }
  # Read an existing lock without creating the state directory or lock file.
  if [[ -e /var/lib/gt-install/lock ]]; then
    if ! exec {LOCK_FD}</var/lib/gt-install/lock || ! flock -n "$LOCK_FD"; then gt_message lock >&2; return 2; fi
  fi
  umask 077
  SCRATCH=$(mktemp -d) || return 1
  trap gt_cleanup EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  export GIT_TERMINAL_PROMPT=0
  case "$MODE" in --dry-run) gt_dry_run ;; --prepare) gt_prepare ;; --bootstrap|--install-core) gt_install_core ;; --install-app) gt_install_app ;; --install-web) gt_install_web ;; --check-mail) gt_check_mail ;; *) gt_check ;; esac
}

[[ "${GT_INSTALL_SOURCE_ONLY:-}" == 1 ]] || main "$@"
