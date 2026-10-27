# Minimal asar reader: list entries or extract one file (Electron app archives).
#   powershell -File asar.ps1 -Asar <app.asar> -List "plugin"
#   powershell -File asar.ps1 -Asar <app.asar> -Extract "js/plugin_loader.js" -Out <file>
param(
    [Parameter(Mandatory = $true)][string]$Asar,
    [string]$List,
    [string]$Extract,
    [string]$Out
)
$ErrorActionPreference = "Stop"
$bytes = [IO.File]::ReadAllBytes($Asar)
$headerSize = [BitConverter]::ToUInt32($bytes, 4)
$headerJson = [Text.Encoding]::UTF8.GetString($bytes, 8, $headerSize)
$dataStart = 8 + $headerSize
$from = $headerJson.IndexOf('{')
$to = $headerJson.LastIndexOf('}')
$root = $headerJson.Substring($from, $to - $from + 1) | ConvertFrom-Json

function Walk($node, $prefix, $script) {
    foreach ($p in $node.files.PSObject.Properties) {
        $name = $p.Name
        $child = $p.Value
        $path = if ($prefix) { "$prefix/$name" } else { $name }
        if ($child.files) { Walk $child $path $script } else { & $script $path $child }
    }
}

if ($List) {
    $pattern = $List
    Walk $root "" { param($path, $entry)
        if ($path -match $pattern) { "{0}`t{1}" -f $entry.size, $path }
    }
}
if ($Extract) {
    $want = $Extract -replace '\\', '/'
    Walk $root "" { param($path, $entry)
        if ($path -eq $want) {
            $off = $dataStart + [int]$entry.offset
            $len = [int]$entry.size
            $buf = New-Object byte[] $len
            [Array]::Copy($bytes, $off, $buf, 0, $len)
            [IO.File]::WriteAllBytes($Out, $buf)
            Write-Output "extracted $path ($len bytes) -> $Out"
        }
    }
}
