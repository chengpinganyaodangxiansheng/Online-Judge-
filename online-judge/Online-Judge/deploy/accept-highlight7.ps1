param(
    [Parameter(Mandatory=$true)][string]$Maven,
    [switch]$Http
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $projectRoot 'target/acceptance7'
New-Item -ItemType Directory -Force $outputDir | Out-Null
Push-Location $projectRoot
try {
    $config = 'file:' + ((Join-Path $PSScriptRoot 'dev/oj-judge.local.yml') -replace '\\','/')
    $arguments = @('-pl','oj-modules/oj-judge','-am','test','-Djudge.integration=true','-Djudge.benchmark=true',
        '-Dsurefire.failIfNoSpecifiedTests=false',"-Dspring.config.additional-location=$config")
    & $Maven @arguments *> (Join-Path $outputDir 'judge.log')
    if ($LASTEXITCODE -ne 0) { throw 'Judge acceptance failed; see target/acceptance7/judge.log' }
    if ($Http) {
        # HTTP suite requires a separately running Judge against oj_reliability_test.
        $friendConfig = 'file:' + ((Join-Path $PSScriptRoot 'dev/oj-friend.local.yml') -replace '\\','/')
        & $Maven -pl oj-modules/oj-friend -am test '-Djudge.http.integration=true' '-Dtest=JudgeSubmissionHttpIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dspring.config.additional-location=$friendConfig" *> (Join-Path $outputDir 'http.log')
        if ($LASTEXITCODE -ne 0) { throw 'HTTP acceptance failed; see target/acceptance7/http.log' }
    }
} finally { Pop-Location }
