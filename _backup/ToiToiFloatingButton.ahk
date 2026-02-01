#NoEnv
#SingleInstance Force
#Persistent
SetBatchLines, -1
SetWorkingDir, %A_ScriptDir%

; ===============================
;  PATHS (GitHub flow)
; ===============================
copyVbs    := "C:\toitoitour-share\copy_mainactivity_silent.vbs"
gitPushBat := "C:\toitoitour-share\git_push.bat"

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
;  MAIN ACTION (GitHub update)
; ===============================
RunAll:
    ; 1) COPY MainActivity.kt (VBS – silent)
    RunWait, %copyVbs%,, Hide
    Sleep, 300

    ; 2) GIT add/commit/push (WAIT da završi)
    if !FileExist(gitPushBat) {
        ToolTip, ERROR: git_push.bat not found
        SetTimer, RemoveTip, -2000
        return
    }

    RunWait, %ComSpec% /c ""%gitPushBat%"",, Hide
    Sleep, 200

    ; 3) COPY GitHub RAW link (clipboard)
    Clipboard :=
    Clipboard := "https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.kt"
    ClipWait, 1

    ToolTip, UPDATED ✅ (GitHub)
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