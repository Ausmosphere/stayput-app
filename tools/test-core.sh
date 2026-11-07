#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
mkdir -p app/build/core-tests
javac -d app/build/core-tests app/src/main/java/app/stayput/FocusMath.java tests/CoreTests.java
java -cp app/build/core-tests CoreTests
