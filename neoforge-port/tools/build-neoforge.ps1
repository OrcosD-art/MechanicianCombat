param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-21",
    [string]$InstallDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$build = Join-Path $root "build"
$dist = Join-Path $root "dist"
$minecraftRoot = Get-ChildItem -LiteralPath "D:\Game\mc" -Directory |
    ForEach-Object { Join-Path $_.FullName ".minecraft" } |
    Where-Object {
        Test-Path -LiteralPath (Join-Path $_ `
                "versions\1.21.1-NeoForge\1.21.1-NeoForge.json")
    } |
    Select-Object -First 1
if (-not $minecraftRoot) {
    throw "Minecraft test root not found"
}
$libraries = Join-Path $minecraftRoot "libraries"
$asmVersion = "9.9.1"
$toolLibs = Join-Path $build "tool-libs"
$asm = Join-Path $toolLibs "asm-$asmVersion.jar"
$asmTree = Join-Path $toolLibs "asm-tree-$asmVersion.jar"
$asmCommons = Join-Path $toolLibs "asm-commons-$asmVersion.jar"
$java = Join-Path $JavaHome "bin\java.exe"
$javac = Join-Path $JavaHome "bin\javac.exe"
$jar = Join-Path $JavaHome "bin\jar.exe"
$tinyRemapper = "D:\codex\mod\rebuild\tools\remap\tiny-remapper-fat.jar"
$mappings = Join-Path $root "mappings"
$classes = Join-Path $build "classes"
$classesYarn = Join-Path $build "classes-yarn.jar"
$classesMojmap = Join-Path $build "classes-mojmap.jar"
$classesNeoForge = Join-Path $build "classes-neoforge.jar"
$clientYarn = Join-Path $build "neoforge-client-yarn.jar"
$clientMojmap = Join-Path $build "neoforge-client-mojmap.jar"
$javacArgs = Join-Path $build "javac-generated.args"
$output = Join-Path $dist "MechanicianCombat-1.21.1-neoforge-1.0.0.jar"

function Reset-Directory([string]$path) {
    $full = [IO.Path]::GetFullPath($path)
    if (-not $full.StartsWith($root + [IO.Path]::DirectorySeparatorChar,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe build path: $full"
    }
    if (Test-Path -LiteralPath $full) {
        Remove-Item -LiteralPath $full -Recurse -Force
    }
    New-Item -ItemType Directory -Path $full | Out-Null
}

function Assert-Success([string]$step) {
    if ($LASTEXITCODE -ne 0) {
        throw "$step failed with exit code $LASTEXITCODE"
    }
}

function Invoke-Native([string]$log, [string]$step, [scriptblock]$action) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $action 2>&1 | Tee-Object -FilePath $log
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($exitCode -ne 0) {
        throw "$step failed with exit code $exitCode"
    }
}

New-Item -ItemType Directory -Path $build, $dist -Force | Out-Null
New-Item -ItemType Directory -Path $toolLibs -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $libraries `
        "org\ow2\asm\asm\$asmVersion\asm-$asmVersion.jar") `
        -Destination $asm -Force
Copy-Item -LiteralPath (Join-Path $libraries `
        "org\ow2\asm\asm-tree\$asmVersion\asm-tree-$asmVersion.jar") `
        -Destination $asmTree -Force
Copy-Item -LiteralPath (Join-Path $libraries `
        "org\ow2\asm\asm-commons\$asmVersion\asm-commons-$asmVersion.jar") `
        -Destination $asmCommons -Force
Reset-Directory $classes
$tools = Join-Path $build "tools"
New-Item -ItemType Directory -Path $tools -Force | Out-Null
Invoke-Native (Join-Path $build "tools-javac.log") "Build tool compilation" {
    & $javac -encoding UTF8 --release 21 `
            -cp (@($asm, $asmTree, $asmCommons) -join [IO.Path]::PathSeparator) `
            -d $tools (Join-Path $root "src\tools\PostRemapFixer.java")
}

$templateArgs = Get-Content -LiteralPath (Join-Path $build "javac.args") `
        -Encoding UTF8
