# Download the Fabric 1.21.1 dependency jars for the tkgun test instance from Modrinth.
#   powershell -NoProfile -ExecutionPolicy Bypass -File fetch_mods.ps1
param(
    [string]$OutDir = "D:\codex\mod\mods_to_install"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# slug, wanted version_number ("" = newest available for fabric 1.21.1)
$targets = @(
    @{ slug = "geckolib";                  want = "4.8.2" },
    @{ slug = "playeranimator";            want = "2.0.1+1.21.1" },
    @{ slug = "combat-roll";               want = "2.0.6+1.21.1" },
    @{ slug = "cloth-config";              want = "" },
    @{ slug = "shoulder-surfing-reloaded"; want = "4.21.0" },
    @{ slug = "forge-config-api-port";     want = "" }
)

$facets = '[["project_type:mod"],["versions:1.21.1"],["categories:fabric"]]'
foreach ($t in $targets) {
    $slug = $t.slug
    $url = "https://api.modrinth.com/v2/project/$slug/version?loaders=" + [uri]::EscapeDataString('["fabric"]') +
           "&game_versions=" + [uri]::EscapeDataString('["1.21.1"]')
    try { $vers = Invoke-RestMethod -Uri $url -TimeoutSec 40 } catch { Write-Output "!! $slug : $($_.Exception.Message)"; continue }
    if (-not $vers) { Write-Output "!! $slug : no fabric/1.21.1 versions"; continue }
    $pick = $null
    if ($t.want) { $pick = $vers | Where-Object { $_.version_number -eq $t.want } | Select-Object -First 1 }
    if (-not $pick) { $pick = $vers | Select-Object -First 1 }
    if (-not $pick) { Write-Output "!! $slug : version $($t.want) not found"; continue }
    $file = $pick.files | Where-Object { $_.primary } | Select-Object -First 1
    if (-not $file) { $file = $pick.files | Select-Object -First 1 }
    $dest = Join-Path $OutDir $file.filename
    Invoke-WebRequest -Uri $file.url -OutFile $dest -UseBasicParsing -TimeoutSec 180
    $h = (Get-FileHash $dest -Algorithm SHA1).Hash
    $ok = if ($file.hashes.sha1) { ($h -eq $file.hashes.sha1.ToUpper()) } else { $null }
    Write-Output ("{0,-26} v{1,-16} -> {2} ({3:N0} bytes, sha1-ok={4})" -f $slug, $pick.version_number, $file.filename, (Get-Item $dest).Length, $ok)
}
