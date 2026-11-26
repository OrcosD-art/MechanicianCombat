param(
    [string]$AnimationRoot = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\animations\entity"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$files = @(
    "bowgun_rig.animation.json",
    "abyssal_ancient_cannon_rig.animation.json",
    "veritas_et_justitia_rig.animation.json"
)
$variants = @(
    @{Name = "Focus_ready_quick"; Duration = 0.5D}
)

function Format-Time([double]$value) {
    $text = ("{0:0.0000}" -f $value).TrimEnd("0").TrimEnd(".")
    if (-not $text -or $text -eq "-0") {
        return "0"
    }
    return $text
}

function Scale-TimedObject([object]$value, [double]$factor) {
    if ($null -eq $value -or $value -isnot [pscustomobject]) {
        return $value
    }
    $properties = @($value.PSObject.Properties)
    if ($properties.Count -eq 0) {
        return $value
    }
    if ($properties.Name -contains "vector") {
        return $value
    }

    $scaled = [ordered]@{}
    $timed = $true
    foreach ($property in $properties) {
        $time = 0.0D
        if (-not [double]::TryParse($property.Name,
                [Globalization.NumberStyles]::Float,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$time)) {
            $timed = $false
            break
        }
        $scaledTime = Format-Time ($time * $factor)
        $scaled[$scaledTime] = Copy-JsonValue $property.Value
    }
    if (-not $timed) {
        return $value
    }
    return $scaled
}

function Copy-JsonValue([object]$value) {
    return ($value | ConvertTo-Json -Depth 100 -Compress |
        ConvertFrom-Json)
}

foreach ($file in $files) {
    $path = Join-Path $AnimationRoot $file
    $document = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    $source = $document.animations.Focus_ready
    if ($null -eq $source) {
        throw "$file is missing Focus_ready"
    }
    $duration = [double]$source.animation_length
    if ($duration -le 0.0D) {
        throw "$file Focus_ready has invalid duration $duration"
    }
    if ($document.animations.PSObject.Properties.Name.Contains(
            "Focus_ready_fast")) {
        $document.animations.PSObject.Properties.Remove("Focus_ready_fast")
    }

    foreach ($variant in $variants) {
        $factor = $variant.Duration / $duration
        $clone = Copy-JsonValue $source
        $clone.animation_length = $variant.Duration

        foreach ($bone in $clone.bones.PSObject.Properties) {
            foreach ($channel in @($bone.Value.PSObject.Properties)) {
                $bone.Value.PSObject.Properties[$channel.Name].Value =
                    Scale-TimedObject $channel.Value $factor
            }
        }
        foreach ($section in "sound_effects", "particle_effects", "timeline") {
            if ($clone.PSObject.Properties.Name.Contains($section)) {
                $clone.$section = Scale-TimedObject $clone.$section $factor
            }
        }

        $document.animations | Add-Member -NotePropertyName $variant.Name `
            -NotePropertyValue $clone -Force
        $message = ("{0}: {1} = {2:0.###}s (x{3:0.####})" -f
            $file, $variant.Name, $variant.Duration, $factor)
        Write-Output $message
    }

    [IO.File]::WriteAllText(
        $path,
        ($document | ConvertTo-Json -Depth 100 -Compress),
        (New-Object Text.UTF8Encoding($false)))
}
