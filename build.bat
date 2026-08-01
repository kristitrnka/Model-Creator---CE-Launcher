@echo off
set ROOT=%~dp0
if exist "%ROOT%build" rmdir /s /q "%ROOT%build"
mkdir "%ROOT%build\classes"
mkdir "%ROOT%build\jar\payload"

javac -source 8 -target 8 -encoding UTF-8 -d "%ROOT%build\classes" "%ROOT%src\ce\launcher\Main.java"
if errorlevel 1 exit /b 1
xcopy /e /i /y "%ROOT%build\classes\*" "%ROOT%build\jar\" >nul

if not exist "%ROOT%payload\editor.jar" (
  echo Missing payload\editor.jar
  exit /b 1
)
copy /y "%ROOT%payload\editor.jar" "%ROOT%build\jar\payload\editor.jar" >nul
jar cfm "%ROOT%Model-Creator-CE-Launcher-1.3.6.jar" "%ROOT%MANIFEST.MF" -C "%ROOT%build\jar" .
echo Built: %ROOT%Model-Creator-CE-Launcher-1.3.6.jar
