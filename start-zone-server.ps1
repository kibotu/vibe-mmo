# Start an additional Zone Server instance with a custom port
# Usage: .\start-zone-server.ps1 [PORT]
# Example: .\start-zone-server.ps1 28083

param(
    [int]$Port = 28082
)

$ErrorActionPreference = "Stop"

Write-Host "Starting Zone Server on port $Port..." -ForegroundColor Green

# Get the current directory (full path)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) {
    $scriptDir = $PWD.Path
}

# Check if port is already in use
$connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
if ($connections) {
    Write-Host "WARNING: Port $Port is already in use (PID: $($connections[0].OwningProcess))" -ForegroundColor Yellow
    Write-Host "Zone servers can run multiple instances, but each needs a unique port." -ForegroundColor Yellow
    $response = Read-Host "Do you want to stop the existing process and start a new one? (y/N)"
    if ($response -eq "y" -or $response -eq "Y") {
        try {
            Stop-Process -Id $connections[0].OwningProcess -Force -ErrorAction SilentlyContinue
            Start-Sleep -Milliseconds 500
            Write-Host "Stopped existing process." -ForegroundColor Green
        } catch {
            Write-Host "Failed to stop existing process: $_" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "Aborted." -ForegroundColor Yellow
        exit 0
    }
}

# Set up Gradle home directory for zone server
$zoneGradleHome = Join-Path $scriptDir "zone-server\.gradle-home-$Port"
New-Item -ItemType Directory -Force -Path $zoneGradleHome | Out-Null

# Start Zone Server with custom port
# Using --no-daemon to prevent file locking conflicts on Windows
Write-Host "Starting Zone Server instance on port $Port..." -ForegroundColor Cyan
$env:GRADLE_USER_HOME = $zoneGradleHome
$env:PORT = $Port.ToString()

& ".\gradlew.bat" ":zone-server:run" "--no-daemon"

