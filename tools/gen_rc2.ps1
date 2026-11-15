# Rebuild the RC 2 front-facing HUD artwork using native vector primitives.
Add-Type -AssemblyName System.Drawing
$outputDirectory = Join-Path $PSScriptRoot '../fabric_mod/src/main/resources/assets/djimod/textures/gui'
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$bitmap = [Drawing.Bitmap]::new(1000, 560)
$g = [Drawing.Graphics]::FromImage($bitmap)
$g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([Drawing.Color]::Transparent)
function Box($x, $y, $w, $h, $r, $color) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $d = $r * 2
    $path.AddArc($x,$y,$d,$d,180,90)
    $path.AddArc(($x+$w-$d),$y,$d,$d,270,90)
    $path.AddArc(($x+$w-$d),($y+$h-$d),$d,$d,0,90)
    $path.AddArc($x,($y+$h-$d),$d,$d,90,90)
    $path.CloseFigure()
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $g.FillPath($brush,$path)
    $brush.Dispose(); $path.Dispose()
}
function Circle($x,$y,$r,$color) {
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $g.FillEllipse($brush,($x-$r),($y-$r),($r*2),($r*2))
    $brush.Dispose()
}
function Label($text,$x,$y,$size,$color) {
    $font = [Drawing.Font]::new('Arial',$size,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
    $brush = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml($color))
    $g.DrawString($text,$font,$brush,$x,$y)
    $brush.Dispose(); $font.Dispose()
}
# Antennas, shoulder buttons, grips and magnesium-gray shell.
Box 116 5 38 150 17 '#45494d'
Box 846 5 38 150 17 '#45494d'
Box 118 6 34 108 15 '#666c72'
Box 848 6 34 108 15 '#666c72'
Box 65 93 110 38 12 '#4e5256'
Box 825 93 110 38 12 '#4e5256'
Box 0 110 1000 445 64 '#6d7379'
Box 4 106 992 437 60 '#9da3a8'
Box 12 108 976 423 55 '#b9bfc3'
Box 20 115 960 408 50 '#c5cbcf'
Box 32 268 147 253 45 '#a6adb2'
Box 821 268 147 253 45 '#a6adb2'
foreach($y in 320,337,354,371,388,405,422,439,456) {
    Box 48 $y 112 3 1 '#959da3'
    Box 840 $y 112 3 1 '#959da3'
}
# Recessed gimbal wells, metal shafts, textured thumbsticks.
foreach($x in 187,813) {
    Circle $x 194 71 '#e0e4e6'
    Circle $x 194 65 '#737a80'
    Circle $x 194 56 '#363b41'
    Circle $x 194 45 '#21262b'
    Circle $x 194 23 '#8d969f'
    Circle $x 190 18 '#171b1f'
    Circle $x 187 15 '#555e67'
    Circle $x 187 10 '#252b31'
}
# Screen surround: the live image is drawn inside this aperture in-game.
Box 220 195 560 329 17 '#8c959d'
Box 226 201 548 317 14 '#20262c'
Box 240 215 520 293 3 '#0b141a'
Label 'DJI' 464 123 35 '#525b62'
Label 'RC 2' 477 165 15 '#69747d'
Circle 329 156 19 '#78838b'
Circle 329 156 15 '#cdd2d5'
Label 'H' 323 145 17 '#525d65'
Circle 672 156 19 '#78838b'
Circle 672 156 15 '#cdd2d5'
Label 'I' 669 145 17 '#525d65'
foreach($x in 412,427,442,457) { Box $x 178 8 4 1 '#43c990' }
Label 'C  N  S' 527 175 12 '#64717a'
Box 509 174 8 16 3 '#4a545c'
Label 'DJI RC 2' 50 490 14 '#58646d'
Label 'AVATA LINK' 844 490 11 '#58646d'
$bitmap.Save((Join-Path $outputDirectory 'rc2.png'),[Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bitmap.Dispose()
