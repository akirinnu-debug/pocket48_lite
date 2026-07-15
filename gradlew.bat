@echo off
REM Gradle wrapper 启动脚本 (Windows)
REM 使用 Android Studio 打开项目会自动生成完整的 wrapper

set DIR=%~dp0
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
