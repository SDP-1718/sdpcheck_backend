. (Join-Path $PSScriptRoot 'Common.ps1')
try {
    $path = Join-Path $PSScriptRoot 'deploy.settings.local.json'
    $source = if (Test-Path -LiteralPath $path) { $path } else { Join-Path $PSScriptRoot 'deploy.settings.example.json' }
    $config = Get-Content -LiteralPath $source -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Host 'PC deployment settings. Press Enter to keep the current value. No API secrets belong here.'
    foreach ($entry in @(
        @('ServerAddress', 'Current EC2 public IP / DNS'),
        @('KeyPath', 'Full path to your .pem private key'),
        @('TrustedHostAlias', 'Previously verified EC2 IP / SSH host alias'),
        @('DeployBranch', 'Merged branch to deploy'),
        @('JavaHome', 'Java 21 installation directory (empty uses JAVA_HOME / PATH)'),
        @('InstanceId', 'EC2 instance ID (optional, for AWS CLI address lookup)'),
        @('Region', 'AWS region'),
        @('AwsProfile', 'AWS CLI profile (optional, configure authentication separately)')
    )) {
        $name = $entry[0]
        $value = Read-Host ("{0} [{1}]" -f $entry[1], $config.$name)
        if ($value) { $config.$name = $value.Trim().Trim('"') }
    }
    if (-not $config.TrustedHostAlias) { $config.TrustedHostAlias = $config.ServerAddress }
    $value = Read-Host "Require HTTPS login callback URLs before deployment? y/n [$($config.RequireLoginUrls)]"
    if ($value -match '^[yn]$') { $config.RequireLoginUrls = $value -eq 'y' }
    $config | ConvertTo-Json | Set-Content -LiteralPath $path -Encoding UTF8
    Write-Host 'Saved PC settings. Keep the private key file outside Git.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
