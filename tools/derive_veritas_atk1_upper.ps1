param(
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath
)

$ErrorActionPreference = "Stop"

$target = [IO.File]::ReadAllText(
    $AnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json

foreach ($sourceName in @("ATK_1", "ATK_1_to_idle")) {
    $source = $target.animations.$sourceName
    if ($null -eq $source) {
        throw "Missing source animation: $sourceName"
    }

    $clone = $source | ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $keptBones = [ordered]@{}
    foreach ($property in $clone.bones.PSObject.Properties) {
        if ($property.Name -notin @("leg_left", "leg_right")) {
            $keptBones[$property.Name] = $property.Value
        }
    }
    $clone.bones = [pscustomobject]$keptBones

    $targetName = "${sourceName}_upper"
    $target.animations | Add-Member -NotePropertyName $targetName `
        -NotePropertyValue $clone -Force
}

$encoding = New-Object System.Text.UTF8Encoding($false)
$json = $target | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $json, $encoding)

Write-Output "Derived ATK_1_upper and ATK_1_to_idle_upper without leg channels."
