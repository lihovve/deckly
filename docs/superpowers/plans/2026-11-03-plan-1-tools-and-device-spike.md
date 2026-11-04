# План 1: инструменты и прототип на планшете

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** поставить инструменты сборки и надёжную установку APK на Boox, затем одноразовым прототипом
на самом Go 10.3 Gen II ответить на вопросы архитектуры: какой путь пера основной, может ли
база жить в `/sdcard/Deckly/` и годится ли перьевая ручка с толщиной от нажима (задача 6).

**Architecture:** корневая Gradle-сборка создаётся здесь и переиспользуется планом 2 (каталог
версий, репозитории, обёртка). В ней один модуль `:spike` — одноразовое приложение на Compose с
холстом через `AndroidView`, как в будущем `ink`. Скрипты в `tools/` на bash; скрипт установки
проверяется тестом на подменном `adb`.

**Tech Stack:** AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21 (встроенный в AGP), KSP 2.3.13, Compose BOM
2026.09.00, Room 3.0.3, Onyx SDK (pen 1.5.8, device 1.3.6, base 1.8.9), bash.

**Spec:** [`docs/superpowers/specs/2026-11-03-architecture-design.md`](../specs/2026-11-03-architecture-design.md),
разделы «Порядок работ» (п. 1), «Установка на планшет», «Хранение и резервная копия»; раздел
«Onyx SDK» в ТЗ.

Это первый из четырёх планов: 1 — инструменты и прототип; 2 — каркас и `domain`; 3 — `data`;
4 — `ink`, `ui-contract`, `app`, `design-e`. Итоги этого плана меняют планы 3 и 4.

## Global Constraints

- `compileSdk = 37`, `targetSdk = 37`, `minSdk = 35`; `ndk { abiFilters += "arm64-v8a" }`.
- Gradle запускается на системном JDK 25; `compileOptions` source/target — `JavaVersion.VERSION_21`.
- Версии только из `gradle/libs.versions.toml` (задача 1), последние стабильные на 2026-11-03. Если
  AGP 9.5.0 + Gradle 9.8.2 не собирают пустой проект — откат на AGP 9.4.1 и Gradle 9.7.1, запись
  причины в коммит.
- Kotlin встроен в AGP 9: плагин `org.jetbrains.kotlin.android` **не** применяется. Версию KGP 2.4.21
  поднимает `buildscript { dependencies { classpath(...) } }` в корне.
- Репозитории: `google()`, `mavenCentral()`, `http://repo.boox.com/repository/maven-public/` с
  `isAllowInsecureProtocol = true`. jitpack не подключается: ни одна зависимость из него не
  берётся (расхождение с ТЗ, см. ниже).
- Разрешения: `INTERNET` нет. Разрешения, которые приносят манифесты Onyx SDK
  (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `BLUETOOTH`, `DUMP`,
  `WAKE_LOCK`, `FOREGROUND_SERVICE`, `SYSTEM_ALERT_WINDOW`, `READ_EXTERNAL_STORAGE`,
  `WRITE_EXTERNAL_STORAGE`), прототип вырезает (задача 3), а прогон на планшете показывает, работает
  ли SDK без них (задача 5). Итог решает, что вырезать в настоящем приложении.
- `adb` — только из `~/Android/Sdk/platform-tools` (37.0.1). Debian-овский `/usr/bin/adb` 34.0.5
  перед работой останавливается (`/usr/bin/adb kill-server`): два сервера adb мешают друг другу.
- Ни одному `adb install` / `adb push` большого файла не верить: только `tools/install-apk.sh`.
- `applicationId` прототипа — `app.deckly.spike`, тег logcat — `DecklySpike`.
- Прототип одноразовый: на его Kotlin-код unit-тестов нет, его тест — прогон на планшете
  (задача 5). В основной код он не переносится; модуль удаляется, когда план 4 даст `ink`.
- Сообщения коммитов — по-русски, как в истории репозитория, с последней строкой
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Расхождения с ТЗ (раздел «Onyx SDK»)

Найдены по коду Notable (`main` @ `dcd4ac9`, 2026-11-01) и байткоду SDK 1.5.8. Прототип делает так,
как работает SDK, а не как написано в ТЗ. ТЗ правится в задаче 5, после подтверждения на
планшете и с согласия владельца.

