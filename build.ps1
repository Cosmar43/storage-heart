# Build + package StorageHeart (no Gradle needed).
# Usage: .\build.ps1 -ServerJar 'C:\path\to\Server-0.7.2.jar' [-Jdk 'C:\Program Files\Java\jdk-25']
param(
    [Parameter(Mandatory = $true)][string]$ServerJar,
    [string]$Jdk = 'C:\Program Files\Java\jdk-25'
)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$javac = Join-Path $Jdk 'bin\javac.exe'
$jar = Join-Path $Jdk 'bin\jar.exe'
$version = (Get-Content "$root\src\main\resources\manifest.json" -Raw | ConvertFrom-Json).Version

if (-not (Test-Path $ServerJar)) { throw "Server jar not found: $ServerJar" }
if (-not (Test-Path $javac)) { throw "javac not found: $javac" }
if (-not (Test-Path $jar)) { throw "jar not found: $jar" }

$files = Get-ChildItem -Recurse "$root\src\main\java" -Filter *.java | ForEach-Object { '"' + ($_.FullName -replace '\\', '/') + '"' }
# El directorio build/ está gitignored: en un clon limpio hay que crearlo
# antes de escribir sources.txt.
New-Item -ItemType Directory -Force -Path "$root\build" | Out-Null
[IO.File]::WriteAllLines("$root\build\sources.txt", $files)
Remove-Item -Recurse -Force "$root\build\servercheck" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$root\build\servercheck" | Out-Null
New-Item -ItemType Directory -Force -Path "$root\dist" | Out-Null
& $javac -encoding UTF-8 -cp $ServerJar -d "$root\build/servercheck" "@$root/build/sources.txt"
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit $LASTEXITCODE" }
# Empaqueta TODO src/main/resources (manifest.json en la raíz + Common/ + Server/).
& $jar --create --file "$root\dist\StorageHeart-$version.jar" -C "$root\build/servercheck" . -C "$root\src\main\resources" .
if ($LASTEXITCODE -ne 0) { throw "jar failed with exit $LASTEXITCODE" }
$hash = (Get-FileHash "$root\dist\StorageHeart-$version.jar" -Algorithm SHA256).Hash
Write-Output "Built dist\StorageHeart-$version.jar SHA256=$hash"
