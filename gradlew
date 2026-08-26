#!/bin/sh
# Gradle wrapper launcher. The matching wrapper JAR and properties live in gradle/wrapper.
DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec java -classpath "$DIR/gradle/wrapper/gradle-wrapper.jar:$DIR/gradle/wrapper/gradle-wrapper-shared.jar" org.gradle.wrapper.GradleWrapperMain "$@"
