#!/bin/sh
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
ls lib/*.jar >/dev/null 2>&1 || echo "WARNING: no MySQL driver in ./lib - run ./download-driver.sh first."
java -cp "out:lib/*" ai.Main
