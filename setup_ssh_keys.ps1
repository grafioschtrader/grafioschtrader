# ============================================================================
# Script: setup_ssh_keys.ps1
# Purpose: Setup passwordless SSH authentication to remote Linux computers
# Usage: .\setup_ssh_keys.ps1
# ============================================================================

$RemoteHosts = @(
    "192.168.100.16",
    "192.168.100.87"
)

$RemoteUser = "grafioschtrader"
$SshKeyPath = "$env:USERPROFILE\.ssh\id_rsa"

Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host " SSH Key Setup for Passwordless Authentication" -ForegroundColor Cyan
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Check if SSH key exists
if (-not (Test-Path $SshKeyPath)) {
    Write-Host "[STEP 1] Generating SSH key pair..." -ForegroundColor Yellow
    Write-Host "         Press Enter for default options (no passphrase for automation)" -ForegroundColor Gray
    ssh-keygen -t rsa -b 4096 -f $SshKeyPath
}
else {
    Write-Host "[STEP 1] SSH key already exists at $SshKeyPath" -ForegroundColor Green
}

Write-Host ""
Write-Host "[STEP 2] Copying public key to remote hosts..." -ForegroundColor Yellow
Write-Host "         You will be prompted for password for each host." -ForegroundColor Gray
Write-Host ""

foreach ($Host_ in $RemoteHosts) {
    Write-Host "Copying key to $RemoteUser@$Host_..." -ForegroundColor Cyan
    Write-Host "  Enter password for ${RemoteUser}@${Host_}:" -ForegroundColor Yellow

    # Windows-compatible method: pipe public key via type command
    # This properly prompts for password and copies the key
    $PubKeyFile = "$SshKeyPath.pub"

    # Use cmd to pipe the key content to ssh
    $CopyResult = cmd /c "type `"$PubKeyFile`" | ssh $RemoteUser@$Host_ `"mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys`"" 2>&1

    if ($LASTEXITCODE -eq 0) {
        Write-Host "  [OK] Key copied to $Host_" -ForegroundColor Green
    }
    else {
        Write-Host "  [FAILED] Could not copy key to $Host_" -ForegroundColor Red
        Write-Host "  Error: $CopyResult" -ForegroundColor Gray
    }
    Write-Host ""
}

Write-Host ""
Write-Host "[STEP 3] Testing connections..." -ForegroundColor Yellow
Write-Host ""

foreach ($Host_ in $RemoteHosts) {
    Write-Host "Testing $Host_..." -NoNewline
    $Result = ssh -o BatchMode=yes -o ConnectTimeout=5 "$RemoteUser@$Host_" "echo OK" 2>&1

    if ($Result -eq "OK") {
        Write-Host " SUCCESS" -ForegroundColor Green
    }
    else {
        Write-Host " FAILED" -ForegroundColor Red
        Write-Host "  Error: $Result" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "============================================================================" -ForegroundColor Cyan
Write-Host " Setup complete!" -ForegroundColor Green
Write-Host " If any hosts failed, manually run:" -ForegroundColor Gray
Write-Host "   type $env:USERPROFILE\.ssh\id_rsa.pub | ssh $RemoteUser@<hostname> `"mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys`"" -ForegroundColor Gray
Write-Host "============================================================================" -ForegroundColor Cyan
