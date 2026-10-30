Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Invoke-Checked {
    param([string]$Command, [string[]]$ArgumentList)
    & $Command @ArgumentList
    if ($LASTEXITCODE -ne 0) { throw "$Command failed (exit $LASTEXITCODE). Stopped." }
}

function Get-CheckedOutput {
    param([string]$Command, [string[]]$ArgumentList)
    $result = & $Command @ArgumentList 2>$null
    if ($LASTEXITCODE -ne 0) { throw "$Command failed. Check credentials, network and configuration." }
    return ($result -join "`n").Trim()
}

function Read-DeploySettings {
    $path = Join-Path $PSScriptRoot 'deploy.settings.local.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Run Configure.cmd first to set the server address and SSH key path.'
    }
    $config = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($name in @('ssh', 'scp', 'ssh-keygen', 'git')) {
        if (-not (Get-Command $name -ErrorAction SilentlyContinue)) { throw "$name is required on this PC." }
    }
    if ($config.SshUser -notmatch '^[a-z_][a-z0-9_-]*$') { throw 'Invalid SSH user.' }
    if (-not (Test-Path -LiteralPath $config.KeyPath -PathType Leaf)) { throw 'SSH private key file not found.' }
    if ($config.DeployBranch -notmatch '^[A-Za-z0-9][A-Za-z0-9_./#-]*$' -or $config.DeployBranch.Contains('..')) {
        throw 'Invalid deployment branch.'
    }
    if ($config.TrustedHostAlias -notmatch '^[A-Za-z0-9.-]+$') { throw 'Set a previously verified SSH host alias in Configure.cmd.' }
    $knownHosts = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.ssh\known_hosts'
    & ssh-keygen -F $config.TrustedHostAlias -f $knownHosts *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Trusted server key not found. First verify the EC2 SSH fingerprint and connect manually; then configure its trusted host alias.'
    }
    return $config
}

function Test-SshPort {
    param([string]$Address)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $pending = $client.BeginConnect($Address, 22, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(3000)) { return $false }
        $client.EndConnect($pending)
        return $true
    } catch { return $false } finally { $client.Dispose() }
}

function Connect-DeployTarget {
    param($Config, [switch]$NonInteractive)
    $address = [string]$Config.ServerAddress
    if ($Config.AwsProfile) {
        if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw 'AWS profile configured, but AWS CLI is not installed.' }
        if ($Config.InstanceId -notmatch '^i-[0-9a-f]+$' -or $Config.Region -notmatch '^[a-z0-9-]+$') {
            throw 'Invalid AWS instance ID or region.'
        }
        $address = Get-CheckedOutput 'aws' @('ec2', 'describe-instances', '--region', $Config.Region, '--profile', $Config.AwsProfile, '--instance-ids', $Config.InstanceId, '--query', 'Reservations[0].Instances[0].PublicIpAddress', '--output', 'text', '--no-cli-pager')
    }
    if ($address -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$' -or $address -eq 'None') { throw 'Start EC2 and set its public address in Configure.cmd.' }
    if (-not (Test-SshPort $address)) {
        if ($NonInteractive) { throw 'SSH port is unreachable. Check EC2 state, current public IP and port 22 rule.' }
        Write-Host 'EC2 may have a new IP after restart. Find Public IPv4 address in the AWS console.'
        $address = Read-Host 'Current EC2 public IP or DNS name'
        if ($address -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$' -or -not (Test-SshPort $address)) {
            throw 'SSH is unreachable. Check the address and the SSH source-IP rule.'
        }
        $Config.ServerAddress = $address
        $Config | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'deploy.settings.local.json') -Encoding UTF8
    }
    # Reuse the key already trusted for this same EC2, even when its public IP changes.
    $script:SshOptions = @('-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes', '-o', "HostKeyAlias=$($Config.TrustedHostAlias)", '-o', 'ConnectTimeout=10', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4', '-i', $Config.KeyPath)
    $script:SshTarget = "$($Config.SshUser)@$address"
    $script:ServerAddress = $address
    Invoke-Checked 'ssh' ($script:SshOptions + @($script:SshTarget, 'true'))
}

function Invoke-Remote {
    param([string]$RemoteCommand)
    Invoke-Checked 'ssh' ($script:SshOptions + @($script:SshTarget, $RemoteCommand))
}

function Get-ProductionStatus {
    param([bool]$RequireLoginUrls)
    $helper = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'environment.py') -Raw -Encoding UTF8
    $required = if ($RequireLoginUrls) { '1' } else { '0' }
    $result = $helper | & ssh @script:SshOptions $script:SshTarget "python3 - status $required" 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Production environment check failed. Run Environment.cmd. No secret values were printed.' }
    return (($result -join "`n") | ConvertFrom-Json)
}
