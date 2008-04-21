@echo off

if "%OS%"=="Windows_NT" @setlocal

rem %~dp0 is expanded pathname of the current script under NT

if "%SATIN_HOME%X"=="X" set SATIN_HOME=%~dp0..

set JAVACLASSPATH=%CLASSPATH%;
for %%i in ("%SATIN_HOME%\lib\*.jar") do call "%SATIN_HOME%\bin\AddToClassPath.bat" %%i

set IBISC_ARGS=
if ""%1""=="""" goto doneStart
set IBISC_ARGS=%IBISC_ARGS% "%1"
shift
goto setupArgs

:doneStart

java -classpath "%JAVACLASSPATH%" -Dlog4j.configuration=file:"%SATIN_HOME%"\log4j.properties ibis.compile.Ibisc %IBISC_ARGS%

if "%OS%"=="Windows_NT" @endlocal

