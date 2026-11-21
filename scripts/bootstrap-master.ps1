$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root 'backend/.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'backend/.env ausente. Inicie a stack com .\scripts\start.ps1.'
}

Write-Host 'O provisionamento exige PowerShell interativo e uma organização já criada.'
Write-Host 'Senha: 12 a 72 bytes UTF-8. Entradas inválidas podem ser corrigidas sem reiniciar.'
Write-Host 'UUID demo: 00000000-0000-4000-8000-000000000001'
Write-Host 'A conta MASTER é única por organização e não pode ser recriada por este comando.'

$compose = @(
    'compose', '--env-file', $envFile,
    '-f', (Join-Path $root 'backend/compose.yaml'),
    '-f', (Join-Path $root 'backend/compose.full.yaml'),
    '--profile', 'app'
)
$arguments = $compose + @(
    'run', '--rm', '-it', '--no-deps', 'app',
    '--spring.main.web-application-type=none',
    '--srm.security.bootstrap-master.enabled=true'
)
& docker @arguments
if ($LASTEXITCODE -ne 0) {
    throw 'Falha no provisionamento MASTER. Consulte os logs e confirme a organização.'
}
