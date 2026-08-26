@echo off
set DIR=%~dp0
java -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar;%DIR%gradle\wrapper\gradle-wrapper-shared.jar" org.gradle.wrapper.GradleWrapperMain %*
