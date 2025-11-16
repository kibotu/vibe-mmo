@echo off
setlocal
REM Start an additional Zone Server instance with a custom port
REM Usage: start-zone-server.bat [PORT]
REM Example: start-zone-server.bat 8083
REM Pipe 'Y' to auto-answer "Terminate batch job" prompt if it appears
(echo Y) | powershell.exe -ExecutionPolicy Bypass -File "%~dp0start-zone-server.ps1" %*
exit /b %errorlevel%

