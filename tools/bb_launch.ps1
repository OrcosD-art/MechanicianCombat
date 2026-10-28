param(
    [int]$Port = 9333,
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb4126",
    # NOTE: keep this file ASCII-only (Windows PowerShell 5.1 reads .ps1 as ANSI when there is no BOM).
    [string]$Exe = "C:\Users\lmc\AppData\Local\Programs\Blockbench\Blockbench.exe"
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Profile | Out-Null
Start-Process -FilePath $Exe -ArgumentList "--remote-debugging-port=$Port", "--no-sandbox", "--user-data-dir=$Profile" -WindowStyle Hidden
Write-Output "launched $Exe on port $Port profile=$Profile"
