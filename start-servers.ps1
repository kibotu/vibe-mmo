# Start Ragnarok Online Servers with cleanup
$ErrorActionPreference = "Stop"

# Array to store process objects
$serverProcesses = @()

# Flag to prevent multiple cleanup executions
$script:cleanupExecuted = $false

# Cleanup function
function Stop-Servers {
    # Prevent multiple executions
    if ($script:cleanupExecuted) {
        return
    }
    $script:cleanupExecuted = $true
    
    Write-Host ""
    Write-Host "Stopping all servers..." -ForegroundColor Yellow
    
    # Track PIDs we've already stopped to avoid duplicates
    $stoppedPids = @{}
    
    # Stop tracked PowerShell processes (the server windows)
    foreach ($proc in $serverProcesses) {
        if (-not $proc.HasExited -and -not $stoppedPids.ContainsKey($proc.Id)) {
            Write-Host "Stopping server window (PID: $($proc.Id))..." -ForegroundColor Cyan
            try {
                # Kill the PowerShell window and its children
                Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue
                $stoppedPids[$proc.Id] = $true
            } catch {
                # Process may have already exited
            }
        }
    }
    
    # Kill processes on server ports (the actual Java server processes)
    # This is the most reliable way to find running servers
    $ports = @(28080, 28081, 28082)
    foreach ($port in $ports) {
        $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
        foreach ($conn in $connections) {
            if ($conn.State -eq "Listen" -and -not $stoppedPids.ContainsKey($conn.OwningProcess)) {
                Write-Host "Stopping process on port $port (PID: $($conn.OwningProcess))..." -ForegroundColor Cyan
                try {
                    # Kill the process and its children
                    $proc = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
                    if ($proc) {
                        Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
                        $stoppedPids[$conn.OwningProcess] = $true
                    }
                } catch {
                    # Process may have already exited
                }
            }
        }
    }
    
    # Only kill Java processes that are actually running servers (listening on ports)
    # Skip this if we already stopped processes via port detection above
    # This prevents killing Gradle build processes that happen to have server names in their command line
    
    Write-Host "All servers stopped." -ForegroundColor Green
}

# Handle Ctrl+C with trap
trap {
    Write-Host ""
    Write-Host "Interrupted!" -ForegroundColor Yellow
    Stop-Servers
    break
}

# Note: We don't register cleanup on PowerShell.Exiting because it can cause duplicate calls
# The finally block and trap handler are sufficient

