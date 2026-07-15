#!/bin/sh
# Gradle wrapper 启动脚本 (Unix)
# 使用 Android Studio 打开项目会自动生成完整的 wrapper

DIR="$(cd "$(dirname "$0")" && pwd)"
exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
