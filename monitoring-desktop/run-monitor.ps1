$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$envFile = Join-Path $PSScriptRoot 'monitor.env'
if (-not (Test-Path $envFile)) {
    throw 'Copy monitor.env.example to monitor.env and set the secrets first.'
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) {
            [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1].Trim(), 'Process')
        }
    }
}

if (-not (Test-Path 'monitor.properties')) {
    throw 'Copy monitor.properties.example to monitor.properties and review the URLs first.'
}

& java -jar 'target\example-security-monitor.jar' 'monitor.properties'
