param(
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath,
    [string[]]$Names = @(
        "ATK_2",
        "ATK_2_to_idle",
        "ATK_3",
        "ATK_3_to_idle",
        "ATK_4",
        "ATK_4_to_idle",
        "ATK_hold_over",
        "ATK_holdover_to_idle"
    )
)

$ErrorActionPreference = "Stop"
$document = [IO.File]::ReadAllText(
    $AnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$ordered = [ordered]@{}
foreach ($property in $document.animations.PSObject.Properties) {
    $ordered[$property.Name] = $property.Value
}

foreach ($name in $Names) {
    $sourceProperty = $document.animations.PSObject.Properties[$name]
    if ($null -eq $sourceProperty) {
        throw "Missing source animation: $name"
    }
    $source = $sourceProperty.Value
    $lower = [ordered]@{
        loop = $source.loop
        animation_length = $source.animation_length
        bones = [ordered]@{}
    }
    foreach ($boneName in @("body", "leg_left", "leg_right")) {
        $bone = $source.bones.PSObject.Properties[$boneName]
        if ($null -ne $bone) {
            $lower.bones[$boneName] = $bone.Value
        }
    }
    $ordered["${name}_lower"] = [pscustomobject]$lower
}

$document.animations = [pscustomobject]$ordered
$encoding = New-Object System.Text.UTF8Encoding($false)
$json = $document | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $json, $encoding)
Write-Output ("Derived {0} lower-body animations." -f $Names.Count)
