@echo off
setlocal
REM Use PowerShell for better Ctrl+C handling - exit immediately on interrupt
REM Pipe 'Y' to auto-answer "Terminate batch job" prompt if it appears
(echo Y) | powershell.exe -ExecutionPolicy Bypass -File "%~dp0start-servers.ps1"
set EXITCODE=%errorlevel%
if %EXITCODE% neq 0 (
    echo.
    echo Running cleanup...
    call "%~dp0stop-servers.bat"
)
exit /b %EXITCODE%

