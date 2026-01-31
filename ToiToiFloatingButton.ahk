#NoEnv
#SingleInstance Force
#Persistent
SetBatchLines, -1
SetWorkingDir, %A_ScriptDir%

; ===============================
;  PATHS
; ===============================
copyVbs       := "C:\toitoitour-share\copy_mainactivity_silent.vbs"
stopBat       := "C:\toitoitour-share\stop_server.bat"
startBat      := "C:\toitoitour-share\start_server.bat"
ngrokBat      := "C:\toitoitour-share\server onlinengrok_8099.bat"
stopNgrokBat  := "C:\toitoitour-share\stop ngrok-powerschell online server.bat"

; ===============================
;  SCREEN / TASKBAR POSITION
; ===============================
SysGet, screenW, 78
SysGet, screenH, 79

btnW := 150
btnH := 30
margin := 16
taskbarOffset := 56

posX := screenW - btnW - margin
posY := screenH - btnH - taskbarOffset

; ===============================
;  FLOATING GUI
; ===============================
Gui, +AlwaysOnTop -Caption +ToolWindow +HwndhGui
Gui, Color, 202020
Gui, Font, s10 cFFFFFF, Segoe UI
Gui, Add, Button, x10 y8 w%btnW% h%btnH% gRunAll, UPDATE
Gui, Show, x%posX% y%posY% w%btnW% h48, ToiToi Floating

OnMessage(0x201, "WM_LBUTTONDOWN")
return

; ===============================
;  MAIN ACTION
; ===============================
RunAll:
    ; 1) STOP NGROK (tiho)
    Run, %stopNgrokBat%,, Hide, pidStopNgrok
    Sleep, 600

    ; 2) STOP SERVER (tiho)
    Run, %stopBat%,, Hide, pidStopServer
    Sleep, 600

    ; 3) COPY MainActivity (VBS – silent)
    Run, %copyVbs%,, Hide, pidCopy
    Sleep, 400

    ; 3.5) COPY NGROK RAW LINK (Windows clipboard)
    Clipboard := ""
    Clipboard := "https://ayaan-unhumoured-nondepressingly.ngrok-free.dev/raw`n!Otvori link u pc modu!"
    ClipWait, 1

    ; 4) START SERVER (odmah minimiziran)

    ; 4) START SERVER (odmah minimiziran)
    Run, %startBat%,, Min, pidServer
    Sleep, 900

    ; 5) START NGROK (odmah minimiziran)
    Run, %ngrokBat%,, Min, pidNgrok
    Sleep, 1200

    ; SIGURNO MINIMIZIRAJ SAMO OVE PROZORE
    WinWait, ahk_pid %pidServer%,, 2
    if !ErrorLevel
        WinMinimize, ahk_pid %pidServer%

    WinWait, ahk_pid %pidNgrok%,, 2
    if !ErrorLevel
        WinMinimize, ahk_pid %pidNgrok%

    ToolTip, UPDATED ✅
    SetTimer, RemoveTip, -1500
return

RemoveTip:
    ToolTip
return

; ===============================
;  DRAG WINDOW
; ===============================
WM_LBUTTONDOWN(wParam, lParam, msg, hwnd) {
    PostMessage, 0xA1, 2,,, ahk_id %hwnd%
}

GuiClose:
ExitApp