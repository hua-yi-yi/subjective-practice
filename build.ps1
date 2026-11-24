param(
    [string]$SdkRoot = $env:ANDROID_HOME
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

if (-not $SdkRoot) {
    throw 'Set ANDROID_HOME or pass -SdkRoot with the path to your Android SDK.'
}
$SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).Path
$platform = Join-Path $SdkRoot 'platforms/android-35/android.jar'
$buildTools = Join-Path $SdkRoot 'build-tools/35.0.0'
if (-not (Test-Path -LiteralPath $platform) -or
    -not (Test-Path -LiteralPath "$buildTools/aapt2.exe")) {
    throw 'Install Android SDK Platform 35 and Build-Tools 35.0.0.'
}

Push-Location $PSScriptRoot
$scratch = $null
try {
    $buildRoot = (New-Item -ItemType Directory -Path 'build' -Force).FullName
    $scratch = 'build/compile-' + [Guid]::NewGuid().ToString('N')
    $classes = "$scratch/classes"
    $dex = "$scratch/dex"
    New-Item -ItemType Directory -Path $classes, $dex -Force | Out-Null

    # AAPT2 on Windows opens project-relative paths reliably in directories with Unicode names.
    $androidJar = "$scratch/android.jar"
    Copy-Item -LiteralPath $platform -Destination $androidJar
    & "$buildTools/aapt2.exe" compile --dir 'app/res' -o "$scratch/resources.zip"
    & "$buildTools/aapt2.exe" link -I $androidJar --manifest 'app/AndroidManifest.xml' -o "$scratch/unsigned.apk" "$scratch/resources.zip"
    $sources = (Get-ChildItem -LiteralPath 'app/src' -Filter '*.java' -Recurse -File).FullName
    javac -encoding UTF-8 --release 8 -classpath $androidJar -d $classes $sources
    $classFiles = (Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse -File).FullName
    java -cp "$buildTools/lib/d8.jar" com.android.tools.r8.D8 --min-api 23 --lib $androidJar --output $dex $classFiles
    jar uf "$scratch/unsigned.apk" -C $dex classes.dex
    & "$buildTools/zipalign.exe" -f -p 4 "$scratch/unsigned.apk" "$scratch/aligned.apk"

    $keystore = 'build/local-signing.p12'
    $secretFile = 'build/local-signing.pass'
    if (-not (Test-Path -LiteralPath $keystore)) {
        [Guid]::NewGuid().ToString('N') | Set-Content -LiteralPath $secretFile -NoNewline -Encoding ascii
        keytool -genkeypair -keystore $keystore -storetype PKCS12 -storepass:file $secretFile -alias localbuild -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Subjective Practice Development'
    }
    $signer = "$buildTools/lib/apksigner.jar"
    java -jar $signer sign --ks $keystore --ks-key-alias localbuild --ks-pass "file:$secretFile" --out "$scratch/signed.apk" "$scratch/aligned.apk"
    java -jar $signer verify "$scratch/signed.apk"
    Move-Item -LiteralPath "$scratch/signed.apk" -Destination 'build/subjective-practice.apk' -Force
    Get-Item -LiteralPath 'build/subjective-practice.apk'
} finally {
    if ($scratch -and (Test-Path -LiteralPath $scratch)) {
        $resolvedScratch = (Resolve-Path -LiteralPath $scratch).Path
        if ((Split-Path -Parent $resolvedScratch) -eq $buildRoot) {
            Remove-Item -LiteralPath $resolvedScratch -Recurse -Force
        }
    }
    Pop-Location
}
