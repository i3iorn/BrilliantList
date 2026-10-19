package dev.schrammel.brilliantlist.core_ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D9DC5),
    onPrimary = Color.White,
    secondary = Color(0xFFF6C28B),
    onSecondary = Color.Black,
    background = Color(0xFFF7FFF7),
    onBackground = Color(0xFF22223B),
    surface = Color.White,
    onSurface = Color(0xFF22223B)
)

@Composable
fun BrilliantListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}

