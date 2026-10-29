#!/usr/bin/env bash
# Drives Neer Zen on an emulator, captures screenshots and checks key behaviour.
# Usage: capture-screens.sh <apk> <outdir>
# Writes <outdir>/*.png and <outdir>/report.txt (PASS/FAIL lines).
set -u
APK="$1"
OUT="$2"
PKG=com.zen.neer
mkdir -p "$OUT"
REPORT="$OUT/report.txt"
: > "$REPORT"

log() { echo "$*" | tee -a "$REPORT"; }
shot() { adb exec-out screencap -p > "$OUT/$1.png" && echo "captured $1"; }
dump() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml > /dev/null 2>&1
}
# Prints "x y" for the first node whose text or content-desc equals $1.
find_node() {
  dump
  python3 - "$1" <<'PY'
import re, sys
want = sys.argv[1]
try:
    xml = open('/tmp/ui.xml', encoding='utf-8').read()
except OSError:
    sys.exit(0)
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n)
    d = re.search(r' content-desc="([^"]*)"', n)
    if (t and t.group(1) == want) or (d and d.group(1) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        x1, y1, x2, y2 = map(int, b.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}
tap() {
  for _ in 1 2 3; do
    xy=$(find_node "$1")
    if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped '$1' at $xy"; return 0; fi
    sleep 1
  done
  log "FAIL could not find '$1' to tap"
  return 1
}
expect() {
  for _ in 1 2 3; do
    if [ -n "$(find_node "$1")" ]; then log "PASS shows '$1'"; return 0; fi
    sleep 1
  done
  log "FAIL expected '$1' on screen"
  return 1
}
launch() {
  adb shell am force-stop $PKG
  adb shell am start -W -n $PKG/.MainActivity > /dev/null
}

adb install -r "$APK" > /dev/null && log "PASS installed"
adb shell settings put system font_scale 1.0

# --- Mindful Sip ---------------------------------------------------------------
launch
sleep 0.5; shot 00-splash
sleep 2.8; shot 01-sip-ready
expect "0 of 5 mindful sips today"
tap "Begin mindful sip"
# Burst through the first two beats to catch a droplet forming, falling and landing.
sleep 0.9
for i in $(seq -w 1 14); do shot "02-burst-$i"; done
sleep 2.2; shot 04-sip-midway
sleep 7.2; shot 05-sip-well-done
expect "Well done"
tap "Done"
sleep 1.2; shot 06-sip-after-one
expect "1 of 5 mindful sips today"

# Cancel stops the session and records nothing. While the ritual animates, uiautomator
# can't settle, so Cancel is tapped at the action slot it shares with "Begin mindful sip".
slot=$(find_node "Begin mindful sip")
tap "Begin mindful sip"
sleep 3; shot 07-sip-running-before-cancel
if [ -n "$slot" ]; then adb shell input tap $slot; echo "tapped Cancel slot at $slot"; fi
sleep 1.5; shot 08-sip-cancelled
expect "Begin mindful sip"
expect "1 of 5 mindful sips today"

# --- Focus ----------------------------------------------------------------------
tap "Focus"
sleep 1.5; shot 09-focus
expect "Start focus"
tap "Start focus"
sleep 3.5; shot 10-focus-running
expect "Pause" || true

# Clear state so the running focus timer doesn't affect the next screens
adb shell pm clear $PKG > /dev/null
launch
sleep 3

# --- Infusions --------------------------------------------------------------------
tap "Infusions"
sleep 1.5; shot 11-infusions
tap "Jeera Water"
sleep 1.5; shot 12-infusion-detail
# The steep timer button sits below the fold on the recipe page.
adb shell input swipe 540 1900 540 700 400
sleep 0.8
adb shell input swipe 540 1900 540 700 400
sleep 0.8; shot 12b-infusion-detail-lower
tap "Start 5-min steep timer"
sleep 2.5; shot 13-steep
# Back dismisses the steep timer (the countdown keeps uiautomator from settling).
adb shell input keyevent KEYCODE_BACK
sleep 1.2
expect "Herbal Infusions" || expect "Jeera Water"

# --- Settings ---------------------------------------------------------------------
tap "Sip"
sleep 1
tap "Settings"
sleep 1.2; shot 14-settings
adb shell input keyevent KEYCODE_BACK
sleep 1

# --- Larger font and a small screen -------------------------------------------------
adb shell settings put system font_scale 1.3
launch
sleep 3.2; shot 15-sip-font-130
expect "Begin mindful sip"
adb shell settings put system font_scale 1.0
adb shell wm size 720x1280
adb shell wm density 320
launch
sleep 3.2; shot 16-sip-small-screen
tap "Begin mindful sip"
sleep 5.5; shot 17-sip-small-running
adb shell wm size reset
adb shell wm density reset

log "done"
