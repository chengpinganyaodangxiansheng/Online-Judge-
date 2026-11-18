param([Parameter(Mandatory=$true)][string]$Maven)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$outputDir = Join-Path $projectRoot 'target/acceptance7'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$worker = $null
Push-Location $projectRoot
try {
    if (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue) {
        throw 'Port 8081 already occupied; use an isolated Judge before running this script.'
    }
    $worker = Start-Process java -WindowStyle Hidden -PassThru -ArgumentList @(
        '-Dfile.encoding=UTF-8','-jar','oj-modules/oj-judge/target/oj-judge-1.0-SNAPSHOT.jar',
        '--spring.cloud.nacos.config.enabled=false','--spring.cloud.nacos.discovery.enabled=false',
        '--spring.config.additional-location=file:deploy/dev/oj-judge.local.yml',
        '--sandbox.docker.name-prefix=oj-reliable-test-jdk'
    ) -RedirectStandardOutput (Join-Path $outputDir 'worker.log') -RedirectStandardError (Join-Path $outputDir 'worker-error.log')
    $deadline = (Get-Date).AddSeconds(120)
    while (-not (Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue)) {
        if ($worker.HasExited -or (Get-Date) -gt $deadline) { throw 'Judge startup failed or timed out' }
        Start-Sleep -Milliseconds 500
    }
    $config = 'file:' + ((Join-Path $PSScriptRoot 'dev/oj-friend.local.yml') -replace '\\','/')
    & $Maven -pl oj-modules/oj-friend -am test '-Djudge.http.integration=true' '-Dtest=JudgeSubmissionHttpIntegrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dspring.config.additional-location=$config" *> (Join-Path $outputDir 'http.log')
    if ($LASTEXITCODE -ne 0) { throw 'HTTP acceptance failed' }
    Copy-Item 'oj-modules/oj-friend/target/surefire-reports/TEST-org.example.friend.JudgeSubmissionHttpIntegrationTest.xml' (Join-Path $outputDir 'http-results.xml')
    Stop-Process -Id $worker.Id
    $worker.WaitForExit()
    $worker = $null
    # Crash case owns both workers and forces termination only of its own process.
    & $Maven -pl oj-modules/oj-friend -am test '-Djudge.http.integration=true' '-Djudge.worker.integration=true' '-Dtest=JudgeSubmissionHttpIntegrationTest#workerCrashAfterClaimRecoversOnNewProcess' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dspring.config.additional-location=$config" *> (Join-Path $outputDir 'worker-crash.log')
    if ($LASTEXITCODE -ne 0) { throw 'Worker crash acceptance failed' }
    Copy-Item 'oj-modules/oj-friend/target/surefire-reports/TEST-org.example.friend.JudgeSubmissionHttpIntegrationTest.xml' (Join-Path $outputDir 'worker-crash-results.xml')
} finally {
    if ($null -ne $worker -and -not $worker.HasExited) { Stop-Process -Id $worker.Id }
    Pop-Location
}
