FROM ubuntu:26.04@sha256:7c274af287d8f66f861d90e6ceab5cc27349b8db1fea54d44fc2bb6442210c8b

LABEL org.opencontainers.image.title="Moonlight Android development environment" \
      org.opencontainers.image.description="Pinned Android SDK/NDK toolchain for moonlight-android-keyboard" \
      org.opencontainers.image.base.name="docker.io/library/ubuntu:26.04"

ARG DEBIAN_FRONTEND=noninteractive
ARG DEV_UID=1000
ARG DEV_GID=1000
ARG ANDROID_CMDLINE_TOOLS_VERSION=16111833
ARG ANDROID_CMDLINE_TOOLS_SHA256=0877a1d048fe4a24efe2eff536ca4223f7adeb58648bb81909d33c446918cfa8

ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 \
    ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_COMPILE_SDK=34 \
    ANDROID_BUILD_TOOLS_VERSION=34.0.0 \
    ANDROID_NDK_VERSION=27.0.12077973 \
    ANDROID_NDK_HOME=/opt/android-sdk/ndk/27.0.12077973 \
    ANDROID_NDK_ROOT=/opt/android-sdk/ndk/27.0.12077973 \
    NDK_HOME=/opt/android-sdk/ndk/27.0.12077973 \
    GRADLE_VERSION=8.7 \
    ANDROID_GRADLE_PLUGIN_VERSION=8.5.1 \
    ANDROID_CMDLINE_TOOLS_VERSION=${ANDROID_CMDLINE_TOOLS_VERSION}

ENV PATH=${JAVA_HOME}/bin:${ANDROID_SDK_ROOT}/cmdline-tools/${ANDROID_CMDLINE_TOOLS_VERSION}/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/build-tools/${ANDROID_BUILD_TOOLS_VERSION}:${ANDROID_NDK_HOME}:${PATH}

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        file \
        git \
        make \
        openjdk-17-jdk-headless \
        openssh-client \
        unzip \
        xz-utils \
        zip \
    && rm -rf /var/lib/apt/lists/*

RUN test "$(dpkg --print-architecture)" = "amd64" \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && curl --fail --location --show-error --silent \
        --retry 5 --retry-delay 2 \
        --output /tmp/android-commandlinetools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${ANDROID_CMDLINE_TOOLS_SHA256}  /tmp/android-commandlinetools.zip" | sha256sum --check --strict \
    && unzip -q /tmp/android-commandlinetools.zip -d /tmp/android-commandlinetools \
    && mv /tmp/android-commandlinetools/cmdline-tools "${ANDROID_SDK_ROOT}/cmdline-tools/${ANDROID_CMDLINE_TOOLS_VERSION}" \
    && ln -s "${ANDROID_CMDLINE_TOOLS_VERSION}" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm -rf /tmp/android-commandlinetools /tmp/android-commandlinetools.zip

RUN set +o pipefail \
    && yes | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null \
    && license_status=${PIPESTATUS[1]} \
    && set -o pipefail \
    && test "${license_status}" -eq 0 \
    && sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --install \
        "platform-tools" \
        "platforms;android-${ANDROID_COMPILE_SDK}" \
        "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
        "ndk;${ANDROID_NDK_VERSION}" \
    && rm -rf /root/.android/cache /root/.android/repositories.cfg /tmp/*

# The current Android CLI installs some host executables with owner-only mode.
# The SDK is immutable at runtime, but every development user must be able to
# read it and execute its tools.
RUN chmod -R a+rX "${ANDROID_SDK_ROOT}"

ARG GRADLE_DIST_SHA256=544c35d6bd849ae8a5ed0bcea39ba677dc40f49df7d1835561582da2009b961d
RUN curl --fail --location --show-error --silent \
        --retry 5 --retry-delay 2 \
        --output /tmp/gradle-bin.zip \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    && echo "${GRADLE_DIST_SHA256}  /tmp/gradle-bin.zip" | sha256sum --check --strict \
    && mkdir -p /opt/gradle \
    && unzip -q /tmp/gradle-bin.zip -d /opt/gradle \
    && rm -f /tmp/gradle-bin.zip \
    && chmod -R a+rX /opt/gradle

ENV GRADLE_HOME=/opt/gradle/gradle-${GRADLE_VERSION}
ENV PATH=${GRADLE_HOME}/bin:${PATH}

RUN existing_user="$(getent passwd "${DEV_UID}" | cut -d: -f1 || true)" \
    && existing_group="$(getent group "${DEV_GID}" | cut -d: -f1 || true)" \
    && if [[ -n "${existing_user}" ]]; then \
           usermod --login developer --home /home/developer --move-home "${existing_user}"; \
       fi \
    && if [[ -n "${existing_group}" ]]; then \
           groupmod --new-name developer "${existing_group}"; \
       else \
           groupadd --gid "${DEV_GID}" developer; \
       fi \
    && if [[ -z "${existing_user}" ]]; then \
           useradd --uid "${DEV_UID}" --gid "${DEV_GID}" \
               --create-home --shell /bin/bash developer; \
       else \
           usermod --gid "${DEV_GID}" developer; \
       fi \
    && usermod --groups "" developer \
    && mkdir -p /workspace /home/developer/.android /home/developer/.gradle \
    && chown -R "${DEV_UID}:${DEV_GID}" \
        /workspace /home/developer/.android /home/developer/.gradle

COPY verify-environment.sh /usr/local/bin/verify-android-environment
RUN chmod 0755 /usr/local/bin/verify-android-environment

USER developer
ENV HOME=/home/developer \
    GRADLE_USER_HOME=/home/developer/.gradle
WORKDIR /workspace

RUN verify-android-environment

CMD ["/bin/bash"]
