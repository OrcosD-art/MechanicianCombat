param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDir,
    [Parameter(Mandatory = $true)]
    [string]$SourceAssets,
    [string]$SevenZip = "C:\Program Files\7-Zip\7z.exe"
)

$ErrorActionPreference = "Stop"
$workspace = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\.."))
$rebuild = Join-Path $workspace "rebuild"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$stage = Join-Path $rebuild "handoff\MechanicianCombat-source-r349-$stamp"
$packageName = "MechanicianCombat-source-r349-2026-10-25"
$archive = Join-Path $OutputDir "$packageName.7z"

New-Item -ItemType Directory -Force -Path $stage | Out-Null
foreach ($dir in @("mod", "tools", "fapi-named", "deps", "build",
        "source_assets", "docs", "release")) {
    New-Item -ItemType Directory -Force -Path (Join-Path $stage $dir) | Out-Null
}

Copy-Item -LiteralPath (Join-Path $rebuild "mod\src") -Destination (Join-Path $stage "mod\src") -Recurse

$toolFiles = Get-ChildItem -LiteralPath (Join-Path $rebuild "tools") -File |
    Where-Object {
        $_.Extension -in @(".ps1", ".py", ".js", ".java", ".json", ".reg") -and
        $_.Name -ne "package_source_handoff.ps1"
    }
foreach ($file in $toolFiles) {
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $stage "tools") -Force
}
Copy-Item -LiteralPath (Join-Path $rebuild "tools\remap") -Destination (Join-Path $stage "tools\remap") -Recurse

foreach ($file in @("mc-named.jar", "mc-intermediary.jar")) {
    Copy-Item -LiteralPath (Join-Path $rebuild "build\$file") -Destination (Join-Path $stage "build") -Force
}
Copy-Item -Path (Join-Path $workspace "tkgun_handoff_20261016\03_tools\fapi-named\*") `
    -Destination (Join-Path $stage "fapi-named") -Force
Copy-Item -Path (Join-Path $workspace "tkgun_handoff_20261016\04_deps\*") `
    -Destination (Join-Path $stage "deps") -Force

foreach ($dir in Get-ChildItem -LiteralPath $SourceAssets -Directory) {
    Copy-Item -LiteralPath $dir.FullName `
        -Destination (Join-Path $stage "source_assets") -Recurse -Force
}
Copy-Item -LiteralPath (Join-Path $rebuild "SOURCE_HANDOFF.md") -Destination $stage -Force
Copy-Item -LiteralPath (Join-Path $rebuild "SOURCE_BUILD.ps1") -Destination $stage -Force
Copy-Item -LiteralPath (Join-Path $rebuild "PACKAGING_RULES.txt") -Destination (Join-Path $stage "docs") -Force
foreach ($file in Get-ChildItem -LiteralPath $rebuild -File -Filter "*.txt") {
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $stage "docs") -Force
}
foreach ($file in Get-ChildItem -LiteralPath $SourceAssets -File |
        Where-Object { $_.Extension -eq ".txt" }) {
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $stage "docs") -Force
}
Copy-Item -LiteralPath (Join-Path $rebuild "dist\MechanicianCombat-1.0.0.jar") `
    -Destination (Join-Path $stage "release") -Force

$versionText = @(
    "MechanicianCombat source handoff",
    "Build: r349-2026-10-25",
    "Minecraft: 1.21.1",
    "Java: 21",
    "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')"
)
[IO.File]::WriteAllLines((Join-Path $stage "VERSION.txt"), $versionText,
    [Text.UTF8Encoding]::new($false))

$manifestPath = Join-Path $stage "SHA256SUMS.txt"
$manifest = foreach ($file in Get-ChildItem -LiteralPath $stage -Recurse -File |
        Where-Object { $_.Name -ne "SHA256SUMS.txt" } | Sort-Object FullName) {
    $relative = $file.FullName.Substring($stage.Length + 1).Replace("\", "/")
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash
    "$hash  $relative"
}
[IO.File]::WriteAllLines($manifestPath, $manifest, [Text.UTF8Encoding]::new($false))

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
if (-not (Test-Path -LiteralPath $SevenZip)) {
    throw "7-Zip not found: $SevenZip"
}
if (Test-Path -LiteralPath $archive) {
    throw "Archive already exists: $archive"
}
Push-Location (Split-Path -Parent $stage)
try {
    & $SevenZip a -t7z -mx=9 $archive (Split-Path -Leaf $stage) | Out-Host
} finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0) {
    throw "7-Zip packaging failed with exit code $LASTEXITCODE"
}

Write-Output "SOURCE_PACKAGE=$archive"
Get-FileHash -Algorithm SHA256 -LiteralPath $archive | Format-List Path,Hash
