param(
    [string]$AnimationPath = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\combat_backpack.animation.json"
)

$ErrorActionPreference = "Stop"
$json = Get-Content -Raw -LiteralPath $AnimationPath | ConvertFrom-Json
$animations = $json.animations

function Frame([double[]]$Vector) {
    return [pscustomobject]@{
        "0.0" = [pscustomobject]@{ vector = $Vector }
    }
}

# Blockbench's Maintenance page is the model's rest pose. Explicit channels are
# required here because GeckoLib otherwise carries C1/C2/C3 values over from AIM.
$maintenance = [pscustomobject]@{
    loop = "hold_on_last_frame"
    animation_length = 1.0
    bones = [pscustomobject]@{
        A = [pscustomobject]@{
            rotation = Frame @(-45, 0, 0)
            position = Frame @(0, 2, -1)
        }
        B = [pscustomobject]@{
            rotation = Frame @(-22.5, 0, 0)
            position = Frame @(0, 0, 0)
        }
        C1 = [pscustomobject]@{
            rotation = Frame @(0, 0, 0)
            position = Frame @(0, 0, 0)
        }
        C2 = [pscustomobject]@{
            rotation = Frame @(0, 0, 0)
            position = Frame @(0, 0, 0)
        }
        C3 = [pscustomobject]@{
            rotation = Frame @(0, 0, 0)
            position = Frame @(0, 0, 0)
        }
    }
}

$animations | Add-Member -NotePropertyName Maintenance -NotePropertyValue $maintenance -Force

$text = $json | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $text, [Text.UTF8Encoding]::new($false))
Write-Output "Maintenance animation entry is present."
