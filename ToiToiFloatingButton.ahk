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
    ; 0) sanity
    if !FileExist(copyVbs) {
        ToolTip, ERROR: copy_mainactivity_silent.vbs not found
        SetTimer, RemoveTip, -2500
        return
    }
    if !FileExist(gitPushBat) {
        ToolTip, ERROR: git_push.bat not found
        SetTimer, RemoveTip, -2500
        return
    }

    ; 1) COPY MainActivity.kt (VBS – silent, WAIT)
    RunWait, %copyVbs%,, Hide
    if (ErrorLevel != 0) {
        ToolTip, ERROR: copy failed (%ErrorLevel%)
        SetTimer, RemoveTip, -3000
        return
    }

    ; 2) GIT add/commit/push (WAIT)
    RunWait, %ComSpec% /c ""%gitPushBat%"",, Hide
    if (ErrorLevel != 0) {
        ToolTip, ERROR: git_push failed (%ErrorLevel%)
        SetTimer, RemoveTip, -3500
        return
    }
; 3) COPY links (hash link = NO CACHE)
tmp := A_Temp "\toitoi_head.txt"
RunWait, %ComSpec% /c "cd /d C:\toitoitour-share && git rev-parse HEAD > ""%tmp%""",, Hide
FileRead, head, %tmp%
head := RegExReplace(head, "\s+", "")  ; trim

Clipboard =
(
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/%head%/MainActivity.kt
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.kt
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.meta.json
)
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