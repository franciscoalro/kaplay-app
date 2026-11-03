#!/usr/bin/env bash

set -euo pipefail

: "${old_apk:?old_apk is required}"
: "${new_apk:?new_apk is required}"
: "${RUNNER_TEMP:?RUNNER_TEMP is required}"

adb install "$old_apk"
adb shell pm path com.kaplay.app | grep -Fq 'base.apk'
old_version="$(adb shell dumpsys package com.kaplay.app | grep -m1 'versionName=' | tr -d '\r')"
marker_path='/sdcard/Android/data/com.kaplay.app/files/ka-update-smoke-marker.txt'
adb shell "mkdir -p /sdcard/Android/data/com.kaplay.app/files && printf 'preserve-me\\n' > '$marker_path'"
test "$(adb shell cat "$marker_path" | tr -d '\r')" = 'preserve-me'
adb install -r "$new_apk" > "$RUNNER_TEMP/ka-update-smoke-install.log" 2>&1
grep -Fq 'Success' "$RUNNER_TEMP/ka-update-smoke-install.log"
adb shell pm path com.kaplay.app | grep -Fq 'base.apk'
new_version="$(adb shell dumpsys package com.kaplay.app | grep -m1 'versionName=' | tr -d '\r')"
test "$(adb shell cat "$marker_path" | tr -d '\r')" = 'preserve-me'
{
  printf 'old=%s\n' "$old_version"
  printf 'new=%s\n' "$new_version"
  printf 'package=com.kaplay.app\n'
  printf 'operation=adb install -r\n'
  printf 'data_marker=preserved\n'
  printf 'uninstall=not-run\nclear=not-run\n'
} | tee "$RUNNER_TEMP/ka-update-smoke-evidence.txt"
