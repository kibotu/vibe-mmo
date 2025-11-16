# Start Ragnarok Online Client with cleanup
$ErrorActionPreference = "Stop"

# Variable to store client process
$clientProcess = $null

# Cleanup function
function Stop-Client {
    Write-Host ""
    Write-Host "Cleaning up client processes..." -ForegroundColor Yellow
    
    if ($clientProcess -and -not $clientProcess.HasExited) {
        Write-Host "Stopping client process (PID: $($clientProcess.Id))..." -ForegroundColor Cyan
        try {
            Stop-Process -Id $clientProcess.Id -Force -ErrorAction SilentlyContinue
        } catch {
            # Process may have already exited
        }
    }
    
    # Kill Gradle daemon processes for game-client
    $gradleProcs = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object {
        try {
            $cmdLine = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
            $cmdLine -like "*game-client*"
        } catch {
            $false
        }
    }
    foreach ($proc in $gradleProcs) {
        Write-Host "Stopping client process (PID: $($proc.Id))..." -ForegroundColor Cyan
        try {
            Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
        } catch {
            # Process may have already exited
        }
    }
}

# Handle Ctrl+C with trap
trap {
    Write-Host ""
    Write-Host "Interrupted!" -ForegroundColor Yellow
    Stop-Client
    break
}

# Register cleanup on script exit
$null = Register-EngineEvent PowerShell.Exiting -Action { Stop-Client } -ErrorAction SilentlyContinue

try {
    Write-Host "Starting Ragnarok Online Client..." -ForegroundColor Green
    Write-Host "Note: Multiple client instances are allowed." -ForegroundColor Gray
    Write-Host "Press Ctrl+C to stop the client."
    Write-Host ""
    
    # Start Game Client - run directly to allow Ctrl+C to work
    & ".\gradlew.bat" ":game-client:run"
    
    Write-Host ""
    Write-Host "Client has exited." -ForegroundColor Green
} catch {
    if ($_.Exception.Message -notlike "*interrupted*") {
        Write-Host "Error occurred: $_" -ForegroundColor Red
    }
    Stop-Client
    exit 1
} finally {
    Stop-Client
}

