#!/usr/bin/env bash
# Тесты tools/spike-db-killtest.sh на заглушке adb. Заглушка помнит вызовы в $STUB_ROOT/calls и
# отвечает на `logcat -d` строкой PROBE_CHECK только после `am start … --es probe check`, как и
# настоящее приложение, и только если с тех пор не было `logcat -c`.
# Запуск: bash tools/test/spike-db-killtest-test.sh. Печатает «N passed, M failed», код выхода — M.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KILLTEST="$HERE/../spike-db-killtest.sh"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
failed=0
test_ok=1

DENIED_LINE='PROBE_DENIED'

# Строка PROBE_CHECK: make_check_line ЧИСЛО_СТРОК [СЧЁТЧИК=число строк] [INTEGRITY=ok].
make_check_line() {
  local entries=$1 counter=${2:-$1} integrity=${3:-ok} consistent=false
  if [[ "$counter" == "$entries" ]]; then consistent=true; fi
  printf 'PROBE_CHECK file=/storage/emulated/0/Deckly/spike/probe.db journal=truncate integrity=%s counter=%s entries=%s consistent=%s' \
    "$integrity" "$counter" "$entries" "$consistent"
}

# --- заглушка adb --------------------------------------------------------------------------------

write_stub_adb() {
  cat > "$WORK/stub-adb.sh" <<'STUB'
#!/usr/bin/env bash
# Заглушка adb. Настройка: STUB_ROOT, STUB_NO_DEVICE=1, STUB_FAIL_FIRST_SERIES_START=1.
# Как настоящий Android: am start при уже запущенном приложении не создаёт его заново, а отдаёт
# intent работающему экземпляру (проверка не выполняется); am force-stop приложение останавливает.
set -u
echo "$*" >> "$STUB_ROOT/calls"
is_app_running() { [[ -e "$STUB_ROOT/running" ]]; }
case "$1" in
  wait-for-device)
    if [[ "${STUB_NO_DEVICE:-0}" == 1 ]]; then exec sleep 3600; fi
    ;;
  shell)
    case "$*" in
      *"am force-stop"*)
        rm -f "$STUB_ROOT/running"
        ;;
      *"am start"*)
        if [[ "$*" == *"--es probe series"* && "${STUB_FAIL_FIRST_SERIES_START:-0}" == 1 && ! -e "$STUB_ROOT/failed-once" ]]; then
          touch "$STUB_ROOT/failed-once"
          echo "error: device offline"
          exit 1
        fi
        if is_app_running; then
          # Настоящий Android не создаёт приложение заново; серия или проверка при этом не выполняются.
          touch "$STUB_ROOT/ignored-start"
          echo "Warning: Activity not started, intent has been delivered to currently running top-most instance."
          exit 0
        fi
        touch "$STUB_ROOT/running"
        if [[ "$*" == *"--es probe check"* ]]; then
          number=$(grep -c -- '--es probe check' "$STUB_ROOT/calls")
          line=$(sed -n "${number}p" "$STUB_ROOT/check-lines")
          [[ -n "$line" ]] || line=$(tail -n 1 "$STUB_ROOT/check-lines")
          printf '%s\n' "$line" > "$STUB_ROOT/pending"
        fi
        ;;
    esac
    ;;
  logcat)
    case "${2:-}" in
      -c) rm -f "$STUB_ROOT/pending" ;;
      -d) [[ -s "$STUB_ROOT/pending" ]] && grep -v '^NONE$' "$STUB_ROOT/pending" ;;
    esac
    ;;
esac
exit 0
STUB
  chmod +x "$WORK/stub-adb.sh"
}

# --- помощники -----------------------------------------------------------------------------------

# Свежая папка заглушки; аргументы — строки, которые «приложение» напечатает в 1-м, 2-м… запуске.
prepare_case() {
  local name=$1
  shift
  STUB_ROOT="$WORK/$name"
  mkdir -p "$STUB_ROOT"
  : > "$STUB_ROOT/calls"
  touch "$STUB_ROOT/running"
  printf '%s\n' "$@" > "$STUB_ROOT/check-lines"
}

# run_killtest [аргументы скрипта] — окружение задаётся через env перед вызовом; результат в $exit_code, $output.
run_killtest() {
  output="$(timeout 60 env ADB="$WORK/stub-adb.sh" STUB_ROOT="$STUB_ROOT" \
    KILL_DELAY_MIN_MS=0 KILL_DELAY_MAX_MS=5 CHECK_TIMEOUT=2 POLL_INTERVAL=0.1 RETRY_DELAY=0 \
    "${EXTRA_ENV[@]}" bash "$KILLTEST" "$@" 2>&1)"
  exit_code=$?
}

