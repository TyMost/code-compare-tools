$ErrorActionPreference = 'Stop'

$backendPath = 'D:\Coding\code-compare-tools\backend'
$jarPath = Join-Path $backendPath 'target\migratediff-backend-0.0.1-SNAPSHOT.jar'
$baseUri = 'http://localhost:8081'
$taskId = 'doc-rerun-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-8081'

$job = Start-Job -InitializationScript { $ErrorActionPreference = 'Stop' } -ScriptBlock {
    param($backendPath, $jarPath)
    Set-Location $backendPath
    java -jar $jarPath
} -ArgumentList $backendPath, $jarPath

try {
    $ready = $false
    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Milliseconds 500
        try {
            Invoke-WebRequest -Uri ($baseUri + '/api/scan/presets') -UseBasicParsing -TimeoutSec 5 | Out-Null
            $ready = $true
            break
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $ready) {
        throw 'Backend did not become ready within 30 seconds.'
    }

    function Invoke-Api {
        param(
            [string]$Method,
            [string]$Path,
            [object]$Body
        )
        $uri = $baseUri + $Path
        if ($PSBoundParameters.ContainsKey('Body') -and $null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 16
            return Invoke-RestMethod -Method $Method -Uri $uri -Body $json -ContentType 'application/json'
        }
        return Invoke-RestMethod -Method $Method -Uri $uri
    }

    $results = [ordered]@{}
    $results['taskId'] = $taskId

    $scanBody = @{
        taskId = $taskId
        persistResult = $true
        presetName = 'default-og'
    }
    $results['/api/scan/full'] = Invoke-Api -Method 'Post' -Path '/api/scan/full' -Body $scanBody

    $filePaths = @(
        'src/main/java/com/example/migration/billing/SettlementProcessor.java',
        'src/main/java/com/example/migration/customer/CustomerSyncService.java'
    )

    foreach ($file in $filePaths) {
        $detailBody = @{ taskId = $taskId; filePath = $file }
        $results['/api/scan/detail::' + $file] = Invoke-Api -Method 'Post' -Path '/api/scan/detail' -Body $detailBody

        $generateBody = @{ taskId = $taskId; filePath = $file }
        $results['/api/migrate/generate::' + $file] = Invoke-Api -Method 'Post' -Path '/api/migrate/generate' -Body $generateBody
    }

    foreach ($entry in $results.GetEnumerator()) {
        Write-Output ('=== ' + $entry.Key + ' ===')
        if ($entry.Value -is [string]) {
            Write-Output ($entry.Value)
        } else {
            Write-Output (($entry.Value | ConvertTo-Json -Depth 32))
        }
        Write-Output ''
    }
}
finally {
    if ($job -ne $null) {
        Stop-Job -Job $job -Force -ErrorAction SilentlyContinue | Out-Null
        Receive-Job -Job $job -ErrorAction SilentlyContinue | Out-Null
        Remove-Job -Job $job -Force -ErrorAction SilentlyContinue
    }
}
