@ECHO OFF
SETLOCAL

where mvn >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  mvn %*
  EXIT /B %ERRORLEVEL%
)

ECHO Maven (mvn) wurde nicht gefunden. Bitte Maven installieren oder eine Umgebung mit Maven nutzen.
EXIT /B 1
