#!/bin/bash
cd "$(dirname "$0")" || exit 1

if [ -z "$ANDROID_NDK_HOME" ]; then
    echo "ERROR: ANDROID_NDK_HOME is not set."
    echo "Please set it before running this script, e.g.:"
    echo "export ANDROID_NDK_HOME=/path/to/android-ndk"
    exit 1
fi

echo "Building xaozora_daemon..."
cd rust/xaozora_daemon || exit 1
cargo ndk -t arm64-v8a build --release

echo "Copying xaozora_daemon to assets..."
mkdir -p ../../assets
cp target/aarch64-linux-android/release/xaozora_daemon ../../assets/xaozora_daemon

echo "Done!"
