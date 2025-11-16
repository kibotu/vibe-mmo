@echo off
setlocal
echo Stopping all Ragnarok Online Servers...
echo.

REM Kill processes on ports 28080, 28081, 28082
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":28080" ^| findstr "LISTENING"') do (
    echo Stopping process on port 28080 (PID: %%a)...
    taskkill /PID %%a /F >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":28081" ^| findstr "LISTENING"') do (
    echo Stopping process on port 28081 (PID: %%a)...
    taskkill /PID %%a /F >nul 2>&1
)
for /f "tokens=5" %%a in ('netstat -ano 2^>nul ^| findstr ":28082" ^| findstr "LISTENING"') do (
    echo Stopping process on port 28082 (PID: %%a)...
    taskkill /PID %%a /F >nul 2>&1
)

REM Kill processes by window title
taskkill /FI "WINDOWTITLE eq Account Server*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Character Server*" /F >nul 2>&1
taskkill /FI "WINDOWTITLE eq Zone Server*" /F >nul 2>&1

echo.
echo All servers stopped.
exit /b 0

