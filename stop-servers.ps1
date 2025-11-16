# Stop all Ragnarok Online Servers
Write-Host "Stopping all Ragnarok Online Servers..." -ForegroundColor Yellow
Write-Host ""

# Get the current directory (full path)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $scriptDir) {
    $scriptDir = $PWD.Path
}

# Stop Gradle daemons first to prevent file locking issues
Write-Host "Stopping Gradle daemons..." -ForegroundColor Cyan
try {
    & "$scriptDir\gradlew.bat" --stop 2>&1 | Out-Null
} catch {
    # Ignore errors if gradlew doesn't exist or daemons aren't running
}

# Kill processes on server ports
$ports = @(28080, 28081, 28082)
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    foreach ($conn in $connections) {
        if ($conn.State -eq "Listen") {
            Write-Host "Stopping process on port $port (PID: $($conn.OwningProcess))..." -ForegroundColor Cyan
            try {
                Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            } catch {
                # Process may have already exited
            }
        }
    }
}

# Kill processes by window title
Get-Process | Where-Object { $_.MainWindowTitle -like "*Account Server*" -or $_.MainWindowTitle -like "*Character Server*" -or $_.MainWindowTitle -like "*Zone Server*" } | ForEach-Object {
    Write-Host "Stopping process: $($_.ProcessName) (PID: $($_.Id))..." -ForegroundColor Cyan
    Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "All servers stopped." -ForegroundColor Green

