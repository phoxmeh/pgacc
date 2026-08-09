package com.catcontroller.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DisplayGreen   = Color(0xFF00FF66)
val DisplayAmber   = Color(0xFFFFAA00)
val BtnOn          = Color(0xFF2ECC71)
val BtnOff         = Color(0xFF2A2A2A)
val BtnPtt         = Color(0xFFE74C3C)
val BtnPttActive   = Color(0xFFFF1744)
val SurfaceDark    = Color(0xFF1A1A1A)
val SurfaceMid     = Color(0xFF252525)
val SurfaceCard    = Color(0xFF2F2F2F)
val OnSurface      = Color(0xFFE0E0E0)
val Accent         = Color(0xFFFFAA00)
val Muted          = Color(0xFF888888)
val MeterGreen     = Color(0xFF00E676)
val MeterOrange    = Color(0xFFFF9100)
val MeterRed       = Color(0xFFFF1744)

private val DarkColorScheme = darkColorScheme(
    primary         = Accent,
    onPrimary       = Color.Black,
    primaryContainer= Color(0xFF3D2B00),
    secondary       = BtnOn,
    onSecondary     = Color.Black,
    background      = Color(0xFF111111),
    onBackground    = OnSurface,
    surface         = SurfaceDark,
    onSurface       = OnSurface,
    surfaceVariant  = SurfaceMid,
    outline         = Color(0xFF444444),
    error           = BtnPtt,
)

@Composable
fun CatControllerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = Typography(),
        content     = content,
    )
}
