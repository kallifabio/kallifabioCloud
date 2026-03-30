@ECHO OFF
SETLOCAL ENABLEDELAYEDEXPANSION

SET "BASE_DIR=%~dp0"
IF "%BASE_DIR:~-1%"=="\" SET "BASE_DIR=%BASE_DIR:~0,-1%"
SET "WRAPPER_DIR=%BASE_DIR%\.mvn\wrapper"
SET "PROPERTIES_FILE=%WRAPPER_DIR%\maven-wrapper.properties"
SET "DIST_URL="

IF EXIST "%PROPERTIES_FILE%" (
  FOR /F "usebackq tokens=1,* delims==" %%A IN ("%PROPERTIES_FILE%") DO (
    IF /I "%%A"=="distributionUrl" SET "DIST_URL=%%B"
  )
)

IF "%DIST_URL%"=="" SET "DIST_URL=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"

FOR %%F IN ("%DIST_URL%") DO SET "DIST_FILE=%%~nxF"
SET "DIST_NAME=%DIST_FILE:-bin.zip=%"
SET "DIST_PATH=%BASE_DIR%\.mvn\wrapper\dists\%DIST_NAME%"
SET "MAVEN_HOME=%DIST_PATH%\%DIST_NAME%"
SET "MAVEN_BIN=%MAVEN_HOME%\bin\mvn.cmd"
SET "ARCHIVE_FILE=%DIST_PATH%\%DIST_FILE%"

IF NOT EXIST "%MAVEN_BIN%" (
  IF NOT EXIST "%DIST_PATH%" MKDIR "%DIST_PATH%"
  IF NOT EXIST "%ARCHIVE_FILE%" (
    POWERSHELL -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing -Uri '%DIST_URL%' -OutFile '%ARCHIVE_FILE%'"
    IF ERRORLEVEL 1 (
      ECHO Fehler: Maven konnte nicht heruntergeladen werden.
      EXIT /B 1
    )
  )
  POWERSHELL -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '%ARCHIVE_FILE%' -DestinationPath '%DIST_PATH%'"
  IF ERRORLEVEL 1 (
    ECHO Fehler: Maven konnte nicht entpackt werden.
    EXIT /B 1
  )
)

CALL "%MAVEN_BIN%" %*
EXIT /B %ERRORLEVEL%
