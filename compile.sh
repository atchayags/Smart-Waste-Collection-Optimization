#!/bin/sh
# Compile application and tests (requires a JDK, Java 11+).
set -e
rm -rf out out-test
mkdir -p out out-test
javac -Xlint:all -d out src/marudham/*.java
javac -Xlint:all -cp out -d out-test test/marudham/*.java
echo "Compiled OK -> out/ and out-test/"
