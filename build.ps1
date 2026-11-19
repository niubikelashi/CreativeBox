# CreativeBox offline build script (no Maven / Gradle needed).
# Steps: download dependency jars -> javac -> jar (with plugin.yml + config.yml) -> zip release.
#
# Windows PowerShell 5.1 note: run with
#   powershell -ExecutionPolicy Bypass -File build.ps1
# PowerShell 7:
#   pwsh -File build.ps1
#
# Options:
#   -Offline   deps already in libs/, just compile + package
#   -Version   jar/zip version suffix (default 1.0-SNAPSHOT)
#   -NoZip     skip the zip release package
param(
    [string]$Version = '1.0-SNAPSHOT',
    [switch]$Offline,
    [switch]$NoZip
)

$ErrorActionPreference = 'Stop'
$Root = if ($PSScriptRoot) { $PSScriptRoot } else { (Get-Location).Path }
$Libs = Join-Path $Root 'libs'
$Build = Join-Path $Root 'build'
$Classes = Join-Path $Build 'classes'
$Dist = Join-Path $Root 'dist'
$PaperBase = 'https://repo.papermc.io/repository/maven-public'
$Central = 'https://repo1.maven.org/maven2'

function Find-Tool([string]$name) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($hint in @('C:\Program Files\Java\jdk-17.0.2\bin', 'C:\Program Files\Java\latest\jdk-17\bin')) {
        $p = Join-Path $hint ($name + '.exe')
        if (Test-Path $p) { return $p }
    }
    throw "Tool '$name' not found. Install JDK 17+ or add its bin folder to PATH."
}

function Get-IfMissing([string]$Url, [string]$Dest) {
    if (Test-Path $Dest) { return "cached: " + (Split-Path $Dest -Leaf) }
    try {
        Invoke-WebRequest $Url -OutFile $Dest -TimeoutSec 120 -UseBasicParsing
        return "downloaded: " + (Split-Path $Dest -Leaf) + " " + (Get-Item $Dest).Length + " bytes"
    } catch {
        Remove-Item $Dest -ErrorAction SilentlyContinue
        return "FAILED: " + $Url + " -> " + $_.Exception.Message
    }
}

function Resolve-PaperApiJar([string]$Coord) {
    # A SNAPSHOT artifact's real file name carries timestamp+buildNumber; parse maven-metadata.xml for it.
    $dir = $PaperBase + '/io/papermc/paper/paper-api/' + $Coord
    [xml]$meta = (Invoke-WebRequest ($dir + '/maven-metadata.xml') -UseBasicParsing -TimeoutSec 60).Content
    $v = $meta.metadata.versioning.snapshotVersions.snapshotVersion |
        Where-Object { $_.extension -eq 'jar' -and -not $_.classifier } | Select-Object -First 1
    if (-not $v) { throw "paper-api " + $Coord + ": snapshot version resolution is empty" }
    return $dir + '/paper-api-' + $v.value + '.jar'
}

New-Item -ItemType Directory -Force -Path $Libs, $Classes, $Dist | Out-Null

