param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination,
    [string]$JavaHome = "C:\Program Files\Java\jdk-21",
    [string]$GsonJar = ""
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Source)) {
    throw "Source template not found: $Source"
}
if (-not $GsonJar) {
    $GsonJar = Get-ChildItem -Path 'D:\Game\mc\*\.minecraft\libraries\com\google\code\gson\gson\2.10.1\gson-2.10.1.jar' `
        -File | Select-Object -First 1 -ExpandProperty FullName
}
if (-not (Test-Path -LiteralPath $GsonJar)) {
    throw "Gson jar not found: $GsonJar"
}

$toolSource = Join-Path $PSScriptRoot "ExtractGunSubtreeTemplate.java"
$toolClasses = Join-Path (Split-Path -Parent $PSScriptRoot) "build\gun-only-template-tool"
New-Item -ItemType Directory -Force -Path $toolClasses | Out-Null
& (Join-Path $JavaHome "bin\javac.exe") -cp $GsonJar -d $toolClasses $toolSource
if ($LASTEXITCODE -ne 0) {
    throw "Gun-only template tool failed to compile."
}
& (Join-Path $JavaHome "bin\java.exe") -cp "$toolClasses;$GsonJar" `
    ExtractGunSubtreeTemplate $Source $Destination
if ($LASTEXITCODE -ne 0) {
    throw "Gun-only template extraction failed."
}

$doc = [IO.File]::ReadAllText($Destination, [Text.Encoding]::UTF8) | ConvertFrom-Json
if (@($doc.outliner).Count -ne 1 -or $doc.outliner[0].name -ne "gun") {
    throw "Extracted template does not have gun as its sole root group."
}
$animationNames = @($doc.animations | ForEach-Object { $_.name })
if ($animationNames.Count -ne 2 -or
        $animationNames -notcontains "Focus_ready" -or
        $animationNames -notcontains "Focus_over") {
    throw "Extracted template animation set is incorrect."
}
Write-Output "Validated gun-only template: $Destination"
