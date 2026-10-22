# Windows: creates the release identity once. Never prints or uploads the password.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $root '.local/signing'
New-Item -ItemType Directory -Force $folder | Out-Null
$keyFile = Join-Path $folder 'koenami-release.p12'
if ((Test-Path -LiteralPath $keyFile) -or (Test-Path -LiteralPath (Join-Path $folder 'store-password.dpapi'))) { throw 'Signing material already exists; refusing to overwrite the update identity or password.' }
$jdk = Get-ChildItem -LiteralPath (Join-Path $root '.toolchain/jdk') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))
$secure = ConvertTo-SecureString $password -AsPlainText -Force
$secure | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $folder 'store-password.dpapi') -Encoding utf8
try {
    $env:KOENAMI_STORE_PASSWORD = $password
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $keyFile -storetype PKCS12 -storepass:env KOENAMI_STORE_PASSWORD -keypass:env KOENAMI_STORE_PASSWORD -alias koenami -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10950 -dname 'CN=Isomeria, OU=Koenami for Android'
    if ($LASTEXITCODE -ne 0) { throw 'Signing key generation failed; inspect the private folder before retrying.' }
    & "$env:JAVA_HOME/bin/keytool.exe" -exportcert -rfc -keystore $keyFile -storepass:env KOENAMI_STORE_PASSWORD -alias koenami -file (Join-Path $root 'docs/release-signing-cert.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Public certificate export failed.' }
    Write-Output 'Release identity created. Back up keystore and password separately before changing Windows accounts or reinstalling Windows.'
} finally { Remove-Item Env:KOENAMI_STORE_PASSWORD -ErrorAction SilentlyContinue; $password = $null }
