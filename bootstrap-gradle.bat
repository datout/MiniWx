@echo off
setlocal
set VERSION=9.7.0
set BASE=%~dp0
set CACHE=%BASE%.gradle-bootstrap
set ZIP=%CACHE%\gradle-%VERSION%-bin.zip
set GRADLE_HOME=%CACHE%\gradle-%VERSION%
if not exist "%CACHE%" mkdir "%CACHE%"
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%ZIP%" (
    echo Downloading Gradle %VERSION%...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%ZIP%'"
    if errorlevel 1 exit /b 1
  )
  if exist "%GRADLE_HOME%" rmdir /s /q "%GRADLE_HOME%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%CACHE%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%
