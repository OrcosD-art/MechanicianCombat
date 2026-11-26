param(
    [string]$AssetRoot = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\animations\entity"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$sourceName = "bowgun_rig.animation.json"
$targetNames = @(
    "abyssal_ancient_cannon_rig.animation.json",
    "veritas_et_justitia_rig.animation.json"
)

# These clips contain shared player motion updated by the BG261015 pass.
# Weapon-only clips and the Kinetic Sword deliberately remain untouched.
$clips = @(
    "Focus_hold",
    "Focus_ready",
    "Focus_over",
    "Focus_shooting_extra",
    "Focus_shooting1",
    "Focus_shooting4",
    "Focus_shooting6",
    "Focus_shooting7",
    "focus-roll-run",
    "roll",
    "roll_to_idle",
    "roll_to_idle_upper",
    "rolling_fast_ready",
    "sliding",
    "sliding_fast_ready",
    "stand_shooting"
)

$playerBones = @(
    "body",
    "chest",
    "head",
    "arm_left",
    "arm_right",
    "leg_left",
    "leg_right"
)

function Read-Json([string]$path) {
    return Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
}

function Write-Json([string]$path, [object]$document) {
    $json = $document | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText(
        $path,
        $json,
        (New-Object Text.UTF8Encoding($false)))
}

function Copy-JsonValue([object]$value) {
    return ($value | ConvertTo-Json -Depth 100 -Compress | ConvertFrom-Json)
}

$sourcePath = Join-Path $AssetRoot $sourceName
$source = Read-Json $sourcePath

foreach ($targetName in $targetNames) {
    $targetPath = Join-Path $AssetRoot $targetName
    $target = Read-Json $targetPath
    Write-Output "syncing: $targetName"

    foreach ($clip in $clips) {
        $sourceAnimation = $source.animations.$clip
        $targetAnimation = $target.animations.$clip
        if ($null -eq $sourceAnimation) {
            throw "Source clip missing: $clip"
        }
        if ($null -eq $targetAnimation) {
            Write-Output "  skip missing target clip: $clip"
            continue
        }

        $changed = New-Object System.Collections.Generic.List[string]
        foreach ($bone in $playerBones) {
            $sourceHasBone = $sourceAnimation.bones.PSObject.Properties.Name.Contains($bone)
            $targetHasBone = $targetAnimation.bones.PSObject.Properties.Name.Contains($bone)
            $sourceValue = if ($sourceHasBone) {
                $sourceAnimation.bones.$bone
            } else {
                $null
            }
            $targetValue = if ($targetHasBone) {
                $targetAnimation.bones.$bone
            } else {
                $null
            }

            $sourceJson = if ($null -eq $sourceValue) {
                "<absent>"
            } else {
                $sourceValue | ConvertTo-Json -Depth 100 -Compress
            }
            $targetJson = if ($null -eq $targetValue) {
                "<absent>"
            } else {
                $targetValue | ConvertTo-Json -Depth 100 -Compress
            }
            if ($sourceJson -eq $targetJson) {
                continue
            }

            $changed.Add($bone)
            if ($sourceHasBone) {
                $targetAnimation.bones |
                    Add-Member -NotePropertyName $bone `
                        -NotePropertyValue (Copy-JsonValue $sourceValue) -Force
            } elseif ($targetHasBone) {
                $targetAnimation.bones.PSObject.Properties.Remove($bone)
            }
        }

        if ($changed.Count -gt 0) {
            Write-Output ("  {0}: {1}" -f $clip, ($changed -join ", "))
        }
    }

    Write-Json $targetPath $target
}
