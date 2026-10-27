# Offline build for tkgun: compile against a named (yarn) Minecraft jar built from the
# 1.21.1 client jar in the local launcher libraries, remap named -> intermediary, package.
#   powershell -NoProfile -ExecutionPolicy Bypass -File build.ps1
param(
    [string]$Root = "D:\codex\mod\rebuild",
    [string]$Libs = "D:\Game\mc\建筑端\.minecraft\libraries",
    [string]$Fapi = "D:\codex\mod\tkgun_handoff_20261016\03_tools\fapi-named",
    [string]$Deps = "D:\codex\mod\tkgun_handoff_20261016\04_deps",
    [string]$Out = "D:\codex\mod\rebuild\dist\MechanicianCombat-1.0.0.jar",
    [string]$JavaHome = "C:\Program Files\Java\jdk-21"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.IO.Compression.FileSystem

$JAVA = Join-Path $JavaHome "bin\java.exe"
$JAVAC = Join-Path $JavaHome "bin\javac.exe"
$REMAPPER = "$Root\tools\remap\tiny-remapper-fat.jar"
$YARN = "$Root\tools\remap\yarn-1.21.1.tiny"
$INTER = "$Root\tools\remap\intermediary-1.21.1.tiny"
$MC_SLIM = "$Libs\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-slim.jar"
$MC_INT = "$Root\build\mc-intermediary.jar"
$MC_NAMED = "$Root\build\mc-named.jar"
$CLASSES = "$Root\build\classes"
$BUILD = "$Root\build"
$SRC = "$Root\mod\src\main"

if (-not (Test-Path -LiteralPath $JAVA)) { throw "Java executable not found: $JAVA" }
if (-not (Test-Path -LiteralPath $JAVAC)) { throw "javac executable not found: $JAVAC" }

function Check([string]$what) {
    if ($LASTEXITCODE -ne 0) { throw "$what failed (exit $LASTEXITCODE)" }
    Write-Output "   ok: $what"
}

# 1) named Minecraft jar (official -> intermediary -> named)
if (-not (Test-Path $MC_SLIM)) { throw "client jar not found: $MC_SLIM" }
if (-not (Test-Path $MC_NAMED)) {
    if (-not (Test-Path $MC_INT)) {
        Write-Output "== remap minecraft official -> intermediary"
        & $JAVA -jar $REMAPPER $MC_SLIM $MC_INT $INTER official intermediary
        Check "minecraft official -> intermediary"
    }
    Write-Output "== remap minecraft intermediary -> named"
    & $JAVA -jar $REMAPPER $MC_INT $MC_NAMED $YARN intermediary named
    Check "minecraft intermediary -> named"
}
Write-Output ("   mc-named.jar: {0:N0} bytes" -f (Get-Item $MC_NAMED).Length)

# 2) classpath: named MC + launcher libraries + fabric api + our remapped deps
$cp = New-Object System.Collections.Generic.List[string]
$cp.Add($MC_NAMED)
Get-ChildItem -Path $Libs -Recurse -File -Filter *.jar | ForEach-Object { $cp.Add($_.FullName) }
Get-ChildItem -Path "$Fapi\*.jar" -File | ForEach-Object { $cp.Add($_.FullName) }
Get-ChildItem -Path "$Deps\*.jar" -File | ForEach-Object { $cp.Add($_.FullName) }
Write-Output ("   classpath entries: {0}" -f $cp.Count)

# 3) javac
if (Test-Path $CLASSES) { Remove-Item -LiteralPath $CLASSES -Recurse -Force }
New-Item -ItemType Directory -Force -Path $CLASSES | Out-Null
$srcs = Get-ChildItem -Path "$SRC\java" -Recurse -File -Filter *.java | ForEach-Object { $_.FullName }
Write-Output ("   java sources: {0}" -f $srcs.Count)
$argFile = "$BUILD\javac.args"
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("-proc:none")
$lines.Add("-encoding")
$lines.Add("UTF-8")
$lines.Add("--release")
$lines.Add("21")
$lines.Add("-nowarn")
$lines.Add("-cp")
# javac @argfiles treat backslash as an escape character -> use forward slashes
$lines.Add('"' + (($cp | ForEach-Object { $_.Replace("\", "/") }) -join ";") + '"')
$lines.Add("-d")
$lines.Add('"' + $CLASSES.Replace("\", "/") + '"')
foreach ($s in $srcs) { $lines.Add('"' + $s.Replace("\", "/") + '"') }
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllLines($argFile, $lines, $utf8NoBom)
Write-Output "== javac"
& $JAVAC "@$argFile"
Check "javac"
$ncls = (Get-ChildItem -Path $CLASSES -Recurse -File -Filter *.class | Measure-Object).Count
Write-Output ("   compiled classes: {0}" -f $ncls)

# 4) classes.jar -> remap named -> intermediary
$classesJar = "$BUILD\classes.jar"
if (Test-Path $classesJar) { Remove-Item -LiteralPath $classesJar -Force }
[IO.Compression.ZipFile]::CreateFromDirectory($CLASSES, $classesJar)
$remapped = "$BUILD\remapped.jar"
if (Test-Path $remapped) { Remove-Item -LiteralPath $remapped -Force }
Write-Output "== remap tkgun named -> intermediary"
& $JAVA -jar $REMAPPER $classesJar $remapped $YARN named intermediary $MC_NAMED
Check "tkgun remap"

# 5) final jar = remapped classes + resources
$res = "$SRC\resources"
if (Test-Path $Out) { Remove-Item -LiteralPath $Out -Force }
New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
$zip = [IO.Compression.ZipFile]::Open($Out, [IO.Compression.ZipArchiveMode]::Create)
try {
    $in = [IO.Compression.ZipFile]::OpenRead($remapped)
    try {
        foreach ($e in $in.Entries) {
            if (-not $e.FullName.EndsWith(".class")) { continue }
            $dst = $zip.CreateEntry($e.FullName)
            $s = $e.Open(); $d = $dst.Open()
            $s.CopyTo($d); $d.Dispose(); $s.Dispose()
        }
    } finally { $in.Dispose() }
    foreach ($f in (Get-ChildItem -Path $res -Recurse -File)) {
        $rel = $f.FullName.Substring($res.Length + 1).Replace("\", "/")
        $bytes = [IO.File]::ReadAllBytes($f.FullName)
        if ($rel -eq "fabric.mod.json") {
            $txt = [Text.Encoding]::UTF8.GetString($bytes).Replace('${' + 'version}', "1.0.0")
            $bytes = [Text.Encoding]::UTF8.GetBytes($txt)
        }
        $dst = $zip.CreateEntry($rel)
        $d = $dst.Open(); $d.Write($bytes, 0, $bytes.Length); $d.Dispose()
    }
} finally { $zip.Dispose() }

Write-Output ("== jar: {0} ({1:N0} bytes)" -f $Out, (Get-Item $Out).Length)
