Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$fixture = Join-Path ([System.IO.Path]::GetTempPath()) ("ms-v206-setup-test-" + [guid]::NewGuid())

try {
    New-Item -ItemType Directory -Path (Join-Path $fixture 'config') -Force | Out-Null
    @'
MYSQL_PASSWORD=keep-this-password
API_PORT=19083
LOGIN_PORT=19084
CHANNEL_PORT_RANGE=1-2
'@ | Set-Content -Path (Join-Path $fixture '.env') -NoNewline
    @'
server:
  channels: 10
network:
  loginPort: 8484
  apiPort: 8483
'@ | Set-Content -Path (Join-Path $fixture 'config/config.yaml') -NoNewline

    & (Join-Path $root 'scripts/setup.ps1') -Root $fixture -SkipDockerCheck

    $values = @{}
    Get-Content -Path (Join-Path $fixture '.env') | ForEach-Object {
        if ($_ -match '^([^=]+)=(.*)$') { $values[$matches[1]] = $matches[2] }
    }

    if ($values['MYSQL_PASSWORD'] -ne 'keep-this-password') { throw 'MYSQL_PASSWORD was not preserved.' }
    if ($values['API_PORT'] -ne '19083') { throw 'API_PORT was not preserved.' }
    if ($values['LOGIN_PORT'] -ne '19084') { throw 'LOGIN_PORT was not preserved.' }
    if ($values['SERVER_API_PORT'] -ne '8483') { throw 'SERVER_API_PORT was not generated from YAML.' }
    if ($values['SERVER_LOGIN_PORT'] -ne '8484') { throw 'SERVER_LOGIN_PORT was not generated from YAML.' }
    if ($values['CHANNEL_PORT_RANGE'] -ne '8585-8594') { throw 'CHANNEL_PORT_RANGE was not generated correctly.' }

    Write-Host 'Setup configuration test passed.'
} finally {
    if (Test-Path $fixture) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
