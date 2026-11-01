#!/bin/bash
# Baut die Soul-Sprachmodell-Engine (llama.cpp) fuer Android arm64.
# Ergebnis: app/lib/arm64-v8a/libvelora_llm.so (schnell, ab ARMv8.2 mit dotprod)
#           app/lib/arm64-v8a/libvelora_llm_base.so (fuer aeltere Handys)
set -e
cd "$(dirname "$0")"
NDK=${ANDROID_NDK:-/opt/android-sdk/ndk/29.0.14206865}
LLAMA=${LLAMA_DIR:-/tmp/llm/llama.cpp}
LLAMA_COMMIT=dde879bc4f954b809892fd0d660515f42536394a
if [ ! -d "$LLAMA" ]; then git clone https://github.com/ggml-org/llama.cpp "$LLAMA"; fi
(cd "$LLAMA" && git checkout -q $LLAMA_COMMIT)
mkdir -p app/lib/arm64-v8a
build() { # name arch
  B=/tmp/llm/build-android-$1
  cmake -S native -B $B -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
    -DLLAMA_DIR=$LLAMA -DLIB_NAME=$1 -DGGML_CPU_ARM_ARCH=$2 \
    -DCMAKE_C_FLAGS="-O3" -DCMAKE_CXX_FLAGS="-O3" >/dev/null
  cmake --build $B --target $1 -j$(nproc)
  cp $B/lib$1.so app/lib/arm64-v8a/
}
build velora_llm armv8.2-a+dotprod+fp16
build velora_llm_base armv8-a
ls -la app/lib/arm64-v8a
