FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless aapt apksigner zip unzip curl ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /toolchain
RUN curl --fail --location --retry 3 https://dl.google.com/android/repository/platform-35_r02.zip -o platform.zip \
    && unzip -q platform.zip && rm platform.zip \
    && curl --fail --location --retry 3 https://storage.googleapis.com/r8-releases/raw/8.3.37/r8.jar -o r8.jar
WORKDIR /work
RUN apt-get update && apt-get install -y --no-install-recommends zipalign \
    && rm -rf /var/lib/apt/lists/* \
    && curl --fail --location --retry 3 https://dl.google.com/android/repository/platform-28_r06.zip -o /tmp/platform28.zip \
    && unzip -q /tmp/platform28.zip -d /toolchain && rm /tmp/platform28.zip \
    && chmod -R a+rX /toolchain/android-9
ENTRYPOINT ["bash", "build-inside.sh"]
RUN curl --fail --location --retry 3 https://dl.google.com/android/repository/platform-30_r03.zip -o /tmp/platform30.zip \
    && unzip -q /tmp/platform30.zip -d /toolchain && rm /tmp/platform30.zip \
    && chmod -R a+rX /toolchain/android-11 \
    && curl --fail --location https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar -o /toolchain/json.jar
