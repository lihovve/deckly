#!/usr/bin/env bash
# Ставит Android SDK в ~/Android/Sdk: cmdline-tools, platform-tools, платформу 37 и build-tools 37.
# Идемпотентный: то, что уже стоит, пропускает. Ничего не требует от sudo и apt.
set -euo pipefail

CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"

SDK_ROOT="$HOME/Android/Sdk"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

install_cmdline_tools() {
  if [[ -x "$SDKMANAGER" ]]; then
    echo "cmdline-tools уже стоят: $SDK_ROOT/cmdline-tools/latest"
    return
  fi

  local work zip actual
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' RETURN
  zip="$work/commandlinetools.zip"

  echo "Скачиваю cmdline-tools: $CMDLINE_TOOLS_URL"
  curl --fail --location --silent --show-error --output "$zip" "$CMDLINE_TOOLS_URL"

  actual="$(sha1sum "$zip" | cut -d' ' -f1)"
  if [[ "$actual" != "$CMDLINE_TOOLS_SHA1" ]]; then
    echo "SHA-1 не совпал: ожидался $CMDLINE_TOOLS_SHA1, получен $actual" >&2
    exit 1
  fi

  unzip -q "$zip" -d "$work/unpacked"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  mv "$work/unpacked/cmdline-tools" "$SDK_ROOT/cmdline-tools/latest"
  echo "cmdline-tools распакованы в $SDK_ROOT/cmdline-tools/latest"
}

accept_licenses() {
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses < <(yes) > /dev/null
  echo "Лицензии приняты"
}

install_packages() {
  local missing=()
  [[ -x "$SDK_ROOT/platform-tools/adb" ]]     || missing+=("platform-tools")
  [[ -d "$SDK_ROOT/platforms/android-37.0" ]] || missing+=("platforms;android-37.0")
  [[ -d "$SDK_ROOT/build-tools/37.0.0" ]]     || missing+=("build-tools;37.0.0")

  if (( ${#missing[@]} == 0 )); then
    echo "platform-tools, platforms;android-37.0 и build-tools;37.0.0 уже стоят"
    return
  fi
  "$SDKMANAGER" --sdk_root="$SDK_ROOT" "${missing[@]}"
}

write_local_properties() {
  echo "sdk.dir=$SDK_ROOT" > "$REPO_ROOT/local.properties"
  echo "Записан $REPO_ROOT/local.properties"
}

install_cmdline_tools
accept_licenses
install_packages
write_local_properties

echo
echo "Добавьте в ~/.bashrc:"
echo "export ANDROID_HOME=\$HOME/Android/Sdk"
