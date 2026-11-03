#!/usr/bin/env bash
# Устанавливает APK на планшет по adb. Обычному `adb install` и `adb push` большого файла верить
# нельзя: соединение рвётся, файл доходит нужного размера, но с нулями внутри и без ошибки.
# Поэтому APK режется на куски, каждый кусок передаётся и сверяется по md5, на планшете куски
# склеиваются, md5 целого сверяется ещё раз, и только потом идёт `pm install`.
#
# Связь с планшетом рвётся по нескольку раз в минуту, поэтому каждый шаг по adb переживает обрыв:
# перед попыткой скрипт ждёт планшет (wait-for-device), каждая операция ограничена по времени
# (OP_TIMEOUT), а подготовка папки, передача куска, склейка и установка повторяются до MAX_TRIES
# раз. Отказ pm install («Failure …») не повторяется: это ответ системы, а не обрыв.
#
# Использование: tools/install-apk.sh <файл.apk>
#
# Коды выхода:
#   0  установлено
#   1  неверные аргументы, нет файла или нет adb
#   2  планшет не найден за WAIT_TIMEOUT или не отвечает
#   3  кусок не дошёл за MAX_TRIES попыток
#   4  md5 склеенного APK на планшете не совпал за MAX_TRIES попыток
#   5  pm install ответил Failure или не вернул Success за MAX_TRIES попыток
#
# Переменные окружения:
#   ADB           путь к adb; по умолчанию $ANDROID_HOME/platform-tools/adb,
#                 иначе ~/Android/Sdk/platform-tools/adb
#   CHUNK_SIZE    размер куска в байтах (1048576)
#   MAX_TRIES     попыток на каждый шаг (5)
#   WAIT_TIMEOUT  сколько секунд ждать планшет перед попыткой (60)
#   OP_TIMEOUT    сколько секунд может длиться одна операция adb push или adb shell (120);
#                 зависшая операция считается неудачной попыткой
#   RETRY_DELAY   пауза в секундах перед повтором (2)
set -euo pipefail

ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
CHUNK_SIZE="${CHUNK_SIZE:-1048576}"
MAX_TRIES="${MAX_TRIES:-5}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"
OP_TIMEOUT="${OP_TIMEOUT:-120}"
RETRY_DELAY="${RETRY_DELAY:-2}"

DEVICE_DIR=/data/local/tmp/deckly-install
CLEANUP_TIMEOUT=10
KILL_AFTER=5

work_dir=""
device_dir_exists=0

fail() {
  local code=$1
  shift
  echo "$*" >&2
  exit "$code"
}

# Одна операция adb push или adb shell, не дольше OP_TIMEOUT. Ввод закрыт: adb в фоновой группе
# процессов, которую создаёт timeout, не должен ждать терминал.
adb_op() {
  timeout --kill-after="$KILL_AFTER" "$OP_TIMEOUT" "$ADB" "$@" < /dev/null
}

remove_device_dir() {
  if (( device_dir_exists )); then
    device_dir_exists=0
    if ! timeout --kill-after="$KILL_AFTER" "$CLEANUP_TIMEOUT" "$ADB" shell "rm -rf $DEVICE_DIR" \
        < /dev/null > /dev/null 2>&1; then
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
  if ! timeout "$WAIT_TIMEOUT" "$ADB" wait-for-device < /dev/null; then
    fail 2 "Ошибка: планшет не найден за ${WAIT_TIMEOUT} с. Проверьте кабель, отладку по USB и что экран не заблокирован."
  fi
}

# Начало попытки номер $2 шага $1: при повторе сообщает об этом и выдерживает паузу; планшет
# дожидаемся перед каждой попыткой.
begin_attempt() {
  local what=$1 try=$2
  if (( try > 1 )); then
    echo "$what: попытка $try"
    sleep "$RETRY_DELAY"
  fi
  wait_for_device
}

# md5 файла на планшете (первые 32 символа вывода md5sum); пусто, если спросить не удалось.
read_device_md5() {
  local md5
  md5="$(adb_op shell "md5sum $1" 2> /dev/null | cut -c1-32)" || md5=""
  echo "$md5"
}

prepare_device_dir() {
  local try
  for (( try = 1; try <= MAX_TRIES; try++ )); do
    begin_attempt "подготовка папки" "$try"
    device_dir_exists=1
    if adb_op shell "rm -rf $DEVICE_DIR && mkdir -p $DEVICE_DIR" > /dev/null; then
      return 0
    fi
  done
  fail 2 "Ошибка: планшет не отвечает: не удалось создать $DEVICE_DIR за $MAX_TRIES попыток."
}

# Передаёт один кусок и возвращается, когда его md5 на планшете сошёлся с локальным.
push_chunk() {
  local chunk=$1 name suffix local_md5 device_md5 try
  name="$(basename "$chunk")"
  suffix="${name##*.}"
  local_md5="$(md5sum "$chunk" | cut -c1-32)"

  for (( try = 1; try <= MAX_TRIES; try++ )); do
    begin_attempt "кусок $suffix" "$try"
    adb_op push "$chunk" "$DEVICE_DIR/$name" > /dev/null || true
    device_md5="$(read_device_md5 "$DEVICE_DIR/$name")"
    if [[ "$device_md5" == "$local_md5" ]]; then
      echo "кусок $suffix: md5 совпал"
      return 0
    fi
  done

  fail 3 "Ошибка: кусок $suffix не дошёл за $MAX_TRIES попыток."
}

# Склеивает куски на планшете и сверяет md5 целого с md5 исходного APK ($1).
assemble_apk() {
  local expected_md5=$1 try device_md5=""
  for (( try = 1; try <= MAX_TRIES; try++ )); do
    begin_attempt "склейка" "$try"
    adb_op shell "cat $DEVICE_DIR/app.apk.part.* > $DEVICE_DIR/app.apk" || true
    device_md5="$(read_device_md5 "$DEVICE_DIR/app.apk")"
    if [[ "$device_md5" == "$expected_md5" ]]; then
      return 0
    fi
  done
  fail 4 "Ошибка: md5 склеенного APK на планшете не совпал за $MAX_TRIES попыток: ожидался $expected_md5, получен ${device_md5:-<пусто>}."
}

# pm install. «Failure» — ответ системы, сразу код 5. Ни «Success», ни «Failure» (обрыв
# транспорта, «error: device offline», пустой вывод, таймаут) — повтор.
install_apk() {
  local try install_output=""
  for (( try = 1; try <= MAX_TRIES; try++ )); do
    begin_attempt "установка" "$try"
    install_output="$(adb_op shell "pm install -r -t $DEVICE_DIR/app.apk" 2>&1)" || true
    if grep -q 'Failure' <<< "$install_output"; then
      fail 5 "Ошибка: pm install отказал:
$install_output"
    fi
    if grep -q '^Success' <<< "$install_output"; then
      return 0
    fi
  done
  fail 5 "Ошибка: pm install не вернул Success за $MAX_TRIES попыток. Последний вывод:
${install_output:-<пусто>}"
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

work_dir="$(mktemp -d)"
mkdir "$work_dir/chunks"
split -b "$CHUNK_SIZE" -d -a 3 "$apk" "$work_dir/chunks/app.apk.part."
chunks=("$work_dir"/chunks/app.apk.part.*)

# --- установка ---------------------------------------------------------------------------------

prepare_device_dir
for chunk in "${chunks[@]}"; do
  push_chunk "$chunk"
done
assemble_apk "$apk_md5"
install_apk

remove_device_dir
echo "Установлено: $apk, кусков ${#chunks[@]}, md5 $apk_md5"
