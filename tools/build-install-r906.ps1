$ErrorActionPreference = "Stop"
$libraries = Get-ChildItem -LiteralPath "D:\Game\mc" -Directory |
    ForEach-Object { Join-Path $_.FullName ".minecraft\libraries" } |
    Where-Object {
        Test-Path -LiteralPath (Join-Path $_ `
            "net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-slim.jar")
    } |
    Select-Object -First 1
if (-not $libraries) {
    throw "Minecraft libraries directory not found"
}
$out = "D:\codex\mod\rebuild\dist\MechanicianCombat-r906.jar"
& "D:\codex\mod\rebuild\tools\build.ps1" `
    -Libs $libraries `
    -BuildDir "D:\codex\mod\rebuild\build-r906" `
    -Out $out
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $out)) {
    throw "r906 build failed"
}

$mcRoot = Get-ChildItem -LiteralPath "D:\Game\mc" -Directory |
    Where-Object {
        Test-Path -LiteralPath (Join-Path $_.FullName `
            ".minecraft\libraries\net\minecraft\client\1.21.1-20240808.144430\client-1.21.1-20240808.144430-slim.jar")
    } |
    Select-Object -First 1
$mods = Join-Path $mcRoot.FullName ".minecraft\versions\1.21.1-Fabric 0.18.2\mods"
foreach ($jar in Get-ChildItem -LiteralPath $mods `
        -Filter "MechanicianCombat-r*.jar" -File) {
    if ($jar.Name -ne "MechanicianCombat-r906.jar") {
        Move-Item -LiteralPath $jar.FullName `
            -Destination (Join-Path "D:\codex\mod\rebuild\backups" $jar.Name) `
            -Force
    }
}
Copy-Item -LiteralPath $out `
    -Destination (Join-Path $mods "MechanicianCombat-r906.jar") -Force
Get-ChildItem -LiteralPath $mods -Filter "MechanicianCombat-r*.jar" -File |
    Select-Object Name, Length, LastWriteTime
