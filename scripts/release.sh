#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=env.sh
source "$SCRIPT_DIR/env.sh"

itv_fail() {
  echo "$1" >&2
  exit 1
}

if ! command -v unzip >/dev/null 2>&1; then
  itv_fail 'unzip is required to check APK ZIP integrity.'
fi

ITV_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort | tail -1)"
ITV_APKSIGNER="$ITV_TOOLS/apksigner"
ITV_ZIPALIGN="$ITV_TOOLS/zipalign"
if [[ -x "$ITV_TOOLS/aapt" ]]; then
  ITV_AAPT="$ITV_TOOLS/aapt"
elif [[ -x "$ITV_TOOLS/aapt2" ]]; then
  ITV_AAPT="$ITV_TOOLS/aapt2"
else
  itv_fail "aapt/aapt2 not found in $ITV_TOOLS"
fi
[[ -x "$ITV_APKSIGNER" ]] || itv_fail "apksigner not found in $ITV_TOOLS"
[[ -x "$ITV_ZIPALIGN" ]] || itv_fail "zipalign not found in $ITV_TOOLS"

itv_apk_badging() {
  "$ITV_AAPT" dump badging "$1"
}

itv_apk_version_code() {
  local code
  code="$(itv_apk_badging "$1" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
  printf '%s' "$code"
}

