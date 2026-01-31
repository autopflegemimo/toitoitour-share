@echo off
for /f "tokens=2 delims=:" %%A in ('ipconfig ^| findstr /c:"IPv4 Address"') do set ip=%%A
set ip=%ip: =%
start http://%ip%:8099/MainActivity.kt