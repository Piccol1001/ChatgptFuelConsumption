#!/usr/bin/env bash
# Repeatable cloud setup. Application source, manifests and lockfiles are untouched.
set -euo pipefail
fuel_tools=/workspace/.tooling
mkdir -p "$fuel_tools"
fuel_download=$(mktemp -d "$fuel_tools/download.XXXXXX")
trap 'rm -rf "$fuel_download"' EXIT

if [[ ! -x "$fuel_tools/jdk-21.0.12.1+1/bin/javac" ]]; then
    fuel_jdk_url='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz'
    curl -fSL --retry 3 "$fuel_jdk_url" -o "$fuel_download/jdk.tar.gz"
    (cd "$fuel_download"; printf '%s\n' 'ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94  jdk.tar.gz' | sha256sum -c -)
    tar -xzf "$fuel_download/jdk.tar.gz" -C "$fuel_tools"
fi
# Trust the platform's managed Java CA store, including its HTTPS proxy CA.
# TLS verification stays enabled. Do not use trust-all flags.
if [[ -r /etc/ssl/certs/java/cacerts ]]; then
    cp /etc/ssl/certs/java/cacerts "$fuel_tools/jdk-21.0.12.1+1/lib/security/cacerts"
fi
if [[ ! -x "$fuel_tools/android-sdk/cmdline-tools/19.0/bin/sdkmanager" ]]; then
    curl -fSL --retry 3 https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip -o "$fuel_download/android.zip"
    # Google's repository manifest publishes this archive's SHA-1 checksum.
    (cd "$fuel_download"; printf '%s\n' '5fdcc763663eefb86a5b8879697aa6088b041e70  android.zip' | sha1sum -c -)
    unzip -q "$fuel_download/android.zip" -d "$fuel_download/sdk"
    mkdir -p "$fuel_tools/android-sdk/cmdline-tools"
    mv "$fuel_download/sdk/cmdline-tools" "$fuel_tools/android-sdk/cmdline-tools/19.0"
fi
cat > "$fuel_tools/fuel-ledger-env.sh" <<'ENV'
export JAVA_HOME='/workspace/.tooling/jdk-21.0.12.1+1'
export ANDROID_HOME=/workspace/.tooling/android-sdk
export ANDROID_USER_HOME=/workspace/.tooling/android-user
export GRADLE_USER_HOME=/workspace/.tooling/gradle-home
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
ENV
source "$fuel_tools/fuel-ledger-env.sh"
mkdir -p "$ANDROID_USER_HOME" "$GRADLE_USER_HOME"
# Java/Gradle do not automatically consume HTTPS_PROXY. Reuse the platform proxy.
# Write only non-secret proxy host/port settings; never copy proxy credentials.
python3 - <<'PY'
import os
import urllib.parse
from pathlib import Path
u = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if u.hostname:
    p = Path(os.environ['GRADLE_USER_HOME']) / 'gradle.properties'
    current = p.read_text() if p.exists() else ''
    wanted = {
        'systemProp.https.proxyHost': u.hostname,
        'systemProp.https.proxyPort': str(u.port or 80),
        'systemProp.http.proxyHost': u.hostname,
        'systemProp.http.proxyPort': str(u.port or 80),
        'systemProp.http.nonProxyHosts': 'localhost|127.*',
        'systemProp.https.nonProxyHosts': 'localhost|127.*',
    }
    lines = [line for line in current.splitlines() if line.split('=', 1)[0] not in wanted]
    lines.extend(f'{key}={value}' for key, value in wanted.items())
    p.write_text('\n'.join(lines) + '\n')
PY
# Avoid a pipe failure from yes exiting with SIGPIPE after the license reader.
fuel_answers="$fuel_download/licenses.txt"
printf 'y\n%.0s' {1..100} > "$fuel_answers"
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --licenses < "$fuel_answers"
"$ANDROID_HOME/cmdline-tools/19.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
cd /workspace/ChatgptFuelConsumption
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
