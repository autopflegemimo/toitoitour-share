@echo off
setlocal
cd /d "C:\toitoitour-share"

if not exist "MainActivity.kt" (
  echo ERROR: MainActivity.kt not found in C:\toitoitour-share
  exit /b 1
)

node -e "const fs=require('fs'); const data=fs.readFileSync('MainActivity.kt','utf8'); const obj={ok:true,filename:'MainActivity.kt',content:data}; fs.writeFileSync('raw.json', JSON.stringify(obj), 'utf8');"
if errorlevel 1 (
  echo ERROR: Failed generating raw.json (node missing?)
  exit /b 1
)

echo OK: raw.json updated
exit /b 0