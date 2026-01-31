@echo off
echo ================================
echo Copying MainActivity.kt
echo ================================

set SOURCE=C:\Users\sanji\AndroidStudioProjects\ToiToiTour\app\src\main\java\com\example\toitoitour\MainActivity.kt
set TARGET=C:\toitoitour-share\MainActivity.kt

if not exist "%SOURCE%" (
    echo ERROR: Source file not found!
    echo %SOURCE%
    pause
    exit /b 1
)

copy /Y "%SOURCE%" "%TARGET%"

echo.
echo DONE ✅ MainActivity.kt copied to:
echo %TARGET%
echo ================================
pause