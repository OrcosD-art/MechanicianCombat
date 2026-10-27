param(
    [string]$Root = "D:\codex\mod\rebuild",
    [Parameter(Mandatory = $true)]
    [string]$SourceDir
)

$ErrorActionPreference = "Stop"
$rawGeoPath = Join-Path $Root "build\summon_antenna.raw.geo.json"
$rawAnimationPath = Join-Path $Root "build\summon_antenna.raw.animation.json"
$wearablePath = Join-Path $Root "mod\src\main\resources\assets\tkgun\geo\entity\combat_backpack.geo.json"
$rigPath = Join-Path $Root "mod\src\main\resources\assets\tkgun\geo\entity\combat_backpack_rig.geo.json"
$animationPath = Join-Path $Root "mod\src\main\resources\assets\tkgun\animations\entity\combat_backpack.animation.json"
$itemOutput = Join-Path $Root "mod\src\main\resources\assets\tkgun\models\item\summon_antenna.json"

function Clone-JsonValue($value) {
    return $value | ConvertTo-Json -Depth 100 | ConvertFrom-Json
}

$sourceTexture = Get-ChildItem -LiteralPath (Split-Path $SourceDir -Parent) `
    -Recurse -File -Filter "mechanician_combat_backpack.png" |
    Select-Object -First 1 -ExpandProperty FullName
if ($sourceTexture) {
    Copy-Item -LiteralPath $sourceTexture `
        -Destination (Join-Path $Root "mod\src\main\resources\assets\tkgun\textures\entity\combat_backpack.png") -Force
    Copy-Item -LiteralPath $sourceTexture `
        -Destination (Join-Path $Root "mod\src\main\resources\assets\tkgun\textures\item\combat_backpack.png") -Force
}

function Move-Point($point, [double]$dx, [double]$dy, [double]$dz) {
    if ($null -eq $point) {
        return $null
    }
    $px = [double]$point.GetValue(0) + $dx
    $py = [double]$point.GetValue(1) + $dy
    $pz = [double]$point.GetValue(2) + $dz
    return @($px, $py, $pz)
}

function Convert-Cube($sourceCube, [double]$dx, [double]$dy, [double]$dz) {
    $cube = Clone-JsonValue $sourceCube
    $cube.origin = Move-Point $cube.origin $dx $dy $dz
    if ($null -ne $cube.pivot) {
        $cube.pivot = Move-Point $cube.pivot $dx $dy $dz
    }
    return $cube
}

function New-SummonBones($sourceBones, [double]$dx, [double]$dy, [double]$dz) {
    $result = @()
    foreach ($sourceName in @("C1", "C2", "C3")) {
        $source = $sourceBones | Where-Object { $_.name -eq $sourceName } | Select-Object -First 1
        if ($null -eq $source) {
            throw "Missing source bone $sourceName"
        }
        $bone = [ordered]@{
            name = "summon_$sourceName"
            parent = if ($sourceName -eq "C1") { "combat_backpack" } else { "summon_C" + ([int]$sourceName.Substring(1) - 1) }
            pivot = Move-Point $source.pivot $dx $dy $dz
        }
        if ($null -ne $source.rotation) {
            $bone.rotation = @($source.rotation)
        }
        $cubes = @()
        foreach ($cube in @($source.cubes)) {
            $cubes += Convert-Cube $cube $dx $dy $dz
        }
        if ($cubes.Count -gt 0) {
            $bone.cubes = $cubes
        }
        if ($null -ne $source.locators -and $null -ne $source.locators.locator) {
            $bone.locators = @{ locator = Move-Point $source.locators.locator $dx $dy $dz }
        }
        $result += $bone
    }
    $sourceC3 = $sourceBones | Where-Object { $_.name -eq "C3" } | Select-Object -First 1
    $locator = if ($null -ne $sourceC3.locators) { $sourceC3.locators.locator } else { $null }
    if ($null -ne $locator) {
        $result += [ordered]@{
            name = "summon_locator"
            parent = "summon_C3"
            pivot = Move-Point $locator $dx $dy $dz
        }
    }
    return $result
}

$rawGeo = Get-Content -Raw -Encoding UTF8 $rawGeoPath | ConvertFrom-Json
$sourceBones = $rawGeo."minecraft:geometry"[0].bones
foreach ($target in @(
        @{ Path = $wearablePath; Dx = -4.0; Dy = -24.0; Dz = -2.0 },
        @{ Path = $rigPath; Dx = 0.0; Dy = -12.0; Dz = 0.0 })) {
    $document = Get-Content -Raw -Encoding UTF8 $target.Path | ConvertFrom-Json
    $geometry = $document."minecraft:geometry"[0]
    $geometry.bones = @($geometry.bones | Where-Object {
        -not ([string]$_.name).StartsWith("summon_")
    })
    $geometry.bones += New-SummonBones $sourceBones $target.Dx $target.Dy $target.Dz
    [IO.File]::WriteAllText(
        $target.Path,
        ($document | ConvertTo-Json -Depth 100 -Compress),
        [Text.UTF8Encoding]::new($false))
}

$rawAnimation = Get-Content -Raw -Encoding UTF8 $rawAnimationPath | ConvertFrom-Json
$animation = Get-Content -Raw -Encoding UTF8 $animationPath | ConvertFrom-Json
foreach ($name in @("CD", "ready", "aim", "Maintenance")) {
    $targetBones = $animation.animations.$name.bones
    foreach ($old in @($targetBones.PSObject.Properties.Name | Where-Object {
                $_ -in @("summon_C1", "summon_C2", "summon_C3")
            })) {
        $targetBones.PSObject.Properties.Remove($old)
    }
    $sourceBones = $rawAnimation.animations.$name.bones
    if ($null -eq $sourceBones) {
        continue
    }
    foreach ($mapping in @(
            @{ Old = "C1"; New = "summon_C1" },
            @{ Old = "C2"; New = "summon_C2" },
            @{ Old = "C3"; New = "summon_C3" })) {
        $value = $sourceBones.PSObject.Properties[$mapping.Old]
        if ($null -ne $value) {
            $targetBones | Add-Member -NotePropertyName $mapping.New `
                -NotePropertyValue (Clone-JsonValue $value.Value) -Force
        }
    }
}
[IO.File]::WriteAllText(
    $animationPath,
    ($animation | ConvertTo-Json -Depth 100 -Compress),
    [Text.UTF8Encoding]::new($false))

