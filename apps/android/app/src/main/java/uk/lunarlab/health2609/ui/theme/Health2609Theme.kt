package uk.lunarlab.health2609.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightPalette = lightColorScheme(
    primary = Color(0xFF286552),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFAEECD5),
    onPrimaryContainer = Color(0xFF07382A),
    secondary = Color(0xFF52645D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8DF),
    onSecondaryContainer = Color(0xFF26352F),
    tertiary = Color(0xFF53637A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDAE4FF),
    onTertiaryContainer = Color(0xFF26344A),
    background = Color(0xFFF7FAF7),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF7FAF7),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DF),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFC0C9C3)
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFF92D0BA),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0C4F3D),
    onPrimaryContainer = Color(0xFFAEECD5),
    secondary = Color(0xFFB9CCC2),
    onSecondary = Color(0xFF24342E),
    secondaryContainer = Color(0xFF3A4B44),
    onSecondaryContainer = Color(0xFFD5E8DF),
    tertiary = Color(0xFFBBC7E2),
    onTertiary = Color(0xFF253044),
    tertiaryContainer = Color(0xFF3B475C),
    onTertiaryContainer = Color(0xFFDAE4FF),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE1E4E1),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE1E4E1),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C3),
    outline = Color(0xFF8A938E),
    outlineVariant = Color(0xFF404944)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Health2609Theme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)

        darkTheme -> DarkPalette
        else -> LightPalette
    }

    val shapes = Shapes(
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp),
        largeIncreased = RoundedCornerShape(40.dp),
        extraLarge = RoundedCornerShape(48.dp),
        extraLargeIncreased = RoundedCornerShape(56.dp)
    )

    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = shapes,
        content = content
    )
}
