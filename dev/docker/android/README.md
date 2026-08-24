# Android development image

This image contains the Linux toolchain required by the current project. The
versions come from the checked-in Gradle configuration rather than floating
`latest` packages:

- Ubuntu 26.04 (official image, pinned by digest)
- OpenJDK 17 runtime for Android Gradle Plugin 8.5.1
- Gradle 8.7, pinned by the official distribution checksum
- Android SDK Platform 34 and Build Tools 34.0.0
- Android NDK 27.0.12077973
- Android Platform Tools for `adb`
- Git, Make, ZIP and host utilities needed by the wrapper and `ndk-build`

The Android Command-line Tools archive is pinned to build `16111833`. It and
the Gradle 8.7 distribution are verified with SHA-256 during the image build.
The image intentionally does not copy the source tree or pre-download Maven
dependencies.

## Build

Run this from the repository root:

```bash
docker build \
  --network host \
  --build-arg DEV_UID="$(id -u)" \
  --build-arg DEV_GID="$(id -g)" \
  --tag moonlight-android-dev:ubuntu-26.04 \
  --file dev/docker/android/Dockerfile \
  dev/docker/android
```

The UID and GID arguments ensure that Gradle outputs written to the mounted
source tree belong to the host user.

If the host uses an HTTP proxy, add these predefined Docker build arguments;
Docker excludes their values from the resulting image history:

```bash
--build-arg HTTP_PROXY --build-arg HTTPS_PROXY --build-arg NO_PROXY
```

## Run

Initialize the native submodule once on the host:

```bash
git submodule update --init --recursive
```

Start a development shell:

```bash
docker run --rm -it \
  --network host \
  --volume "$PWD:/workspace" \
  --volume "${HOME}/.gradle:/home/developer/.gradle" \
  moonlight-android-dev:ubuntu-26.04
```

The image sets `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `ANDROID_NDK_HOME`,
`ANDROID_NDK_ROOT`, `NDK_HOME`, `JAVA_HOME`, and the required `PATH`. A
`local.properties` file is not needed for command-line builds in the container.

Verify that the mounted project still matches the image:

```bash
verify-android-environment /workspace
```

Build and run the unit tests with the preinstalled Gradle 8.7:

```bash
gradle --no-daemon \
  testNonRootDebugUnitTest \
  :optional-stereo3d-api:test \
  assembleNonRootDebug
```

The checked-in `./gradlew` remains authoritative and is verified to reference
the same Gradle 8.7 version. It can also be used when its distribution is
already cached or network access to `services.gradle.org` is available.

For a USB-connected Android device, pass only the required USB device or bus
to `docker run`, then use the included `adb`. Network ADB works directly with
the documented `--network host` mode.
