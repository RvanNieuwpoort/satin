@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT

if "%SATIN_HOME%X"=="X" set SATIN_HOME=%~dp0..

set IBISC_ARGS=

:setupArgs
if ""%1""=="""" goto doneStart
set IBISC_ARGS=%IBISC_ARGS% "%1"
shift
goto setupArgs

:doneStart

java -classpath "%CLASSPATH%;%SATIN_HOME%\lib\*" -Dlog4j.configuration=file:"%SATIN_HOME%"\log4j.properties ibis.satin.impl.syncrewriter.SyncRewriter %IBISC_ARGS%

if "%OS%"=="Windows_NT" @endlocal

