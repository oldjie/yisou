param([string]$JdkRoot = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Force "$projectRoot/build/tests" | Out-Null
& "$JdkRoot/bin/javac.exe" -encoding UTF-8 -d "$projectRoot/build/tests" "$projectRoot/app/src/main/java/org/nofeed/android/SearchTarget.java" "$projectRoot/tests/SearchTargetTest.java" "$projectRoot/app/src/main/java/org/nofeed/android/TargetOrdering.java" "$projectRoot/tests/TargetOrderingTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
& "$JdkRoot/bin/java.exe" -cp "$projectRoot/build/tests" org.nofeed.android.SearchTargetTest
if ($LASTEXITCODE -ne 0) { throw 'Tests failed' }

& "$JdkRoot/bin/java.exe" -cp "$projectRoot/build/tests" org.nofeed.android.TargetOrderingTest
if ($LASTEXITCODE -ne 0) { throw 'Ordering tests failed' }
