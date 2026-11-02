. (Join-Path $PSScriptRoot 'Common.ps1')
try {
    $config = Read-DeploySettings
    Connect-DeployTarget $config
    $domain = 'sdpcheck.duckdns.org'
    $dnsAddresses = @([System.Net.Dns]::GetHostAddresses($domain) | ForEach-Object { $_.IPAddressToString })
    $serverAddresses = @([System.Net.Dns]::GetHostAddresses($script:ServerAddress) | ForEach-Object { $_.IPAddressToString })
    if (-not @($dnsAddresses | Where-Object { $serverAddresses -contains $_ }).Count) {
        throw 'DuckDNS does not point to this EC2. Update its current ip first.'
    }
    Write-Host 'Setting up HTTPS for sdpcheck.duckdns.org. EC2 inbound TCP ports 80 and 443 must be open.'
    $setup = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'https-remote.sh') -Raw -Encoding UTF8
    $setup.Replace("`r`n", "`n") | & ssh @script:SshOptions $script:SshTarget 'bash -s'
    if ($LASTEXITCODE -ne 0) { throw 'HTTPS setup stopped. See the message above.' }
    $url = 'https://sdpcheck.duckdns.org/swagger-ui/index.html'
    Write-Host 'Checking HTTPS (certificate issuance may take a few minutes)...'
    Invoke-Checked 'curl.exe' @('--fail', '--silent', '--show-error', '--location', '--connect-timeout', '5', '--max-time', '15', '--retry', '4', '--retry-delay', '5', '--retry-all-errors', '--output', 'NUL', $url)
    Write-Host "HTTPS and Swagger verified: $url"
    Start-Process $url
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'Check EC2 inbound HTTP 80 / HTTPS 443, DuckDNS IP, and backend deployment. Run HTTPS.cmd again after fixing.'
    exit 1
}
