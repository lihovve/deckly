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

GOOD_LINE='PROBE_CHECK file=/storage/emulated/0/Deckly/spike/probe.db journal=truncate integrity=ok counter=10 entries=10 consistent=true'
SKEWED_LINE='PROBE_CHECK file=/storage/emulated/0/Deckly/spike/probe.db journal=truncate integrity=ok counter=9 entries=10 consistent=false'
CORRUPT_LINE='PROBE_CHECK file=/storage/emulated/0/Deckly/spike/probe.db journal=truncate integrity="*** in database main" counter=10 entries=10 consistent=true'

# --- заглушка adb --------------------------------------------------------------------------------

write_stub_adb() {
  cat > "$WORK/stub-adb.sh" <<'STUB'
#!/usr/bin/env bash
# Заглушка adb. Настройка: STUB_ROOT, STUB_NO_DEVICE=1, STUB_FAIL_FIRST_SERIES_START=1.
set -u
echo "$*" >> "$STUB_ROOT/calls"
case "$1" in
  wait-for-device)
    if [[ "${STUB_NO_DEVICE:-0}" == 1 ]]; then exec sleep 3600; fi
    ;;
  shell)
    case "$*" in
      *"am start"*"--es probe series"*)
        if [[ "${STUB_FAIL_FIRST_SERIES_START:-0}" == 1 && ! -e "$STUB_ROOT/failed-once" ]]; then
          touch "$STUB_ROOT/failed-once"
          echo "error: device offline"
          exit 1
        fi
        ;;
      *"am start"*"--es probe check"*)
        number=$(grep -c -- '--es probe check' "$STUB_ROOT/calls")
        line=$(sed -n "${number}p" "$STUB_ROOT/check-lines")
        [[ -n "$line" ]] || line=$(tail -n 1 "$STUB_ROOT/check-lines")
        printf '%s\n' "$line" > "$STUB_ROOT/pending"
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
has_call_count() { [[ "$(grep -c -- "$1" "$STUB_ROOT/calls")" -eq "$2" ]]; }

# Строки журнала вызовов $1, $2… встречаются в этом порядке (первое вхождение каждой).
has_calls_in_order() {
  local previous=0 pattern line_number
  for pattern in "$@"; do
    line_number=$(grep -n -m1 -- "$pattern" "$STUB_ROOT/calls" | cut -d: -f1)
    [[ -n "$line_number" && "$line_number" -gt "$previous" ]] || return 1
    previous=$line_number
  done
}

EXTRA_ENV=()

# --- тесты ---------------------------------------------------------------------------------------

test_all_runs_consistent() {
  prepare_case all_runs_consistent "$GOOD_LINE"
  run_killtest 3
  check_that "код 0" has_exit_code 0
  check_that "итог «consistent 3 из 3, integrity ok 3 из 3»" has_output_containing "consistent 3 из 3, integrity ok 3 из 3"
}

test_default_is_twenty_runs() {
  prepare_case default_is_twenty_runs "$GOOD_LINE"
  run_killtest
  check_that "код 0" has_exit_code 0
  check_that "итог «consistent 20 из 20, integrity ok 20 из 20»" has_output_containing "consistent 20 из 20, integrity ok 20 из 20"
}

test_calls_follow_the_plan_order() {
  prepare_case calls_follow_the_plan_order "$GOOD_LINE"
  run_killtest 1
  check_that "порядок: ждём планшет, серия, остановка, очистка журнала, проверка, чтение журнала" \
    has_calls_in_order 'wait-for-device' '--es probe series' 'force-stop app.deckly.spike' 'logcat -c' '--es probe check' 'logcat -d'
}

test_inconsistent_run_fails() {
  prepare_case inconsistent_run_fails "$GOOD_LINE" "$SKEWED_LINE" "$GOOD_LINE"
  run_killtest 3
  check_that "код 1" has_exit_code 1
  check_that "итог «consistent 2 из 3, integrity ok 3 из 3»" has_output_containing "consistent 2 из 3, integrity ok 3 из 3"
}

test_integrity_failure_fails() {
  prepare_case integrity_failure_fails "$GOOD_LINE" "$CORRUPT_LINE" "$GOOD_LINE"
  run_killtest 3
  check_that "код 1" has_exit_code 1
  check_that "итог «consistent 3 из 3, integrity ok 2 из 3»" has_output_containing "consistent 3 из 3, integrity ok 2 из 3"
}

test_missing_check_line_fails() {
  prepare_case missing_check_line_fails "$GOOD_LINE" "NONE" "$GOOD_LINE"
  EXTRA_ENV=(CHECK_TIMEOUT=1)
  run_killtest 3
  EXTRA_ENV=()
  check_that "код 1" has_exit_code 1
  check_that "сказано, что PROBE_CHECK не появился" has_output_containing "PROBE_CHECK не появился"
  check_that "итог «consistent 2 из 3, integrity ok 2 из 3»" has_output_containing "consistent 2 из 3, integrity ok 2 из 3"
}

test_denied_stops_early() {
  prepare_case denied_stops_early "PROBE_DENIED"
  run_killtest 5
  check_that "код 3" has_exit_code 3
  check_that "сказано про PROBE_DENIED" has_output_containing "PROBE_DENIED"
  check_that "остановились после первого запуска" has_call_count '--es probe series' 1
}

test_no_device_exits_with_two() {
  prepare_case no_device_exits_with_two "$GOOD_LINE"
  EXTRA_ENV=(STUB_NO_DEVICE=1 WAIT_TIMEOUT=1)
  run_killtest 3
  EXTRA_ENV=()
  check_that "код 2" has_exit_code 2
  check_that "сказано, что планшет не найден" has_output_containing "планшет не найден"
}

test_transient_adb_failure_is_retried() {
  prepare_case transient_adb_failure_is_retried "$GOOD_LINE"
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
  test_all_runs_consistent \
  test_default_is_twenty_runs \
  test_calls_follow_the_plan_order \
  test_inconsistent_run_fails \
  test_integrity_failure_fails \
  test_missing_check_line_fails \
  test_denied_stops_early \
  test_no_device_exits_with_two \
  test_transient_adb_failure_is_retried; do
  run_test "$test_name"
done

echo "$passed passed, $failed failed"
exit "$failed"