$classpathIndex = [Array]::IndexOf($templateArgs, "-cp")
if ($classpathIndex -lt 0 -or $classpathIndex + 1 -ge $templateArgs.Count) {
    throw "Classpath not found in build/javac.args"
}
$projectClasspath = $templateArgs[$classpathIndex + 1].Trim('"').Replace('\', '/')
$sourceFiles = @(
    Get-ChildItem -LiteralPath (Join-Path $root "src\main\java"),
            (Join-Path $root "src\bridge\java") -Recurse -File -Filter "*.java"
) | Sort-Object FullName
$javacLines = @(
    "-proc:none",
    "-encoding", "UTF-8",
    "--release", "21",
    "-nowarn",
    "-Xmaxerrs", "1000",
    "-cp", ('"' + $projectClasspath + '"'),
    "-d", ('"' + $classes.Replace('\', '/') + '"')
)
$javacLines += $sourceFiles | ForEach-Object {
    '"' + $_.FullName.Replace('\', '/') + '"'
}
[IO.File]::WriteAllLines($javacArgs, $javacLines,
        (New-Object Text.UTF8Encoding($false)))

Invoke-Native (Join-Path $build "javac-errors.txt") "Java compilation" {
    & $javac "@$javacArgs"
}

foreach ($artifact in @($classesYarn, $classesMojmap, $classesNeoForge,
        $output)) {
    if (Test-Path -LiteralPath $artifact) {
        Remove-Item -LiteralPath $artifact -Force
    }
}

Invoke-Native (Join-Path $build "jar-classes.log") "Yarn class archive" {
    & $jar --create --file $classesYarn -C $classes .
}

$remapArguments = @(
    "-jar", $tinyRemapper,
    $classesYarn, $classesMojmap,
    (Join-Path $mappings "yarn-mojang.tiny"),
    "named", "official", $clientYarn, "--ignoreConflicts"
)
Invoke-Native (Join-Path $build "remap-mod.log") "Mod remap" {
    & $java @remapArguments
}

if (-not (Test-Path -LiteralPath $clientMojmap)) {
    $fullRemapArguments = @(
        "-jar", $tinyRemapper,
        $clientYarn, $clientMojmap,
        (Join-Path $mappings "yarn-mojang.tiny"),
        "named", "official", $clientYarn, "--ignoreConflicts"
    )
    Invoke-Native (Join-Path $build "remap-full.log") "Client hierarchy remap" {
        & $java @fullRemapArguments
    }
}

$toolClasspath = @($tools, $asm, $asmTree, $asmCommons) -join
        [IO.Path]::PathSeparator
Invoke-Native (Join-Path $build "post-remap.log") "Post-remap fixes" {
    & $java -cp $toolClasspath PostRemapFixer `
            $classesMojmap $classesNeoForge `
            (Join-Path $mappings "runtime-annotations.tsv") `
            (Join-Path $mappings "post-remap-members.tsv") `
            $clientMojmap
}

$staging = Join-Path $build "package-classes"
$stage = Join-Path $build "package-stage"
Reset-Directory $staging
Reset-Directory $stage
Push-Location $staging
try {
    Invoke-Native (Join-Path $build "extract-classes.log") "Class extraction" {
        & $jar xf $classesNeoForge
    }
} finally {
    Pop-Location
}
Copy-Item -Path (Join-Path $staging "*") -Destination $stage -Recurse -Force
Copy-Item -Path (Join-Path $root "src\main\resources\*") `
        -Destination $stage -Recurse -Force
Invoke-Native (Join-Path $build "package.log") "Release archive" {
    & $jar --create --file $output `
            --manifest (Join-Path $build "MANIFEST.MF") -C $stage .
}

if ($InstallDir) {
    New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    Copy-Item -LiteralPath $output -Destination $InstallDir -Force
}

Get-Item -LiteralPath $output |
    Select-Object FullName, Length, LastWriteTime
