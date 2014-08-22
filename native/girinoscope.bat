@echo off
set SCRIPT_PATH=%~dp0
set SCRIPT_DIR=%SCRIPT_PATH:~0,-1%

set PATH=C:\Program Files (x86)\Arduino\java\bin;%JAVA_HOME%\bin;%PATH%
java -cp "%SCRIPT_DIR%\lib\Girinoscope.jar;%SCRIPT_DIR%\lib\RXTXcomm.jar" ^
-Dgirinoscope.native.path="%SCRIPT_DIR%" ^
org.hihan.girinoscope.ui.UI
