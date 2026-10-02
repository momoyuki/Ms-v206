param(
    [string]$Root = (Split-Path -Parent $PSScriptRoot),
    [switch]$SkipDockerCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-YamlInteger {
    param(
        [Parameter(Mandatory)] [string]$Path,
        [Parameter(Mandatory)] [string]$Section,
        [Parameter(Mandatory)] [string]$Key
    )

    $inSection = $false
    foreach ($line in Get-Content -LiteralPath $Path) {
        if ($line -match "^$([regex]::Escape($Section))\s*:\s*(?:#.*)?$") {
            $inSection = $true
            continue
        }
        if ($inSection -and $line -match '^\S') { break }
        if ($inSection -and $line -match "^\s+$([regex]::Escape($Key))\s*:\s*(\d+)\s*(?:#.*)?$") {
            return [int]$matches[1]
        }
    }

    throw "config/config.yaml must contain $Section.$Key as a non-negative integer."
}

if (-not $SkipDockerCheck) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required.' }
    & docker compose version | Out-Null
}

$envFile = Join-Path $Root '.env'
$configDirectory = Join-Path $Root 'config'
$configFile = Join-Path $configDirectory 'config.yaml'
$configExample = Join-Path $configDirectory 'config.yaml.example'

if (-not (Test-Path $envFile)) { Copy-Item (Join-Path $Root '.env.example') $envFile }
if (-not (Test-Path $configFile)) {
    New-Item -ItemType Directory -Force -Path $configDirectory | Out-Null
    Copy-Item $configExample $configFile
}

$apiPort = Get-YamlInteger -Path $configFile -Section 'network' -Key 'apiPort'
$loginPort = Get-YamlInteger -Path $configFile -Section 'network' -Key 'loginPort'
$channels = Get-YamlInteger -Path $configFile -Section 'server' -Key 'channels'
if ($apiPort -lt 1 -or $apiPort -gt 65535) { throw 'network.apiPort must be between 1 and 65535.' }
if ($loginPort -lt 1 -or $loginPort -gt 65435) { throw 'network.loginPort must be between 1 and 65435.' }
if ($channels -lt 1 -or $channels -gt 100) { throw 'server.channels must be between 1 and 100.' }

$firstChannelPort = $loginPort + 101
$lastChannelPort = $loginPort + 100 + $channels
if ($lastChannelPort -gt 65535) { throw 'The configured channel ports exceed 65535.' }

$generated = @{
    SERVER_API_PORT = $apiPort
    SERVER_LOGIN_PORT = $loginPort
    CHANNEL_PORT_RANGE = "$firstChannelPort-$lastChannelPort"
}
$preserved = Get-Content -LiteralPath $envFile | Where-Object {
    $_ -notmatch '^(SERVER_API_PORT|SERVER_LOGIN_PORT|CHANNEL_PORT_RANGE)='
}
$updated = @($preserved + ($generated.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" }))
$temporaryEnvFile = "$envFile.tmp"
Set-Content -LiteralPath $temporaryEnvFile -Value $updated
Move-Item -LiteralPath $temporaryEnvFile -Destination $envFile -Force

Write-Host 'Setup complete. Edit .env for database credentials and host ports, edit config/config.yaml for server settings, then run .\scripts\start.ps1.'
