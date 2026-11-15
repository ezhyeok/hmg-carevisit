package dev.carevisit.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pleos grayscale, hierarchy and button sizes; mirrored from the proven 쉼표 tokens.
 * Sources: docs-design/foundations/{colors,typography}, components/actions/basic-button.
 * System font supports Korean; no network font or unlicensed bundled font is required.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF131417), onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEEF2), onPrimaryContainer = Color(0xFF131417),
    secondary = Color(0xFF44464E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEEF2), onSecondaryContainer = Color(0xFF131417),
    tertiary = Color(0xFF0064FF), onTertiary = Color.White,
    background = Color(0xFFF7F8FA), onBackground = Color(0xFF131417),
    surface = Color.White, onSurface = Color(0xFF131417),
    surfaceVariant = Color(0xFFEDEEF2), onSurfaceVariant = Color(0xFF44464E),
    outline = Color(0xFF686A72), outlineVariant = Color(0xFFE0E1E6),
    error = Color(0xFFB3261E), onError = Color.White,
)
private val Dark = darkColorScheme(
    primary = Color.White, onPrimary = Color(0xFF131417),
    primaryContainer = Color(0xFF313236), onPrimaryContainer = Color.White,
    secondary = Color(0xFFE0E1E6), onSecondary = Color(0xFF131417),
    secondaryContainer = Color(0xFF313236), onSecondaryContainer = Color.White,
    tertiary = Color(0xFF82B2FF), onTertiary = Color(0xFF131417),
    background = Color(0xFF131417), onBackground = Color.White,
    surface = Color(0xFF1D1E21), onSurface = Color.White,
    surfaceVariant = Color(0xFF313236), onSurfaceVariant = Color(0xFFE0E1E6),
    outline = Color(0xFFA4A8AE), outlineVariant = Color(0xFF44464E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF601410),
)
object VisitType {
    val Hero = TextStyle(fontSize = 48.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold)
    val Title = TextStyle(fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Bold)
    val Section = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
    val Body = TextStyle(fontSize = 28.sp, lineHeight = 38.sp)
    val Strong = Body.copy(fontWeight = FontWeight.SemiBold)
    val Small = TextStyle(fontSize = 24.sp, lineHeight = 32.sp)
    val Label = TextStyle(fontSize = 20.sp, lineHeight = 28.sp)
}
@Composable fun VisitTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Typography(
            displaySmall = VisitType.Hero, headlineMedium = VisitType.Title,
            headlineSmall = VisitType.Section, titleLarge = VisitType.Strong,
            bodyLarge = VisitType.Body, bodyMedium = VisitType.Body, bodySmall = VisitType.Small,
            labelLarge = VisitType.Strong, labelMedium = VisitType.Small, labelSmall = VisitType.Label,
        ), content = content,
    )
}
