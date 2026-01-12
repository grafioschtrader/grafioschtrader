# ============================================================================
# Script: trigger_remote_update.ps1
# Purpose: Trigger gtupdate.sh on multiple remote Linux computers in parallel
# Usage: .\trigger_remote_update.ps1 [-WaitForCompletion] [-Verbose]
# ============================================================================

param(
    [switch]$WaitForCompletion,  # Wait for all jobs to complete and show results
    [switch]$ShowOutput          # Show output from remote commands
)

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
$SshTimeout = 120  # seconds

# Colors for output
$ColorSuccess = "Green"
$ColorError = "Red"
$ColorInfo = "Cyan"
$ColorWarning = "Yellow"

Write-Host "============================================================================" -ForegroundColor $ColorInfo
Write-Host " Remote Update Trigger - Parallel Execution" -ForegroundColor $ColorInfo
Write-Host " Target hosts: $($RemoteHosts.Count)" -ForegroundColor $ColorInfo
Write-Host " Remote user: $RemoteUser" -ForegroundColor $ColorInfo
Write-Host " Remote script: $RemoteScript" -ForegroundColor $ColorInfo
Write-Host "============================================================================" -ForegroundColor $ColorInfo
Write-Host ""

# Start timestamp
$StartTime = Get-Date

# Create jobs array
$Jobs = @()

foreach ($Host_ in $RemoteHosts) {
    Write-Host "[STARTING] $Host_..." -ForegroundColor $ColorInfo

    # Start SSH command as background job
    $Job = Start-Job -Name "Update_$Host_" -ScriptBlock {
        param($RemoteUser, $Host_, $RemoteScript, $SshTimeout)

        $Result = @{
            Host = $Host_
            Success = $false
            Output = ""
            Error = ""
            Duration = 0
        }

        $JobStart = Get-Date

        try {
            # Execute SSH command with timeout
            # -o BatchMode=yes: Disable password prompts (requires key-based auth)
            # -o ConnectTimeout: Connection timeout
            # -o StrictHostKeyChecking=no: Auto-accept new host keys (remove for stricter security)
            $SshArgs = @(
                "-o", "BatchMode=yes",
                "-o", "ConnectTimeout=10",
                "-o", "StrictHostKeyChecking=accept-new",
                "$RemoteUser@$Host_",
                $RemoteScript
            )

            $Process = Start-Process -FilePath "ssh" -ArgumentList $SshArgs `
                -NoNewWindow -Wait -PassThru `
                -RedirectStandardOutput "$env:TEMP\ssh_out_$Host_.txt" `
                -RedirectStandardError "$env:TEMP\ssh_err_$Host_.txt"

            # Read output files
            if (Test-Path "$env:TEMP\ssh_out_$Host_.txt") {
                $Result.Output = Get-Content "$env:TEMP\ssh_out_$Host_.txt" -Raw
                Remove-Item "$env:TEMP\ssh_out_$Host_.txt" -Force -ErrorAction SilentlyContinue
            }
            if (Test-Path "$env:TEMP\ssh_err_$Host_.txt") {
                $Result.Error = Get-Content "$env:TEMP\ssh_err_$Host_.txt" -Raw
                Remove-Item "$env:TEMP\ssh_err_$Host_.txt" -Force -ErrorAction SilentlyContinue
            }

            $Result.Success = ($Process.ExitCode -eq 0)
        }
        catch {
            $Result.Error = $_.Exception.Message
        }

        $Result.Duration = ((Get-Date) - $JobStart).TotalSeconds
        return $Result
    } -ArgumentList $RemoteUser, $Host_, $RemoteScript, $SshTimeout

    $Jobs += $Job
}

Write-Host ""
Write-Host "[INFO] All $($Jobs.Count) jobs started in parallel." -ForegroundColor $ColorInfo

if ($WaitForCompletion -or $ShowOutput) {
    Write-Host "[INFO] Waiting for jobs to complete..." -ForegroundColor $ColorInfo
    Write-Host ""

    # Wait for all jobs
    $Jobs | Wait-Job | Out-Null

    # Collect results
    $SuccessCount = 0
    $FailCount = 0

    foreach ($Job in $Jobs) {
        $Result = Receive-Job -Job $Job

        if ($Result.Success) {
            Write-Host "[SUCCESS] $($Result.Host) - completed in $([math]::Round($Result.Duration, 1))s" -ForegroundColor $ColorSuccess
            $SuccessCount++
        }
        else {
            Write-Host "[FAILED]  $($Result.Host) - $($Result.Error)" -ForegroundColor $ColorError
            $FailCount++
        }

        if ($ShowOutput -and $Result.Output) {
            Write-Host "          Output: $($Result.Output.Trim())" -ForegroundColor Gray
        }
    }

    # Cleanup jobs
    $Jobs | Remove-Job -Force

    # Summary
    $TotalDuration = ((Get-Date) - $StartTime).TotalSeconds
    Write-Host ""
    Write-Host "============================================================================" -ForegroundColor $ColorInfo
    Write-Host " Summary: $SuccessCount successful, $FailCount failed" -ForegroundColor $(if ($FailCount -eq 0) { $ColorSuccess } else { $ColorWarning })
    Write-Host " Total time: $([math]::Round($TotalDuration, 1)) seconds" -ForegroundColor $ColorInfo
    Write-Host "============================================================================" -ForegroundColor $ColorInfo
}
else {
    Write-Host "[INFO] Jobs running in background. Use -WaitForCompletion to see results." -ForegroundColor $ColorInfo
    Write-Host "[INFO] Check job status with: Get-Job" -ForegroundColor $ColorInfo
}
