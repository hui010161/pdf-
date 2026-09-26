@echo off
setlocal
cd /d "%~dp0"
rem ---- keep this file ASCII only; see the note in the build script ----
set LIB=lib\pdfbox-3.0.3.jar;lib\fontbox-3.0.3.jar;lib\pdfbox-io-3.0.3.jar;lib\commons-logging-1.3.4.jar
set CP=build;%LIB%
if exist build\juanzi\App.class goto run
echo Not built yet, compiling...
if not exist build mkdir build
java -Dfile.encoding=UTF-8 tools\GenList.java build\sources.txt src
if errorlevel 1 goto fail
javac -encoding UTF-8 -cp "%LIB%" -d build @build\sources.txt
if errorlevel 1 goto fail
:run
start "" javaw -Dfile.encoding=UTF-8 -cp "%CP%" juanzi.App
exit /b 0
:fail
echo.
echo Build FAILED - install a JDK first (check: javac -version).
pause
exit /b 1