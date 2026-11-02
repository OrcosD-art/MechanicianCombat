param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath,
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath,
    [string[]]$Animations = @("skill_load", "ready_gather", "gather", "hit", "focus_hit")
)

$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)
$importer = Join-Path $PSScriptRoot "import_veritas_single_animation.ps1"

foreach ($name in $Animations) {
    & $importer -ModelPath $ModelPath -AnimationPath $AnimationPath `
        -AnimationName $name -IncludeLegs
}

$doc = [IO.File]::ReadAllText(
    $AnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$soundFixes = @{
    skill_load = @{
        "0.375" = "reload2"
        "0.58333" = "double_axe_swing01"
        "0.66667" = "mag"
    }
    ready_gather = @{
        "0" = "double_axe_swing01"
    }
    gather = @{
        "0" = "current"
        "0.5" = "imp1"
        "1" = "imp1"
        "1.5" = "imp1"
    }
    hit = @{
        "0" = "smash_ground4"
        "0.04167" = "explosion1"
        "0.5" = "double_axe_swing01"
        "0.58333" = "vent"
        "1.625" = "reload"
        "1.75" = "reload2"
    }
    focus_hit = @{
        "0" = "smash_ground4"
        "0.04167" = "explosion1"
        "0.5" = "double_axe_swing01"
        "0.58333" = "vent"
        "1.625" = "reload"
        "1.75" = "reload2"
    }
}

foreach ($name in $Animations) {
    if (-not $soundFixes.ContainsKey($name)) {
        continue
    }
    $targetEffects = $doc.animations.$name.sound_effects
    foreach ($time in $soundFixes[$name].Keys) {
        $effect = $targetEffects.PSObject.Properties[$time]
        if ($null -ne $effect) {
            $effect.Value.effect = $soundFixes[$name][$time]
        }
    }
}

# The authored skill_load clip has no pose keys after 0.875 s. Keep the
# previous 19-tick gameplay window instead of rendering a long frozen tail.
foreach ($name in @("skill_load", "skill_load_upper")) {
    if ($null -ne $doc.animations.$name) {
        $doc.animations.$name.animation_length = 0.875
    }
}

foreach ($name in $Animations) {
    $source = $doc.animations.$name
    if ($null -eq $source) {
        throw "Missing imported fortress skill animation: $name"
    }
    $clone = $source | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $bones = [ordered]@{}
    foreach ($property in $clone.bones.PSObject.Properties) {
        if ($property.Name -notin @("leg_left", "leg_right")) {
            $bones[$property.Name] = $property.Value
        }
    }
    $clone.bones = [pscustomobject]$bones
    $doc.animations | Add-Member -NotePropertyName "${name}_upper" `
        -NotePropertyValue $clone -Force

    $lowerClone = $source | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $lowerBones = [ordered]@{}
    foreach ($property in $lowerClone.bones.PSObject.Properties) {
        if ($property.Name -in @("body", "leg_left", "leg_right")) {
            $lowerBones[$property.Name] = $property.Value
        }
    }
    $lowerClone.bones = [pscustomobject]$lowerBones
    foreach ($field in @("sound_effects", "particle_effects", "timeline")) {
        if ($null -ne $lowerClone.$field) {
            $lowerClone.PSObject.Properties.Remove($field)
        }
    }
    $doc.animations | Add-Member -NotePropertyName "${name}_lower" `
        -NotePropertyValue $lowerClone -Force
}

# When the skill starts from the deployed crouch, make the first upper-body
# key exactly match Focus_hold. This prevents a one-frame body/chest reset from
# moving both the gun and the backpack before the authored load pose begins.
$focusPose = $doc.animations.Focus_hold
$loadUpper = $doc.animations.skill_load_upper
if ($null -ne $focusPose -and $null -ne $loadUpper) {
    $focusLoad = $loadUpper | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    foreach ($boneProperty in $focusLoad.bones.PSObject.Properties) {
        $focusBone = $focusPose.bones.($boneProperty.Name)
        if ($null -eq $focusBone) {
            continue
        }
        foreach ($channelProperty in $boneProperty.Value.PSObject.Properties) {
            $focusChannel = $focusBone.($channelProperty.Name)
            if ($null -eq $focusChannel) {
                continue
            }
            $value = @()
            if ($focusChannel -is [System.Array]) {
                $value = @($focusChannel)
            } else {
                $first = @($focusChannel.PSObject.Properties |
                    Sort-Object { [double]::Parse($_.Name,
                        [Globalization.CultureInfo]::InvariantCulture) })[0]
                $raw = $first.Value
                if ($null -ne $raw.vector) {
                    $value = @($raw.vector)
                } elseif ($null -ne $raw.post) {
                    $value = @($raw.post)
                } else {
                    $value = @($raw)
                }
            }
            $channelProperty.Value | Add-Member -NotePropertyName "0" `
                -NotePropertyValue ([pscustomobject]@{ vector = $value }) -Force
        }
    }

    # PowerShell Add-Member moves an overwritten "0" key to the end. GeckoLib
    # rejects timestamp 0 when it appears after any other keyframe, so rebuild
    # every timed channel in numeric key order.
    $orderedBones = [ordered]@{}
    foreach ($boneProperty in $focusLoad.bones.PSObject.Properties) {
        $orderedChannels = [ordered]@{}
        foreach ($channelProperty in $boneProperty.Value.PSObject.Properties) {
            $keys = @($channelProperty.Value.PSObject.Properties)
            $numeric = $keys.Count -gt 0
            foreach ($key in $keys) {
                $parsed = 0.0
                if (-not [double]::TryParse($key.Name,
                        [Globalization.NumberStyles]::Float,
                        [Globalization.CultureInfo]::InvariantCulture,
                        [ref]$parsed)) {
                    $numeric = $false
                    break
                }
            }
            if (-not $numeric) {
                $orderedChannels[$channelProperty.Name] = $channelProperty.Value
                continue
            }
            $orderedKeys = [ordered]@{}
            foreach ($key in $keys | Sort-Object {
                    [double]::Parse($_.Name,
                        [Globalization.CultureInfo]::InvariantCulture) }) {
                $orderedKeys[$key.Name] = $key.Value
            }
            $orderedChannels[$channelProperty.Name] =
                [pscustomobject]$orderedKeys
        }
        $orderedBones[$boneProperty.Name] =
            [pscustomobject]$orderedChannels
    }
    $focusLoad.bones = [pscustomobject]$orderedBones

    $doc.animations | Add-Member -NotePropertyName "skill_load_focus_upper" `
        -NotePropertyValue $focusLoad -Force
}

foreach ($name in @("ready_hit", "ready_hit_upper")) {
    if ($null -ne $doc.animations.$name) {
        $doc.animations.PSObject.Properties.Remove($name)
    }
}

[IO.File]::WriteAllText($AnimationPath,
    ($doc | ConvertTo-Json -Depth 100 -Compress), $utf8)

Write-Output "Prepared fortress skill flow: skill_load -> ready_gather -> gather -> hit"
