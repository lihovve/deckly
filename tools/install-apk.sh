#!/usr/bin/env bash
# Устанавливает APK на планшет по adb. Обычному `adb install` и `adb push` большого файла верить
# нельзя: соединение рвётся, файл доходит нужного размера, но с нулями внутри и без ошибки.
# Поэтому APK режется на куски, каждый кусок передаётся и сверяется по md5, на планшете куски
# склеиваются, md5 целого сверяется ещё раз, и только потом идёт `pm install`.
#
# Использование: tools/install-apk.sh <файл.apk>
#
# Коды выхода:
#   0  установлено
#   1  неверные аргументы, нет файла или нет adb
#   2  планшет не найден за WAIT_TIMEOUT или не отвечает
#   3  кусок не дошёл за MAX_TRIES попыток
#   4  md5 склеенного APK на планшете не совпал
#   5  pm install не вернул Success
#
# Переменные окружения:
#   ADB           путь к adb; по умолчанию $ANDROID_HOME/platform-tools/adb,
#                 иначе ~/Android/Sdk/platform-tools/adb
#   CHUNK_SIZE    размер куска в байтах (1048576)
#   MAX_TRIES     попыток передать один кусок (5)
#   WAIT_TIMEOUT  сколько секунд ждать планшет (60)
#   RETRY_DELAY   пауза в секундах перед повторной передачей куска (2)
set -euo pipefail

ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
CHUNK_SIZE="${CHUNK_SIZE:-1048576}"
MAX_TRIES="${MAX_TRIES:-5}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"
RETRY_DELAY="${RETRY_DELAY:-2}"

DEVICE_DIR=/data/local/tmp/deckly-install
CLEANUP_TIMEOUT=10

work_dir=""
device_dir_exists=0

fail() {
  local code=$1
  shift
  echo "$*" >&2
  exit "$code"
}

remove_device_dir() {
  if (( device_dir_exists )); then
    device_dir_exists=0
    if ! timeout "$CLEANUP_TIMEOUT" "$ADB" shell "rm -rf $DEVICE_DIR" > /dev/null 2>&1; then
      echo "Предупреждение: не удалось убрать $DEVICE_DIR на планшете" >&2
    fi
  fi
}

cleanup() {
  remove_device_dir
  if [[ -n "$work_dir" ]]; then
    rm -rf "$work_dir"
  fi
  return 0
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

wait_for_device() {
  if ! timeout "$WAIT_TIMEOUT" "$ADB" wait-for-device; then
    fail 2 "Ошибка: планшет не найден за ${WAIT_TIMEOUT} с. Проверьте кабель, отладку по USB и что экран не заблокирован."
  fi
}

# md5 файла на планшете (первые 32 символа вывода md5sum); пусто, если спросить не удалось.
read_device_md5() {
  local md5
  md5="$("$ADB" shell "md5sum $1" 2> /dev/null | cut -c1-32)" || md5=""
  echo "$md5"
}

# Передаёт один кусок и возвращается, когда его md5 на планшете сошёлся с локальным.
push_chunk() {
  local chunk=$1 name suffix local_md5 device_md5 try
  name="$(basename "$chunk")"
  suffix="${name##*.}"
  local_md5="$(md5sum "$chunk" | cut -c1-32)"

  for (( try = 1; try <= MAX_TRIES; try++ )); do
    if (( try > 1 )); then
      echo "кусок $suffix: попытка $try"
      sleep "$RETRY_DELAY"
    fi
    wait_for_device
    "$ADB" push "$chunk" "$DEVICE_DIR/$name" > /dev/null || true
    device_md5="$(read_device_md5 "$DEVICE_DIR/$name")"
    if [[ "$device_md5" == "$local_md5" ]]; then
      echo "кусок $suffix: md5 совпал"
      return 0
    fi
  done

  fail 3 "Ошибка: кусок $suffix не дошёл за $MAX_TRIES попыток."
}

# --- аргументы ---------------------------------------------------------------------------------

if (( $# != 1 )); then
  fail 1 "Использование: $0 <файл.apk>"
fi
apk="$1"
if [[ ! -f "$apk" ]]; then
  fail 1 "Ошибка: файл не найден: $apk"
fi
if [[ ! -s "$apk" ]]; then
  fail 1 "Ошибка: файл пуст: $apk"
fi
if [[ ! -x "$ADB" ]]; then
  fail 1 "Ошибка: adb не найден или не исполняемый: $ADB (задайте переменную ADB)"
fi

apk_md5="$(md5sum "$apk" | cut -c1-32)"

# --- подготовка --------------------------------------------------------------------------------

wait_for_device
device_dir_exists=1
if ! "$ADB" shell "rm -rf $DEVICE_DIR && mkdir -p $DEVICE_DIR" > /dev/null; then
  fail 2 "Ошибка: планшет не отвечает: не удалось создать $DEVICE_DIR"
fi

work_dir="$(mktemp -d)"
mkdir "$work_dir/chunks"
split -b "$CHUNK_SIZE" -d -a 3 "$apk" "$work_dir/chunks/app.apk.part."
chunks=("$work_dir"/chunks/app.apk.part.*)

# --- передача и сборка -------------------------------------------------------------------------

for chunk in "${chunks[@]}"; do
  push_chunk "$chunk"
done

"$ADB" shell "cat $DEVICE_DIR/app.apk.part.* > $DEVICE_DIR/app.apk" || true
assembled_md5="$(read_device_md5 "$DEVICE_DIR/app.apk")"
if [[ "$assembled_md5" != "$apk_md5" ]]; then
  fail 4 "Ошибка: md5 склеенного APK на планшете не совпал: ожидался $apk_md5, получен ${assembled_md5:-<пусто>}."
fi

# --- установка ---------------------------------------------------------------------------------

install_output="$("$ADB" shell "pm install -r -t $DEVICE_DIR/app.apk" 2>&1)" || true
if ! grep -q '^Success' <<< "$install_output"; then
  fail 5 "Ошибка: pm install не вернул Success:
$install_output"
fi

remove_device_dir
echo "Установлено: $apk, кусков ${#chunks[@]}, md5 $apk_md5"