itv_assert_new_release_version() {
  local version_name="$1"
  local version_code="$2"
  local dest prior prior_code

  [[ "$version_name" =~ ^[0-9A-Za-z._-]+$ ]] || itv_fail "Unsafe versionName for release filename: $version_name"
  [[ "$version_code" =~ ^[0-9]+$ ]] || itv_fail "versionCode must be a non-negative integer: $version_code"

  dest="$ITV_ROOT/release/iTV-v${version_name}-release.apk"
  if [[ -e "$dest" ]]; then
    itv_fail "Refusing to overwrite existing release $dest. Bump both versionCode and versionName in app/build.gradle.kts, then publish again."
  fi

  for prior in "$ITV_ROOT/release"/iTV-v*-release.apk; do
    [[ -f "$prior" ]] || continue
    prior_code="$(itv_apk_version_code "$prior")"
    [[ "$prior_code" =~ ^[0-9]+$ ]] || itv_fail "Failed to read numeric versionCode via aapt from $prior"
    if (( 10#$version_code <= 10#$prior_code )); then
      itv_fail "versionCode $version_code is not strictly greater than $prior_code from $(basename "$prior"). Bump both versionCode and versionName in app/build.gradle.kts, then publish again."
    fi
  done
}

ITV_KEYS="${ITV_SIGNING_DIR:-$(cd -- "$ITV_ROOT/.." && pwd)/.itv-signing}"
ITV_KEY_ALIAS="${ITV_KEY_ALIAS:-itv-release}"
ITV_KS="$ITV_KEYS/release.jks"
ITV_PASS="$ITV_KEYS/password"

if [[ ! -d "$ITV_KEYS" ]]; then
  itv_fail "Private signing directory is missing: $ITV_KEYS. Restore the backup or set ITV_SIGNING_DIR."
fi
chmod 700 "$ITV_KEYS"
if [[ ! -f "$ITV_KS" ]]; then
  itv_fail "Release keystore is missing: $ITV_KS. Restore the private signing directory from backup."
fi
if [[ ! -f "$ITV_PASS" ]]; then
  itv_fail "Release password file is missing: $ITV_PASS. Restore the private signing directory from backup."
fi
chmod 600 "$ITV_KS" "$ITV_PASS"

ITV_GRADLE_VERSION_CODE="$(sed -n 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$ITV_ROOT/app/build.gradle.kts" | head -1)"
ITV_GRADLE_VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$ITV_ROOT/app/build.gradle.kts" | head -1)"
[[ -n "$ITV_GRADLE_VERSION_CODE" ]] || itv_fail 'Failed to read versionCode from app/build.gradle.kts.'
[[ -n "$ITV_GRADLE_VERSION_NAME" ]] || itv_fail 'Failed to read versionName from app/build.gradle.kts.'
itv_assert_new_release_version "$ITV_GRADLE_VERSION_NAME" "$ITV_GRADLE_VERSION_CODE"

cd "$ITV_ROOT"
./gradlew --no-daemon :app:assembleRelease "$@"

ITV_UNSIGNED="$ITV_ROOT/app/build/outputs/apk/release/app-release-unsigned.apk"
ITV_SIGNED_BY_GRADLE="$ITV_ROOT/app/build/outputs/apk/release/app-release.apk"
if [[ ! -f "$ITV_UNSIGNED" ]]; then
  if [[ -f "$ITV_SIGNED_BY_GRADLE" ]]; then
    itv_fail 'assembleRelease produced a signed APK. In-module release signing must stay disabled so the output is app-release-unsigned.apk.'
  fi
  itv_fail "Unsigned release APK was not produced at $ITV_UNSIGNED"
fi
if "$ITV_APKSIGNER" verify --min-sdk-version 23 "$ITV_UNSIGNED" >/dev/null 2>&1; then
  itv_fail 'assembleRelease output is already signed. Refusing to use a pre-signed or debug-signed release APK.'
fi

mkdir -p "$ITV_ROOT/release"
ITV_TMP_ALIGNED="$ITV_ROOT/release/.tmp-aligned.apk"
ITV_TMP_SIGNED="$ITV_ROOT/release/.tmp-signed.apk"
ITV_TO_SIGN="$ITV_UNSIGNED"

itv_cleanup() {
  rm -f "${ITV_TMP_ALIGNED:-}" "${ITV_TMP_SIGNED:-}" "${ITV_TMP_SIGNED:-}.idsig"
}
trap itv_cleanup EXIT

if ! "$ITV_ZIPALIGN" -c -p 4 "$ITV_UNSIGNED" >/dev/null 2>&1; then
  "$ITV_ZIPALIGN" -p -f 4 "$ITV_UNSIGNED" "$ITV_TMP_ALIGNED"
  ITV_TO_SIGN="$ITV_TMP_ALIGNED"
fi

"$ITV_APKSIGNER" sign \
  --ks "$ITV_KS" \
  --ks-key-alias "$ITV_KEY_ALIAS" \
  --ks-pass "file:$ITV_PASS" \
  --v1-signing-enabled true \
  --v2-signing-enabled true \
  --v3-signing-enabled true \
  --v4-signing-enabled false \
  --min-sdk-version 23 \
  --out "$ITV_TMP_SIGNED" \
  "$ITV_TO_SIGN"

"$ITV_ZIPALIGN" -c -p 4 "$ITV_TMP_SIGNED" >/dev/null \
  || itv_fail 'zipalign verification failed for the signed APK.'

unzip -tqq "$ITV_TMP_SIGNED" >/dev/null \
  || itv_fail 'ZIP integrity check failed for the signed APK.'

ITV_VERIFY="$("$ITV_APKSIGNER" verify --verbose --min-sdk-version 23 "$ITV_TMP_SIGNED")"
printf '%s\n' "$ITV_VERIFY" | grep -E 'Verified using v2 scheme \(APK Signature Scheme v2\): true' >/dev/null \
  || itv_fail 'APK is missing a verified v2 signature.'
printf '%s\n' "$ITV_VERIFY" | grep -E 'Verified using v3 scheme \(APK Signature Scheme v3\): true' >/dev/null \
  || itv_fail 'APK is missing a verified v3 signature.'

ITV_BADGING="$(itv_apk_badging "$ITV_TMP_SIGNED")"

ITV_PACKAGE="$(printf '%s\n' "$ITV_BADGING" | sed -n "s/^package: name='\([^']*\)'.*/\1/p" | head -1)"
ITV_VERSION_CODE="$(printf '%s\n' "$ITV_BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" | head -1)"
ITV_VERSION_NAME="$(printf '%s\n' "$ITV_BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
ITV_MIN_SDK="$(printf '%s\n' "$ITV_BADGING" | sed -n "s/^sdkVersion:'\([^']*\)'.*/\1/p" | head -1)"
ITV_TARGET_SDK="$(printf '%s\n' "$ITV_BADGING" | sed -n "s/^targetSdkVersion:'\([^']*\)'.*/\1/p" | head -1)"

