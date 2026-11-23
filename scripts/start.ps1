param(
    [ValidateSet('start', 'restart', 'stop', 'status', 'logs')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$envFile = Join-Path $root 'backend/.env'
$base = Join-Path $root 'backend/compose.yaml'
$full = Join-Path $root 'backend/compose.full.yaml'
$compose = @('compose', '--env-file', $envFile, '-f', $base, '-f', $full, '--profile', 'app')

function New-RandomHex {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
        return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $rng.Dispose()
    }
}

function Test-KeyValue([string[]]$lines, [string]$name) {
    return @($lines | Where-Object { $_ -match ('^' + [regex]::Escape($name) + '=[^\r\n]+$') }).Count -gt 0
}

& docker compose version | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Instale Docker Desktop com Docker Compose v2.' }
& docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'O Docker Desktop não está em execução.' }

if ($Action -eq 'start') {
    if (-not (Test-Path -LiteralPath $envFile)) {
        Write-Host 'Criando backend/.env com segredos aleatórios locais...'
        $values = @(
            'SRM_DB_USER=srm_local'
            ('SRM_DB_PASSWORD=' + (New-RandomHex))
            ('SRM_JWT_SECRET=' + (New-RandomHex))
        )
        $values | Set-Content -LiteralPath $envFile -Encoding Ascii
    }

    $lines = @(Get-Content -LiteralPath $envFile)
    if (-not (Test-KeyValue $lines 'SRM_DB_USER')) {
        throw 'Configure SRM_DB_USER no backend/.env.'
    }
    if (-not (Test-KeyValue $lines 'SRM_DB_PASSWORD')) {
        throw 'Configure SRM_DB_PASSWORD no backend/.env.'
    }
    if (-not (@($lines | Where-Object { $_ -match '^SRM_JWT_SECRET=' }).Count -gt 0)) {
        Add-Content -LiteralPath $envFile -Value ('SRM_JWT_SECRET=' + (New-RandomHex)) -Encoding Ascii
        $lines = @(Get-Content -LiteralPath $envFile)
    }
    if (-not (Test-KeyValue $lines 'SRM_JWT_SECRET')) {
        throw 'Configure SRM_JWT_SECRET no backend/.env com pelo menos 32 caracteres.'
    }

    & docker @compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar a stack. Consulte: .\scripts\start.ps1 logs' }
    Write-Host ''
    Write-Host 'SRM Credit Engine iniciado.'
    Write-Host 'Frontend: http://localhost:5173/ (porta alterável por SRM_FRONTEND_PORT)'
    Write-Host 'Primeiro MASTER: .\scripts\bootstrap-master.ps1'
} else {
    if (-not (Test-Path -LiteralPath $envFile)) {
        throw 'backend/.env ausente. Execute start primeiro.'
    }
    switch ($Action) {
        'stop' { & docker @compose down }
        'restart' {
            # Reinicia backend/frontend sem afetar PostgreSQL ou volumes existentes.
            # Não recompila imagens nem reaplica variáveis do contêiner.
            & docker @compose restart app frontend
            if ($LASTEXITCODE -eq 0) {
                Write-Host 'Backend e frontend reiniciados, sem reconstruir imagens nem reiniciar o PostgreSQL.'
            }
        }
        'status' { & docker @compose ps }
        'logs' { & docker @compose logs -f --tail=100 }
    }
    if ($LASTEXITCODE -ne 0) { throw ('Falha na operação: ' + $Action) }
}
