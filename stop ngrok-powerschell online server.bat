taskkill /IM ngrok.exe /F
taskkill /IM powershell.exe /F
Get-Process ngrok -ErrorAction SilentlyContinue | Stop-Process -Force
taskkill /IM ngrok.exe /F
taskkill /IM powershell.exe /F