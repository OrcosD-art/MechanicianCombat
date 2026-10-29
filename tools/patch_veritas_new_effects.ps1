param(
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath
)

$ErrorActionPreference = "Stop"
$encoding = New-Object System.Text.UTF8Encoding($false)
$document = [IO.File]::ReadAllText($AnimationPath, [Text.Encoding]::UTF8) |
    ConvertFrom-Json

$animations = $document.animations

$atk3Sound = $animations.ATK_3.sound_effects.'0.375'
if ($null -eq $atk3Sound) {
    throw "ATK_3 is missing its authored 0.375 sound keyframe"
}
$atk3Sound.effect = "smash_ground1"

$atk3HoldSound = [pscustomobject][ordered]@{
    "0" = [pscustomobject][ordered]@{
        effect = "smash_air1"
        locator = "locator"
    }
}
$animations.ATK_3_hold | Add-Member -NotePropertyName sound_effects `
    -NotePropertyValue $atk3HoldSound -Force

$holdEntries = [ordered]@{}
foreach ($entry in $animations.ATK_hold_over.sound_effects.PSObject.Properties) {
    if ($entry.Name -ne "0.625") {
        $holdEntries[$entry.Name] = $entry.Value
    }
}
$holdEntries["0.79167"] = [pscustomobject][ordered]@{
    effect = "smash_ground3"
    locator = ""
}
$sortedHoldEntries = [ordered]@{}
foreach ($entry in $holdEntries.GetEnumerator() |
        Sort-Object { [double]::Parse($_.Key, [Globalization.CultureInfo]::InvariantCulture) }) {
    $sortedHoldEntries[$entry.Key] = $entry.Value
}
$animations.ATK_hold_over.sound_effects = [pscustomobject]$sortedHoldEntries

if ($null -eq $animations.ATK_hold_over.particle_effects.'0.5' -or
        $null -eq $animations.ATK_hold_over.particle_effects.'0.875') {
    throw "ATK_hold_over is missing its authored thunder particle keyframes"
}

$json = $document | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $json, $encoding)
Write-Output "Applied Veritas sound and particle keyframes: $AnimationPath"
