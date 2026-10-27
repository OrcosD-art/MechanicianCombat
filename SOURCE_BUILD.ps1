param(
    [Parameter(Mandatory = $true)]
    [string]$Libs,
    [string]$Out = "",
    [string]$JavaHome = "C:\Program Files\Java\jdk-21"
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
if (-not $Out) {
    $Out = Join-Path $root "release\MechanicianCombat-1.0.0.jar"
}

& (Join-Path $root "tools\build.ps1") `
    -Root $root `
    -Libs $Libs `
    -Fapi (Join-Path $root "fapi-named") `
    -Deps (Join-Path $root "deps") `
    -Out $Out `
    -JavaHome $JavaHome

