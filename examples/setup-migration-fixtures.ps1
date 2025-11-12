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
$timelineFile = Join-Path $fixtureRoot 'timeline.json'
$timelineMap = @{}
if (Test-Path $timelineFile) {
    $timelineEntries = Get-Content -LiteralPath $timelineFile -Raw | ConvertFrom-Json
    foreach ($entry in @($timelineEntries)) {
        if (-not $entry.repo -or -not $entry.branch) {
            continue
        }
        if (-not $timelineMap.ContainsKey($entry.repo)) {
            $timelineMap[$entry.repo] = @{}
        }
        $timelineMap[$entry.repo][$entry.branch] = $entry.timestamp
    }
}

function Reset-GitCommitDate {
    Remove-Item Env:GIT_AUTHOR_DATE -ErrorAction SilentlyContinue
    Remove-Item Env:GIT_COMMITTER_DATE -ErrorAction SilentlyContinue
}

function Apply-GitCommitDate {
    param(
        [string]$RepoName,
        [string]$BranchName
    )

    if ($timelineMap.ContainsKey($RepoName)) {
        $repoTimeline = $timelineMap[$RepoName]
        if ($repoTimeline.ContainsKey($BranchName) -and [string]::IsNullOrWhiteSpace($repoTimeline[$BranchName]) -eq $false) {
            $timestamp = $repoTimeline[$BranchName]
            $env:GIT_AUTHOR_DATE = $timestamp
            $env:GIT_COMMITTER_DATE = $timestamp
            return
        }
    }
    Reset-GitCommitDate
}

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
    Apply-GitCommitDate -RepoName $repoName -BranchName $baseline
    git -C $targetPath commit -m "seed $repoName $baseline" | Out-Null
    Reset-GitCommitDate

    for ($i = 1; $i -lt $repoBranches.Count; $i++) {
        $branch = $repoBranches[$i]
        $branchFixture = Join-Path $fixtureRoot (Join-Path $repoName $branch)

        git -C $targetPath checkout -b $branch | Out-Null

        Get-ChildItem -LiteralPath $targetPath -Force |
            Where-Object { $_.Name -ne '.git' } |
            Remove-Item -Recurse -Force

        Copy-FixtureContent -FixturePath $branchFixture -Destination $targetPath

        git -C $targetPath add . | Out-Null
        Apply-GitCommitDate -RepoName $repoName -BranchName $branch
        git -C $targetPath commit -m "seed $repoName $branch" | Out-Null
        Reset-GitCommitDate
    }

    git -C $targetPath checkout $baseline | Out-Null
}

Write-Host "Fixture repositories have been prepared under $Root." -ForegroundColor Green
