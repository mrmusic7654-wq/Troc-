#!/bin/bash
set -e

echo "=== Troc APK Builder ==="
echo "Building debug and release APKs..."

# Check if local.properties exists, create if not
if [ ! -f local.properties ]; then
    echo "Creating local.properties..."
    if [ -n "$ANDROID_HOME" ]; then
        echo "sdk.dir=$ANDROID_HOME" > local.properties
    elif [ -n "$ANDROID_SDK_ROOT" ]; then
        echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
    else
        echo "sdk.dir=/opt/android-sdk" > local.properties
    fi
    echo "MISTRAL_API_KEY=" >> local.properties
    echo "GROQ_API_KEY=" >> local.properties
fi

# Make gradlew executable
chmod +x gradlew

echo "Cleaning..."
./gradlew clean --no-daemon || true

echo "Building Debug APK..."
./gradlew :app:assembleDebug --stacktrace --no-daemon

echo "Building Release APK..."
./gradlew :app:assembleRelease --stacktrace --no-daemon || echo "Release build failed (likely signing), debug is available"

echo ""
echo "=== APKs Built ==="
ls -lh app/build/outputs/apk/debug/ || true
ls -lh app/build/outputs/apk/release/ || true

echo ""
echo "Debug APK location: app/build/outputs/apk/debug/app-debug.apk"
echo "To install: adb install app/build/outputs/apk/debug/app-debug.apk"
