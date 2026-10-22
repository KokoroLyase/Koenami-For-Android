param([string[]]$Tasks = @('lintDebug','testDebugUnitTest','assembleDebug'))
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $root '.toolchain/jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    $env:GRADLE_USER_HOME = Join-Path $root '.toolchain/gradle-home'
    & ./gradlew.bat @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $LASTEXITCODE" }
} finally { Pop-Location }
