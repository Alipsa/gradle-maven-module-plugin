#!/usr/bin/env bash
if command -v jdk17 &> /dev/null; then
  echo "Found jdk17 command, executing it..."
  . jdk17
  export PATH="$JAVA_HOME/bin:$PATH"
fi
./gradlew build
./gradlew publishPlugins --validate-only