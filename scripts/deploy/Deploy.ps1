param([switch]$CheckOnly, [switch]$NonInteractive)
. (Join-Path $PSScriptRoot 'Common.ps1')
try {
    $config = Read-DeploySettings
    if ($config.JavaHome) { $env:JAVA_HOME = $config.JavaHome }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
    $version = Get-CheckedOutput $java @('--version')
    if ($version -notmatch '(openjdk|java) 21[.\s-]') { throw 'Java 21 is required. Set JavaHome in Configure.cmd.' }
    Connect-DeployTarget $config -NonInteractive:$NonInteractive
    Invoke-Remote 'sudo -n docker version --format "{{.Server.Version}}"'
    Invoke-Remote 'sudo -n docker compose version'
    $status = Get-ProductionStatus ([bool]$config.RequireLoginUrls)
    Write-Host "Production core settings ready: $($status.core_ready)"
    Write-Host "HTTPS callback URLs ready: $($status.login_urls_ready)"
    Write-Host "OpenAI enabled: $($status.openai_enabled); key/model configured: $($status.openai_credentials_present)."
    Write-Host "Frontend CORS origins configured: $($status.cors_configured)."
    if (-not $status.login_urls_ready) { Write-Host 'Swagger/API-server checks only: browser Kakao login and frontend integration are still pending.' -ForegroundColor Yellow }
    if ($CheckOnly) {
        Write-Host 'Preflight passed. No code fetched, files uploaded, build run or containers changed.' -ForegroundColor Green
        exit 0
    }

    $repository = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
    Push-Location $repository
    try {
        Write-Host "Fetching the merged branch: origin/$($config.DeployBranch)"
        $refspec = "+refs/heads/$($config.DeployBranch):refs/remotes/origin/$($config.DeployBranch)"
        Invoke-Checked 'git' @('fetch', 'origin', $refspec)
        $revision = Get-CheckedOutput 'git' @('rev-parse', "origin/$($config.DeployBranch)")
        if ($revision -notmatch '^[0-9a-f]{40}$') { throw 'Could not resolve deployment commit.' }
        Write-Host "Deploying commit: $revision"
        $tracked = Get-CheckedOutput 'git' @('ls-tree', '-r', '--name-only', $revision)
        foreach ($file in ($tracked -split "`n")) {
            $leaf = ($file -split '/')[-1]
            if (($leaf -eq '.env' -or $leaf.StartsWith('.env.')) -and $leaf -notmatch '\.example$') {
                throw 'Tracked environment file found in the target commit. Deployment stopped.'
            }
        }
        $work = Join-Path $repository ("build\deploy\{0}-{1}" -f (Get-Date -Format 'yyyyMMdd-HHmmss-fff'), $revision.Substring(0, 12))
        New-Item -ItemType Directory -Path $work | Out-Null
        $archive = Join-Path $work 'source.zip'
        $source = Join-Path $work 'source'
        Invoke-Checked 'git' @('archive', '--format=zip', "--output=$archive", $revision)
        Expand-Archive -LiteralPath $archive -DestinationPath $source
        $properties = Get-Content -LiteralPath (Join-Path $source 'src\main\resources\application-prod.properties') -Raw -Encoding UTF8
        foreach ($property in @('springdoc.api-docs.enabled', 'springdoc.swagger-ui.enabled')) {
            if ($properties -notmatch ('(?m)^\s*' + [regex]::Escape($property) + '\s*=\s*true\s*$')) {
                throw 'The merged commit does not enable production Swagger. Merge the configuration changes first.'
            }
        }
        Write-Host 'Building a clean snapshot of the remote commit. Your working files and local .env are not used.'
        Push-Location $source
        try {
            $tasks = @('bootJar', '--no-daemon', '--console=plain')
            if ($config.RunChecks) { $tasks = @('check') + $tasks }
            Invoke-Checked '.\gradlew.bat' $tasks
        } finally { Pop-Location }
        $upload = Join-Path $work 'upload'
        New-Item -ItemType Directory -Path $upload | Out-Null
        Copy-Item -LiteralPath (Join-Path $source 'build\libs\app.jar') -Destination (Join-Path $upload 'app.jar')
        Copy-Item -LiteralPath (Join-Path $source 'compose.prod.yaml') -Destination $upload
        foreach ($name in @('Dockerfile.prebuilt', 'compose.prebuilt.yaml', 'deploy-remote.sh', 'environment.py')) {
            Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $upload
        }
        # Context allowlist: only the JAR and its runtime Dockerfile enter the image build.
        [IO.File]::WriteAllText((Join-Path $upload '.dockerignore'), "*`n!app.jar`n!Dockerfile.prebuilt`n", (New-Object Text.UTF8Encoding($false)))
        $checksumLines = foreach ($file in (Get-ChildItem -LiteralPath $upload -File)) {
            '{0}  {1}' -f (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant(), $file.Name
        }
        [IO.File]::WriteAllText((Join-Path $upload 'checksums.sha256'), (($checksumLines -join "`n") + "`n"), (New-Object Text.UTF8Encoding($false)))
        $remoteRelease = "/home/ubuntu/sdpcheck/releases/$revision"
        Invoke-Remote "test ! -L /home/ubuntu/sdpcheck && umask 077 && mkdir -p $remoteRelease"
        $files = @(Get-ChildItem -LiteralPath $upload -File | ForEach-Object { $_.FullName })
        Invoke-Checked 'scp' ($script:SshOptions + $files + @("${script:SshTarget}:$remoteRelease/"))
        $required = if ($config.RequireLoginUrls) { '1' } else { '0' }
        Invoke-Remote "bash $remoteRelease/deploy-remote.sh $revision $required"
        Write-Host "Swagger: http://${script:ServerAddress}:8080/swagger-ui/index.html" -ForegroundColor Green
        Write-Host 'This is the direct EC2 address. Use the agreed HTTPS address once it is configured.'
    } finally { Pop-Location }
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    exit 1
}
