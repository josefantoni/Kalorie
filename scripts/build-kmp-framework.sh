#!/bin/sh
set -e

MODULE_NAME="$1"

if [ -z "$MODULE_NAME" ]; then
    echo "error: usage: build-kmp-framework.sh <ModuleName>" >&2
    exit 1
fi

if [[ "$(uname -m)" == arm64 ]]
then
    export PATH="/opt/homebrew/bin:$PATH"
fi

if [ -z "$JAVA_HOME" ]; then
    if command -v brew >/dev/null 2>&1 && BREW_JDK_PREFIX="$(brew --prefix openjdk@21 2>/dev/null)"; then
        export JAVA_HOME="$BREW_JDK_PREFIX/libexec/openjdk.jdk/Contents/Home"
    elif JAVA_HOME_CANDIDATE="$(/usr/libexec/java_home -v 21 2>/dev/null)"; then
        export JAVA_HOME="$JAVA_HOME_CANDIDATE"
    else
        echo "error: no JDK 21 found. Install with 'brew install openjdk@21' or set JAVA_HOME manually." >&2
        exit 1
    fi
fi

cd "$SRCROOT/../$MODULE_NAME"
./gradlew "assemble${MODULE_NAME}ReleaseXCFramework"