try {
    Write-Host "Starting Ragnarok Online Servers..." -ForegroundColor Green
    Write-Host "Press Ctrl+C to stop all servers and exit."
    Write-Host ""
    
    # Get the current directory (full path)
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    if (-not $scriptDir) {
        $scriptDir = $PWD.Path
    }
    
    # Function to check if a server is already running on a port
    function Test-ServerRunning {
        param([int]$Port, [string]$ServerName)
        $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue | Where-Object { $_.State -eq "Listen" }
        if ($connections) {
            $pid = $connections[0].OwningProcess
            Write-Host "$ServerName is already running on port $Port (PID: $pid)" -ForegroundColor Yellow
            return $true
        }
        return $false
    }
    
    # Check for existing account and character servers (only ONE allowed)
    Write-Host "Checking for existing servers..." -ForegroundColor Yellow
    $accountRunning = Test-ServerRunning -Port 28080 -ServerName "Account Server"
    $characterRunning = Test-ServerRunning -Port 28081 -ServerName "Character Server"
    
    if ($accountRunning) {
        Write-Host "ERROR: Account Server is already running. Only one instance is allowed." -ForegroundColor Red
        Write-Host "Please stop the existing instance before starting a new one." -ForegroundColor Red
        exit 1
    }
    
    if ($characterRunning) {
        Write-Host "ERROR: Character Server is already running. Only one instance is allowed." -ForegroundColor Red
        Write-Host "Please stop the existing instance before starting a new one." -ForegroundColor Red
        exit 1
    }
    
    # Zone servers can run multiple instances, so we don't check for them here
    Write-Host ""
    
    # Stop all Gradle daemons to prevent file locking conflicts
    Write-Host "Stopping existing Gradle daemons to prevent file locking conflicts..." -ForegroundColor Yellow
    try {
        & "$scriptDir\gradlew.bat" --stop 2>&1 | Out-Null
        Start-Sleep -Seconds 2
    } catch {
        # Ignore errors if gradlew doesn't exist or daemons aren't running
    }
    
    # Pre-build the shared module to avoid concurrent build conflicts
    Write-Host "Pre-building shared module..." -ForegroundColor Yellow
    try {
        & "$scriptDir\gradlew.bat" :shared:build --no-daemon 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Host "Warning: Shared module build had issues, but continuing..." -ForegroundColor Yellow
        }
    } catch {
        Write-Host "Warning: Could not pre-build shared module: $_" -ForegroundColor Yellow
    }
    Write-Host ""
    
    # Set up Gradle home directories for each server to avoid file locking conflicts
    $accountGradleHome = Join-Path $scriptDir "account-server\.gradle-home"
    $characterGradleHome = Join-Path $scriptDir "character-server\.gradle-home"
    $zoneGradleHome = Join-Path $scriptDir "zone-server\.gradle-home"
    
    # Create directories if they don't exist
    New-Item -ItemType Directory -Force -Path $accountGradleHome | Out-Null
    New-Item -ItemType Directory -Force -Path $characterGradleHome | Out-Null
    New-Item -ItemType Directory -Force -Path $zoneGradleHome | Out-Null
    
    # Start Account Server (Port 28080)
    # Using --no-daemon to prevent file locking conflicts on Windows
    Write-Host "Starting Account Server on port 28080..." -ForegroundColor Cyan
    $accountProc = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptDir'; `$env:GRADLE_USER_HOME='$accountGradleHome'; .\gradlew.bat :account-server:run --no-daemon" -WorkingDirectory $scriptDir -WindowStyle Normal -PassThru
    if ($accountProc) {
        $serverProcesses += $accountProc
    }
    
    # Wait longer for account server to finish building before starting character server
    # This prevents concurrent builds of the shared module
    Write-Host "Waiting for Account Server to build and start..." -ForegroundColor Gray
    Start-Sleep -Seconds 10
    
    # Start Character Server (Port 28081)
    # Using --no-daemon to prevent file locking conflicts on Windows
    Write-Host "Starting Character Server on port 28081..." -ForegroundColor Cyan
    $characterProc = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptDir'; `$env:GRADLE_USER_HOME='$characterGradleHome'; .\gradlew.bat :character-server:run --no-daemon" -WorkingDirectory $scriptDir -WindowStyle Normal -PassThru
    if ($characterProc) {
        $serverProcesses += $characterProc
    }
    
    # Wait longer for character server to finish building before starting zone server
    Write-Host "Waiting for Character Server to build and start..." -ForegroundColor Gray
    Start-Sleep -Seconds 10
    
    # Start Zone Server (Port 28082) - Multiple instances allowed
    # Using --no-daemon to prevent file locking conflicts on Windows
    Write-Host "Starting Zone Server on port 28082..." -ForegroundColor Cyan
    Write-Host "Note: You can start additional zone servers with different ports using start-zone-server.ps1" -ForegroundColor Gray
    $zoneProc = Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$scriptDir'; `$env:GRADLE_USER_HOME='$zoneGradleHome'; .\gradlew.bat :zone-server:run --no-daemon" -WorkingDirectory $scriptDir -WindowStyle Normal -PassThru
    if ($zoneProc) {
        $serverProcesses += $zoneProc
    }
    
    Write-Host ""
    Write-Host "Servers are starting in separate windows." -ForegroundColor Green
    Write-Host "- Account Server: Port 28080 (single instance)" -ForegroundColor Gray
    Write-Host "- Character Server: Port 28081 (single instance)" -ForegroundColor Gray
    Write-Host "- Zone Server: Port 28082 (multiple instances allowed)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "Press Ctrl+C to stop all servers and exit."
    Write-Host ""
    
    # Wait for user interrupt - use Read-Host with timeout or simple loop
    try {
        while ($true) {
            Start-Sleep -Seconds 1
        }
    } catch {
        # Ctrl+C will be caught here
        throw
    }
} catch {
    if ($_.Exception.Message -notlike "*interrupted*") {
        Write-Host "Error occurred: $_" -ForegroundColor Red
    }
    # Stop-Servers will be called in finally block
    exit 1
} finally {
    # Only call Stop-Servers if we haven't already (trap handler may have called it)
    if (-not $script:cleanupExecuted) {
        Stop-Servers
    }
}

