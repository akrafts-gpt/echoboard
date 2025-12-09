#!/usr/bin/env sh

# Gradle start up script for UN*X
# Generated manually for minimal setup

APP_HOME=$(cd "$(dirname "$0")" && pwd)
DEFAULT_JVM_OPTS=""

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "Gradle wrapper JAR not present. Please supply gradle-wrapper.jar to use the wrapper." >&2
  exit 1
fi

JAVA_OPTS="${JAVA_OPTS:-}" 

exec "${JAVA_HOME:-}"/bin/java $DEFAULT_JVM_OPTS $JAVA_OPTS -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
