#!/bin/bash
# Scripted whiptail for the dialog tests. Each line of $WHIPTAIL_SCRIPT is "title<TAB>status<TAB>value"; a dialog whose
# --title matches the first line consumes it. Any other dialog accepts its proposal: the init value of an input box,
# the default item (or first entry) of a menu, Yes/OK for the rest. Password boxes are never answered automatically.
# Every call is logged to $WHIPTAIL_LOG as "title|arguments"; credentials never travel as arguments.
set -u
title='' box='' fd=1 default='' init='' first=''
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  # Positional arguments follow the box option, after an optional '--'.
  p=$((i + 1)); [[ "${args[p]:-}" != -- ]] || p=$((p + 1))
  case "${args[i]}" in
    --title) title=${args[i+1]} ;;
    --output-fd) fd=${args[i+1]} ;;
    --default-item) default=${args[i+1]} ;;
    --inputbox) box=input; init=${args[p+3]:-}; break ;;
    --passwordbox) box=password; break ;;
    --menu) box=menu; first=${args[p+4]:-}; break ;;
    --gauge) box=gauge; break ;;
    --yesno|--msgbox|--textbox) box=${args[i]#--}; break ;;
  esac
done
all="$*"
printf '%s|%s\n' "$title" "${all//$'\n'/ }" >> "$WHIPTAIL_LOG"
if [[ "$box" == gauge ]]; then cat > /dev/null; exit 0; fi
calls=$(wc -l < "$WHIPTAIL_LOG")
(( calls <= 300 )) || exit 97
line=''
[[ ! -s "$WHIPTAIL_SCRIPT" ]] || line=$(head -n 1 "$WHIPTAIL_SCRIPT")
if [[ -n "$line" && "${line%%$'\t'*}" == "$title" ]]; then
  sed -i 1d "$WHIPTAIL_SCRIPT"
  rest=${line#*$'\t'}
  status=${rest%%$'\t'*} value=${rest#*$'\t'}
  [[ "$rest" == *$'\t'* ]] || value=''
  printf '%s' "$value" >&"$fd"
  exit "$status"
fi
case "$box" in
  input) printf '%s' "$init" >&"$fd" ;;
  menu) printf '%s' "${default:-$first}" >&"$fd" ;;
  password) exit 96 ;;
esac
exit 0