[[ -n "$ITV_PACKAGE" ]] || itv_fail 'Failed to read package name from APK metadata.'
[[ -n "$ITV_VERSION_CODE" ]] || itv_fail 'Failed to read versionCode from APK metadata.'
[[ -n "$ITV_VERSION_NAME" ]] || itv_fail 'Failed to read versionName from APK metadata.'
[[ -n "$ITV_MIN_SDK" ]] || itv_fail 'Failed to read minSdk from APK metadata.'
[[ -n "$ITV_TARGET_SDK" ]] || itv_fail 'Failed to read targetSdk from APK metadata.'
[[ "$ITV_PACKAGE" == "app.itv.prototype" ]] || itv_fail "Unexpected package name in APK metadata: $ITV_PACKAGE"
[[ "$ITV_VERSION_NAME" =~ ^[0-9A-Za-z._-]+$ ]] || itv_fail "Unsafe versionName for release filename: $ITV_VERSION_NAME"
[[ "$ITV_VERSION_CODE" == "$ITV_GRADLE_VERSION_CODE" ]] \
  || itv_fail "APK versionCode $ITV_VERSION_CODE does not match app/build.gradle.kts ($ITV_GRADLE_VERSION_CODE)."
[[ "$ITV_VERSION_NAME" == "$ITV_GRADLE_VERSION_NAME" ]] \
  || itv_fail "APK versionName $ITV_VERSION_NAME does not match app/build.gradle.kts ($ITV_GRADLE_VERSION_NAME)."
itv_assert_new_release_version "$ITV_VERSION_NAME" "$ITV_VERSION_CODE"

ITV_OUTPUT="$ITV_ROOT/release/iTV-v${ITV_VERSION_NAME}-release.apk"
mv -f "$ITV_TMP_SIGNED" "$ITV_OUTPUT"
ITV_TMP_SIGNED=""

"$ITV_ZIPALIGN" -c -p 4 "$ITV_OUTPUT" >/dev/null \
  || itv_fail 'zipalign verification failed after moving the signed APK.'
unzip -tqq "$ITV_OUTPUT" >/dev/null \
  || itv_fail 'ZIP integrity check failed after moving the signed APK.'
"$ITV_APKSIGNER" verify --verbose --min-sdk-version 23 "$ITV_OUTPUT" >/dev/null \
  || itv_fail 'apksigner verification failed after moving the signed APK.'

ITV_DESKTOP_APK="$HOME/Desktop/iTV-v${ITV_VERSION_NAME}-release.apk"
[[ -d "$HOME/Desktop" && ! -L "$ITV_DESKTOP_APK" && ! -d "$ITV_DESKTOP_APK" ]] \
  || itv_fail 'Desktop destination is unavailable or unsafe.'
cp -p "$ITV_OUTPUT" "$ITV_DESKTOP_APK"
cmp "$ITV_OUTPUT" "$ITV_DESKTOP_APK" \
  || itv_fail 'Desktop APK does not match the project release APK.'

echo "package: $ITV_PACKAGE"
echo "versionCode: $ITV_VERSION_CODE"
echo "versionName: $ITV_VERSION_NAME"
echo "minSdk: $ITV_MIN_SDK"
echo "targetSdk: $ITV_TARGET_SDK"
echo "Signed APK: $ITV_OUTPUT"
echo "Desktop APK: $ITV_DESKTOP_APK"
echo "Back up the private signing directory outside the repository: $ITV_KEYS"
