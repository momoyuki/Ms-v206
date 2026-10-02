Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required.' }
& docker compose version | Out-Null

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
$configDirectory = Join-Path $root 'config'
$configFile = Join-Path $configDirectory 'server.properties'

if (-not (Test-Path $envFile)) { Copy-Item (Join-Path $root '.env.example') $envFile }
if (-not (Test-Path $configFile)) {
    New-Item -ItemType Directory -Force -Path $configDirectory | Out-Null
    Copy-Item (Join-Path $configDirectory 'server.properties.example') $configFile
}

$channelsLine = Select-String -Path $configFile -Pattern '^channels=(\d+)$' | Select-Object -First 1
if (-not $channelsLine) { throw 'config/server.properties must contain channels=<positive integer>.' }
$channels = [int]$channelsLine.Matches[0].Groups[1].Value
if ($channels -lt 1) { throw 'channels must be at least 1.' }
$range = "CHANNEL_PORT_RANGE=8584-$((8584 + $channels) - 1)"
$envLines = Get-Content $envFile | Where-Object { $_ -notmatch '^CHANNEL_PORT_RANGE=' }
Set-Content -Path $envFile -Value @($envLines + $range)

Write-Host 'Setup complete. Edit .env and config/server.properties, then run .\scripts\start.ps1.'
