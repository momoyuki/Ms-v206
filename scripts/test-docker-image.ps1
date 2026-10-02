$ErrorActionPreference = 'Stop'

$dockerfilePath = Join-Path $PSScriptRoot '..\Dockerfile'
$dockerfile = Get-Content -LiteralPath $dockerfilePath -Raw
$handlerSourceCopy = 'COPY --from=build /build/src/main/java/net/swordie/ms/handlers ./src/main/java/net/swordie/ms/handlers'

if (-not $dockerfile.Contains($handlerSourceCopy)) {
    throw 'The runtime image must contain handler source files for dynamic handler discovery.'
}

Write-Host 'Docker image handler-source check passed.'
