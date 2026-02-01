@echo off
setlocal
cd /d "C:\toitoitour-share"

REM safety: make sure this is a git repo
if not exist ".git\" (
  echo ERROR: .git not found in C:\toitoitour-share
  exit /b 1
)

git add MainActivity.kt raw.json MainActivity.json >nul 2>&1

REM if nothing changed, exit OK
git diff --cached --quiet
if %errorlevel%==0 (
  echo OK: no changes to push
  exit /b 0
)

git commit -m "auto update" >nul 2>&1
git push origin main
exit /b %errorlevel%