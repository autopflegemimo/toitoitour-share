#NoEnv
#SingleInstance Force
#Persistent
SetBatchLines, -1
SetWorkingDir, %A_ScriptDir%

copyVbs    := "C:\toitoitour-share\copy_mainactivity_silent.vbs"
gitPushBat := "C:\toitoitour-share\git_push.bat"

SysGet, screenW, 78
SysGet, screenH, 79

btnW := 150
btnH := 30
margin := 16
taskbarOffset := 56

posX := screenW - btnW - margin
posY := screenH - btnH - taskbarOffset

Gui, +AlwaysOnTop -Caption +ToolWindow +HwndhGui
Gui, Color, 202020
Gui, Font, s10 cFFFFFF, Segoe UI
Gui, Add, Button, x10 y8 w%btnW% h%btnH% gRunAll, UPDATE
Gui, Show, x%posX% y%posY% w%btnW% h48, ToiToi Floating

OnMessage(0x201, "WM_LBUTTONDOWN")
return

RunAll:
    if !FileExist(copyVbs) {
        ToolTip, ERROR: copy_mainactivity_silent.vbs missing
        SetTimer, RemoveTip, -2500
        return
    }
    if !FileExist(gitPushBat) {
        ToolTip, ERROR: git_push.bat missing
        SetTimer, RemoveTip, -2500
        return
    }

    ; 1) COPY (WAIT)
    RunWait, %copyVbs%,, Hide
    if (ErrorLevel != 0) {
        ToolTip, ERROR: copy failed (%ErrorLevel%)
        SetTimer, RemoveTip, -3000
        return
    }

    ; 2) GIT PUSH (WAIT)
    RunWait, %ComSpec% /c ""%gitPushBat%"",, Hide
    if (ErrorLevel != 0) {
        ToolTip, ERROR: git_push failed (%ErrorLevel%)
        SetTimer, RemoveTip, -3500
        return
    }

    ; 3) GET HEAD COMMIT (NO-CACHE RAW LINK)
    tmp := A_Temp "\toitoi_head.txt"
    RunWait, %ComSpec% /c "cd /d C:\toitoitour-share && git rev-parse HEAD > ""%tmp%""",, Hide
    FileRead, head, %tmp%
    head := RegExReplace(head, "\s+", "")

    Clipboard =
(
HASH (instant):
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/%head%/MainActivity.kt

STALNI (moze kasniti cache):
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.kt

META JSON:
https://raw.githubusercontent.com/autopflegemimo/toitoitour-share/main/MainActivity.meta.json
)
    ClipWait, 1

    ToolTip, UPDATED ✅ (use HASH link)
    SetTimer, RemoveTip, -1800
return

RemoveTip:
    ToolTip
return

WM_LBUTTONDOWN(wParam, lParam, msg, hwnd) {
    PostMessage, 0xA1, 2,,, ahk_id %hwnd%
}

GuiClose:
ExitApp