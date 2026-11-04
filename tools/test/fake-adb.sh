#!/usr/bin/env bash
# Подменный adb для tools/test/install-apk-test.sh. «Планшет» — папка $FAKE_ROOT:
# удалённый путь /data/local/tmp/... превращается в $FAKE_ROOT/data/local/tmp/...
#
# Понимает: wait-for-device, push <src> <dst>, shell "<команда>"; из команд — pm install,
# pm enable --user 0 <пакет>, dumpsys package <пакет>, appops set|get --uid <пакет> <операция>.
# Сбои, включаются переменными окружения:
#   FAKE_CORRUPT_PUSHES="001:2,003:1"  первые 2 передачи куска с суффиксом .001 (и первая .003)
#                                      пишут файл правильного размера, где после первых 4096 байт
#                                      одни нули, как при обрыве USB 20 октября. Кусок короче
#                                      4096 байт этим способом не портится. Счётчик передач
#                                      лежит в $FAKE_ROOT/.pushes.
#   FAKE_HANG_PUSH="002:1"             первая передача куска .002 пишет первые 4096 байт и зависает
#                                      (как push при оборванном USB); формат тот же.
#   FAKE_PUSH_FAIL="001:1"             первая передача куска .001 печатает ошибку adb и выходит
#                                      с 1, ничего не записав; формат тот же.
#   FAKE_NO_DEVICE=1                   wait-for-device висит.
#   FAKE_ENABLED_AFTER_INSTALL=N       состояние пакета (enabled=N у пользователя 0) после pm install;
#                                      по умолчанию 3, как у прошивки Boox 3 ноября. После pm enable
#                                      — 1. Доступ ко всем файлам после pm install сбрасывается.
#   FAKE_ENABLE_REFUSED=1              pm enable печатает «new state: enabled», но состояние не
#                                      меняет (прошивка снова отключила пакет).
#   FAKE_PM_FLAKY=1                    первый вызов pm install печатает «error: device offline» и
#                                      выходит с 1, дальше работает как обычно. Все вызовы pm
#                                      считаются в $FAKE_ROOT/.pm_calls.
#   FAKE_PM_FAIL=1                     pm install печатает Failure [INSTALL_FAILED_INVALID_APK].
#   FAKE_BAD_ASSEMBLY=1                склейка кусков (cat … > файл) дописывает в файл лишний байт.
#
# Каждый вызов дописывается в $FAKE_ROOT/.calls одной строкой: wait-for-device, push или
# shell:<первое слово команды>. По журналу тесты проверяют порядок операций. Отдельно: вызовы
# pm enable — в $FAKE_ROOT/.enable_calls, appops set — в $FAKE_ROOT/.appops_calls. Состояние
# пакета — в $FAKE_ROOT/package.enabled и package.appops («allow»).
set -euo pipefail

: "${FAKE_ROOT:?FAKE_ROOT не задан}"
REMOTE_PREFIX=/data/local/tmp
KEEP_BYTES=4096

map_path() {
  printf '%s' "${1//$REMOTE_PREFIX/$FAKE_ROOT$REMOTE_PREFIX}"
}

# Сколько первых передач куска с суффиксом $2 затрагивает настройка вида "001:2,003:1" ($1).
find_limit() {
  local spec=$1 suffix=$2 pair
  local -a pairs=()
  IFS=, read -ra pairs <<< "$spec"
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
  echo "wait-for-device" >> "$FAKE_ROOT/.calls"
  if [[ "${FAKE_NO_DEVICE:-0}" == 1 ]]; then
    exec sleep 3600
  fi
}