1. jitpack не нужен (Notable убрал его в PR #373).
2. Нужен `packaging { jniLibs { pickFirsts += "**/libc++_shared.so" } }`: `libc++_shared.so` есть и
   в pen, и в pennative.
3. Кроме `ResManager.init(this)` нужны `HiddenApiBypass.setHiddenApiExemptions("")` и
   `RxManager.Builder.initAppContext(this)`, RxJava 2.2.21 / RxAndroid 2.1.1 и исключения
   support-compat, appcompat-v7, mmkv.
4. Стиль пера задаётся **после** `openRawDrawing()` (open и close сбрасывают стиль в PENCIL), а
   трек ластика включается заново после **каждого** `setRawDrawingEnabled(true)`: этот вызов его
   выключает.
5. Падают сухие Neo-кисти (`NeoCharcoalPenV2` и др.), а не живые стили прошивки. Базовый
   `STROKE_STYLE_PENCIL` (= 0) остаётся.
6. Новое, в ТЗ нет: `TouchHelper.create(view, cb)` выбирает быстрый рендер прошивки, только если имя
   устройства ввода содержит `onyx_emp`, `Wacom I2C Digitizer` или `hanvon_tp`; иначе молча берёт
   медленный `AppTouchRender`. Имя активного пера InkSense Plus неизвестно. Прототип проверяет оба
   вызова: обычный и `TouchHelper.create(view, TouchHelper.FEATURE_SF_TOUCH_RENDER, cb)`.
7. Новое: на Go 10.3 (`Go103_2Lumi`, прошивка 4.2) ластик кнопкой пера оставляет серый след; в
   незакрытом Notable PR #346 его снимают обновлением `UpdateMode.GC` по грязному прямоугольнику
   через 200 мс после стирания. Прототип проверяет.

## Review Focus

1. Обрыв USB посреди установки: скрипт не должен сообщать успех при недошедшем или испорченном
   куске и должен выходить с ненулевым кодом после исчерпания попыток (тесты задачи 2).
2. Планшет спит или не подключён: скрипт не висит вечно, а через `WAIT_TIMEOUT` выходит с понятным
   сообщением (тест задачи 2).
3. Приложение убито посреди серии записей: при следующем запуске база цела и счётчик сходится с
   числом строк (скрипт `spike-db-killtest.sh`, задача 4).
4. Доступ ко всем файлам не выдан: прототип не падает, показывает это в статусе и не трогает базу
   (задача 4, шаг проверки на планшете — задача 5).
5. SDK Onyx не поднялся: прототип не падает, показывает ошибку и рисует запасным Canvas (задача 3;
   проверка — переключатель «Canvas» в задаче 5).

---

### Task 1: SDK, обёртка Gradle и пустой модуль прототипа

**Files:**
- Create: `tools/setup-android-sdk.sh`
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create (генерирует Gradle): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
  `gradle/wrapper/gradle-wrapper.properties`
- Create: `spike/build.gradle.kts`, `spike/src/main/AndroidManifest.xml`,
  `spike/src/main/kotlin/app/deckly/spike/SpikeApplication.kt`,
  `spike/src/main/kotlin/app/deckly/spike/MainActivity.kt`
- Modify: `.gitignore` (сейчас там только `.remember/`)

**Interfaces:**
- Produces: `./gradlew :spike:assembleDebug` → `spike/build/outputs/apk/debug/spike-debug.apk`;
  `~/Android/Sdk` с `platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`;
  каталог версий с алиасами ниже — их используют задачи 3–4 и план 2.

- [x] **Step 1: Написать `tools/setup-android-sdk.sh`**

  Идемпотентный: пропускает то, что уже стоит. Делает:
  1. Скачивает `https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip`
     во временную папку, проверяет SHA-1 `e025545c62a8e64c7559119566a569fb1dec5f60`, при
     несовпадении выходит с кодом 1.
  2. Распаковывает в `~/Android/Sdk/cmdline-tools/latest/` (внутри `bin/`, `lib/`, `source.properties`).
  3. `yes | sdkmanager --sdk_root=$HOME/Android/Sdk --licenses`.
  4. `sdkmanager --sdk_root=$HOME/Android/Sdk "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"`.
  5. Пишет `local.properties` в корень репозитория: `sdk.dir=/home/<user>/Android/Sdk`.
  6. Печатает строку для `~/.bashrc`: `export ANDROID_HOME=$HOME/Android/Sdk`.

- [x] **Step 2: Запустить и проверить**

  Run: `bash tools/setup-android-sdk.sh && ~/Android/Sdk/platform-tools/adb version`
  Expected: `Version 37.0.1-…`; `ls ~/Android/Sdk/platforms` показывает `android-37.0`.

- [x] **Step 3: Создать обёртку Gradle**

  Создать `settings.gradle.kts` из одной строки `rootProject.name = "deckly"` (Gradle 9 без файла
  настроек не работает). Скачать `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip`
  во временную папку, проверить SHA-256
  `21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`, распаковать и в корне
  репозитория выполнить:
  `<dist>/bin/gradle wrapper --gradle-version 9.8.2 --distribution-type bin --gradle-distribution-sha256-sum 21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607`

  Run: `sha256sum gradle/wrapper/gradle-wrapper.jar && ./gradlew --version`
  Expected: `3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`; `Gradle 9.8.2`.

- [x] **Step 4: Каталог версий `gradle/libs.versions.toml`**

  ```toml
  [versions]
  agp = "9.5.0"
  kotlin = "2.4.21"
  ksp = "2.3.13"
  composeBom = "2026.09.00"
  activityCompose = "1.13.0"
  lifecycle = "2.11.0"
  coreKtx = "1.19.1"
  room3 = "3.0.3"
  coroutines = "1.11.0"
  onyxPen = "1.5.8"
  onyxDevice = "1.3.6"
  onyxBase = "1.8.9"
  hiddenApiBypass = "6.2"
  rxjava = "2.2.21"
  rxandroid = "2.1.1"

  [libraries]
  kotlin-gradle-plugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
  compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
  compose-foundation = { module = "androidx.compose.foundation:foundation" }
  compose-ui = { module = "androidx.compose.ui:ui" }
  activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
  lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
  core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
  room3-runtime = { module = "androidx.room3:room3-runtime", version.ref = "room3" }
  room3-compiler = { module = "androidx.room3:room3-compiler", version.ref = "room3" }
  coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
  onyx-pen = { module = "com.onyx.android.sdk:onyxsdk-pen", version.ref = "onyxPen" }
  onyx-device = { module = "com.onyx.android.sdk:onyxsdk-device", version.ref = "onyxDevice" }
  onyx-base = { module = "com.onyx.android.sdk:onyxsdk-base", version.ref = "onyxBase" }
  hiddenapibypass = { module = "org.lsposed.hiddenapibypass:hiddenapibypass", version.ref = "hiddenApiBypass" }
  rxjava2 = { module = "io.reactivex.rxjava2:rxjava", version.ref = "rxjava" }
  rxandroid2 = { module = "io.reactivex.rxjava2:rxandroid", version.ref = "rxandroid" }

  [plugins]
  android-application = { id = "com.android.application", version.ref = "agp" }
  android-library = { id = "com.android.library", version.ref = "agp" }
  kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
  kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
  ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
  ```

- [x] **Step 5: Корневая сборка**

  - `settings.gradle.kts`: `pluginManagement` (google, mavenCentral, gradlePluginPortal);
    `dependencyResolutionManagement` с `RepositoriesMode.FAIL_ON_PROJECT_REPOS` и репозиториями из
    Global Constraints; `include(":spike")`.
  - `build.gradle.kts`: `buildscript { dependencies { classpath(libs.kotlin.gradle.plugin) } }` и все
    плагины каталога с `apply false`.
  - `gradle.properties`: `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`,
    `android.useAndroidX=true`, `android.nonTransitiveRClass=true`.
  - `.gitignore`: добавить `local.properties`, `.gradle/`, `.kotlin/`, `build/`.

- [x] **Step 6: Модуль `:spike`**

  `spike/build.gradle.kts`: плагины `android-application`, `kotlin-compose`, `ksp`;
  `namespace = applicationId = "app.deckly.spike"`, версии SDK и ABI из Global Constraints,
  `buildFeatures { compose = true }`, `compileOptions` на Java 21 и:

  ```kotlin
  packaging {
      resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
      jniLibs { pickFirsts += "**/libc++_shared.so" }
  }
  dependencies {
      implementation(libs.onyx.device) { exclude(group = "com.android.support", module = "support-compat") }
      implementation(libs.onyx.pen) {
          exclude(group = "com.android.support", module = "support-compat")
          exclude(group = "com.android.support", module = "appcompat-v7")
          exclude(group = "com.tencent", module = "mmkv")
      }
      implementation(libs.onyx.base) {
          exclude(group = "com.android.support", module = "support-compat")
          exclude(group = "com.android.support", module = "appcompat-v7")
          exclude(group = "com.tencent", module = "mmkv")
      }
      implementation(libs.hiddenapibypass); implementation(libs.rxjava2); implementation(libs.rxandroid2)
      // плюс compose-bom (platform), compose-foundation, compose-ui, activity-compose,
      // lifecycle-runtime-compose, core-ktx, coroutines-android, room3-runtime; ksp(libs.room3.compiler)
  }
  ```

  Исключение `onyxsdk-geometry`, которое есть в Notable, **не** переносить: артефакт теперь
  находится, а pen его использует.

  Манифест: `SpikeApplication`, одна `MainActivity` с `android:screenOrientation="portrait"`,
  `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>`.
  `SpikeApplication.onCreate()` пока пустой; `MainActivity` показывает `BasicText("Deckly spike")`.

- [x] **Step 7: Собрать и проверить APK**

  Run: `./gradlew :spike:assembleDebug && ./gradlew buildEnvironment | grep kotlin-gradle-plugin && unzip -l spike/build/outputs/apk/debug/spike-debug.apk | awk '$4 ~ /^lib\// {split($4, p, "/"); print p[2]}' | sort -u`
  Expected: `BUILD SUCCESSFUL`; `kotlin-gradle-plugin:2.4.21`; единственная папка ABI — `arm64-v8a`;
  в APK есть `lib/arm64-v8a/libonyx_pen_touch_reader.so`.

- [x] **Step 8: Commit**

  ```bash
  git add tools/setup-android-sdk.sh settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat .gitignore spike/
  git commit  # «Сборка: Android SDK, обёртка Gradle 9.8.2, каталог версий, пустой прототип»
  ```

### Task 2: установка APK кусками с проверкой md5

**Files:**
- Create: `tools/install-apk.sh`
- Create: `tools/test/fake-adb.sh`, `tools/test/install-apk-test.sh`

**Interfaces:**
- Produces: `tools/install-apk.sh <apk>` — коды выхода: 0 установлено, 1 неверные аргументы или нет
  файла, 2 планшет не найден за `WAIT_TIMEOUT`, 3 кусок не дошёл за `MAX_TRIES` попыток, 4 md5
  склеенного APK не совпал, 5 `pm install` не вернул `Success`. Переменные окружения: `ADB`
  (по умолчанию `$ANDROID_HOME/platform-tools/adb`, иначе `~/Android/Sdk/platform-tools/adb`),
  `CHUNK_SIZE` (1048576), `MAX_TRIES` (5), `WAIT_TIMEOUT` (60 секунд).

- [x] **Step 1: Подменный adb `tools/test/fake-adb.sh`**

  Эмулирует планшет папкой `$FAKE_ROOT`: удалённый путь `/data/local/tmp/...` превращается в
  `$FAKE_ROOT/data/local/tmp/...`. Понимает `wait-for-device`, `push <src> <dst>`, `shell "<команда>"`
  (выполняет через `bash -c` с той же заменой путей; `md5sum`, `cat`, `rm`, `mkdir` настоящие).
  `shell "pm install …"` записывает путь в `$FAKE_ROOT/installed.path`, копирует файл в
  `$FAKE_ROOT/installed.apk` и печатает `Success`.
  Сбои: `FAKE_CORRUPT_PUSHES="001:2,003:1"` — первые 2 передачи куска с суффиксом `.001` (и 1 —
  `.003`) пишут файл правильного размера с нулями после первых 4096 байт, как 20 октября;
  счётчик попыток — в `$FAKE_ROOT/.pushes`. `FAKE_NO_DEVICE=1` — `wait-for-device` висит.
  `FAKE_PM_FAIL=1` — `pm install` печатает `Failure [INSTALL_FAILED_INVALID_APK]`.

- [x] **Step 2: Написать падающие тесты `tools/test/install-apk-test.sh`**

  Каждый тест создаёт свежий `FAKE_ROOT`, APK из 3,5 МБ случайных байт и вызывает скрипт с
  `ADB=tools/test/fake-adb.sh`. В конце печатает `N passed, M failed`, код выхода — `M`.

  ```bash
  test_clean_install()          # код 0; md5 installed.apk == md5 APK; вывод содержит «Установлено»;
                                # папки $FAKE_ROOT/data/local/tmp/deckly-install больше нет
  test_retries_corrupted_chunk() # FAKE_CORRUPT_PUSHES=001:2 → код 0; md5 совпадает;
                                # в выводе «кусок 001: попытка 3»
  test_gives_up_after_max_tries() # FAKE_CORRUPT_PUSHES=000:5 MAX_TRIES=5 → код 3;
                                # installed.path не существует
  test_no_device_times_out()    # FAKE_NO_DEVICE=1 WAIT_TIMEOUT=2 → код 2 не позже чем за 5 с;
                                # вывод содержит «планшет не найден»
  test_pm_failure_reported()    # FAKE_PM_FAIL=1 → код 5; вывод содержит INSTALL_FAILED_INVALID_APK
  test_missing_apk()            # путь к несуществующему файлу → код 1
  ```

- [x] **Step 3: Убедиться, что тесты падают**

  Run: `bash tools/test/install-apk-test.sh`
  Expected: `0 passed, 6 failed` (скрипта ещё нет).

- [x] **Step 4: Написать `tools/install-apk.sh`**

  Порядок: проверить аргумент → посчитать md5 APK → `timeout $WAIT_TIMEOUT $ADB wait-for-device`
  (иначе код 2) → `shell rm -rf` и `mkdir -p /data/local/tmp/deckly-install` →
  `split -b $CHUNK_SIZE -d -a 3` во временную папку → для каждого куска до `MAX_TRIES` раз:
  `wait-for-device`, `push`, сравнить `shell md5sum … | cut -c1-32` с локальным, при
  несовпадении `sleep 2` и снова, печатая `кусок NNN: попытка K` → склеить на устройстве
  `cat …/app.apk.part.* > …/app.apk` → сравнить md5 целого (иначе код 4) →
  `shell pm install -r -t …/app.apk`, в выводе должно быть `Success` (иначе код 5) → удалить папку
  на устройстве (через `trap`, в том числе при ошибке) → `Установлено: <apk>, кусков N, md5 X`.

- [x] **Step 5: Тесты проходят**

  Run: `bash tools/test/install-apk-test.sh`
  Expected: `6 passed, 0 failed`.

- [x] **Step 6: Commit** — `tools/install-apk.sh tools/test/`, «Установка APK на Boox кусками с проверкой md5».

- [x] **Дополнение к задаче 2 (после приёмки): переживать обрывы связи** — отдельный коммит

  Планшет рвёт связь по нескольку раз в минуту, поэтому каждый шаг по `adb` переживает обрыв:
  - `OP_TIMEOUT` (120 секунд) вокруг каждого `adb push` и `adb shell`; зависшая операция — неудачная
    попытка, кусок передаётся заново;
  - `wait-for-device` перед каждой попыткой, в том числе перед склейкой и `pm install`;
  - склейка и сверка md5 целого — до `MAX_TRIES` попыток, и только потом код 4;
  - `pm install`: `Failure` в выводе — сразу код 5; нет ни `Success`, ни `Failure` (обрыв транспорта,
    `error: device offline`, пустой вывод, таймаут) — повтор до `MAX_TRIES` попыток;
  - новые тесты: `test_hung_push_retried` (`FAKE_HANG_PUSH=002:1 OP_TIMEOUT=1`) и
    `test_pm_transport_error_retried` (`FAKE_PM_FLAKY=1`).

  Run: `bash tools/test/install-apk-test.sh`
  Expected: `9 passed, 0 failed` (шесть тестов шага 2, седьмой `test_bad_assembly_reported` на код 4 и
  два новых).

- [x] **Дополнение к задаче 2 (после прогона на планшете): пакет включён и есть доступ к файлам** — отдельный коммит

  3 ноября прошивка Boox после `pm install` отключала пакет (`enabled=3`) и сбрасывала доступ ко всем
  файлам. 4 ноября пакет остался `enabled=0`, но и `pm enable`, и `appops` делались руками после каждой
  установки. Скрипт делает это сам.
  - Использование: `tools/install-apk.sh <файл.apk> [пакет]`. Без пакета поведение прежнее.
  - С пакетом, после `Success`:
    1. всегда `pm enable --user 0 <пакет>`, повторять незачем;
    2. `appops set --uid <пакет> MANAGE_EXTERNAL_STORAGE allow`;
    3. проверка: в `dumpsys package <пакет>` у пользователя 0 `enabled=0` или `enabled=1`, а
       `appops get --uid <пакет> MANAGE_EXTERNAL_STORAGE` содержит `allow`.
  - Каждая из трёх операций идёт через `run_adb` с `wait-for-device` и повтором до `MAX_TRIES`, как
    остальные шаги.
  - Новый код 6: пакет не включился или доступ не выдан за `MAX_TRIES` попыток.
  - Вывод: строки `Пакет включён: <пакет>` и `Доступ ко всем файлам: allow`.
  - Шум `adb push` («1 file pushed …») в вывод не попадает; при неудаче куска текст ошибки показывается.
  - Заголовок скрипта (использование, коды выхода) обновить.
  - Подменный `adb`: `pm enable`, `dumpsys package` с `enabled=N` из `FAKE_ENABLED_AFTER_INSTALL`
    (по умолчанию 3, после `pm enable` — 1), `appops set/get`. `FAKE_ENABLE_REFUSED=1` — `pm enable`
    состояние не меняет.
  - Новые тесты:
    - `test_package_enabled_and_access_granted` — код 0, обе строки вывода, `enabled=1`, `allow`;
    - `test_without_package_skips_post_install` — `pm enable` и `appops` не вызывались;
    - `test_enable_refused_reported` — код 6.
  - Задача 5, шаг 4, и `tools/spike-db-killtest.sh`: если там есть ручные `pm enable` и `appops` после
    установки, заменить их пакетом в аргументе.

  Run: `bash tools/test/install-apk-test.sh && bash tools/test/spike-db-killtest-test.sh`
  Expected: `12 passed, 0 failed`; `13 passed, 0 failed`.
  На планшете: `tools/install-apk.sh spike/build/outputs/apk/debug/spike-debug.apk app.deckly.spike` →
  код 0, обе строки, приложение запускается без ручных шагов.

### Task 3: прототип пера

**Files:**
- Modify: `spike/src/main/kotlin/app/deckly/spike/SpikeApplication.kt`, `MainActivity.kt`
- Create (в `spike/src/main/kotlin/app/deckly/spike/`): `SdkStatus.kt`, `SpikeLog.kt`,
  `StrokeStore.kt`, `OnyxPenView.kt`, `CanvasPenView.kt`

**Interfaces:**
- Consumes: модуль `:spike` и каталог версий из задачи 1.
- Produces: `SpikeLog.write(event: String, vararg fields: Pair<String, Any?>)` — пишет в logcat
  с тегом `DecklySpike` строку `EVENT k=v k=v` и добавляет её в `SpikeLog.lines: StateFlow<List<String>>`
  (последние 30). Задача 4 пишет через него же.

- [x] **Step 1: `SdkStatus` и инициализация**

  `object SdkStatus { var isOnyxDevice: Boolean; var initError: Throwable?; var deviceClass: String? }`.
  `SpikeApplication.onCreate()`: `isOnyxDevice` — `Build.MANUFACTURER` или `Build.BRAND` равен
  `ONYX` без учёта регистра **и** `Class.forName("com.onyx.android.sdk.pen.TouchHelper")` удался.
  Если да — в `try/catch (Throwable)`: `HiddenApiBypass.setHiddenApiExemptions("")`,
  `RxManager.Builder.initAppContext(this)`, `ResManager.init(this)`
  (`com.onyx.android.sdk.utils.ResManager`), `deviceClass = Device.currentDevice().javaClass.simpleName`.
  Итог — `SpikeLog.write("SDK", "onyx" to …, "init" to (initError?.toString() ?: "ok"), "device" to …)`.
  Для проверки отказа SDK: экстра `--ez noSdk true` в интенте `MainActivity` до создания листа
  выставляет `initError = IllegalStateException("noSdk")`, и экран ведёт себя так, будто SDK не
  поднялся. Инициализация в `Application` при этом остаётся как есть.
  Затем по одной строке на каждое устройство ввода:
  `INPUT_DEVICE name=… sources=0x… isStylus=…`.

- [x] **Step 2: `StrokeStore`**

  Общий для обоих холстов: `data class SpikeStroke(val points: FloatArray /* x,y,pressure… */, val width: Float)`;
  `add(stroke)`, `eraseAlong(path: List<PointF>, radius: Float): Rect?` (удаляет штрихи целиком, если
  любая точка ближе `radius`; возвращает грязный прямоугольник или `null`), `clear()`,
  `drawTo(canvas: Canvas)`. Координаты — в пикселях холста: в прототипе система координат
  карточки не нужна.

- [x] **Step 3: `OnyxPenView : SurfaceView`**

  Конструктор принимает `useSfRender: Boolean`. `TouchHelper` создаётся в `surfaceCreated`: при
  `useSfRender` — `TouchHelper.create(this, TouchHelper.FEATURE_SF_TOUCH_RENDER, callback)`, иначе
  `TouchHelper.create(this, callback)`; ошибка создания пишется `PEN_CREATE error=…`.
  Сразу после — строка `PEN_RENDER class=…` с классом выбранного рендера, если SDK его отдаёт; если
  нет — `PEN_RENDER class=unknown`, и это выясняет задача 5 по задержке.
  Сессия (Notable `setupSurface`): `setRawDrawingEnabled(false)` → `closeRawDrawing()` →
  `setLimitRect(listOf(rectOfThisView))` → `openRawDrawing()` → `setRawDrawingEnabled(true)` →
  трек ластика: `Device.currentDevice().setStrokeParameters(8, floatArrayOf(w, 0.5f, 1f))` и
  `setEraserRawDrawingEnabled(true, 8)` в `try/catch` → `setStrokeStyle(TouchHelper.STROKE_STYLE_PENCIL)`,
  `setStrokeWidth(3f)`, `setStrokeColor(Color.BLACK)`. Каждый шаг пишет время: `PEN_SESSION ms=…`.
  `fun pause()` — `setRawDrawingEnabled(false)`; `fun resume()` — снова enable + трек ластика + стиль.
  `surfaceDestroyed` — `setRawDrawingEnabled(false)`, `closeRawDrawing()`.
  `RawInputCallback`: `onRawDrawingTouchPointListReceived` → `StrokeStore.add` и
  `PEN_STROKE points=… firstTs=… lastTs=…`; `onRawErasingTouchPointListReceived` →
  `StrokeStore.eraseAlong`, `PEN_ERASE removed=… rect=…`, перерисовка грязного прямоугольника
  (`refreshScreenRegion(…, UpdateMode.ANIMATION_MONO)`), через 200 мс —
  `EpdController.invalidate(this, l, t, r, b, UpdateMode.GC)`; begin/end ластика и пера пишутся
  отдельными строками.

- [x] **Step 4: `CanvasPenView : View`** (запасной путь)

  `onTouchEvent`: в начале каждого жеста строка `MOTION tool=… buttons=0x…` — так видно, что
  присылает кнопка пера InkSense Plus. `TOOL_TYPE_STYLUS` без кнопки рисует; `TOOL_TYPE_ERASER` или
  стилус с `BUTTON_STYLUS_PRIMARY` стирает через `StrokeStore.eraseAlong`; `TOOL_TYPE_FINGER`
  поглощается и пишет `PEN_FINGER_IGNORED`. После штриха — частичное обновление его прямоугольника:
  `EpdController.invalidate(this, l, t, r, b, UpdateMode.DU)` в `try/catch`, иначе `invalidate()`.

- [x] **Step 5: Экран `MainActivity`** (Compose Foundation, без Material)

  Сверху — блок статуса: последние строки `SpikeLog.lines`. Пока перо Onyx включено, прошивка
  замораживает экран, поэтому статус обновляется кнопкой «Статус»: `pause()` → перерисовка → через
  300 мс `resume()`. Посередине — лист через `AndroidView` с весом 1: `OnyxPenView(false)`,
  `OnyxPenView(true)` или `CanvasPenView` по выбранному режиму. Снизу — кнопки не меньше 64 dp, вне
  листа: «Onyx», «Onyx SF», «Canvas», «Очистить», «GC», «REGAL+», «Статус», и места под кнопки
  задачи 4. «GC» и «REGAL+» — полное обновление: `pause()` → перерисовать лист →
  `EpdController.repaintEveryThing(UpdateMode.GC | REGAL_PLUS)` → `resume()`.
  Если `SdkStatus.isOnyxDevice` ложно или `initError` не пуст, режимы «Onyx» недоступны, а режим
  по умолчанию — «Canvas». `onPause`/`onResume` активити зовут `pause()`/`resume()` листа.

- [x] **Step 6: Вырезать разрешения SDK**

  В `spike/src/main/AndroidManifest.xml` для каждого разрешения из Global Constraints — строка
  `<uses-permission android:name="android.permission.X" tools:node="remove" />`.

- [x] **Step 7: Собрать**

  Run: `./gradlew :spike:assembleDebug && ~/Android/Sdk/build-tools/37.0.0/aapt2 dump permissions spike/build/outputs/apk/debug/spike-debug.apk`
  Expected: `BUILD SUCCESSFUL`; в списке разрешений только `MANAGE_EXTERNAL_STORAGE` и
  служебное `app.deckly.spike.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` от androidx. Проверка
  поведения — задача 5.

- [x] **Step 8: Commit** — `spike/`, «Прототип: перо Onyx (обычный и SF-рендер), запасной Canvas, журнал проверок».

- [x] **Дополнение к задаче 3 (после приёмки): стирание при включённом сыром пере** — отдельный коммит

  Пока сырое перо включено, прошивка замораживает экран, поэтому перерисовка поверхности и обновление
  региона после стирания на экране не видны. Как в коммите стирания у Notable, `handleErase`:
  - выключает перо, перерисовывает поверхность и обновляет регион (`refreshScreenRegion(…, ANIMATION_MONO)`);
  - через `GC_DELAY_MS` = 200 делает GC по накопленному прямоугольнику, если включён переключатель;
  - ещё через `ERASE_RESUME_MS` = 150 включает перо снова, но не включает, если владелец успел поставить
    паузу (активити ушла, окно потеряло фокус); пока перо выключено, штрихи не принимаются;
  - пишет `ERASE_COMMIT rect=… gc=true|false pausedMs=… resumed=true|false`.

  Кнопка «GC после ластика: вкл/выкл» (по умолчанию вкл) в ряду с «GC»/«REGAL+», чтобы владелец мог
  сравнить след ластика до и после GC. `MainActivity.onWindowFocusChanged`: без фокуса — `pause()`
  листа, с фокусом — `resume()` (шторка уведомлений и системные окна ложатся поверх сырого пера).
  Комментарий про поток колбэков исправлен: SDK доставляет их через `hostView.post()` (по байткоду это
  видно для SF-рендера; для остальных рендеров поток не проверялся, и код на него не полагается).

  Run: `./gradlew :spike:assembleDebug`
  Expected: `BUILD SUCCESSFUL`.

### Task 4: прототип базы в `/sdcard/Deckly/`

**Files:**
- Create: `spike/src/main/kotlin/app/deckly/spike/db/ProbeDatabase.kt`, `db/DbProbe.kt`
- Modify: `MainActivity.kt` (кнопки и запуск по интенту)
- Create: `tools/spike-db-killtest.sh`

**Interfaces:**
- Consumes: `SpikeLog.write` из задачи 3.
- Produces: строки logcat `PROBE_SERIES n=… avgMs=… maxMs=…`,
  `PROBE_CHECK file=… journal=… integrity=… counter=… entries=… consistent=…`,
  `PROBE_SCAN path=… uri=…`, `PROBE_DENIED` — их разбирает `spike-db-killtest.sh` и задача 5.
  Запуск по интенту: `adb shell am start -n app.deckly.spike/.MainActivity --es probe series|check|foreign`.

- [x] **Step 1: `ProbeDatabase` на Room 3**

  Сущности: `Counter(@PrimaryKey id: Int = 1, value: Long)` и
  `Entry(@PrimaryKey(autoGenerate = true) id: Long = 0, counterValue: Long, payload: String)`.
  DAO с одной транзакцией `suspend fun step(payload: String)`: увеличить `Counter.value` и вставить
  `Entry` с новым значением. Плюс `suspend fun counter(): Long`, `suspend fun entries(): Long`,
  `suspend fun journalMode(): String` (`PRAGMA journal_mode`), `suspend fun integrity(): String`
  (`PRAGMA integrity_check`). Открытие:
  `fun open(context: Context, file: File): ProbeDatabase` — `Room.databaseBuilder` с
  `file.absolutePath` вместо имени, драйвер Android SQLite, `JournalMode.TRUNCATE`. Точные имена
  API Room 3 сверить с документацией `androidx.room3`.

- [x] **Step 2: `DbProbe`**

  Папка — `/sdcard/Deckly/spike/`. Если `Environment.isExternalStorageManager()` ложно — каждая
  операция пишет `PROBE_DENIED` и ничего не открывает.
  - `suspend fun runSeries(n: Int = 2000)` — `n` вызовов `step` с `payload` из 200 символов, время
    каждого; итог `PROBE_SERIES`; затем `MediaScannerConnection.scanFile` на `probe.db` → `PROBE_SCAN`.
  - `suspend fun check(file: File = probe.db)` — `PROBE_CHECK`, `consistent = counter == entries`.
  - `suspend fun checkForeign()` — `check(File(dir, "foreign.db"))`: база, созданная не этим
    приложением (кладётся по adb в задаче 5).

- [x] **Step 3: Кнопки и интент**

  Кнопки «Доступ к файлам» (`Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION` с
  `package:app.deckly.spike`), «База: серия», «База: проверка», «База: чужая». Экстра `probe` в
  интенте запуска вызывает то же без касаний. Каждый запуск приложения сначала выполняет `check()`.

- [x] **Step 4: `tools/spike-db-killtest.sh [раз=20]`**

  Для каждого раза: `wait-for-device` → `am start … --es probe series` → пауза случайно
  0,3–2,0 с → `am force-stop app.deckly.spike` → `logcat -c` → `am start … --es probe check` →
  ждать строку `PROBE_CHECK` в `logcat -d -s DecklySpike` не дольше 15 с. В конце:
  `consistent K из N, integrity ok K из N`; код выхода 0 только при `N из N` по обоим.
  `adb` берётся так же, как в `install-apk.sh`.

- [x] **Step 5: Собрать**

  Run: `./gradlew :spike:assembleDebug && bash -n tools/spike-db-killtest.sh`
  Expected: `BUILD SUCCESSFUL`, синтаксис скрипта без ошибок.

- [x] **Step 6: Commit** — `spike/ tools/spike-db-killtest.sh`, «Прототип: Room 3 в /sdcard/Deckly, проверка после убийства».

- [x] **Дополнение к задаче 4: тесты скрипта и схема чужой базы**

  - `tools/test/spike-db-killtest-test.sh` — тесты на заглушке adb (порядок вызовов с базовой
    линией, итог, приращения и метки, коды выхода, границы 0/1999/2000, повтор упавшей команды adb).
    Run: `bash tools/test/spike-db-killtest-test.sh`; Expected: `13 passed, 0 failed`.
  - Перед циклом — базовая проверка (`PROBE_CHECK`, затем `force-stop`). Каждый запуск получает
    приращение строк к предыдущей проверке и метку: «до серии» (≤ 0), «посреди серии»
    (0 < Δ < `SERIES_SIZE` = 2000), «после серии» (≥ 2000). Итог:
    «consistent K из N, integrity ok K из N, посреди серии M из N».
  - Коды выхода `spike-db-killtest.sh`: 0 — consistent и integrity N из N и M ≥ ⌈N/2⌉; 1 — хотя бы
    один запуск несогласован, испорчен или не дал строки; 2 — планшет не найден; 3 — `PROBE_DENIED`
    (доступ ко всем файлам не выдан), прогон прекращается; 4 — всё цело, но убийства не попали в
    серию: в stderr приращения по запускам и куда сдвинуть `KILL_DELAY_MIN_MS`/`KILL_DELAY_MAX_MS`.
  - Для задачи 5, шаг 7. Room открывает `foreign.db`, только если схема совпадает с его схемой. Схема из
    сгенерированного `ProbeDatabase_Impl.kt` (`spike/build/generated/ksp/debug/kotlin/app/deckly/spike/db/`):
    ```sql
    CREATE TABLE IF NOT EXISTS `Counter` (`id` INTEGER NOT NULL, `value` INTEGER NOT NULL, PRIMARY KEY(`id`));
    CREATE TABLE IF NOT EXISTS `Entry` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `counterValue` INTEGER NOT NULL, `payload` TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT);
    INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'c8f5e0999649f16c380ef339a423ec53');
    PRAGMA user_version = 1;
    ```
    Строки: `Counter(1, 3)` и три строки `Entry` со значениями счётчика 1, 2, 3. Хеш `identity_hash`
    меняется вместе со схемой: перед созданием файла сверить его с `ProbeDatabase_Impl.kt`.

### Task 5: прогон на планшете и итоги

Нужен владелец: он подключает планшет и проверяет перо руками. Исполнитель ставит, запускает
скрипты и читает logcat.

**Files:**
- Create: `docs/superpowers/specs/2026-11-03-device-spike-results.md`
- Modify: `docs/superpowers/specs/2026-11-03-architecture-design.md` (раздел «Хранение и резервная
  копия», пункт «Риск», и раздел `ink`), ТЗ, раздел «Onyx SDK» — только с согласия владельца.

- [x] **Step 1: Подключение.** `/usr/bin/adb kill-server`; планшет по USB с включённой отладкой;
  `~/Android/Sdk/platform-tools/adb devices -l` показывает устройство. Записать
  `getprop ro.product.model`, `ro.product.device`, `ro.build.display.id`.

- [ ] **Step 2: Wi-Fi.** *(Не делали: USB 3–4 ноября работал без обрывов.)* Владелец включает «Отладку по Wi-Fi»; `adb pair <ip:port>` и
  `adb connect <ip:port>`. Дальнейшие шаги идут по Wi-Fi, если `install-apk.sh` проходит по нему без
  повторов кусков; иначе — по USB. Записать число повторов по каждому способу.

- [x] **Step 3: Имя пера.** `adb shell getevent -lp | grep -E 'add device|name'`. Записать имя
  устройства ввода пера и совпадает ли оно с `onyx_emp`, `Wacom I2C Digitizer`, `hanvon_tp`.

- [x] **Step 4: Установка.**
  Run: `./gradlew :spike:assembleDebug && tools/install-apk.sh spike/build/outputs/apk/debug/spike-debug.apk`
  Expected: `Установлено: …`, код 0.
  До выдачи доступа: `adb shell am start -n app.deckly.spike/.MainActivity --es probe check` →
  в logcat `PROBE_DENIED`, приложение не упало (`adb shell pidof app.deckly.spike` непусто). Затем
  `adb shell appops set --uid app.deckly.spike MANAGE_EXTERNAL_STORAGE allow`.
  Отказ SDK: `adb shell am force-stop app.deckly.spike`,
  `adb shell am start -n app.deckly.spike/.MainActivity --ez noSdk true` → строка `SDK … init=…noSdk`,
  открыт режим «Canvas», режимы «Onyx» недоступны, штрих стилусом рисуется.

- [x] **Step 5: Перо — владелец, в каждом из режимов «Onyx», «Onyx SF», «Canvas»:**
  десять штрихов разной длины — появляется ли линия под пером без заметной задержки; стирание
  кнопкой пера — уходит ли штрих целиком и остаётся ли серый след (до и после GC через 200 мс);
  касание пальцем по листу — ничего не рисует; кнопки внизу — срабатывают и от пальца, и от пера;
  «GC» и «REGAL+» — какой режим чище снимает следы. Исполнитель параллельно читает
  `adb logcat -s DecklySpike` и записывает `SDK`, `INPUT_DEVICE`, `PEN_RENDER`, `PEN_SESSION ms`,
  `MOTION tool/buttons` для кнопки пера. После каждого режима:
  `adb logcat -d | grep -E 'SecurityException|Permission Denial'`. Если перо Onyx не работает и в
  логе отказ по вырезанному разрешению — вернуть именно его, пересобрать, повторить режим и
  записать, какое разрешение SDK действительно нужно.

- [x] **Step 6: База.**
  Run: `tools/spike-db-killtest.sh 20`
  Expected: `consistent 20 из 20, integrity ok 20 из 20, посреди серии M из 20` с M ≥ 10, код 0.
  Код 4 — сдвинуть паузу по подсказке скрипта и повторить. Плюс «База: серия» из интерфейса —
  записать `avgMs` и `maxMs` из `PROBE_SERIES`.

- [x] **Step 7: Чужая база и переустановка.** На компьютере создать `foreign.db` той же схемы
  (`python3 -I` и модуль `sqlite3`: таблицы `Counter` с одной строкой `value=3` и `Entry` с тремя
  строками), передать `adb push` в `/sdcard/Deckly/spike/` и сверить md5. «База: чужая» →
  `PROBE_CHECK … consistent=true`. Затем `adb uninstall app.deckly.spike`, снова шаг 4, «База:
  проверка» → `probe.db` от прошлой установки открывается и `consistent=true`.

- [x] **Step 8: Видимость файлов.** `adb shell ls -la /sdcard/Deckly/spike/`; владелец открывает
  планшет на компьютере по USB как устройство хранения файлов и сверяет размер `probe.db` с `ls`
  после «База: серия» и сворачивания приложения; если на планшете есть Termux —
  `ls -la ~/storage/shared/Deckly/spike/`.

- [x] **Step 9: Решения по итогам**, записать в `2026-11-03-device-spike-results.md` вместе с
  данными шагов 1–8:
  - перо: основной путь — «Onyx», если он прошёл шаг 5; иначе «Onyx SF», если прошёл он; иначе
    «Canvas». Нужен ли GC через 200 мс после стирания;
  - база: остаётся в `/sdcard/Deckly/`, если шаги 6–7 прошли и `avgMs ≤ 30`, `maxMs ≤ 200`
    **(моё решение по порогам)**; иначе — запасной вариант из документа архитектуры (закрытая
    папка + снимок `VACUUM INTO`);
  - копирование по USB: видна ли свежая база после сворачивания;
  - разрешения: какие из вырезанных SDK действительно нужны (по умолчанию в приложении вырезаются все).
  Обновить документ архитектуры по этим решениям. Предложить владельцу правки раздела «Onyx SDK»
  в ТЗ по списку «Расхождения с ТЗ» с учётом подтверждённого на планшете; внести после согласия.

  - ручка (задача 6): рисует ли прошивка стилем ручки толщину по нажиму и не падает ли; какой
    скачок после отрыва пера даёт отрисовка SDK и своя; кривая «нажим → толщина» из
    `PEN_FOUNTAIN_CURVE`. Предложение владельцу **(моё)**: если ручка прошла — стиль FOUNTAIN, нажим
    0..1 обязателен в каждой точке штриха; после отрыва своя отрисовка с кривой по
    `PEN_FOUNTAIN_CURVE`, если владелец считает скачок терпимым, иначе SDK на Onyx и своя без SDK.

- [x] **Step 10: Commit** — итоги, документ архитектуры, ТЗ (если согласовано): «Прототип на
  Go 10.3 Gen II: итоги по перу и базе».

### Task 6: перьевая ручка (толщина от нажима)

Владелец 2026-11-04: толщина линии не зависит от нажима, хочет попробовать перьевую ручку. Нажим
приходит (в журнале фазы Б 95…2555 при максимуме 4096), но прошивка рисует стилем
`STROKE_STYLE_PENCIL`, а `StrokeStore` перерисовывает штрих одной толщиной. На планшете надо узнать:
1. рисует ли прошивка стилем `STROKE_STYLE_FOUNTAIN` (= 1) толщину по нажиму под пером и не падает ли;
2. совпадает ли с этим штрих, перерисованный после отрыва пера через SDK
   `NeoFountainPenWrapper.drawStroke` (внутри нативная `libneopen_jni.so`; кисти Neo падали, про
   ручку неизвестно);
3. насколько заметен скачок при своей отрисовке на Kotlin, которая не зависит от Onyx и нужна
   запасному Canvas.

Код идёт в одну сборку с диагностикой следа ластика (08ca6f9), чтобы ставить на планшет один раз.

**Files:**
- Create: `spike/src/main/kotlin/app/deckly/spike/StrokeRenderer.kt`
- Modify: `PenSheet.kt`, `SdkStatus.kt`, `SpikeApplication.kt`, `StrokeStore.kt`, `OnyxPenView.kt`,
  `CanvasPenView.kt`, `MainActivity.kt`

**Interfaces:**
- `SpikeStroke.points` — тройки `x, y, нажим`; нажим нормирован в 0..1 при вводе. `SpikeStroke.width` —
  толщина из настройки на момент штриха.
- `enum class PenStyle(val label: String, val sdkStyle: Int)`: `PENCIL("карандаш", STROKE_STYLE_PENCIL)`,
  `FOUNTAIN("ручка", STROKE_STYLE_FOUNTAIN)`.
- `enum class StrokeRender(val label: String)`: `CONSTANT("ровно")`, `OWN("свой")`, `SDK("SDK")`.
- `PenSettings`: `@Volatile var penStyle = PenStyle.PENCIL`, `val WIDTH_OPTIONS = floatArrayOf(3f, 6f, 10f)`,
  `@Volatile var strokeWidth = 3f`, `@Volatile var strokeRender = StrokeRender.CONSTANT`.
- `SdkStatus.maxTouchPressure: Float` — сырой максимум нажима.
- `PenSheet.applyPenSettings()` — следующий штрих идёт новым стилем и толщиной, лист перерисован
  новой отрисовкой.
- `object StrokeRenderer`: `drawStroke(canvas: Canvas, stroke: SpikeStroke)`,
  `computePressureWidth(baseWidth: Float, pressure: Float): Float`, `computeMargin(stroke: SpikeStroke): Int`.

- [x] **Step 1: Нажим 0..1 и стиль из настроек**
  - `SpikeApplication` после инициализации SDK: `SdkStatus.maxTouchPressure =
    EpdController.getMaxTouchPressure()`. Если меньше или равно 0 или исключение — `4096f`. Лог
    `PEN_MAX_PRESSURE value=… source=sdk|fallback`.
  - `OnyxPenView.handleStroke`: нажим `point.pressure / SdkStatus.maxTouchPressure`, обрезанный в
    0..1; `width = PenSettings.strokeWidth`; запас прямоугольника — `StrokeRenderer.computeMargin`.
  - `CanvasPenView.collectPoints`: нажим у каждой точки свой — `getHistoricalPressure(0, i)` для
    истории, `getPressure(0)` для текущей, обрезанный в 0..1. Сейчас всем точкам пачки ставится
    последний нажим — это ошибка.
  - `applyPenStyle`: `setStrokeStyle(PenSettings.penStyle.sdkStyle)`,
    `setStrokeWidth(PenSettings.strokeWidth)`; лог `PEN_STYLE style=… width=… render=…`.

- [x] **Step 2: `StrokeRenderer`** по `PenSettings.strokeRender`:
  - `CONSTANT` — нынешний `StrokeStore.drawStroke` без изменений, перенесённый сюда.
  - `OWN` — каждый отрезок `i-1 → i` через `drawLine` с `Cap.ROUND` и толщиной
    `computePressureWidth(stroke.width, (p[i-1] + p[i]) / 2)`; штрих из одной точки — круг этого
    диаметра. `computePressureWidth = baseWidth * (MIN_WIDTH_FACTOR + (1 - MIN_WIDTH_FACTOR) * pressure)`,
    `MIN_WIDTH_FACTOR = 0.3f` **(моё; подгоняется по `PEN_FOUNTAIN_CURVE`)**. Нулевой нажим даёт
    тонкую, но видимую линию.
  - `SDK` — при `SdkStatus.isPenAvailable`: точки в `TouchPoint(x, y, p * maxTouchPressure,
    stroke.width, 0L)`, затем `NeoFountainPenWrapper.drawStroke(canvas, paint, points, 1f, stroke.width,
    SdkStatus.maxTouchPressure, false)`. Порядок аргументов — масштаб, толщина, максимум нажима — взят
    из байткода (`computeStrokePoints(list, F, F, F)` передаёт два последних дальше) и совпадает с
    OnyxAndroidDemo. Перед самым первым вызовом пишется лог `PEN_RENDER_SDK call=first`: если
    процесс упадёт в нативном коде, в logcat будет видно где. При `Throwable` пишется лог
    `PEN_RENDER_SDK error=…` и этот штрих рисуется как `OWN`. Без SDK — `OWN`.
  - `computeMargin = ceil(stroke.width).toInt() * 2 + 2`: ручка SDK может выйти за базовую толщину.
  - `StrokeStore.drawTo` и `computeBounds` идут через `StrokeRenderer`. `CanvasPenView.drawLiveStroke`
    рисует текущий штрих тем же `drawStroke`, чтобы в Canvas нажим был виден под пером.

- [x] **Step 3: Данные для подгонки.** После каждого штриха пишется лог
  `PEN_PRESSURE points=N min=… max=… avg=…` (нажим 0..1, два знака). Если стиль `FOUNTAIN` и SDK
  поднят, вызывается `NeoFountainPenWrapper.computeStrokePoints(points, 1f, width, maxTouchPressure)`
  и пишется лог `PEN_FOUNTAIN_CURVE width=… pairs=p:size,…`: до 12 равномерно взятых точек
  результата, `p` — нормированный нажим, `size` — поле `size`. В результате точек может быть
  больше или меньше, чем на входе. Ошибка — лог `PEN_FOUNTAIN_CURVE error=…`.

- [x] **Step 4: Кнопки.** Новый ряд: «Перо: карандаш|ручка», «Толщина: 3|6|10», «Отрисовка:
  ровно|свой|SDK». Нажатие переключает значение по кругу, пишет его в `PenSettings` и вызывает
  `getSheet()?.applyPenSettings()`.
  - `OnyxPenView.applyPenSettings`: если сессия открыта и стирание не коммитится — `disableRawDrawing()`,
    затем `enableRawDrawing()`. Последний заново ставит стиль и трек ластика. После этого `redrawAll()`.
  - Если стирание коммитится — только `redrawAll()`: стиль поставит возобновление пера.
  - `CanvasPenView` — `redrawAll()`.

- [x] **Step 5: Собрать**
  Run: `./gradlew :spike:assembleDebug && unzip -l spike/build/outputs/apk/debug/spike-debug.apk | grep libneopen_jni`
  Expected: `BUILD SUCCESSFUL`; `lib/arm64-v8a/libneopen_jni.so` в APK.

- [x] **Step 6: Commit** — «Прототип: перьевая ручка и толщина от нажима (не проверено на планшете)».

- [x] **Step 7: Планшет — владелец, режим «Onyx».** Установка как в задаче 5, плюс `pm enable` и
  `appops`. Пока перо включено, экран заморожен, поэтому наша перерисовка видна только после
  выключения и включения пера. Это «Статус», любая кнопка ряда «Перо/Толщина/Отрисовка» и фиксация
  ластика. В приложении так же: штрих прошивки стоит до ближайшего обновления, а при обучении
  карточку рисует только наш код. Поэтому в проверках 1–3 после штрихов нажимают «Статус» и
  сравнивают с тем, что было под пером. Проверки:
  1. «Перо: ручка», «Отрисовка: ровно», «Толщина: 6». Штрихи слабым и сильным нажимом: меняется ли
     толщина под пером? После «Статус» штрих становится ровным. Это контроль: так видно, что
     «Статус» показывает нашу перерисовку.
  2. «Отрисовка: SDK». Остаётся ли штрих после «Статус» таким, каким был под пером? Не упало ли
     приложение?
  3. «Отрисовка: свой». Насколько заметен скачок после «Статус»? SDK рисует так же, как «свой»
     (`PenUtils.drawStrokeByPointSize`: отрезки `drawLine` с круглыми концами), так что разница
     только в кривой «нажим → толщина».
  4. Толщины 3 и 10: видна ли разница от нажима.
  5. Ластик кнопкой пера по штрихам ручки: уходят ли целиком. Не выросла ли задержка пера.
  6. Режим «Canvas», «Отрисовка: свой»: видна ли толщина по нажиму под пером.

  Исполнитель записывает `PEN_MAX_PRESSURE`, `PEN_STYLE`, `PEN_PRESSURE`, `PEN_FOUNTAIN_CURVE`,
  `PEN_RENDER_SDK`. После падения — `adb logcat -b crash -d | tail -50`. Итоги идут в шаг 9 задачи 5.