# ---- 1. Dependencies ----
if (-not $Offline) {
    Write-Host 'dependencies:'
    Get-IfMissing (Resolve-PaperApiJar '1.20.1-R0.1-SNAPSHOT') (Join-Path $Libs 'paper-api.jar') | ForEach-Object { Write-Host ('  ' + $_) }
    Get-IfMissing ($PaperBase + '/net/md-5/bungeecord-chat/1.20-R0.1-deprecated+build.14/bungeecord-chat-1.20-R0.1-deprecated+build.14.jar') (Join-Path $Libs 'bungeecord-chat.jar') | ForEach-Object { Write-Host ('  ' + $_) }
    # paper-api signatures reference Adventure + examination types, so javac needs them on the classpath.
    foreach ($a in @('adventure-api', 'adventure-key', 'adventure-text-minimessage', 'adventure-text-serializer-legacy', 'adventure-text-serializer-gson', 'adventure-text-serializer-plain')) {
        Get-IfMissing ($Central + '/net/kyori/' + $a + '/4.14.0/' + $a + '-4.14.0.jar') (Join-Path $Libs ($a + '.jar')) | ForEach-Object { Write-Host ('  ' + $_) }
    }
    Get-IfMissing ($Central + '/net/kyori/examination-api/1.3.0/examination-api-1.3.0.jar') (Join-Path $Libs 'examination-api.jar') | ForEach-Object { Write-Host ('  ' + $_) }
    Get-IfMissing ($Central + '/com/google/code/gson/gson/2.10/gson-2.10.jar') (Join-Path $Libs 'gson.jar') | ForEach-Object { Write-Host ('  ' + $_) }
    Get-IfMissing ($Central + '/org/checkerframework/checker-qual/3.21.0/checker-qual-3.21.0.jar') (Join-Path $Libs 'checker-qual.jar') | ForEach-Object { Write-Host ('  ' + $_) }
}

$jars = Get-ChildItem $Libs -Filter *.jar -ErrorAction SilentlyContinue
if (-not $jars) {
    throw 'No dependency jars in libs/. Run without -Offline first, or place paper-api.jar there manually.'
}
$cp = ($jars | ForEach-Object { $_.FullName }) -join ';'

# ---- 2. Compile ----
$javac = Find-Tool 'javac'
$jarTool = Find-Tool 'jar'
$sources = Get-ChildItem (Join-Path $Root 'src\main\java') -Recurse -Filter *.java | ForEach-Object { $_.FullName }
$argFile = Join-Path $Build 'javac-args.txt'
$argLines = @('-encoding', 'UTF-8', '--release', '17', '-classpath', $cp, '-d', $Classes) + $sources
($argLines -join "`n") | Set-Content -Encoding ASCII $argFile
Write-Host ('compile: ' + $sources.Count + ' source files, ' + $jars.Count + ' jars on classpath')
& $javac ('@' + $argFile)
if ($LASTEXITCODE -ne 0) { throw 'javac failed (exit ' + $LASTEXITCODE + ')' }

# ---- 3. Package ----
Copy-Item (Join-Path $Root 'src\main\resources\plugin.yml') $Classes
Copy-Item (Join-Path $Root 'src\main\resources\config.yml') $Classes
$jarPath = Join-Path $Dist ('CreativeBox-' + $Version + '.jar')
Remove-Item $jarPath -ErrorAction SilentlyContinue
& $jarTool --create --file $jarPath -C $Classes .
if ($LASTEXITCODE -ne 0) { throw 'jar packaging failed (exit ' + $LASTEXITCODE + ')' }
Write-Host ('jar: ' + $jarPath + ' ' + (Get-Item $jarPath).Length + ' bytes')

$zipPath = Join-Path $Dist ('CreativeBox-' + $Version + '.zip')
if (-not $NoZip) {
    Remove-Item $zipPath -ErrorAction SilentlyContinue
    $stage = Join-Path $Build 'zipstage'
    Remove-Item $stage -Recurse -ErrorAction SilentlyContinue
    $plugins = Join-Path $stage 'plugins'
    New-Item -ItemType Directory -Force -Path $plugins | Out-Null
    Copy-Item $jarPath $plugins
    Copy-Item (Join-Path $Root 'src\main\resources\config.yml') $plugins
    Copy-Item (Join-Path $Root 'README.md') $stage
    Compress-Archive -Path (Join-Path $stage '*') -DestinationPath $zipPath -CompressionLevel Optimal
    Write-Host ('zip: ' + $zipPath + ' ' + (Get-Item $zipPath).Length + ' bytes')
}

Write-Host ('sha256 jar: ' + (Get-FileHash $jarPath -Algorithm SHA256).Hash)
if (-not $NoZip) { Write-Host ('sha256 zip: ' + (Get-FileHash $zipPath -Algorithm SHA256).Hash) }
Write-Host 'OK'
