#!/bin/bash
# Builds queuewall-<version>.jar. Needs a JDK (8+) and these jars:
#   MC_JAR     .minecraft/.fabric/remappedJars/minecraft-1.0.0-0.18.1/client-intermediary.jar
#              (Fabric makes it the first time the 1.0 loader instance starts)
#   MIXIN_JAR  sponge-mixin (net.fabricmc:sponge-mixin on Maven Central)
#   LWJGL_JAR / LWJGL_UTIL_JAR  LWJGL 2.9.0 (org.lwjgl.lwjgl:lwjgl and lwjgl_util)
set -e
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes
javac -proc:none --release 8 -cp "$MC_JAR:$MIXIN_JAR:$LWJGL_JAR:$LWJGL_UTIL_JAR" -d build/classes $(find src -name '*.java')
cp -r resources/* build/classes/
cp LICENSE build/classes/
VERSION=$(grep '"version"' resources/fabric.mod.json | sed 's/.*: "\(.*\)".*/\1/')
grep -q "VERSION = \"$VERSION\"" src/queuewall/QueueWall.java || { echo "QueueWall.VERSION doesn't match fabric.mod.json ($VERSION)"; exit 1; }
(cd build/classes && jar cf ../queuewall-$VERSION.jar .)
echo "built build/queuewall-$VERSION.jar"
