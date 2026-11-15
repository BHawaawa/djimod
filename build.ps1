# Run from the repository root; additional flags are passed to the matrix tool.
# Examples: .\build.ps1 -AllTargets
#           .\build.ps1 -Loader Fabric -Minecraft 1.20.1
& (Join-Path $PSScriptRoot 'fabric_mod\tools\build_matrix.ps1') @args
if ($LASTEXITCODE) { exit $LASTEXITCODE }