check_that() {
  local description=$1
  shift
  if ! "$@"; then
    echo "    не выполнено: $description" >&2
    test_ok=0
  fi
}

has_exit_code() { [[ "$exit_code" -eq "$1" ]]; }
has_output_containing() { [[ "$output" == *"$1"* ]]; }
# Ни один am start не ушёл в уже запущенное приложение (там серия и проверка не выполняются).
has_no_ignored_start() { [[ ! -e "$STUB_ROOT/ignored-start" ]]; }
has_call_count() { [[ "$(grep -c -- "$1" "$STUB_ROOT/calls")" -eq "$2" ]]; }

# Вызовы $1, $2… есть в журнале в этом порядке: каждый ищется после предыдущего найденного.
has_calls_in_order() {
  local previous=0 pattern line_number
  for pattern in "$@"; do
    line_number=$(grep -n -- "$pattern" "$STUB_ROOT/calls" | cut -d: -f1 | awk -v after="$previous" '$1 > after { print; exit }')
    [[ -n "$line_number" ]] || return 1
    previous=$line_number
  done
}

EXTRA_ENV=()

# --- тесты ---------------------------------------------------------------------------------------
# Первая строка каждого случая — базовая проверка до цикла, дальше по строке на запуск.

test_all_runs_in_series_pass() {
  prepare_case all_runs_in_series_pass "$(make_check_line 0)" "$(make_check_line 700)" "$(make_check_line 1400)" "$(make_check_line 2100)"
  run_killtest 3
  check_that "код 0" has_exit_code 0
  check_that "итог «consistent 3 из 3, integrity ok 3 из 3, посреди серии 3 из 3»" \
    has_output_containing "consistent 3 из 3, integrity ok 3 из 3, посреди серии 3 из 3"
  check_that "каждый запуск серии и проверки начат с остановленного приложения" has_no_ignored_start
}

test_default_is_twenty_runs() {
  local lines=() index
  for index in $(seq 0 20); do
    lines+=("$(make_check_line $(( index * 500 )))")
  done
  prepare_case default_is_twenty_runs "${lines[@]}"
  run_killtest
  check_that "код 0" has_exit_code 0
  check_that "итог про 20 из 20" \
    has_output_containing "consistent 20 из 20, integrity ok 20 из 20, посреди серии 20 из 20"
}

test_calls_follow_the_plan_order() {
  prepare_case calls_follow_the_plan_order "$(make_check_line 0)" "$(make_check_line 700)"
  run_killtest 1
  check_that "порядок: остановка, базовая проверка, остановка, серия, остановка, очистка журнала, проверка, чтение журнала" \
    has_calls_in_order 'wait-for-device' 'force-stop app.deckly.spike' 'logcat -c' '--es probe check' 'logcat -d' \
    'force-stop app.deckly.spike' '--es probe series' 'force-stop app.deckly.spike' 'logcat -c' '--es probe check' 'logcat -d'
}

test_classifies_increase_per_run() {
  # Приращения: 1999 (посреди серии), 2000 (после серии), 0 (до серии).
  prepare_case classifies_increase_per_run "$(make_check_line 0)" "$(make_check_line 1999)" "$(make_check_line 3999)" "$(make_check_line 3999)"
  run_killtest 3
  check_that "код 4: посреди серии только 1 из 3" has_exit_code 4
  check_that "+1999 — посреди серии" has_output_containing "+1999 (посреди серии)"
  check_that "+2000 — после серии" has_output_containing "+2000 (после серии)"
  check_that "+0 — до серии" has_output_containing "+0 (до серии)"
  check_that "итог «посреди серии 1 из 3»" has_output_containing "посреди серии 1 из 3"
}

test_half_of_runs_in_series_is_enough() {
  # Из четырёх запусков посреди серии два: ⌈4/2⌉ = 2.
  prepare_case half_of_runs_in_series_is_enough "$(make_check_line 0)" "$(make_check_line 100)" "$(make_check_line 100)" \
    "$(make_check_line 2100)" "$(make_check_line 2200)"
  run_killtest 4
  check_that "код 0" has_exit_code 0
  check_that "итог «посреди серии 2 из 4»" has_output_containing "посреди серии 2 из 4"
}