$itemSource = Get-ChildItem -LiteralPath $SourceDir -Recurse -File `
    -Filter "*.bbmodel" | Sort-Object Length |
    Select-Object -First 1
if ($null -eq $itemSource) {
    throw "Summon antenna item model not found below $SourceDir"
}
$itemDocument = Get-Content -Raw -Encoding UTF8 $itemSource.FullName | ConvertFrom-Json
$uvScale = 16.0D / 48.0D
$itemElements = @()
foreach ($sourceElement in $itemDocument.elements) {
    if ($sourceElement.type -ne "cube") {
        continue
    }
    $faces = [ordered]@{}
    foreach ($faceName in @("north", "east", "south", "west", "up", "down")) {
        $face = $sourceElement.faces.$faceName
        if ($null -eq $face -or $null -eq $face.uv -or $null -eq $face.texture) {
            continue
        }
        $faceValue = [ordered]@{
            uv = @(
                [Math]::Round([double]$face.uv[0] * $uvScale, 6),
                [Math]::Round([double]$face.uv[1] * $uvScale, 6),
                [Math]::Round([double]$face.uv[2] * $uvScale, 6),
                [Math]::Round([double]$face.uv[3] * $uvScale, 6))
            texture = "#0"
        }
        if ($face.rotation) {
            $faceValue.rotation = [int]$face.rotation
        }
        $faces[$faceName] = $faceValue
    }
    $element = [ordered]@{
        from = @($sourceElement.from)
        to = @($sourceElement.to)
        faces = $faces
    }
    if ($sourceElement.rotation) {
        for ($axis = 0; $axis -lt 3; $axis++) {
            if ([Math]::Abs([double]$sourceElement.rotation[$axis]) -gt 0.00001D) {
                $element.rotation = [ordered]@{
                    origin = @($sourceElement.origin)
                    axis = @("x", "y", "z")[$axis]
                    angle = [double]$sourceElement.rotation[$axis]
                    rescale = $false
                }
                break
            }
        }
    }
    $itemElements += $element
}
$itemModel = [ordered]@{
    credit = "Converted from author-provided summon antenna item bbmodel"
    texture_size = @(48, 48)
    textures = [ordered]@{
        "0" = "tkgun:item/combat_backpack"
        particle = "tkgun:item/combat_backpack"
    }
    elements = $itemElements
    display = [ordered]@{
        gui = [ordered]@{
            rotation = @(-158, 45.75, -180)
            translation = @(0.75, 3.5, 0)
        }
        fixed = [ordered]@{
            translation = @(0, 3.5, -3.25)
        }
    }
}
[IO.File]::WriteAllText(
    $itemOutput,
    ($itemModel | ConvertTo-Json -Depth 30),
    [Text.UTF8Encoding]::new($false))

$langDir = Join-Path $Root "mod\src\main\resources\assets\tkgun\lang"
foreach ($langName in @("zh_cn.json", "en_us.json")) {
    $langPath = Join-Path $langDir $langName
    $lang = Get-Content -Raw -Encoding UTF8 $langPath | ConvertFrom-Json
    if ($langName -eq "zh_cn.json") {
        $values = [ordered]@{
            "item.tkgun.summon_antenna" = -join ([char[]]@(0x53EC, 0x5524, 0x5929, 0x7EBF))
            "tooltip.tkgun.summon_antenna.deploy" = -join ([char[]]@(
                0x6309, 0x5C04, 0x51FB, 0x952E, 0x5C55, 0x5F00, 0x5929, 0x7EBF, 0xFF1B,
                0x5C55, 0x5F00, 0x5B8C, 0x6210, 0x524D, 0x4E0D, 0x80FD, 0x6807, 0x8BB0, 0x3002))
            "tooltip.tkgun.summon_antenna.mark" = -join ([char[]]@(
                0x5C55, 0x5F00, 0x540E, 0x6309, 0x5C04, 0x51FB, 0x952E, 0x6807, 0x8BB0,
                0x51C6, 0x661F, 0x9644, 0x8FD1, 0x32, 0x30, 0x683C, 0x5185, 0x5B9E, 0x4F53, 0x3002))
            "tooltip.tkgun.summon_antenna.cast" = -join ([char[]]@(
                0x518D, 0x6B21, 0x6309, 0x952E, 0x53EC, 0x5524, 0x94F8, 0x9020, 0x5080, 0x5121,
                0xFF1B, 0x65E0, 0x76EE, 0x6807, 0x65F6, 0x751F, 0x6210, 0x5728, 0x51C6, 0x661F,
                0x65B9, 0x5411, 0x38, 0x683C, 0x5185, 0x3002))
            "hud.tkgun.summon_antenna.dormant" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0xFF1A, 0x6309, 0x58, 0x5C55, 0x5F00))
            "hud.tkgun.summon_antenna.ready" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0xFF1A, 0x5C55, 0x5F00, 0x4E2D))
            "hud.tkgun.summon_antenna.armed" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0xFF1A, 0x6309, 0x58, 0x6807, 0x8BB0))
            "hud.tkgun.summon_antenna.marked" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0xFF1A, 0x5DF2, 0x6807, 0x8BB0))
            "hud.tkgun.summon_antenna.cast" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0xFF1A, 0x53EC, 0x5524, 0x4E2D))
            "hud.tkgun.summon_antenna.cooldown" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF, 0x51B7, 0x5374, 0xFF1A, 0x25, 0x73, 0x79D2))
            "hud.tkgun.summon_antenna.charge" = -join ([char[]]@(
                0x53EC, 0x5524, 0x5929, 0x7EBF))
            "item.tkgun.backpack_cooldown_reset" = -join ([char[]]@(
                0x80CC, 0x5305, 0x51B7, 0x5374, 0x6E05, 0x9664, 0x5668))
        }
    } else {
        $values = [ordered]@{
            "item.tkgun.summon_antenna" = "Summon Antenna"
            "tooltip.tkgun.summon_antenna.deploy" = "Press the action key to deploy; marking is locked until deployment finishes."
            "tooltip.tkgun.summon_antenna.mark" = "After deployment, press the action key to mark a nearby entity within 20 blocks of the crosshair."
            "tooltip.tkgun.summon_antenna.cast" = "Press again to summon a Molten Golem; without a target it appears up to 8 blocks along the crosshair."
            "hud.tkgun.summon_antenna.dormant" = "Summon Antenna: press X to deploy"
            "hud.tkgun.summon_antenna.ready" = "Summon Antenna: deploying"
            "hud.tkgun.summon_antenna.armed" = "Summon Antenna: press X to mark"
            "hud.tkgun.summon_antenna.marked" = "Summon Antenna: target marked"
            "hud.tkgun.summon_antenna.cast" = "Summon Antenna: summoning"
            "hud.tkgun.summon_antenna.cooldown" = "Summon Antenna cooldown: %s s"
            "hud.tkgun.summon_antenna.charge" = "Summon Antenna"
            "item.tkgun.backpack_cooldown_reset" = "Backpack Cooldown Reset"
        }
    }
    foreach ($entry in $values.GetEnumerator()) {
        $lang | Add-Member -NotePropertyName $entry.Key `
            -NotePropertyValue $entry.Value -Force
    }
    [IO.File]::WriteAllText(
        $langPath,
        ($lang | ConvertTo-Json -Depth 20),
        [Text.UTF8Encoding]::new($false))
}

Write-Output "Merged summon antenna geometry, animation channels, and item model."
