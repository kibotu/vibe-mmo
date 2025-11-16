@echo off
echo Starting Database Viewer Server...
echo.

cd /d %~dp0

REM Stop Gradle daemons to avoid file locking issues
call gradlew --stop >nul 2>&1

REM Start the database viewer server
start "Database Viewer Server" cmd /k "gradlew :db-viewer-server:run --no-daemon"

echo.
echo Database Viewer Server starting on http://localhost:28083
echo.
echo Press any key to exit...
pause >nul