test_pause_outside_series_exits_with_four() {
  prepare_case pause_outside_series_exits_with_four "$(make_check_line 5)"
  run_killtest 3
  check_that "код 4" has_exit_code 4
  check_that "итог «consistent 3 из 3, integrity ok 3 из 3, посреди серии 0 из 3»" \
    has_output_containing "consistent 3 из 3, integrity ok 3 из 3, посреди серии 0 из 3"
  check_that "напечатаны фактические приращения" has_output_containing "+0 +0 +0"
  check_that "подсказка про паузу перед остановкой" has_output_containing "KILL_DELAY_MIN_MS"
}

test_inconsistent_run_fails() {
  prepare_case inconsistent_run_fails "$(make_check_line 0)" "$(make_check_line 700)" "$(make_check_line 1400 1399)" "$(make_check_line 2100)"
  run_killtest 3
  check_that "код 1" has_exit_code 1
  check_that "итог «consistent 2 из 3, integrity ok 3 из 3»" has_output_containing "consistent 2 из 3, integrity ok 3 из 3"
}

test_integrity_failure_fails() {
  prepare_case integrity_failure_fails "$(make_check_line 0)" "$(make_check_line 700)" \
    "$(make_check_line 1400 1400 '"*** in database main"')" "$(make_check_line 2100)"
  run_killtest 3
  check_that "код 1" has_exit_code 1
  check_that "итог «consistent 3 из 3, integrity ok 2 из 3»" has_output_containing "consistent 3 из 3, integrity ok 2 из 3"
}

test_missing_check_line_fails() {
  prepare_case missing_check_line_fails "$(make_check_line 0)" "$(make_check_line 700)" "NONE" "$(make_check_line 2100)"
  EXTRA_ENV=(CHECK_TIMEOUT=1)
  run_killtest 3
  EXTRA_ENV=()
  check_that "код 1" has_exit_code 1
  check_that "сказано, что PROBE_CHECK не появился" has_output_containing "PROBE_CHECK не появился"
  check_that "итог «consistent 2 из 3, integrity ok 2 из 3»" has_output_containing "consistent 2 из 3, integrity ok 2 из 3"
}

test_denied_stops_early() {
  prepare_case denied_stops_early "$(make_check_line 0)" "$DENIED_LINE"
  run_killtest 5
  check_that "код 3" has_exit_code 3
  check_that "сказано про PROBE_DENIED" has_output_containing "PROBE_DENIED"
  check_that "остановились после первого запуска" has_call_count '--es probe series' 1
}

test_denied_at_baseline_stops_before_series() {
  prepare_case denied_at_baseline_stops_before_series "$DENIED_LINE"
  run_killtest 5
  check_that "код 3" has_exit_code 3
  check_that "сказано про PROBE_DENIED" has_output_containing "PROBE_DENIED"
  check_that "серия не запускалась" has_call_count '--es probe series' 0
}

test_no_device_exits_with_two() {
  prepare_case no_device_exits_with_two "$(make_check_line 0)"
  EXTRA_ENV=(STUB_NO_DEVICE=1 WAIT_TIMEOUT=1)
  run_killtest 3
  EXTRA_ENV=()
  check_that "код 2" has_exit_code 2
  check_that "сказано, что планшет не найден" has_output_containing "планшет не найден"
}

test_transient_adb_failure_is_retried() {
  prepare_case transient_adb_failure_is_retried "$(make_check_line 0)" "$(make_check_line 700)"
  EXTRA_ENV=(STUB_FAIL_FIRST_SERIES_START=1)
  run_killtest 1
  EXTRA_ENV=()
  check_that "код 0" has_exit_code 0
  check_that "запуск серии повторён: два вызова" has_call_count '--es probe series' 2
}

# --- запуск --------------------------------------------------------------------------------------

run_test() {
  local name=$1
  test_ok=1
  "$name"
  if (( test_ok )); then
    echo "ok    $name"
    passed=$(( passed + 1 ))
  else
    echo "FAIL  $name"
    echo "$output" | sed 's/^/      | /' >&2
    failed=$(( failed + 1 ))
  fi
}

write_stub_adb

for test_name in \
  test_all_runs_in_series_pass \
  test_default_is_twenty_runs \
  test_calls_follow_the_plan_order \
  test_classifies_increase_per_run \
  test_half_of_runs_in_series_is_enough \
  test_pause_outside_series_exits_with_four \
  test_inconsistent_run_fails \
  test_integrity_failure_fails \
  test_missing_check_line_fails \
  test_denied_stops_early \
  test_denied_at_baseline_stops_before_series \
  test_no_device_exits_with_two \
  test_transient_adb_failure_is_retried; do
  run_test "$test_name"
done

echo "$passed passed, $failed failed"
exit "$failed"
