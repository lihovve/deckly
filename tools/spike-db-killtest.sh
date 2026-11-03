#!/usr/bin/env bash
# Проверка базы прототипа после убийства приложения посреди серии записей.
#
# Сначала один раз снимается базовая линия: проверка (--es probe check) до цикла. Каждый раз потом:
# ждём планшет -> запускаем приложение с серией записей (--es probe series) -> через случайные
# 0,3-2,0 с останавливаем его (am force-stop) -> чистим logcat -> запускаем приложение с проверкой
# (--es probe check) -> ждём строку PROBE_CHECK в logcat (тег DecklySpike). База цела, если
# integrity=ok, и счётчик равен числу строк (consistent=true).
#
# Цело ещё не значит, что убийство попало в запись. Поэтому для каждого запуска считается
# приращение числа строк относительно предыдущей проверки (для первого запуска — относительно
# базовой линии) и запуск помечается: «до серии» (приращение 0, приложение убито раньше первой
# записи), «посреди серии» (0 < приращение < SERIES_SIZE), «после серии» (серия успела закончиться).
#
# Использование: tools/spike-db-killtest.sh [раз=20]
#
# Итог: «consistent K из N, integrity ok K из N, посреди серии M из N».
# Коды выхода:
#   0  consistent N из N, integrity N из N и посреди серии M >= ceil(N/2)
#   1  хотя бы один запуск не прошёл (база не сошлась, повреждена, строка не появилась, adb отказал)
#   2  планшет не найден за WAIT_TIMEOUT
#   3  PROBE_DENIED: приложению не выдан доступ ко всем файлам, проверять нечего
#   4  всё цело, но посреди серии меньше половины запусков: пауза перед остановкой не попадает
#      в серию, проверка ничего не доказывает; печатаются приращения, чтобы подвинуть паузу
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
#   SERIES_SIZE       сколько записей в серии приложения (2000, как в DbProbe.runSeries)
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
SERIES_SIZE="${SERIES_SIZE:-2000}"

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

# Чистит logcat, запускает проверку и ждёт PROBE_CHECK. 1 — не удалось запустить; иначе смотрите
# check_state и check_line (см. wait_for_probe_check).
start_probe_check() {
  run_adb_retrying logcat -c
  if ! run_adb_retrying shell am start -n "$ACTIVITY" --es probe check; then
    return 1
  fi
  wait_for_probe_check
}

# Метка приращения числа строк: до серии, посреди серии или после серии.
describe_increase() {
  local increase=$1
  if (( increase <= 0 )); then
    echo "до серии"
  elif (( increase < SERIES_SIZE )); then
    echo "посреди серии"
  else
    echo "после серии"
  fi
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

# Базовая линия: сколько строк в базе до цикла. Приложение после неё останавливаем, чтобы первый
# запуск серии был таким же холодным, как остальные.
if ! start_probe_check; then
  fail 1 "Ошибка: не удалось запустить базовую проверку"
fi
case "$check_state" in
  denied)
    fail 3 "базовая проверка: PROBE_DENIED. Выдайте доступ ко всем файлам: adb shell appops set --uid $PACKAGE MANAGE_EXTERNAL_STORAGE allow"
    ;;
  timeout)
    fail 1 "базовая проверка: PROBE_CHECK не появился за ${CHECK_TIMEOUT} с"
    ;;
esac
if ! [[ "$check_line" =~ entries=([0-9]+) ]]; then
  fail 1 "базовая проверка: в строке нет числа строк entries: $check_line"
fi
previous_entries="${BASH_REMATCH[1]}"
echo "базовая линия: $previous_entries строк"
if ! run_adb_retrying shell am force-stop "$PACKAGE"; then
  fail 1 "Ошибка: не удалось остановить приложение после базовой проверки"
fi

consistent_count=0
integrity_ok_count=0
middle_count=0
before_count=0
after_count=0
increases=()

for (( run = 1; run <= runs; run++ )); do
  if ! run_adb_retrying shell am start -n "$ACTIVITY" --es probe series; then
    echo "запуск $run/$runs: не удалось запустить серию записей"
    previous_entries=""
    increases+=("?")
    continue
  fi
  sleep_random_delay
  if ! run_adb_retrying shell am force-stop "$PACKAGE"; then
    echo "запуск $run/$runs: не удалось остановить приложение"
    previous_entries=""
    increases+=("?")
    continue
  fi
  if ! start_probe_check; then
    echo "запуск $run/$runs: не удалось запустить проверку"
    previous_entries=""
    increases+=("?")
    continue
  fi

  case "$check_state" in
    denied)
      fail 3 "запуск $run/$runs: PROBE_DENIED. Выдайте доступ ко всем файлам: adb shell appops set --uid $PACKAGE MANAGE_EXTERNAL_STORAGE allow"
      ;;
    timeout)
      echo "запуск $run/$runs: PROBE_CHECK не появился за ${CHECK_TIMEOUT} с"
      previous_entries=""
      increases+=("?")
      continue
      ;;
  esac

  consistent=""
  integrity=""
  entries=""
  [[ "$check_line" =~ consistent=([^[:space:]]+) ]] && consistent="${BASH_REMATCH[1]}"
  [[ "$check_line" =~ integrity=([^[:space:]]+) ]] && integrity="${BASH_REMATCH[1]}"
  [[ "$check_line" =~ entries=([0-9]+) ]] && entries="${BASH_REMATCH[1]}"

  # Приращение известно, только если известна и предыдущая проверка.
  increase_note="приращение неизвестно"
  if [[ -n "$entries" && -n "$previous_entries" ]]; then
    increase=$(( entries - previous_entries ))
    increase_label="$(describe_increase "$increase")"
    increase_note="приращение +${increase} (${increase_label})"
    increases+=("+${increase}")
    case "$increase_label" in
      "посреди серии") middle_count=$(( middle_count + 1 )) ;;
      "до серии") before_count=$(( before_count + 1 )) ;;
      "после серии") after_count=$(( after_count + 1 )) ;;
    esac
  else
    increases+=("?")
  fi
  previous_entries="$entries"
  echo "запуск $run/$runs: $(sed -E 's/^.*(PROBE_CHECK)/\1/' <<< "$check_line") — $increase_note"

  if [[ "$consistent" == true ]]; then
    consistent_count=$(( consistent_count + 1 ))
  fi
  if [[ "$integrity" == ok ]]; then
    integrity_ok_count=$(( integrity_ok_count + 1 ))
  fi
done

echo "consistent $consistent_count из $runs, integrity ok $integrity_ok_count из $runs, посреди серии $middle_count из $runs"
if (( consistent_count < runs || integrity_ok_count < runs )); then
  exit 1
fi

needed_in_series=$(( (runs + 1) / 2 ))
if (( middle_count < needed_in_series )); then
  echo "приращения строк по запускам: ${increases[*]}" >&2
  echo "Всё цело, но посреди серии только $middle_count из $runs (нужно не меньше $needed_in_series): убийство не попадает в запись, проверка ничего не доказывает." >&2
  if (( before_count > after_count )); then
    echo "Чаще «до серии» ($before_count): пауза перед остановкой короче, чем приложению нужно до первой записи. Увеличьте KILL_DELAY_MIN_MS и KILL_DELAY_MAX_MS." >&2
  else
    echo "Чаще «после серии» ($after_count): серия успевает закончиться. Уменьшите KILL_DELAY_MIN_MS и KILL_DELAY_MAX_MS." >&2
  fi
  exit 4
fi
exit 0
