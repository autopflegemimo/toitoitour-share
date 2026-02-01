@echo off
setlocal EnableExtensions
cd /d "C:\toitoitour-share"

echo ===== make_mainactivity_json.bat =====
set "IN=MainActivity.kt"
set "OUT=MainActivity.json"

if not exist "%IN%" (
  echo ERROR: %IN% not found in %CD%
  pause
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $in=Join-Path (Get-Location) '%IN%'; $out=Join-Path (Get-Location) '%OUT%'; $c=Get-Content -Raw -Encoding UTF8 $in; $obj=[ordered]@{ ok=$true; filename='%IN%'; content=$c }; $json=$obj | ConvertTo-Json -Depth 6; [IO.File]::WriteAllText($out,$json,(New-Object System.Text.UTF8Encoding($false)))"

if errorlevel 1 (
  echo ERROR: PowerShell failed.
  pause
  exit /b 1
)

echo OK: wrote %OUT%
dir "%OUT%"
echo Press any key to close...
pause >nul