# Forward Windows' single-address system proxy to Java/Gradle for this process only.
$ErrorActionPreference = 'Stop'
$proxyArgs = @()
$settings = Get-ItemProperty 'HKCU:/Software/Microsoft/Windows/CurrentVersion/Internet Settings' -ErrorAction SilentlyContinue
if ($settings.ProxyEnable -eq 1 -and $settings.ProxyServer -and $settings.ProxyServer -notmatch '=') {
    $address = $settings.ProxyServer
    if ($address -notmatch '://') { $address = "http://$address" }
    $proxyUri = [Uri]$address
    $proxyArgs = @("-Dhttps.proxyHost=$($proxyUri.Host)", "-Dhttps.proxyPort=$($proxyUri.Port)",
                   "-Dhttp.proxyHost=$($proxyUri.Host)", "-Dhttp.proxyPort=$($proxyUri.Port)")
}
$taskArgs = $args
if ($taskArgs.Count -eq 0) { $taskArgs = @('build') }
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat @proxyArgs @taskArgs
    $buildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $buildExitCode
