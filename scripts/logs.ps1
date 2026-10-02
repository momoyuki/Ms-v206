Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
& docker compose logs --follow server
