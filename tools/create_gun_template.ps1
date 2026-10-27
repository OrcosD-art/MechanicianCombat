param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$TemplateName = "MechanicianCombat_Gun_Template_r397",
    [string]$ModelIdentifier = "tkgun:new_gun_rig",
    [string]$JavaHome = "C:\Program Files\Java\jdk-21",
    [string]$GsonJar = ""
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Source)) {
    throw "Template source not found: $Source"
}

$sourcePath = (Resolve-Path -LiteralPath $Source).Path
$destinationPath = [IO.Path]::GetFullPath($Destination)
$destinationDir = [IO.Path]::GetDirectoryName($destinationPath)
if (-not (Test-Path -LiteralPath $destinationDir)) {
    throw "Destination directory not found: $destinationDir"
}

$text = [IO.File]::ReadAllText($sourcePath, [Text.Encoding]::UTF8)
$nameBefore = '"name":"BG261015"'
if (-not $text.Contains($nameBefore)) {
    throw "Source model name marker was not found."
}
$text = $text.Replace($nameBefore, '"name":"' + $TemplateName + '"')
$identifierBefore = '"model_identifier":""'
if (-not $text.Contains($identifierBefore)) {
    throw "Source model_identifier marker was not found."
}
$text = $text.Replace($identifierBefore,
        '"model_identifier":"' + $ModelIdentifier + '"')

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($destinationPath, $text, $utf8NoBom)

$toolSource = Join-Path $PSScriptRoot "DedupeGunAnimations.java"
$toolClasses = Join-Path (Split-Path -Parent $PSScriptRoot) "build\animation-tool"
$toolBackup = Join-Path (Split-Path -Parent $PSScriptRoot) "build\animation-template-backup"
if (-not $GsonJar) {
    $GsonJar = Get-ChildItem -Path 'D:\Game\mc\*\.minecraft\libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar' `
        -File | Select-Object -First 1 -ExpandProperty FullName
}
if (-not (Test-Path -LiteralPath $GsonJar)) {
    throw "Gson jar not found: $GsonJar"
}
New-Item -ItemType Directory -Force -Path $toolClasses | Out-Null
& (Join-Path $JavaHome "bin\javac.exe") -cp $GsonJar -d $toolClasses $toolSource
if ($LASTEXITCODE -ne 0) {
    throw "Animation dedupe tool failed to compile."
}
& (Join-Path $JavaHome "bin\java.exe") -cp "$toolClasses;$GsonJar" `
    DedupeGunAnimations "-" $destinationPath $toolBackup "--bbmodel-only"
if ($LASTEXITCODE -ne 0) {
    throw "Template animation merge failed."
}
$text = [IO.File]::ReadAllText($destinationPath, [Text.Encoding]::UTF8)
$doc = $text | ConvertFrom-Json
$bones = @{}
$boneCount = 0
function Add-OutlinerBones($nodes) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        $script:bones[$node.name] = $true
        $script:boneCount++
        if ($node.children) {
            Add-OutlinerBones $node.children
        }
    }
}
foreach ($rootNode in $doc.outliner) {
    if ($rootNode -isnot [string]) {
        $bones[$rootNode.name] = $true
        $boneCount++
        if ($rootNode.children) {
            Add-OutlinerBones $rootNode.children
        }
    }
}
$animationCount = @($doc.animations).Count
$required = @(
    "body", "chest", "head", "arm_left", "arm_right", "leg_left", "leg_right",
    "gun", "barrel", "fire", "aim", "locator", "shells", "change3",
    "magazine_1", "magazine_2"
)
$missing = @($required | Where-Object { -not $bones.ContainsKey($_) })
if ($animationCount -eq 0) {
    throw "Template contains no animations."
}
if ($missing.Count -gt 0) {
    throw "Template is missing required bones: $($missing -join ', ')"
}
Write-Output "Template: $destinationPath"
Write-Output "Bones: $boneCount, animations: $animationCount"
Write-Output "Required skeleton contract: OK"
