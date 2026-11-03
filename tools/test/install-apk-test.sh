#!/usr/bin/env bash
# Тесты tools/install-apk.sh на подменном adb (tools/test/fake-adb.sh).
# Запуск: bash tools/test/install-apk-test.sh. Печатает «N passed, M failed», код выхода — M.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_SCRIPT="$HERE/../install-apk.sh"
FAKE_ADB="$HERE/fake-adb.sh"
APK_BYTES=3670016   # 3,5 МБ: при куске в 1 МБ получается четыре куска, 000–003

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
failed=0
test_ok=1

# --- помощники ---------------------------------------------------------------------------------

# Свежий «планшет» и APK из случайных байт для каждого теста.
prepare_case() {
  local name=$1
  CASE_DIR="$WORK/$name"
  FAKE_ROOT="$CASE_DIR/tablet"
  APK="$CASE_DIR/app.apk"
  mkdir -p "$FAKE_ROOT"
  head -c "$APK_BYTES" /dev/urandom > "$APK"
  APK_MD5="$(md5sum "$APK" | cut -c1-32)"
}

# run_install [ПЕРЕМЕННАЯ=значение ...] — запускает скрипт; результат в $exit_code и $output.
run_install() {
  output="$(env ADB="$FAKE_ADB" FAKE_ROOT="$FAKE_ROOT" RETRY_DELAY=0 "$@" \
    bash "$INSTALL_SCRIPT" "$APK" 2>&1)"
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
output_contains() { [[ "$output" == *"$1"* ]]; }
output_lacks() { [[ "$output" != *"$1"* ]]; }
path_is_absent() { [[ ! -e "$1" ]]; }
installed_apk_matches() { [[ "$(md5sum "$FAKE_ROOT/installed.apk" 2>/dev/null | cut -c1-32)" == "$APK_MD5" ]]; }

DEVICE_DIR_REL="data/local/tmp/deckly-install"

# --- тесты -------------------------------------------------------------------------------------

test_clean_install() {
  prepare_case clean_install
  run_install
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" installed_apk_matches
  check_that "в выводе «Установлено»" output_contains "Установлено"
  check_that "папка на планшете убрана" path_is_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_retries_corrupted_chunk() {
  prepare_case retries_corrupted_chunk
  run_install FAKE_CORRUPT_PUSHES=001:2
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" installed_apk_matches
  check_that "в выводе «кусок 001: попытка 3»" output_contains "кусок 001: попытка 3"
}

test_gives_up_after_max_tries() {
  prepare_case gives_up_after_max_tries
  run_install FAKE_CORRUPT_PUSHES=000:5 MAX_TRIES=5
  check_that "код 3" has_exit_code 3
  check_that "pm install не вызывался" path_is_absent "$FAKE_ROOT/installed.path"
  check_that "успех не объявлен" output_lacks "Установлено"
  check_that "папка на планшете убрана и после ошибки" path_is_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_no_device_times_out() {
  prepare_case no_device_times_out
  local started=$SECONDS elapsed
  run_install FAKE_NO_DEVICE=1 WAIT_TIMEOUT=2
  elapsed=$(( SECONDS - started ))
  check_that "код 2" has_exit_code 2
  check_that "не позже чем за 5 с (заняло ${elapsed} с)" test "$elapsed" -le 5
  check_that "в выводе «планшет не найден»" output_contains "планшет не найден"
}

test_pm_failure_reported() {
  prepare_case pm_failure_reported
  run_install FAKE_PM_FAIL=1
  check_that "код 5" has_exit_code 5
  check_that "в выводе INSTALL_FAILED_INVALID_APK" output_contains "INSTALL_FAILED_INVALID_APK"
  check_that "успех не объявлен" output_lacks "Установлено"
  check_that "папка на планшете убрана и после ошибки" path_is_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_bad_assembly_reported() {
  prepare_case bad_assembly_reported
  run_install FAKE_BAD_ASSEMBLY=1
  check_that "код 4" has_exit_code 4
  check_that "pm install не вызывался" path_is_absent "$FAKE_ROOT/installed.path"
  check_that "успех не объявлен" output_lacks "Установлено"
  check_that "папка на планшете убрана и после ошибки" path_is_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_missing_apk() {
  prepare_case missing_apk
  APK="$CASE_DIR/нет-такого.apk"
  run_install
  check_that "код 1" has_exit_code 1
}

# --- запуск ------------------------------------------------------------------------------------

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

for test_name in \
  test_clean_install \
  test_retries_corrupted_chunk \
  test_gives_up_after_max_tries \
  test_no_device_times_out \
  test_pm_failure_reported \
  test_bad_assembly_reported \
  test_missing_apk; do
  run_test "$test_name"
done

echo "$passed passed, $failed failed"
exit "$failed"
