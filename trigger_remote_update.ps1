# ============================================================================
# Script: trigger_remote_update.ps1
# Purpose: Trigger gtupdate.sh on multiple remote Linux computers in parallel
# Usage: .\trigger_remote_update.ps1
# ============================================================================

# Configuration - Remote hosts to update
$RemoteHosts = @(
    "grafioschtrader.com",
    "192.168.100.10",
    "192.168.100.16",
    "192.168.100.86",
    "192.168.100.87"
)

$RemoteUser = "grafioschtrader"
$RemoteScript = "./gtupdate.sh"

Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host " Remote Update Trigger - Parallel Execution" -ForegroundColor Cyan
Write-Host " Target hosts: $($RemoteHosts.Count)" -ForegroundColor Cyan
Write-Host " Remote user: $RemoteUser" -ForegroundColor Cyan
Write-Host " Remote script: $RemoteScript" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

$StartTime = Get-Date

# Start all SSH processes in parallel
$Processes = @()

foreach ($HostName in $RemoteHosts) {
    Write-Host "[STARTING] $HostName..." -ForegroundColor Cyan

    # Start SSH process directly with login shell (loads ~/.profile for Maven PATH)
    $Process = Start-Process -FilePath "ssh" `
        -ArgumentList "-o", "BatchMode=yes", "-o", "ConnectTimeout=10", "$RemoteUser@$HostName", "bash -l -c '$RemoteScript'" `
        -NoNewWindow -PassThru

    $Processes += @{
        Process = $Process
        Host = $HostName
        StartTime = Get-Date
    }
}

Write-Host ""
Write-Host "[INFO] All $($Processes.Count) SSH connections started in parallel." -ForegroundColor Cyan
Write-Host "[INFO] Waiting for completion..." -ForegroundColor Cyan
Write-Host ""

# Wait for all processes to complete
$SuccessCount = 0
$FailCount = 0

foreach ($Item in $Processes) {
    $Item.Process.WaitForExit()
    $Duration = ((Get-Date) - $Item.StartTime).TotalSeconds

    if ($Item.Process.ExitCode -eq 0) {
        Write-Host "[SUCCESS] $($Item.Host) - completed in $([math]::Round($Duration, 1))s" -ForegroundColor Green
        $SuccessCount++
    }
    else {
        Write-Host "[FAILED]  $($Item.Host) - Exit code: $($Item.Process.ExitCode)" -ForegroundColor Red
        $FailCount++
    }
}

$TotalDuration = ((Get-Date) - $StartTime).TotalSeconds
Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host " Summary: $SuccessCount successful, $FailCount failed" -ForegroundColor $(if ($FailCount -eq 0) { "Green" } else { "Yellow" })
Write-Host " Total time: $([math]::Round($TotalDuration, 1)) seconds" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
