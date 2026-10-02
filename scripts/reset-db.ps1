param([switch]$Force)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $Force) {
    $confirmation = Read-Host 'This permanently deletes all database data. Type DELETE to continue'
    if ($confirmation -ne 'DELETE') { throw 'Database reset cancelled.' }
}

& docker compose down --volumes
