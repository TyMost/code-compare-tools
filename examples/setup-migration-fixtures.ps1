param(
    [string]$Root = $PSScriptRoot
)

$ErrorActionPreference = 'Stop'

$fixtures = @{
    'o'        = @('o1', 'o2')
    'g'        = @('g1', 'g2')
    'g-extra'  = @('main', 'gauss-rd')
}

$fixtureRoot = Join-Path $Root '_fixtures'

function Copy-FixtureContent {
    param(
        [string]$FixturePath,
        [string]$Destination
    )

    if (!(Test-Path $Destination)) {
        New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    }

    Get-ChildItem -LiteralPath $FixturePath -Force | ForEach-Object {
        $targetPath = Join-Path $Destination $_.Name
        if ($_.PSIsContainer) {
            Copy-Item -LiteralPath $_.FullName -Destination $targetPath -Recurse -Force
        }
        else {
            Copy-Item -LiteralPath $_.FullName -Destination $targetPath -Force
        }
    }
}

foreach ($repoName in $fixtures.Keys) {
    $repoBranches = $fixtures[$repoName]
    $baseline = $repoBranches[0]

    $targetPath = Join-Path $Root $repoName
    if (Test-Path $targetPath) {
        Get-ChildItem -LiteralPath $targetPath -Force | Where-Object { $_.Name -ne '.git' } | Remove-Item -Recurse -Force
    }
    else {
        New-Item -ItemType Directory -Force -Path $targetPath | Out-Null
    }

    $baselineFixture = Join-Path $fixtureRoot (Join-Path $repoName $baseline)
    Copy-FixtureContent -FixturePath $baselineFixture -Destination $targetPath

    git -C $targetPath init -b $baseline | Out-Null
    git -C $targetPath config user.name 'Fixture Bot' | Out-Null
    git -C $targetPath config user.email 'fixtures@example.com' | Out-Null

    git -C $targetPath add . | Out-Null
    git -C $targetPath commit -m "seed $repoName $baseline" | Out-Null

    for ($i = 1; $i -lt $repoBranches.Count; $i++) {
        $branch = $repoBranches[$i]
        $branchFixture = Join-Path $fixtureRoot (Join-Path $repoName $branch)

        git -C $targetPath checkout -b $branch | Out-Null

        Get-ChildItem -LiteralPath $targetPath -Force |
            Where-Object { $_.Name -ne '.git' } |
            Remove-Item -Recurse -Force

        Copy-FixtureContent -FixturePath $branchFixture -Destination $targetPath

        git -C $targetPath add . | Out-Null
        git -C $targetPath commit -m "seed $repoName $branch" | Out-Null
    }

    git -C $targetPath checkout $baseline | Out-Null
}

Write-Host "Fixture repositories have been prepared under $Root." -ForegroundColor Green
