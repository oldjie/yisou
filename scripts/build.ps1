param(
    [string]$SdkRoot = $env:ANDROID_HOME,
    [string]$JdkRoot = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkRoot) { $SdkRoot = Join-Path $projectRoot '.tools/sdk' }
if (-not $JdkRoot) { throw 'Set JAVA_HOME to a JDK (17 or newer).' }
$java = Join-Path $JdkRoot 'bin/java.exe'
$javac = Join-Path $JdkRoot 'bin/javac.exe'
$keytool = Join-Path $JdkRoot 'bin/keytool.exe'
$platform = Join-Path $SdkRoot 'platforms/android-35/android.jar'
if (-not (Test-Path $platform)) { $platform = Join-Path $SdkRoot 'android-35/android.jar' }
$tools = Join-Path $SdkRoot 'build-tools/35.0.0'
if (-not (Test-Path $tools)) { $tools = Join-Path $SdkRoot 'android-15' }
if (-not (Test-Path $platform) -or -not (Test-Path $javac)) { throw 'Missing SDK API 35 or JDK compiler. See README.' }
$build = Join-Path $projectRoot 'build/local'
$dist = Join-Path $projectRoot 'dist'
$buildBoundary = [IO.Path]::GetFullPath($build).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
foreach ($area in @('classes', 'generated', 'dex')) {
    $cleanRoot = [IO.Path]::GetFullPath((Join-Path $build $area))
    if (-not $cleanRoot.StartsWith($buildBoundary, [StringComparison]::OrdinalIgnoreCase)) { throw 'Build cleanup escaped its output directory.' }
    if (Test-Path -LiteralPath $cleanRoot) { Remove-Item -LiteralPath $cleanRoot -Recurse -Force }
}
New-Item -ItemType Directory -Force $build, $dist, "$build/generated", "$build/classes", "$build/dex" | Out-Null
function Invoke-Checked([string]$file, [string[]]$arguments) {
    & $file @arguments
    if ($LASTEXITCODE -ne 0) { throw "$file failed with exit code $LASTEXITCODE" }
}
Invoke-Checked "$tools/aapt2.exe" @('compile', '--dir', "$projectRoot/app/src/main/res", '-o', "$build/resources.zip")
[xml]$manifest = Get-Content -LiteralPath "$projectRoot/app/src/main/AndroidManifest.xml" -Raw -Encoding UTF8
$manifest.DocumentElement.SetAttribute('package', 'org.nofeed.android')
$manifest.Save("$build/AndroidManifest.xml")
Invoke-Checked "$tools/aapt2.exe" @('link', '-o', "$build/base.apk", '-I', $platform, '--manifest', "$build/AndroidManifest.xml", '--java', "$build/generated", '--min-sdk-version', '26', '--target-sdk-version', '35', '--version-code', '9', '--version-name', '0.3.6', "$build/resources.zip")
$sources = @(Get-ChildItem "$projectRoot/app/src/main/java", "$build/generated" -Recurse -Filter '*.java' | ForEach-Object FullName)
Invoke-Checked $javac (@('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', "$platform;$tools/core-lambda-stubs.jar", '-d', "$build/classes") + $sources)
$classes = @(Get-ChildItem "$build/classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
Invoke-Checked $java (@('-cp', "$tools/lib/d8.jar", 'com.android.tools.r8.D8', '--min-api', '26', '--lib', $platform, '--output', "$build/dex") + $classes)
Copy-Item -LiteralPath "$build/base.apk" -Destination "$build/unsigned.apk" -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open("$build/unsigned.apk", [IO.Compression.ZipArchiveMode]::Update)
try { [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, "$build/dex/classes.dex", 'classes.dex') | Out-Null } finally { $archive.Dispose() }
Invoke-Checked "$tools/zipalign.exe" @('-f', '-p', '4', "$build/unsigned.apk", "$build/aligned.apk")
$key = "$projectRoot/.tools/development.p12"
if (-not (Test-Path $key)) {
    New-Item -ItemType Directory -Force "$projectRoot/.tools" | Out-Null
    Invoke-Checked $keytool @('-genkeypair', '-keystore', $key, '-storepass', 'android', '-alias', 'development', '-keypass', 'android', '-dname', 'CN=NoFeed Development', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000')
}
$apk = "$dist/yisou-0.3.6.apk"
Invoke-Checked $java @('-jar', "$tools/lib/apksigner.jar", 'sign', '--ks', $key, '--ks-pass', 'pass:android', '--ks-key-alias', 'development', '--out', $apk, "$build/aligned.apk")
Invoke-Checked $java @('-jar', "$tools/lib/apksigner.jar", 'verify', '--verbose', $apk)
Get-FileHash -Algorithm SHA256 $apk | ForEach-Object { "$($_.Hash.ToLower())  yisou-0.3.6.apk" } | Set-Content "$dist/SHA256SUMS.txt"
Write-Output "APK: $apk"
