# PowerShell script to unlock and delete locked build directories
# Usage: .\unlock-build.ps1 [module-name]
# Example: .\unlock-build.ps1 shared

param(
    [string]$Module = "shared"
)

Write-Host "Attempting to unlock and delete $Module\build directory..." -ForegroundColor Yellow

# Stop Gradle daemons
Write-Host "`n1. Stopping Gradle daemons..." -ForegroundColor Cyan
& .\gradlew --stop 2>&1 | Out-Null

# Wait a moment for processes to release locks
Start-Sleep -Seconds 2

$buildPath = "$Module\build"
if (-not (Test-Path $buildPath)) {
    Write-Host "`n✓ Build directory doesn't exist. Nothing to clean." -ForegroundColor Green
    exit 0
}

# Try to delete
Write-Host "`n2. Attempting to delete $buildPath..." -ForegroundColor Cyan
try {
    Remove-Item -Path $buildPath -Recurse -Force -ErrorAction Stop
    Write-Host "✓ Successfully deleted $buildPath" -ForegroundColor Green
    exit 0
} catch {
    Write-Host "✗ Failed to delete: $_" -ForegroundColor Red
}

# If deletion failed, try to identify the locking process
Write-Host "`n3. File is locked. Checking for common locking processes..." -ForegroundColor Yellow

$lockedFile = "$buildPath\libs\$Module-jvm.jar"
if (Test-Path $lockedFile) {
    Write-Host "`nLocked file: $lockedFile" -ForegroundColor Yellow
    
    # Check for Java processes
    $javaProcesses = Get-Process | Where-Object { $_.ProcessName -like "*java*" -or $_.ProcessName -like "*gradle*" }
    if ($javaProcesses) {
        Write-Host "`nFound Java/Gradle processes:" -ForegroundColor Yellow
        $javaProcesses | Format-Table ProcessName, Id, Path -AutoSize
        Write-Host "Consider stopping these processes if safe to do so." -ForegroundColor Yellow
    }
    
    Write-Host "`nPossible solutions:" -ForegroundColor Cyan
    Write-Host "  1. Close Cursor/IDE and any file explorers with the project open" -ForegroundColor White
    Write-Host "  2. Close any terminals with current directory in $buildPath" -ForegroundColor White
    Write-Host "  3. Restart your computer if the above don't work" -ForegroundColor White
    Write-Host "  4. Use Process Explorer (Sysinternals) to find what's locking the file" -ForegroundColor White
    Write-Host "`nWorkaround: Skip clean and just build:" -ForegroundColor Yellow
    Write-Host "  .\gradlew build" -ForegroundColor White
}

exit 1

