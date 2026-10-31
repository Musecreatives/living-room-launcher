#!/usr/bin/env bash
# On-device smoke test for ShaddaiTV Home: drives the launcher with remote keys over ADB and checks
# what Android launched (from the system log). Screenshots per step go to OUT for a visual pass.
#
# Usage: tools/tv-smoke-test.sh [box-ip:port] [out-dir]
# Don't touch the remote while it runs. The Resume cases briefly start playback in ShaddaiTV.
set -u
BOX="${1:-192.168.18.7:5555}"
OUT="${2:-./smoke-out}"
ADB="${ADB:-adb}"
export MSYS_NO_PATHCONV=1 # Git Bash: keep device paths as-is
mkdir -p "$OUT"
pass=0
fail=0
HOME_ACT="com.livingroom.launcher/com.livingroom.launcher.HomeActivity"

a() { "$ADB" -s "$BOX" "$@"; }
# 1.2 s between presses: this box needs that long to settle each focus move (faster presses can land early)
key() { for k in "$@"; do a shell input keyevent "KEYCODE_$k"; sleep 1.2; done; }
shot() { a exec-out screencap -p >"$OUT/$1.png"; }
front() { a shell dumpsys window | tr -d '\r' | grep -o 'mCurrentFocus=Window{[^}]*}' | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.]+' | head -1; }
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
no() { echo "FAIL  $1  ($2)"; fail=$((fail + 1)); }
on_home() { [ "$(front)" = "$HOME_ACT" ] && ok "$1" || no "$1" "front: $(front)"; }

# Fresh launcher with the first dock tile (ShaddaiTV) focused, log cleared
fresh() {
  a shell am force-stop com.livingroom.launcher
  a logcat -c
  a shell am start -n "$HOME_ACT" >/dev/null
  sleep 10 # cold start: app list, shelf and weather load
}

# Did Android start an activity matching $1 since the last fresh()?
started() { a logcat -d | grep 'START u0' | grep -qE "$1"; }

# name, key..., pattern: fresh launcher, press keys, expect a START matching pattern, then go Home
expect_start() {
  local name="$1" pattern="${!#}"
  local keys=("${@:2:$#-2}")
  fresh
  key "${keys[@]}"
  sleep 3
  shot "$name"
  if started "$pattern"; then ok "$name"; else no "$name" "no START matching $pattern"; fi
  key HOME
  sleep 2
}

# Down from the dock lands in the grid; extra Downs stop on the last row, Rights stop on its last tile
TO_LAST_TILE=(DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN DPAD_DOWN
  DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT)

a connect "$BOX" >/dev/null 2>&1

fresh
shot 01-home
on_home "Home is the launcher"

expect_start 02-dock-youtube DPAD_RIGHT DPAD_CENTER 'cmp=com.google.android.youtube.tv/'
expect_start 03-continue-card DPAD_UP DPAD_CENTER 'act=com.github.damontecres.wholphin.PLAYBACK .*cmp=com.livingroom.tv'
expect_start 04-resume DPAD_UP DPAD_UP DPAD_CENTER 'act=com.github.damontecres.wholphin.PLAYBACK .*cmp=com.livingroom.tv'
expect_start 05-episodes DPAD_UP DPAD_UP DPAD_RIGHT DPAD_CENTER 'act=android.intent.action.VIEW .*cmp=com.livingroom.tv'
# Switch back is the tile before the toggle. Assumes the toggle opens a new row (19 tiles today): Up, then to that row's end
expect_start 06-switch-back "${TO_LAST_TILE[@]}" DPAD_UP DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_RIGHT DPAD_CENTER 'act=android.settings.APPLICATION_DETAILS_SETTINGS'

# Widgets: open weather, Back closes it, Back again returns to the dock without leaving Home
fresh
key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_CENTER
shot 07a-weather-open
key BACK
shot 07b-weather-closed
key BACK
shot 07c-back-to-dock
on_home "Back from widgets stays on Home"

key DPAD_UP DPAD_UP DPAD_UP DPAD_UP DPAD_RIGHT DPAD_CENTER
shot 08-notifications-open
key BACK BACK

# All apps: Down from the dock, then the "Show hidden apps" toggle (last tile)
fresh
key DPAD_DOWN
shot 09a-all-apps
key "${TO_LAST_TILE[@]}"
shot 09b-last-tile
key DPAD_CENTER
sleep 2
shot 09c-hidden-shown
key BACK BACK BACK
on_home "Back from All apps stays on Home"

# Rearranging: long-press OK on the second dock tile, nudge it right, then back left, and drop (order ends unchanged).
# Screenshots 10a-10c show the move mode, the tile one step right, and the dropped result.
fresh
key DPAD_RIGHT
a shell input keyevent --longpress KEYCODE_DPAD_CENTER
sleep 1.5
shot 10a-move-mode
key DPAD_RIGHT
shot 10b-moved-right
key DPAD_LEFT DPAD_CENTER
shot 10c-dropped
on_home "Move mode drops back to Home"

# Hidden-apps toggle keeps focus: 11 should still outline the toggle tile ("Hide them again")
fresh
key "${TO_LAST_TILE[@]}"
key DPAD_CENTER
sleep 3
shot 11-toggle-keeps-focus
key BACK BACK BACK

echo
echo "$pass passed, $fail failed. Screenshots: $OUT"
[ "$fail" -eq 0 ]
