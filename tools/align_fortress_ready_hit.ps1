param(
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath
)

$ErrorActionPreference = 'Stop'
$invariant = [Globalization.CultureInfo]::InvariantCulture
$utf8 = New-Object System.Text.UTF8Encoding($false)

function VectorFromValue([object]$value) {
    if ($null -ne $value.vector) {
        return @($value.vector)
    }
    if ($null -ne $value.post) {
        return @($value.post)
    }
    return @($value)
}

function VectorValue([object[]]$value) {
    return [pscustomobject][ordered]@{ vector = @($value) }
}

function LastKey([object]$channel) {
    return @($channel.PSObject.Properties |
            Sort-Object { [double]::Parse($_.Name, $invariant) })[-1]
}

function FirstKey([object]$channel) {
    return @($channel.PSObject.Properties |
            Sort-Object { [double]::Parse($_.Name, $invariant) })[0]
}

function SetKey([object]$channel, [string]$time, [object[]]$value) {
    $channel | Add-Member -NotePropertyName $time `
            -NotePropertyValue (VectorValue $value) -Force
}

$doc = Get-Content -LiteralPath $AnimationPath -Raw | ConvertFrom-Json
$ready = $doc.animations.ready_hit
$hit = $doc.animations.hit
$readyTime = ([double]$ready.animation_length).ToString('0.#####', $invariant)
$hitTime = '0'

$boneNames = @(
    $ready.bones.PSObject.Properties.Name
    $hit.bones.PSObject.Properties.Name
) | Sort-Object -Unique

foreach ($boneName in $boneNames) {
    if ($null -eq $ready.bones.$boneName) {
        $ready.bones | Add-Member -NotePropertyName $boneName `
                -NotePropertyValue ([pscustomobject]@{}) -Force
    }
    if ($null -eq $hit.bones.$boneName) {
        $hit.bones | Add-Member -NotePropertyName $boneName `
                -NotePropertyValue ([pscustomobject]@{}) -Force
    }
    $readyBone = $ready.bones.$boneName
    $hitBone = $hit.bones.$boneName
    $channelNames = @(
        $readyBone.PSObject.Properties.Name
        $hitBone.PSObject.Properties.Name
    ) | Sort-Object -Unique
    foreach ($channelName in $channelNames) {
        if ($null -eq $readyBone.$channelName) {
            $readyBone | Add-Member -NotePropertyName $channelName `
                    -NotePropertyValue ([pscustomobject]@{}) -Force
        }
        if ($null -eq $hitBone.$channelName) {
            $hitBone | Add-Member -NotePropertyName $channelName `
                    -NotePropertyValue ([pscustomobject]@{}) -Force
        }
        $readyChannel = $readyBone.$channelName
        $hitChannel = $hitBone.$channelName
        $readyKeys = @($readyChannel.PSObject.Properties)
        $hitKeys = @($hitChannel.PSObject.Properties)
        $target = if ($hitKeys.Count -gt 0) {
            VectorFromValue (FirstKey $hitChannel).Value
        } elseif ($readyKeys.Count -gt 0) {
            VectorFromValue (LastKey $readyChannel).Value
        } else {
            @(0.0, 0.0, 0.0)
        }
        if ($channelName -eq 'rotation' -and $target.Count -ge 3) {
            $target[1] = 0.0
        }
        SetKey $readyChannel $readyTime $target
        SetKey $hitChannel $hitTime $target
    }
}

# Keep the whole Ready Hit animation facing model-forward.
foreach ($bone in $ready.bones.PSObject.Properties.Value) {
    if ($null -eq $bone.rotation) {
        continue
    }
    foreach ($key in $bone.rotation.PSObject.Properties) {
        $value = $key.Value
        if ($null -ne $value.vector) {
            $value.vector[1] = 0.0
        } elseif ($null -ne $value.post) {
            $value.pre[1] = 0.0
            $value.post[1] = 0.0
        }
    }
}

$upperBones = [ordered]@{}
foreach ($property in $ready.bones.PSObject.Properties) {
    if ($property.Name -notin @('leg_left', 'leg_right')) {
        $upperBones[$property.Name] = $property.Value
    }
}
$upper = [ordered]@{
    loop = $ready.loop
    animation_length = $ready.animation_length
    bones = [pscustomobject]$upperBones
}
foreach ($field in @('sound_effects', 'particle_effects', 'timeline')) {
    if ($null -ne $ready.$field) {
        $upper[$field] = $ready.$field
    }
}
$doc.animations | Add-Member -NotePropertyName 'ready_hit_upper' `
        -NotePropertyValue ([pscustomobject]$upper) -Force

[IO.File]::WriteAllText($AnimationPath,
        ($doc | ConvertTo-Json -Depth 100 -Compress), $utf8)
