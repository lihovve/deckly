#!/usr/bin/env bash
# Тесты tools/install-apk.sh на подменном adb (tools/test/fake-adb.sh).
# Запуск: bash tools/test/install-apk-test.sh. Печатает «N passed, M failed», код выхода — M.
set -u

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_SCRIPT="$HERE/../install-apk.sh"
FAKE_ADB="$HERE/fake-adb.sh"
APK_BYTES=3670016   # 3,5 МБ: при куске в 1 МБ получается четыре куска, 000–003
RUN_TIMEOUT=60

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
failed=0
test_ok=1

# --- помощники ---------------------------------------------------------------------------------

# Свежий «планшет» и APK из случайных байт для каждого теста. PACKAGE — имя пакета для второго
# аргумента скрипта; пусто — скрипт запускается с одним аргументом.
prepare_case() {
  local name=$1
  PACKAGE=""
  CASE_DIR="$WORK/$name"
  FAKE_ROOT="$CASE_DIR/tablet"
  APK="$CASE_DIR/app.apk"
  mkdir -p "$FAKE_ROOT"
  head -c "$APK_BYTES" /dev/urandom > "$APK"
  APK_MD5="$(md5sum "$APK" | cut -c1-32)"
}

# run_install [ПЕРЕМЕННАЯ=значение ...] — запускает скрипт; результат в $exit_code и $output.
# Зависший скрипт убивается через RUN_TIMEOUT, чтобы поломка не вешала весь прогон (код 124).
run_install() {
  output="$(timeout "$RUN_TIMEOUT" env ADB="$FAKE_ADB" FAKE_ROOT="$FAKE_ROOT" RETRY_DELAY=0 "$@" \
    bash "$INSTALL_SCRIPT" "$APK" ${PACKAGE:+"$PACKAGE"} 2>&1)"
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
has_no_output_containing() { [[ "$output" != *"$1"* ]]; }
is_path_absent() { [[ ! -e "$1" ]]; }
# Каждая строка журнала подменного adb, подходящая под регулярное выражение $1, идёт сразу после
# wait-for-device; такие строки есть.
is_always_after_wait() {
  awk -v re="$1" '
    $0 ~ re { found = 1; if (previous != "wait-for-device") bad = 1 }
    { previous = $0 }
    END { exit (found && !bad) ? 0 : 1 }' "$FAKE_ROOT/.calls"
}
has_pm_called_once() { [[ "$(wc -l < "$FAKE_ROOT/.pm_calls" 2>/dev/null)" -eq 1 ]]; }
# В журнале подменного adb нет строки $1 (например shell:dumpsys).
has_no_call() { ! grep -qx -- "$1" "$FAKE_ROOT/.calls"; }
has_package_state() { [[ "$(cat "$FAKE_ROOT/package.enabled" 2>/dev/null)" == "$1" ]]; }
has_file_access_granted() { [[ "$(cat "$FAKE_ROOT/package.appops" 2>/dev/null)" == allow ]]; }
# Сколько раз скрипт вызвал pm enable ($1 = .enable_calls) или appops set ($1 = .appops_calls).
has_call_count() { [[ "$(wc -l < "$FAKE_ROOT/$1" 2>/dev/null || echo 0)" -eq "$2" ]]; }
has_matching_installed_apk() { [[ "$(md5sum "$FAKE_ROOT/installed.apk" 2>/dev/null | cut -c1-32)" == "$APK_MD5" ]]; }

DEVICE_DIR_REL="data/local/tmp/deckly-install"

# --- тесты -------------------------------------------------------------------------------------

test_clean_install() {
  prepare_case clean_install
  run_install
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" has_matching_installed_apk
  check_that "в выводе «Установлено»" has_output_containing "Установлено"
  check_that "шум adb push в вывод не попадает" has_no_output_containing "file pushed"
  check_that "папка на планшете убрана" is_path_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
  check_that "перед каждой передачей куска ждём планшет" is_always_after_wait '^push$'
  check_that "перед склейкой ждём планшет" is_always_after_wait '^shell:cat$'
  check_that "перед pm install ждём планшет" is_always_after_wait '^shell:pm$'
}

test_retries_corrupted_chunk() {
  prepare_case retries_corrupted_chunk
  run_install FAKE_CORRUPT_PUSHES=001:2
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" has_matching_installed_apk
  check_that "в выводе «кусок 001: попытка 3»" has_output_containing "кусок 001: попытка 3"
}

test_gives_up_after_max_tries() {
  prepare_case gives_up_after_max_tries
  run_install FAKE_CORRUPT_PUSHES=000:5 MAX_TRIES=5
  check_that "код 3" has_exit_code 3
  check_that "pm install не вызывался" is_path_absent "$FAKE_ROOT/installed.path"
  check_that "успех не объявлен" has_no_output_containing "Установлено"
  check_that "папка на планшете убрана и после ошибки" is_path_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_no_device_times_out() {
  prepare_case no_device_times_out
  local started=$SECONDS elapsed
  run_install FAKE_NO_DEVICE=1 WAIT_TIMEOUT=2
  elapsed=$(( SECONDS - started ))
  check_that "код 2" has_exit_code 2
  check_that "не позже чем за 5 с (заняло ${elapsed} с)" test "$elapsed" -le 5
  check_that "в выводе «планшет не найден»" has_output_containing "планшет не найден"
}

test_pm_failure_reported() {
  prepare_case pm_failure_reported
  run_install FAKE_PM_FAIL=1
  check_that "код 5" has_exit_code 5
  check_that "в выводе INSTALL_FAILED_INVALID_APK" has_output_containing "INSTALL_FAILED_INVALID_APK"
  check_that "отказ pm не повторяется: pm вызван один раз" has_pm_called_once
  check_that "успех не объявлен" has_no_output_containing "Установлено"
  check_that "папка на планшете убрана и после ошибки" is_path_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_pm_transport_error_retried() {
  prepare_case pm_transport_error_retried
  run_install FAKE_PM_FLAKY=1
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" has_matching_installed_apk
  check_that "в выводе «Установлено»" has_output_containing "Установлено"
}

test_hung_push_retried() {
  prepare_case hung_push_retried
  run_install FAKE_HANG_PUSH=002:1 OP_TIMEOUT=1
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" has_matching_installed_apk
  check_that "в выводе «кусок 002: попытка 2»" has_output_containing "кусок 002: попытка 2"
}

test_bad_assembly_reported() {
  prepare_case bad_assembly_reported
  run_install FAKE_BAD_ASSEMBLY=1
  check_that "код 4" has_exit_code 4
  check_that "склейка повторена до MAX_TRIES: «склейка: попытка 5»" has_output_containing "склейка: попытка 5"
  check_that "pm install не вызывался" is_path_absent "$FAKE_ROOT/installed.path"
  check_that "успех не объявлен" has_no_output_containing "Установлено"
  check_that "папка на планшете убрана и после ошибки" is_path_absent "$FAKE_ROOT/$DEVICE_DIR_REL"
}

test_push_error_shown() {
  prepare_case push_error_shown
  run_install FAKE_PUSH_FAIL=001:1
  check_that "код 0" has_exit_code 0
  check_that "md5 установленного APK равен md5 исходного" has_matching_installed_apk
  check_that "в выводе «кусок 001: попытка 2»" has_output_containing "кусок 001: попытка 2"
  check_that "текст ошибки adb показан" has_output_containing "remote Operation not permitted"
}

test_package_enabled_and_access_granted() {
  prepare_case package_enabled_and_access_granted
  PACKAGE=app.deckly.spike
  run_install
  check_that "код 0" has_exit_code 0
  check_that "в выводе «Пакет включён: app.deckly.spike»" has_output_containing "Пакет включён: app.deckly.spike"
  check_that "в выводе «Доступ ко всем файлам: allow»" has_output_containing "Доступ ко всем файлам: allow"
  check_that "в выводе по-прежнему «Установлено»" has_output_containing "Установлено"
  check_that "пакет включён (enabled=1)" has_package_state 1
  check_that "доступ ко всем файлам выдан" has_file_access_granted
  check_that "перед appops ждём планшет" is_always_after_wait '^shell:appops$'
  check_that "перед dumpsys ждём планшет" is_always_after_wait '^shell:dumpsys$'
}

test_default_enabled_state_accepted() {
  prepare_case default_enabled_state_accepted
  PACKAGE=app.deckly.spike
  run_install FAKE_ENABLED_AFTER_INSTALL=0 FAKE_ENABLE_REFUSED=1
  check_that "код 0: enabled=0 годится" has_exit_code 0
  check_that "в выводе «Пакет включён: app.deckly.spike»" has_output_containing "Пакет включён: app.deckly.spike"
  check_that "пакет остался enabled=0" has_package_state 0
  check_that "доступ ко всем файлам выдан" has_file_access_granted
}

test_without_package_skips_post_install() {
  prepare_case without_package_skips_post_install
  run_install
  check_that "код 0" has_exit_code 0
  check_that "pm enable не вызывался" is_path_absent "$FAKE_ROOT/.enable_calls"
  check_that "appops не вызывался" is_path_absent "$FAKE_ROOT/.appops_calls"
  check_that "dumpsys не вызывался" has_no_call "shell:dumpsys"
  check_that "строк про пакет в выводе нет" has_no_output_containing "Пакет включён"
  check_that "состояние пакета не тронуто (enabled=3)" has_package_state 3
}

test_enable_refused_reported() {
  prepare_case enable_refused_reported
  PACKAGE=app.deckly.spike
  run_install FAKE_ENABLE_REFUSED=1 MAX_TRIES=3
  check_that "код 6" has_exit_code 6
  check_that "pm enable повторён до MAX_TRIES (3 вызова)" has_call_count .enable_calls 3
  check_that "в выводе имя пакета" has_output_containing "app.deckly.spike"
  check_that "успех по пакету не объявлен" has_no_output_containing "Пакет включён:"
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
  test_pm_transport_error_retried \
  test_hung_push_retried \
  test_push_error_shown \
  test_package_enabled_and_access_granted \
  test_default_enabled_state_accepted \
  test_without_package_skips_post_install \
  test_enable_refused_reported \
  test_missing_apk; do
  run_test "$test_name"
done

echo "$passed passed, $failed failed"
exit "$failed"
