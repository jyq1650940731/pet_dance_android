package app.petdance.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFFF6F2EB)
val Muted = Color(0xFFC4BBB2)
val Line = Color(0xFF34302B)
val AppBackground = Color(0xFF171614)
val Card = Color(0xFF211F1C)
val Chip = Color(0xFF2A2723)
val Accent = Color(0xFFF0B2A6)
val OnAccent = Color(0xFF2A1210)
val Good = Color(0xFF9ED4B8)
val Bad = Color(0xFFF3B4C2)
val Warn = Color(0xFFE6C089)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    background = AppBackground,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Chip,
    onSurfaceVariant = Muted,
    error = Bad,
    onError = Color(0xFF2A1210),
    outline = Line,
)

private val Type = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 13.sp, color = Muted),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun PetDanceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
