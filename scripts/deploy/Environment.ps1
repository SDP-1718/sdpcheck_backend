param([switch]$OpenAIOnly)
. (Join-Path $PSScriptRoot 'Common.ps1')
try {
    $config = Read-DeploySettings
    Connect-DeployTarget $config
    Write-Host 'Update production settings on EC2. Enter = keep the existing value.'
    Write-Host 'DB credentials and JWT keys already exist; leave them unchanged for ordinary deployments.'
    $changes = @{}
    $entries = @(
        @('KAKAO_REDIRECT_URI', 'Backend HTTPS URL ending /login/oauth2/code/kakao', $false),
        @('FRONTEND_AUTH_CALLBACK_URL', 'Frontend HTTPS callback URL', $false),
        @('CORS_ALLOWED_ORIGINS', 'Frontend CORS origin(s), e.g. https://app.example.com (comma-separated, no callback path)', $false),
        @('JWT_REFRESH_COOKIE_SAME_SITE', 'Cookie SameSite: Lax / None / Strict', $false),
        @('KAKAO_CLIENT_ID', 'Kakao REST API key', $true),
        @('KAKAO_CLIENT_SECRET', 'Kakao client secret', $true),
        @('OPENAI_ENABLED', 'Enable actual OpenAI calls: true / false', $false),
        @('OPENAI_MODEL', 'OpenAI model', $false),
        @('OPENAI_API_KEY', 'OpenAI API key', $true)
    )
    if ($OpenAIOnly) {
        Write-Host 'OpenAI setup: use the API project/model approved by the team. Actual API requests are billed to that project.'
        $entries = @($entries | Where-Object { $_[0] -like 'OPENAI_*' })
    }
    foreach ($entry in $entries) {
        $value = ''
        if ($entry[2]) {
            $secure = Read-Host $entry[1] -AsSecureString
            $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
            try { $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
            finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
        } else { $value = Read-Host $entry[1] }
        if ($value) { $changes[$entry[0]] = $value }
    }
    Invoke-Remote 'test -d /home/ubuntu/sdpcheck-config && test ! -L /home/ubuntu/sdpcheck-config && mkdir -p /home/ubuntu/sdpcheck-config/tools'
    Invoke-Checked 'scp' ($script:SshOptions + @((Join-Path $PSScriptRoot 'environment.py'), "${script:SshTarget}:/home/ubuntu/sdpcheck-config/tools/environment.py"))
    $payload = ConvertTo-Json -InputObject $changes -Compress
    $result = $payload | & ssh @script:SshOptions $script:SshTarget 'python3 /home/ubuntu/sdpcheck-config/tools/environment.py update' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Environment update refused. Check URL formats and required fields; secret values were not printed.' }
    $status = Get-ProductionStatus $false
    Write-Host "Saved securely on EC2. Core settings ready: $($status.core_ready); HTTPS callback URLs ready: $($status.login_urls_ready)."
    Write-Host "OpenAI enabled: $($status.openai_enabled); key/model configured: $($status.openai_credentials_present)."
    Write-Host "Frontend CORS origins configured: $($status.cors_configured)."
    Write-Host 'Redeploy / recreate the app after changing its environment. This tool did not start the app.'
} catch { Write-Host $_.Exception.Message -ForegroundColor Red; exit 1 }