fake_push() {
  local src=$1 dst=$2 target suffix count
  echo "push" >> "$FAKE_ROOT/.calls"
  target=$(map_path "$dst")
  if [[ ! -d "$(dirname "$target")" ]]; then
    echo "adb: error: failed to copy '$src' to '$dst': remote couldn't create file: No such file or directory" >&2
    exit 1
  fi

  suffix="${dst##*.}"
  echo "$suffix" >> "$FAKE_ROOT/.pushes"
  count=$(grep -cx -- "$suffix" "$FAKE_ROOT/.pushes")

  if (( count <= $(find_limit "${FAKE_PUSH_FAIL:-}" "$suffix") )); then
    echo "adb: error: failed to copy '$src' to '$dst': remote Operation not permitted" >&2
    exit 1
  elif (( count <= $(find_limit "${FAKE_HANG_PUSH:-}" "$suffix") )); then
    head -c "$KEEP_BYTES" "$src" > "$target"
    exec sleep 3600
  elif (( count <= $(find_limit "${FAKE_CORRUPT_PUSHES:-}" "$suffix") )); then
    write_corrupted_copy "$src" "$target"
  else
    cp -- "$src" "$target"
  fi
  # Итог передачи настоящий adb пишет в stderr.
  echo "$src: 1 file pushed, 0 skipped. 100.0 MB/s (${#src} bytes in 0.010s)" >&2
}

fake_pm_install() {
  local apk_remote=${1##* } calls
  echo call >> "$FAKE_ROOT/.pm_calls"
  calls=$(wc -l < "$FAKE_ROOT/.pm_calls")
  if [[ "${FAKE_PM_FAIL:-0}" == 1 ]]; then
    echo "Failure [INSTALL_FAILED_INVALID_APK]"
    exit 1
  fi
  if [[ "${FAKE_PM_FLAKY:-0}" == 1 && "$calls" -eq 1 ]]; then
    echo "error: device offline"
    exit 1
  fi
  printf '%s\n' "$apk_remote" > "$FAKE_ROOT/installed.path"
  cp -- "$(map_path "$apk_remote")" "$FAKE_ROOT/installed.apk"
  echo "${FAKE_ENABLED_AFTER_INSTALL:-3}" > "$FAKE_ROOT/package.enabled"
  rm -f "$FAKE_ROOT/package.appops"
  echo "Success"
}

fake_pm_enable() {
  local package=${1##* }
  echo call >> "$FAKE_ROOT/.enable_calls"
  if [[ "${FAKE_ENABLE_REFUSED:-0}" != 1 ]]; then
    echo 1 > "$FAKE_ROOT/package.enabled"
  fi
  echo "Package $package new state: enabled"
}

# Как у настоящего dumpsys: строки пользователей, у пользователя 0 — enabled=N. Перед ним стоит
# чужой пользователь с enabled=2, чтобы разбор не брал первое попавшееся enabled=.
fake_dumpsys_package() {
  local package=${1##* }
  if [[ ! -f "$FAKE_ROOT/package.enabled" ]]; then
    echo "Unable to find package: $package"
    return
  fi
  echo "Packages:"
  echo "  Package [$package] (a1b2c3d):"
  echo "    userId=10123"
  echo "    User 10: ceDataInode=1 installed=true hidden=false suspended=false enabled=2 instant=false"
  echo "    User 0: ceDataInode=28807 deDataInode=23760 installed=true hidden=false suspended=false distractionFlags=0 stopped=false notLaunched=false enabled=$(cat "$FAKE_ROOT/package.enabled") instant=false virtual=false quarantined=false"
}

fake_appops() {
  local command=$1 mode=default
  if [[ "$command" == "appops set "* ]]; then
    echo call >> "$FAKE_ROOT/.appops_calls"
    echo allow > "$FAKE_ROOT/package.appops"
    return
  fi
  [[ -f "$FAKE_ROOT/package.appops" ]] && mode=allow
  echo "Uid mode: MANAGE_EXTERNAL_STORAGE: $mode"
  echo "MANAGE_EXTERNAL_STORAGE: default; time=+1d2h ago"
}

# Склейка кусков (cat … > файл) с дефектом: в конец файла дописан лишний байт.
fake_bad_assembly() {
  local command=$1
  bash -c "$(map_path "$command")"
  printf 'x' >> "$(map_path "${command##*> }")"
}

fake_shell() {
  local command=$1
  echo "shell:${command%% *}" >> "$FAKE_ROOT/.calls"
  if [[ "$command" == "pm install"* ]]; then
    fake_pm_install "$command"
  elif [[ "$command" == "pm enable "* ]]; then
    fake_pm_enable "$command"
  elif [[ "$command" == "dumpsys package "* ]]; then
    fake_dumpsys_package "$command"
  elif [[ "$command" == "appops "* ]]; then
    fake_appops "$command"
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
