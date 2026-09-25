#!/usr/bin/env bash
set -euo pipefail

ITV_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export ITV_ROOT

export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

itv_javac_major() {
  local home="$1"
  [[ -x "$home/bin/javac" ]] || return 1
  "$home/bin/javac" -version 2>&1 | sed -n 's/.*javac \([0-9][0-9]*\).*/\1/p'
}

itv_use_jdk17() {
  local home="$1"
  local major
  major="$(itv_javac_major "$home" || true)"
  [[ "$major" == "17" ]] || return 1
  export JAVA_HOME="$home"
}

if ! itv_use_jdk17 "${JAVA_HOME:-}"; then
  unset JAVA_HOME
  for ITV_JAVA in \
    /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
    /opt/homebrew/opt/openjdk@17 \
    "$HOME/Library/Java/JavaVirtualMachines/openjdk-17.jdk/Contents/Home" \
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
  do
    if itv_use_jdk17 "$ITV_JAVA"; then
      break
    fi
  done
fi

if [[ ! -x "${JAVA_HOME:-}/bin/javac" ]]; then
  echo 'A Java 17 JDK is required. Set JAVA_HOME to a JDK 17 install with javac.'
  exit 1
fi

if [[ ! -d "$ANDROID_HOME/platform-tools" ]] || [[ ! -d "$ANDROID_HOME/build-tools" ]]; then
  echo "Android SDK not found. Set ANDROID_HOME (tried: $ANDROID_HOME)."
  exit 1
fi

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
