@echo off
setlocal
REM Use PowerShell for better Ctrl+C handling
REM Pipe 'Y' to auto-answer "Terminate batch job" prompt if it appears
(echo Y) | powershell.exe -ExecutionPolicy Bypass -File "%~dp0start-client.ps1"
exit /b %errorlevel%

