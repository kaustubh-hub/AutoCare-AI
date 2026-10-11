#!/bin/sh
# Compile AutoCare AI (needs JDK 11+).
cd "$(dirname "$0")"
set -e
ls lib/*.jar >/dev/null 2>&1 || echo "NOTE: no MySQL driver in ./lib yet - run ./download-driver.sh before starting the app."
mkdir -p out
javac -encoding UTF-8 -cp "lib/*" -d out src/ai/*.java
echo "Build OK -> run ./run.sh"
