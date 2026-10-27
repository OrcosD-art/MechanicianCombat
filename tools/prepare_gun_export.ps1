param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [Parameter(Mandatory = $true)][string[]]$EnableGroups
)
$ErrorActionPreference = "Stop"
$model = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Source).Path,
    [Text.Encoding]::UTF8) | ConvertFrom-Json

$wanted = New-Object 'System.Collections.Generic.HashSet[string]'
foreach ($name in $EnableGroups) {
    [void]$wanted.Add($name)
}
$visit = $null
$visit = {
    param($node)
    foreach ($child in @($node.children)) {
        if ($child -is [string]) {
            continue
        }
        if ($wanted.Contains([string]$child.name)) {
            if ($child.PSObject.Properties.Name -contains "export") {
                $child.export = $true
            } else {
                $child | Add-Member -NotePropertyName export -NotePropertyValue $true
            }
        }
        & $visit $child
    }
}
& $visit $model.outliner[0]
foreach ($element in @($model.elements)) {
    if ($element.PSObject.Properties.Name -contains "export") {
        $element.export = $true
    } else {
        $element | Add-Member -NotePropertyName export -NotePropertyValue $true
    }
}

$out = [IO.Path]::GetFullPath($Destination)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $out) | Out-Null
[IO.File]::WriteAllText($out, ($model | ConvertTo-Json -Depth 100 -Compress),
    (New-Object Text.UTF8Encoding($false)))
Write-Output "Prepared export model: $out"
