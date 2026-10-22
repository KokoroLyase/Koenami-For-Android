param([string[]]$Tasks = @('lintRelease','testReleaseUnitTest','assembleRelease'), [string[]]$GradleOptions = @())
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $root '.local/signing'
$env:KOENAMI_KEYSTORE = Join-Path $folder 'koenami-release.p12'
if (!(Test-Path -LiteralPath $env:KOENAMI_KEYSTORE)) { throw 'Release key missing; never regenerate an existing published signing identity.' }
$secure = ConvertTo-SecureString ((Get-Content -LiteralPath (Join-Path $folder 'store-password.dpapi') -Raw).Trim())
try {
    $env:KOENAMI_STORE_PASSWORD = [Net.NetworkCredential]::new('', $secure).Password
    & (Join-Path $PSScriptRoot 'build.ps1') -Tasks ($Tasks + @('-PenableRelease=true') + $GradleOptions)
} finally {
    Remove-Item Env:KOENAMI_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:KOENAMI_KEYSTORE -ErrorAction SilentlyContinue
}
