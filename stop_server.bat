@echo off
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :8099') do (
    taskkill /PID %%a /F >nul 2>&1
)
exit