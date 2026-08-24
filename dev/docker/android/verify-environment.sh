#!/usr/bin/env bash
set -Eeuo pipefail

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_file() {
    [[ -f "$1" ]] || fail "required file is missing: $1"
}

require_command() {
    local command_path
    command_path="$(command -v "$1")" || fail "required command is missing: $1"
    [[ -x "${command_path}" ]] || fail "required command is not executable: ${command_path}"
}

: "${ANDROID_SDK_ROOT:?ANDROID_SDK_ROOT is not set}"
: "${ANDROID_COMPILE_SDK:?ANDROID_COMPILE_SDK is not set}"
: "${ANDROID_BUILD_TOOLS_VERSION:?ANDROID_BUILD_TOOLS_VERSION is not set}"
: "${ANDROID_NDK_VERSION:?ANDROID_NDK_VERSION is not set}"
: "${ANDROID_CMDLINE_TOOLS_VERSION:?ANDROID_CMDLINE_TOOLS_VERSION is not set}"
: "${GRADLE_VERSION:?GRADLE_VERSION is not set}"
: "${ANDROID_GRADLE_PLUGIN_VERSION:?ANDROID_GRADLE_PLUGIN_VERSION is not set}"

for tool in java javac gradle sdkmanager adb ndk-build make git; do
    require_command "${tool}"
done

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ "${java_major}" == "17" ]] || fail "expected JDK 17, found: $(java -version 2>&1 | head -n 1)"

gradle_version="$(gradle --version | sed -n 's/^Gradle //p')"
[[ "${gradle_version}" == "${GRADLE_VERSION}" ]] \
    || fail "expected Gradle ${GRADLE_VERSION}, found ${gradle_version:-unknown}"

require_file "${ANDROID_SDK_ROOT}/cmdline-tools/${ANDROID_CMDLINE_TOOLS_VERSION}/source.properties"
require_file "${ANDROID_SDK_ROOT}/platforms/android-${ANDROID_COMPILE_SDK}/package.xml"
require_file "${ANDROID_SDK_ROOT}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}/package.xml"
require_file "${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}/source.properties"

ndk_revision="$(sed -n 's/^Pkg.Revision[[:space:]]*=[[:space:]]*//p' \
    "${ANDROID_SDK_ROOT}/ndk/${ANDROID_NDK_VERSION}/source.properties")"
[[ "${ndk_revision}" == "${ANDROID_NDK_VERSION}" ]] \
    || fail "expected NDK ${ANDROID_NDK_VERSION}, found ${ndk_revision:-unknown}"

project_dir="${1:-}"
if [[ -n "${project_dir}" ]]; then
    project_dir="$(cd "${project_dir}" && pwd)"
    require_file "${project_dir}/app/build.gradle"
    require_file "${project_dir}/build.gradle"
    require_file "${project_dir}/gradle/wrapper/gradle-wrapper.properties"

    grep -Eq "ndkVersion[[:space:]]+['\"]${ANDROID_NDK_VERSION}['\"]" \
        "${project_dir}/app/build.gradle" \
        || fail "project NDK version differs from ${ANDROID_NDK_VERSION}"
    grep -Eq "compileSdk[[:space:]]+${ANDROID_COMPILE_SDK}([^0-9]|$)" \
        "${project_dir}/app/build.gradle" \
        || fail "project compileSdk differs from ${ANDROID_COMPILE_SDK}"
    grep -Fq "com.android.tools.build:gradle:${ANDROID_GRADLE_PLUGIN_VERSION}" \
        "${project_dir}/build.gradle" \
        || fail "project Android Gradle Plugin differs from ${ANDROID_GRADLE_PLUGIN_VERSION}"
    grep -Fq "gradle-${GRADLE_VERSION}-bin.zip" \
        "${project_dir}/gradle/wrapper/gradle-wrapper.properties" \
        || fail "project Gradle Wrapper differs from ${GRADLE_VERSION}"
fi

printf '%-28s %s\n' \
    'Ubuntu' "$(. /etc/os-release && printf '%s' "${VERSION_ID}")" \
    'Java' "$(java -version 2>&1 | head -n 1)" \
    'Android command-line tools' "${ANDROID_CMDLINE_TOOLS_VERSION}" \
    'Android compile SDK' "${ANDROID_COMPILE_SDK}" \
    'Android build tools' "${ANDROID_BUILD_TOOLS_VERSION}" \
    'Android NDK' "${ndk_revision}" \
    'Gradle' "${gradle_version}" \
    'Android Gradle Plugin' "${ANDROID_GRADLE_PLUGIN_VERSION}"

if [[ -n "${project_dir}" ]]; then
    printf '%-28s %s\n' 'Project configuration' 'matched'
fi
