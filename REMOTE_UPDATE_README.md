# Remote Update Scripts

Scripts for triggering `gtupdate.sh` on multiple remote Linux computers after git operations.

## Quick Start

### 1. Setup Passwordless SSH (One-time)

Run the setup script to configure SSH key authentication:

```powershell
.\setup_ssh_keys.ps1
```

Or manually on each remote host:

```bash
# On the remote Linux computer as user 'grafioschtrader':
mkdir -p ~/.ssh
chmod 700 ~/.ssh
# Add your Windows public key (~/.ssh/id_rsa.pub) to:
echo "YOUR_PUBLIC_KEY_CONTENT" >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

### 2. Usage Options

#### Option A: Combined Push + Update
```cmd
pushlocal_and_update.bat "Your commit message"
```
This runs `pushlocal.bat` then triggers all remote updates.

#### Option B: Trigger Updates Separately
```cmd
REM Fire-and-forget (parallel, no waiting)
trigger_remote_update.bat

REM Wait for completion
trigger_remote_update.bat wait

REM Wait + show output
trigger_remote_update.bat verbose
```

#### Option C: Use Git Alias (Recommended)
Add to your global git config:

```cmd
git config --global alias.pushall "!git push -u pi master && powershell -ExecutionPolicy Bypass -File C:/SoftwareProjekte/Hugos/grafioschtrader/trigger_remote_update.ps1 -WaitForCompletion"
```

Then use:
```cmd
git add .
git commit -m "message"
git pushall
```

## Configuration

Edit `trigger_remote_update.ps1` to modify:

```powershell
$RemoteHosts = @(
    "grafioschtrader.com",
    "192.168.100.10",
    "192.168.100.16",
    "192.168.100.86",
    "192.168.100.87"
)

$RemoteUser = "grafioschtrader"
$RemoteScript = "./gtupdate.sh"
```

## Troubleshooting

### SSH Connection Failed
```powershell
# Test connection manually
ssh grafioschtrader@192.168.100.10 "echo OK"

# Check SSH key is loaded
ssh-add -l

# Verbose SSH for debugging
ssh -v grafioschtrader@192.168.100.10
```

### Permission Denied
Ensure on remote host:
- `~/.ssh` directory has permissions `700`
- `~/.ssh/authorized_keys` has permissions `600`
- Public key is correctly added to `authorized_keys`

### Script Not Found on Remote
Ensure `gtupdate.sh` exists in the home directory of user `grafioschtrader` and is executable:
```bash
chmod +x ~/gtupdate.sh
```

## Files

| File | Purpose |
|------|---------|
| `trigger_remote_update.ps1` | Main PowerShell script for parallel SSH execution |
| `trigger_remote_update.bat` | CMD wrapper for the PowerShell script |
| `pushlocal_and_update.bat` | Combined git push + remote update |
| `setup_ssh_keys.ps1` | One-time SSH key setup script |
