#!/bin/sh
# Builds the battery-log service for app_process: compiled against Android 4.4's android.jar and
# shrunk by R8, with the pinned tools from the SDK.
# usage: service/build.sh <out.jar>
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
CABINWRIGHT_SDK=${CABINWRIGHT_SDK:-$HERE/../sdk}
OUT=$1
JDK=${JAVA_HOME:-$(/usr/libexec/java_home -v 11)}
FT=$(sh "$CABINWRIGHT_SDK/api/tools/fetch-tools.sh")
JAR=$FT/android-19.jar
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir "$WORK/c" "$WORK/out"
find "$HERE/src" -name '*.kt' >"$WORK/kt.txt"
JAVA_HOME=$JDK "$FT/kotlinc/bin/kotlinc" -jvm-target 1.8 -no-jdk -no-reflect -Werror \
    -Xno-param-assertions -Xno-call-assertions -Xno-receiver-assertions \
    -classpath "$JAR" -d "$WORK/c" "@$WORK/kt.txt"
(cd "$WORK/c" && "$JDK/bin/jar" cfM ../classes.jar .)
"$JDK/bin/java" -cp "$FT/r8.jar" com.android.tools.r8.R8 --release --min-api 19 --lib "$JAR" \
    --pg-conf "$CABINWRIGHT_SDK/api/tools/app-rules.pro" --pg-conf "$HERE/rules.pro" \
    --pg-map-output "$WORK/r8.map" --output "$WORK/out" \
    "$WORK/classes.jar" "$FT/kotlinc/lib/kotlin-stdlib.jar"
rm -f "$OUT"
# app_process needs a jar, not a bare .dex
(cd "$WORK/out" && zip -q -X "$OUT" classes.dex)
