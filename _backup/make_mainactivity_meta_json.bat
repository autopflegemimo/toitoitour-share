@echo off
setlocal EnableExtensions
cd /d "C:\toitoitour-share"

set "OUT=MainActivity.meta.json"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $u='https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.kt'; $obj=[ordered]@{ ok=$true; filename='MainActivity.kt'; raw_url=$u; updated_utc=(Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ') }; $json=$obj|ConvertTo-Json -Depth 4; [IO.File]::WriteAllText('%OUT%',$json,(New-Object System.Text.UTF8Encoding($false)))"

echo OK: wrote %OUT%