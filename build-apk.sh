#!/usr/bin/env bash
# VideoNext —— 一键搭建 Android 构建环境并产出「正式签名」APK
#
# 需要外网。不需要 sudo，不需要 brew，不需要 Android Studio。
# 幂等：重复运行会跳过已装好的部分，直接重新构建。
#
#   用法：  bash build-apk.sh
#   国内网络：dl.google.com 可能不通，先设代理再跑
#           export HTTPS_PROXY=http://127.0.0.1:7890   # 换成你自己的端口

set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
GRADLE_HOME="${GRADLE_HOME:-$HOME/gradle}"

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$1"; }
die()  { printf '\n\033[1;31m!! %s\033[0m\n' "$1" >&2; exit 1; }

# sdkmanager 的代理参数（仅在设置了 HTTPS_PROXY 时才传）
PROXY_ARGS=()
if [ -n "${HTTPS_PROXY:-}" ] || [ -n "${https_proxy:-}" ]; then
  _p="${HTTPS_PROXY:-${https_proxy:-}}"
  _hp="${_p#*://}"; _hp="${_hp%%/*}"
  PROXY_ARGS=(--proxy=http "--proxy_host=${_hp%%:*}" "--proxy_port=${_hp##*:}")
  echo "检测到代理：$_p"
fi

# ---------------------------------------------------------------- 1. JDK
step "1/5 检查 JDK"
[ -x "$JAVA_HOME/bin/javac" ] || die "找不到 JDK 17：$JAVA_HOME
本机已装的是 Homebrew 的 openjdk@17，若路径不同请先：export JAVA_HOME=<你的JDK17路径>"
"$JAVA_HOME/bin/java" -version 2>&1 | head -1

# ------------------------------------------- 2. Android command-line tools
step "2/5 Android 命令行工具 (sdkmanager)"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ]; then
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  cd "$ANDROID_HOME/cmdline-tools"

  ok=""
  for build in 11076708 13114758 12266719; do
    url="https://dl.google.com/android/repository/commandlinetools-mac-${build}_latest.zip"
    echo "尝试下载：$url"
    if curl -fL --retry 3 --retry-all-errors --connect-timeout 20 -o clt.zip "$url"; then
      ok="yes"; break
    fi
    echo "  ↳ 失败，换下一个版本"
  done

  [ -n "$ok" ] || die "命令行工具下载失败。
国内网络下 dl.google.com 通常不可达。请设置代理后重跑：
    export HTTPS_PROXY=http://127.0.0.1:7890   # 换成你自己的代理端口
    bash build-apk.sh"

  rm -rf cmdline-tools latest
  unzip -q -o clt.zip
  rm -f clt.zip
  mv cmdline-tools latest
  echo "已安装到 $ANDROID_HOME/cmdline-tools/latest"
else
  echo "已存在，跳过"
fi

# ------------------------------------------------------------ 3. SDK 组件
step "3/5 安装 platform-tools / platforms;android-34 / build-tools;34.0.0"
# 组件齐了就跳过。sdkmanager 每次都要联网刷新仓库清单，离线或网络抖动时会卡住或失败，
# 而已装好的组件并不需要重新拉取。
if [ -d "$ANDROID_HOME/platforms/android-34" ] \
   && [ -d "$ANDROID_HOME/build-tools/34.0.0" ] \
   && [ -d "$ANDROID_HOME/platform-tools" ]; then
  echo "已存在，跳过"
else
  yes | "$SDKMANAGER" ${PROXY_ARGS[@]+"${PROXY_ARGS[@]}"} --sdk_root="$ANDROID_HOME" --licenses >/dev/null || true
  "$SDKMANAGER" ${PROXY_ARGS[@]+"${PROXY_ARGS[@]}"} --sdk_root="$ANDROID_HOME" \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0"
fi

# --------------------------------------------------------------- 4. Gradle
step "4/5 Gradle 8.7（必须 8.7，Homebrew 的 9.x 与本工程 AGP 8.5.2 不兼容）"
GRADLE="$GRADLE_HOME/gradle-8.7/bin/gradle"

if [ ! -x "$GRADLE" ]; then
  mkdir -p "$GRADLE_HOME"
  cd "$GRADLE_HOME"
  if ! curl -fL --retry 3 --retry-all-errors --connect-timeout 20 -o g.zip \
        "https://services.gradle.org/distributions/gradle-8.7-bin.zip"; then
    echo "官方源失败，改用腾讯镜像"
    curl -fL --retry 3 --retry-all-errors --connect-timeout 20 -o g.zip \
      "https://mirrors.cloud.tencent.com/gradle/gradle-8.7-bin.zip" \
      || die "Gradle 下载失败，请设好代理后重跑。"
  fi
  unzip -q -o g.zip
  rm -f g.zip
else
  echo "已存在，跳过"
fi

# ---------------------------------------------------------------- 5. 构建
step "5/5 构建 release APK"
cd "$PROJECT_DIR"
echo "sdk.dir=$ANDROID_HOME" > local.properties

"$GRADLE" --no-daemon assembleRelease

APK="$PROJECT_DIR/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || die "构建结束但没找到 APK：$APK"

step "完成"
ls -lh "$APK"
echo
echo "签名信息："
"$ANDROID_HOME/build-tools/34.0.0/apksigner" verify --print-certs "$APK"
