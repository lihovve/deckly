#!/usr/bin/env bash
# Проверка базы прототипа после убийства приложения посреди серии записей.
#
# Каждый раз: ждём планшет -> запускаем приложение с серией записей (--es probe series) -> через
# случайные 0,3-2,0 с останавливаем его (am force-stop) -> чистим logcat -> запускаем приложение
# с проверкой (--es probe check) -> ждём строку PROBE_CHECK в logcat (тег DecklySpike). База цела,
# если integrity=ok, и счётчик равен числу строк (consistent=true).
#
# Использование: tools/spike-db-killtest.sh [раз=20]
#
# Итог: «consistent K из N, integrity ok K из N».
# Коды выхода:
#   0  N из N по обоим показателям
#   1  хотя бы один запуск не прошёл (база не сошлась, повреждена, строка не появилась, adb отказал)
#   2  планшет не найден за WAIT_TIMEOUT
#   3  PROBE_DENIED: приложению не выдан доступ ко всем файлам, проверять нечего
#
# Переменные окружения:
#   ADB               путь к adb; по умолчанию $ANDROID_HOME/platform-tools/adb,
#                     иначе ~/Android/Sdk/platform-tools/adb (как в install-apk.sh)
#   WAIT_TIMEOUT      сколько секунд ждать планшет перед командой (60)
#   OP_TIMEOUT        сколько секунд может длиться одна команда adb (60)
#   MAX_TRIES         попыток на одну управляющую команду adb (3)
#   RETRY_DELAY       пауза в секундах между попытками (2)
#   CHECK_TIMEOUT     сколько секунд ждать PROBE_CHECK после запуска проверки (15)
#   POLL_INTERVAL     как часто читать logcat в ожидании, секунды (0.5)
#   KILL_DELAY_MIN_MS, KILL_DELAY_MAX_MS  разброс паузы до остановки приложения (300, 2000)
set -uo pipefail

ADB="${ADB:-${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-60}"
OP_TIMEOUT="${OP_TIMEOUT:-60}"
MAX_TRIES="${MAX_TRIES:-3}"
RETRY_DELAY="${RETRY_DELAY:-2}"
CHECK_TIMEOUT="${CHECK_TIMEOUT:-15}"
POLL_INTERVAL="${POLL_INTERVAL:-0.5}"
KILL_DELAY_MIN_MS="${KILL_DELAY_MIN_MS:-300}"
KILL_DELAY_MAX_MS="${KILL_DELAY_MAX_MS:-2000}"

PACKAGE=app.deckly.spike
ACTIVITY="$PACKAGE/.MainActivity"
KILL_AFTER=5

fail() {
  local code=$1
  shift
  echo "$*" >&2
  exit "$code"
}

wait_for_device() {
  if ! timeout "$WAIT_TIMEOUT" "$ADB" wait-for-device < /dev/null; then
    fail 2 "Ошибка: планшет не найден за ${WAIT_TIMEOUT} с. Проверьте кабель, отладку по USB и что экран не заблокирован."
  fi
}

# Одна команда adb, не дольше OP_TIMEOUT; ввод закрыт, чтобы adb не ждал терминал.
run_adb() {
  timeout --kill-after="$KILL_AFTER" "$OP_TIMEOUT" "$ADB" "$@" < /dev/null
}

# Управляющая команда: перед каждой попыткой ждём планшет, до MAX_TRIES попыток. 1 — не удалось.
run_adb_retrying() {
  local try
  for (( try = 1; try <= MAX_TRIES; try++ )); do
    wait_for_device
    if run_adb "$@" > /dev/null 2>&1; then
      return 0
    fi
    sleep "$RETRY_DELAY"
  done
  return 1
}

sleep_random_delay() {
  local min=$KILL_DELAY_MIN_MS max=$KILL_DELAY_MAX_MS milliseconds
  if (( max < min )); then
    max=$min
  fi
  milliseconds=$(( min + RANDOM % (max - min + 1) ))
  sleep "$(printf '%d.%03d' $(( milliseconds / 1000 )) $(( milliseconds % 1000 )))"
}

# Ждёт PROBE_CHECK не дольше CHECK_TIMEOUT. Результат: check_state = found | denied | timeout,
# при found строка лежит в check_line.
wait_for_probe_check() {
  local deadline=$(( SECONDS + CHECK_TIMEOUT )) dump
  check_state=timeout
  check_line=""
  while true; do
    dump="$(run_adb logcat -d -v raw -s DecklySpike:I 2> /dev/null | tr -d '\r')" || dump=""
    if check_line="$(grep -m1 'PROBE_CHECK ' <<< "$dump")"; then
      check_state=found
      return
    fi
    check_line=""
    if grep -q 'PROBE_DENIED' <<< "$dump"; then
      check_state=denied
      return
    fi
    if (( SECONDS >= deadline )); then
      return
    fi
    sleep "$POLL_INTERVAL"
  done
}

# --- аргументы ---------------------------------------------------------------------------------

runs="${1:-20}"
if ! [[ "$runs" =~ ^[0-9]+$ ]] || (( runs < 1 )); then
  fail 1 "Использование: $0 [раз=20]"
fi
if [[ ! -x "$ADB" ]]; then
  fail 1 "Ошибка: adb не найден или не исполняемый: $ADB (задайте переменную ADB)"
fi

# --- прогон ------------------------------------------------------------------------------------

consistent_count=0
integrity_ok_count=0

for (( run = 1; run <= runs; run++ )); do
  if ! run_adb_retrying shell am start -n "$ACTIVITY" --es probe series; then
    echo "запуск $run/$runs: не удалось запустить серию записей"
    continue
  fi
  sleep_random_delay
  if ! run_adb_retrying shell am force-stop "$PACKAGE"; then
    echo "запуск $run/$runs: не удалось остановить приложение"
    continue
  fi
  run_adb_retrying logcat -c
  if ! run_adb_retrying shell am start -n "$ACTIVITY" --es probe check; then
    echo "запуск $run/$runs: не удалось запустить проверку"
    continue
  fi

  wait_for_probe_check
  case "$check_state" in
    denied)
      fail 3 "запуск $run/$runs: PROBE_DENIED. Выдайте доступ ко всем файлам: adb shell appops set --uid $PACKAGE MANAGE_EXTERNAL_STORAGE allow"
      ;;
    timeout)
      echo "запуск $run/$runs: PROBE_CHECK не появился за ${CHECK_TIMEOUT} с"
      continue
      ;;
  esac

  consistent=""
  integrity=""
  [[ "$check_line" =~ consistent=([^[:space:]]+) ]] && consistent="${BASH_REMATCH[1]}"
  [[ "$check_line" =~ integrity=([^[:space:]]+) ]] && integrity="${BASH_REMATCH[1]}"
  echo "запуск $run/$runs: $(sed -E 's/^.*(PROBE_CHECK)/\1/' <<< "$check_line")"

  if [[ "$consistent" == true ]]; then
    consistent_count=$(( consistent_count + 1 ))
  fi
  if [[ "$integrity" == ok ]]; then
    integrity_ok_count=$(( integrity_ok_count + 1 ))
  fi
done

echo "consistent $consistent_count из $runs, integrity ok $integrity_ok_count из $runs"
if (( consistent_count == runs && integrity_ok_count == runs )); then
  exit 0
fi
exit 1
