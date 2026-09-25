#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

ITV_SERIAL="${1:?Pass an explicit device or emulator serial}"
ITV_APP_ID="app.itv.prototype"

ITV_APK="$(ls -t "$ITV_ROOT"/release/iTV-v*-release.apk 2>/dev/null | head -1 || true)"
if [[ -z "$ITV_APK" || ! -f "$ITV_APK" ]]; then
  echo 'Run scripts/release.sh first.'
  exit 2
fi

# Never automatically uninstall a differently signed build or erase device data.
adb -s "$ITV_SERIAL" install -r "$ITV_APK"
adb -s "$ITV_SERIAL" shell am start -S -W -n "$ITV_APP_ID/.MainActivity"
echo "Installed $ITV_APK on $ITV_SERIAL"
