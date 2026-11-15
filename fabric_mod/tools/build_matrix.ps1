[CmdletBinding()]
param(
    [string]$OutputDirectory = '',
    [ValidateSet('Fabric', 'Forge', 'NeoForge')][string]$Loader,
    [string]$Minecraft,
    [switch]$AllTargets,
    [switch]$CompileOnly,
    [switch]$SkipTests = $true,
    [switch]$List,
    [string]$Java25Home = $env:DJI_JAVA25_HOME,
    [string]$ProxyUrl = $env:HTTPS_PROXY
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$matrix = Get-Content -Raw (Join-Path $projectRoot 'release\matrix.json') | ConvertFrom-Json
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path (Split-Path $projectRoot -Parent) "mod\V$($matrix.release)"
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$selected = @($matrix.targets | Where-Object {
    (-not $Loader -or $_.loader -eq $Loader) -and
    (-not $Minecraft -or $_.minecraft -eq $Minecraft) -and
    ($AllTargets -or ($Loader -or $Minecraft) -or $_.status -in @('ready', 'compiled'))
})
if ($selected.Count -eq 0) { throw 'No matching build targets.' }
if ($List) {
    $selected | Select-Object loader, minecraft, status, java, project | Format-Table -AutoSize
    return
}
$matrixIds = @($matrix.targets | ForEach-Object { "$($_.loader):$($_.minecraft)" })
if (@($matrixIds | Sort-Object -Unique).Count -ne $matrixIds.Count) { throw 'Duplicate matrix target.' }
foreach ($target in $selected) {
    if (-not $target.project -or -not $target.buildTasks -or -not $target.artifact) {
        throw "$($target.loader) $($target.minecraft) has no platform build configuration."
    }
}

function Find-BuildJava([int]$Major) {
    if ($Major -eq 25 -and $Java25Home) { $candidateRoots = @($Java25Home) }
    else {
        $candidateRoots = @($env:JAVA_HOME,
            [Environment]::GetEnvironmentVariable("JAVA_HOME_${Major}_X64"))
        foreach ($root in @((Join-Path $projectRoot '.cache\toolchains'),
                (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Microsoft'),
                (Join-Path $env:USERPROFILE '.gradle\jdks'))) {
            if (Test-Path -LiteralPath $root) {
                $candidateRoots += @(Get-ChildItem -LiteralPath $root -Directory | ForEach-Object FullName)
            }
        }
    }
    foreach ($candidate in $candidateRoots) {
        if (-not $candidate) { continue }
        $releaseFile = Join-Path $candidate 'release'
        $javaExe = Join-Path $candidate 'bin\java.exe'
        if (-not (Test-Path -LiteralPath $releaseFile) -or -not (Test-Path -LiteralPath $javaExe)) { continue }
        $versionLine = Get-Content -LiteralPath $releaseFile | Where-Object { $_ -match '^JAVA_VERSION=' } | Select-Object -First 1
        if ($versionLine -match ('^JAVA_VERSION="' + $Major + '(\.|"|-)')) { return $candidate }
    }
    throw "JDK $Major is required. Install it or set JAVA_HOME (DJI_JAVA25_HOME for Java 25)."
}

$attempts = @()
$hasFailures = $false
$reportName = if ($CompileOnly) { 'compile-report.json' } else { 'build-report.json' }
$runId = [Guid]::NewGuid().ToString('N')
$logDirectory = Join-Path $OutputDirectory "logs\$runId"
$historyDirectory = Join-Path $OutputDirectory "reports\$runId"
New-Item -ItemType Directory -Force $logDirectory, $historyDirectory | Out-Null
function Save-BuildReport {
    $report = [ordered]@{
        release = $matrix.release
        runId = $runId
        updatedAt = [DateTime]::UtcNow.ToString('o')
        mode = $(if ($CompileOnly) { 'compile' } else { 'release' })
        testsSkipped = [bool]$SkipTests
        selected = @($selected | ForEach-Object { "$($_.minecraft)+$($_.loader)" })
        attempts = @($attempts)
        generated = @($attempts | Where-Object { $_.status -eq 'built' })
        pending = @($matrix.targets | Where-Object { $_.status -notin @('ready', 'compiled') } | ForEach-Object {
            [ordered]@{ minecraft = $_.minecraft; loader = $_.loader; status = $_.status; reason = $_.reason }
        })
    }
    $reportPath = Join-Path $OutputDirectory $reportName
    $temporaryReport = "$reportPath.$runId.tmp"
    $serialized = $report | ConvertTo-Json -Depth 6
    $serialized | Set-Content -Encoding UTF8 -LiteralPath $temporaryReport
    Move-Item -LiteralPath $temporaryReport -Destination $reportPath -Force
    $historyPath = Join-Path $historyDirectory $reportName
    $serialized | Set-Content -Encoding UTF8 -LiteralPath "$historyPath.tmp"
    Move-Item -LiteralPath "$historyPath.tmp" -Destination $historyPath -Force
}
Save-BuildReport
foreach ($target in $selected) {
    $label = "$($target.minecraft)+$($target.loader)"
    $logFile = Join-Path $logDirectory ($label + '.log')
    $started = [DateTime]::UtcNow
    $entry = [ordered]@{ loader = $target.loader; minecraft = $target.minecraft; status = ''; log = "logs/$runId/$label.log"; startedAt = $started.ToString('o') }
    $savedJavaHome = $env:JAVA_HOME
    $savedJavaOptions = $env:JAVA_TOOL_OPTIONS
    $savedUtf8 = $env:PYTHONUTF8
    try {
        $targetRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot $target.project))
        if (-not $targetRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -and $targetRoot -ne $projectRoot) {
            throw 'Platform path leaves the project directory.'
        }
        $gradleJava = if ($target.gradleJava) { [int]$target.gradleJava } else { [int]$target.java }
        $env:JAVA_HOME = Find-BuildJava $gradleJava
        if ($ProxyUrl) {
            $proxy = [Uri]$ProxyUrl
            if (-not $proxy.Host -or $proxy.Port -lt 1 -or $proxy.UserInfo) { throw 'ProxyUrl must be an HTTP proxy URL without embedded credentials.' }
            $env:JAVA_TOOL_OPTIONS = "$savedJavaOptions -Dhttp.proxyHost=$($proxy.Host) -Dhttp.proxyPort=$($proxy.Port) -Dhttps.proxyHost=$($proxy.Host) -Dhttps.proxyPort=$($proxy.Port) -Dhttp.nonProxyHosts=localhost|127.*"
        }
        $wrapper = Join-Path $targetRoot 'gradlew.bat'
        if (-not (Test-Path -LiteralPath $wrapper -PathType Leaf)) { throw "Missing wrapper: $wrapper" }
        # A verified local distribution avoids repeating a large wrapper download.
        $localGradle = if ($target.gradle) { Join-Path $projectRoot ".cache\toolchains\gradle-$($target.gradle)\bin\gradle.bat" } else { '' }
        $gradleCommand = if ($localGradle -and (Test-Path -LiteralPath $localGradle)) { $localGradle } else { $wrapper }
        $jdkPaths = @($env:JAVA_HOME)
        foreach ($jdkMajor in @(8, 16, 17, 21, 25)) {
            $configuredJdk = [Environment]::GetEnvironmentVariable("JAVA_HOME_${jdkMajor}_X64")
            if ($configuredJdk) { $jdkPaths += $configuredJdk }
        }
        foreach ($jdkRoot in @((Join-Path $projectRoot '.cache\toolchains'),
                (Join-Path $env:ProgramFiles 'Eclipse Adoptium'), (Join-Path $env:ProgramFiles 'Microsoft'))) {
            if (Test-Path -LiteralPath $jdkRoot) {
                $jdkPaths += @(Get-ChildItem -LiteralPath $jdkRoot -Directory | Where-Object {
                    Test-Path -LiteralPath (Join-Path $_.FullName 'bin\java.exe')
                } | ForEach-Object FullName)
            }
        }
        $toolchainArgument = '-Porg.gradle.java.installations.paths=' + (($jdkPaths | Select-Object -Unique) -join ',')
        $buildTasks = if ($CompileOnly -and $target.project -ne '.') { @(':common:check', 'compileJava') }
            elseif ($CompileOnly) { @('check') }
            elseif ($target.status -notin @('ready', 'compiled')) { @(':common:check', 'compileJava') }
            else { @($target.buildTasks) }
        if ($SkipTests) {
            if ($CompileOnly) { $buildTasks = @('compileJava') }
            else {
                $buildTasks = @($target.buildTasks | Where-Object { $_ -notin @('check', ':common:check') } | ForEach-Object {
                    if ($_ -eq 'build') { 'assemble' } else { $_ }
                })
            }
        }
        $buildOptions = @($toolchainArgument)
        if ($SkipTests) { $buildOptions += '-PbuildWithoutRuntimeCheck=true' }
        if ($SkipTests) { $buildOptions += @('-x', 'check', '-x', 'test') }
        Write-Host "Building $label ($($buildTasks -join ', '))"
        Push-Location $targetRoot
        try {
            & $gradleCommand @buildTasks @buildOptions --no-daemon --console=plain *> $logFile
            $buildExit = $LASTEXITCODE
        } finally { Pop-Location }
        Get-Content -LiteralPath $logFile -Tail 14 | ForEach-Object { Write-Host $_ }
        if ($buildExit -ne 0) { throw "Gradle exited $buildExit. See $logFile" }
        if ($CompileOnly) { $entry.status = 'compiled'; continue }
        if ($target.status -notin @('ready', 'compiled') -and -not $SkipTests) {
            $entry.status = 'needs_runtime_verification'
            $entry.reason = $target.reason
            $hasFailures = $true
            continue
        }
        $built = Join-Path $targetRoot ($target.artifact.Replace('{release}', $matrix.release))
        if (-not (Test-Path -LiteralPath $built -PathType Leaf)) { throw "Missing release JAR: $built" }
        $env:PYTHONUTF8 = '1'
        & python (Join-Path $PSScriptRoot 'verify_release_jar.py') $built --loader $target.loader --minecraft $target.minecraft --release $matrix.release
        if ($LASTEXITCODE -ne 0) { throw 'Release JAR metadata validation failed.' }
        $name = $matrix.artifactPattern.Replace('{minecraft}', $target.minecraft).Replace('{loader}', $target.loader).Replace('{release}', $matrix.release)
        $destination = Join-Path $OutputDirectory $name
        Copy-Item -LiteralPath $built -Destination $destination -Force
        $entry.status = 'built'
        $entry.runtimeVerified = $target.status -eq 'ready'
        $entry.file = $name
        $entry.sha256 = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
        Write-Host "Built and verified $name"
    } catch {
        $hasFailures = $true
        $entry.status = 'failed'
        $entry.reason = $_.Exception.Message
        Write-Warning "$label failed: $($_.Exception.Message)"
    } finally {
        $env:JAVA_HOME = $savedJavaHome
        $env:JAVA_TOOL_OPTIONS = $savedJavaOptions
        $env:PYTHONUTF8 = $savedUtf8
        $entry.durationSeconds = [Math]::Round(([DateTime]::UtcNow - $started).TotalSeconds, 2)
        $attempts += $entry
        Save-BuildReport
    }
}
Write-Host "Report: $(Join-Path $OutputDirectory $reportName)"
if (-not $CompileOnly) {
    & python (Join-Path $PSScriptRoot 'write_build_status.py') $OutputDirectory
    if ($LASTEXITCODE -ne 0) { $hasFailures = $true }
}
if ($hasFailures) { exit 1 }
