
package de.seb.arbeitszeitapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF246B8F),
    secondary = Color(0xFF5F7C8A),
    tertiary = Color(0xFF2D8F74),
    surface = Color(0xFFF9FAFB),
    background = Color(0xFFF5F7FA),
    surfaceVariant = Color(0xFFE9EEF3),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FC2E0),
    secondary = Color(0xFFADC6CF),
    tertiary = Color(0xFF7ED8C0),
)

@Composable
fun ArbeitszeitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
