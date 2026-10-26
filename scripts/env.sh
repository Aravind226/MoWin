#!/usr/bin/env bash
# Source before building or using adb:  . ./scripts/env.sh
#
# The toolchain is self-contained under ~/Android so no system packages or
# root access are required (the dev host has no passwordless sudo).

export JAVA_HOME="${JAVA_HOME:-$HOME/Android/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
