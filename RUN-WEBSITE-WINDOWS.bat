@echo off
setlocal
cd /d "%~dp0"
title Marudham Waste Collection Website

echo ======================================================
echo  MARUDHAM SMART CITY WASTE COLLECTION OPTIMIZATION
echo ======================================================
echo.

where java >nul 2>nul
if errorlevel 1 (
  echo ERROR: Java was not found.
  echo Install JDK 17 or newer from https://adoptium.net/
  echo Then close and reopen this window and try again.
  pause
  exit /b 1
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo ERROR: Maven was not found.
  echo Install Apache Maven from https://maven.apache.org/download.cgi
  echo Add Maven's bin folder to PATH, then reopen this window.
  pause
  exit /b 1
)

echo Java version:
java -version
echo.
echo Maven version:
mvn -version
echo.
echo BEFORE CONTINUING:
echo 1. Start MySQL in XAMPP or your MySQL service.
echo 2. The database marudham_waste is created automatically.
echo 3. Check src\main\resources\application.properties for your password.
echo.
pause

echo Starting website. Keep this window open.
echo When startup succeeds open http://localhost:8080 (admin / admin123) and http://localhost:8081 (citizens).
echo.
mvn spring-boot:run
if errorlevel 1 (
  echo.
  echo The application did not start. Read the error above.
  echo Common causes: MySQL not running, wrong password, or internet unavailable for Maven downloads.
  pause
  exit /b 1
)
endlocal
