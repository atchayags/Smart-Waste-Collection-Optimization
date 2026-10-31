@echo off
if exist out rmdir /s /q out
if exist out-test rmdir /s /q out-test
mkdir out
mkdir out-test
javac -Xlint:all -d out src\marudham\*.java || exit /b 1
javac -Xlint:all -cp out -d out-test test\marudham\*.java || exit /b 1
echo Compiled OK
