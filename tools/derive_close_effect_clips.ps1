param(
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath,
    [string[]]$Names = @("ATK_1", "ATK_2", "ATK_4", "ATK_hold_over")
)

$ErrorActionPreference = "Stop"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$document = [IO.File]::ReadAllText(
    $AnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$effectBones = @(
    "effect_1", "1_1", "1_2", "1_3", "1_4",
    "effect_2", "2_1", "2_2", "2_3", "2_4"
)

foreach ($name in $Names) {
    $sourceProperty = $document.animations.PSObject.Properties[$name]
    if ($null -eq $sourceProperty) {
        throw "Missing source animation: $name"
    }
    $source = $sourceProperty.Value
    $bones = [ordered]@{}
    foreach ($boneName in $effectBones) {
        $bone = $source.bones.PSObject.Properties[$boneName]
        if ($null -ne $bone) {
            $bones[$boneName] = $bone.Value
        }
    }
    $clip = [ordered]@{
        loop = "hold_on_last_frame"
        animation_length = $source.animation_length
        bones = [pscustomobject]$bones
    }
    $document.animations |
        Add-Member -NotePropertyName "close_effect_$name" `
            -NotePropertyValue ([pscustomobject]$clip) -Force
}

$json = $document | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $json, $utf8NoBom)
Write-Output ("Derived close effect clips: {0}" -f ($Names -join ", "))
