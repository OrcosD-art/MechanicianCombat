param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Runtime
)

$ErrorActionPreference = "Stop"
$dressName = [string][char]0x76D4 + [char]0x7532 + [char]0x7740 +
    [char]0x88C5
$activeName = [string][char]0x6FC0 + [char]0x6D3B
$inactiveName = [string][char]0x672A + [char]0x542F + [char]0x52A8
$animationNames = [ordered]@{}
$animationNames[$dressName] = $dressName
$animationNames["2"] = $inactiveName
$animationNames[$activeName] = $activeName

function Normalize-Key([string]$key) {
    $number = 0.0
    if ([double]::TryParse($key,
            [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$number)) {
        return $number.ToString("R",
            [Globalization.CultureInfo]::InvariantCulture)
    }
    return $key
}

function Add-Leaves([object]$value, [string]$path,
        [Collections.Generic.List[object]]$output) {
    if ($null -eq $value) {
        $output.Add([pscustomobject]@{ Path = $path; Value = "<null>" })
        return
    }
    if ($value -is [array]) {
        $text = ($value | ForEach-Object {
            [Convert]::ToString($_,
                [Globalization.CultureInfo]::InvariantCulture)
        }) -join ","
        $output.Add([pscustomobject]@{ Path = $path; Value = $text })
        return
    }
    if ($value -is [pscustomobject]) {
        foreach ($property in $value.PSObject.Properties) {
            $next = if ($path) {
                $path + "/" + (Normalize-Key ([string]$property.Name))
            } else {
                Normalize-Key ([string]$property.Name)
            }
            Add-Leaves $property.Value $next $output
        }
        return
    }
    $output.Add([pscustomobject]@{
        Path = $path
        Value = [Convert]::ToString($value,
            [Globalization.CultureInfo]::InvariantCulture)
    })
}

function Read-Leaves([string]$path, [bool]$isSource) {
    $document = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    $output = [Collections.Generic.List[object]]::new()
    foreach ($animation in $document.animations.PSObject.Properties) {
        $name = if ($isSource) {
            [string]$animationNames[[string]$animation.Name]
        } else {
            [string]$animation.Name
        }
        Add-Leaves $animation.Value ("anim/" + $name) $output
    }
    return $output
}

$sourceLeaves = Read-Leaves $Source $true
$runtimeLeaves = Read-Leaves $Runtime $false
$sourceMap = @{}
foreach ($leaf in $sourceLeaves) {
    $sourceMap[$leaf.Path] = $leaf.Value
}
$runtimeMap = @{}
foreach ($leaf in $runtimeLeaves) {
    $runtimeMap[$leaf.Path] = $leaf.Value
}

$paths = @($sourceMap.Keys) + @($runtimeMap.Keys) |
    Sort-Object -Unique
$differences = 0
foreach ($path in $paths) {
    $sourceValue = if ($sourceMap.ContainsKey($path)) {
        $sourceMap[$path]
    } else {
        "<missing>"
    }
    $runtimeValue = if ($runtimeMap.ContainsKey($path)) {
        $runtimeMap[$path]
    } else {
        "<missing>"
    }
    if ($sourceValue -ne $runtimeValue) {
        $differences++
        Write-Output ("DIFF {0} source={1} runtime={2}" -f
            $path, $sourceValue, $runtimeValue)
    }
}
Write-Output ("DIFFERENCES={0}" -f $differences)
