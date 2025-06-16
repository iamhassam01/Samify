# Samify Icon Update Script
# This script replaces all legacy webp icon files with PNG versions

Write-Host "=== Samify Icon Update Script ===" -ForegroundColor Green

# Define the source icon path
$sourcePath = "c:\Users\hp 255 G9\Downloads\Metrolist-12.2.0\Metrolist-12.2.0\app\src\main\res\drawable\ic_launcher_playstore.png"

# Define the target directories and their dimensions
$iconDensities = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

$baseResPath = "c:\Users\hp 255 G9\Downloads\Metrolist-12.2.0\Metrolist-12.2.0\app\src\main\res"

# Check if source file exists
if (-not (Test-Path $sourcePath)) {
    Write-Host "Error: Source icon file not found at $sourcePath" -ForegroundColor Red
    exit 1
}

Write-Host "Source icon found: $sourcePath" -ForegroundColor Green

# For each density, we'll copy the PNG file to replace the webp files
foreach ($density in $iconDensities.Keys) {
    $targetDir = Join-Path $baseResPath $density
    $targetIcon = Join-Path $targetDir "ic_launcher.png"
    $targetIconRound = Join-Path $targetDir "ic_launcher_round.png"
    
    Write-Host "Processing $density..." -ForegroundColor Yellow
    
    # Create directory if it doesn't exist
    if (-not (Test-Path $targetDir)) {
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
    }
    
    # Copy the source icon to both regular and round icon locations
    Copy-Item -Path $sourcePath -Destination $targetIcon -Force
    Copy-Item -Path $sourcePath -Destination $targetIconRound -Force
    
    # Remove old webp files if they exist
    $webpIcon = Join-Path $targetDir "ic_launcher.webp"
    $webpIconRound = Join-Path $targetDir "ic_launcher_round.webp"
    
    if (Test-Path $webpIcon) {
        Remove-Item $webpIcon -Force
        Write-Host "  Removed old webp file: $webpIcon" -ForegroundColor Cyan
    }
    
    if (Test-Path $webpIconRound) {
        Remove-Item $webpIconRound -Force
        Write-Host "  Removed old webp file: $webpIconRound" -ForegroundColor Cyan
    }
    
    Write-Host "  Created PNG icons for $density" -ForegroundColor Green
}

Write-Host ""
Write-Host "=== Icon Update Complete ===" -ForegroundColor Green
Write-Host "All legacy webp icons have been replaced with your custom PNG icon." -ForegroundColor Green
Write-Host "The app should now show consistent icons across all Android versions." -ForegroundColor Green
