#!/usr/bin/env bash
# Подменный adb для tools/test/install-apk-test.sh. «Планшет» — папка $FAKE_ROOT:
# удалённый путь /data/local/tmp/... превращается в $FAKE_ROOT/data/local/tmp/...
#
# Понимает: wait-for-device, push <src> <dst>, shell "<команда>".
# Сбои, включаются переменными окружения:
#   FAKE_CORRUPT_PUSHES="001:2,003:1"  первые 2 передачи куска с суффиксом .001 (и первая .003)
#                                      пишут файл правильного размера, где после первых 4096 байт
#                                      одни нули, как при обрыве USB 20 октября. Кусок короче
#                                      4096 байт этим способом не портится. Счётчик передач
#                                      лежит в $FAKE_ROOT/.pushes.
#   FAKE_NO_DEVICE=1                   wait-for-device висит.
#   FAKE_PM_FAIL=1                     pm install печатает Failure [INSTALL_FAILED_INVALID_APK].
#   FAKE_BAD_ASSEMBLY=1                склейка кусков (cat … > файл) дописывает в файл лишний байт.
set -euo pipefail

: "${FAKE_ROOT:?FAKE_ROOT не задан}"
REMOTE_PREFIX=/data/local/tmp
KEEP_BYTES=4096

map_path() {
  printf '%s' "${1//$REMOTE_PREFIX/$FAKE_ROOT$REMOTE_PREFIX}"
}

# Сколько первых передач куска с этим суффиксом нужно испортить.
find_corrupt_limit() {
  local suffix=$1 pair
  local -a pairs=()
  IFS=, read -ra pairs <<< "${FAKE_CORRUPT_PUSHES:-}"
  for pair in "${pairs[@]}"; do
    if [[ "${pair%%:*}" == "$suffix" ]]; then
      echo "${pair##*:}"
      return
    fi
  done
  echo 0
}

write_corrupted_copy() {
  local src=$1 target=$2 size keep
  size=$(stat -c %s "$src")
  keep=$(( size < KEEP_BYTES ? size : KEEP_BYTES ))
  { head -c "$keep" "$src"; head -c $(( size - keep )) /dev/zero; } > "$target"
}

fake_wait_for_device() {
  if [[ "${FAKE_NO_DEVICE:-0}" == 1 ]]; then
    exec sleep 3600
  fi
}

fake_push() {
  local src=$1 dst=$2 target suffix count
  target=$(map_path "$dst")
  if [[ ! -d "$(dirname "$target")" ]]; then
    echo "adb: error: failed to copy '$src' to '$dst': remote couldn't create file: No such file or directory" >&2
    exit 1
  fi

  suffix="${dst##*.}"
  echo "$suffix" >> "$FAKE_ROOT/.pushes"
  count=$(grep -cx -- "$suffix" "$FAKE_ROOT/.pushes")

  if (( count <= $(find_corrupt_limit "$suffix") )); then
    write_corrupted_copy "$src" "$target"
  else
    cp -- "$src" "$target"
  fi
  echo "$src: 1 file pushed."
}

fake_pm_install() {
  local apk_remote=${1##* }
  if [[ "${FAKE_PM_FAIL:-0}" == 1 ]]; then
    echo "Failure [INSTALL_FAILED_INVALID_APK]"
    exit 1
  fi
  printf '%s\n' "$apk_remote" > "$FAKE_ROOT/installed.path"
  cp -- "$(map_path "$apk_remote")" "$FAKE_ROOT/installed.apk"
  echo "Success"
}

# Склейка кусков (cat … > файл) с дефектом: в конец файла дописан лишний байт.
fake_bad_assembly() {
  local command=$1
  bash -c "$(map_path "$command")"
  printf 'x' >> "$(map_path "${command##*> }")"
}

fake_shell() {
  local command=$1
  if [[ "$command" == "pm install"* ]]; then
    fake_pm_install "$command"
  elif [[ "${FAKE_BAD_ASSEMBLY:-0}" == 1 && "$command" == "cat "*"> "* ]]; then
    fake_bad_assembly "$command"
  else
    exec bash -c "$(map_path "$command")"
  fi
}

case "${1:-}" in
  wait-for-device) fake_wait_for_device ;;
  push)            fake_push "${2:?нет источника}" "${3:?нет назначения}" ;;
  shell)           shift; fake_shell "$*" ;;
  *)               echo "fake-adb: неизвестная команда: $*" >&2; exit 2 ;;
esac
