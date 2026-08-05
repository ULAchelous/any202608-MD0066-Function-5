package io.ula.aiy.mb.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ClaudeAccentOrange,
    secondary = ClaudeMutedOrange,
    tertiary = ClaudeMutedOlive,
    background = ClaudeDarkBackground,
    surface = ClaudeDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = ClaudeDarkText,
    onSurface = ClaudeDarkText,
    surfaceVariant = Color(0xFF33322E),
    onSurfaceVariant = ClaudeWarmWhite,
    secondaryContainer = Color(0xFF4A3926),
    onSecondaryContainer = ClaudeMutedOrange,
    outline = ClaudeDivider
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudeDeepCharcoal,
    secondary = ClaudeMutedOrange,
    tertiary = ClaudeMutedOlive,
    background = ClaudeWarmWhite,
    surface = ClaudeCream,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = ClaudeDeepCharcoal,
    onSurface = ClaudeDeepCharcoal,
    surfaceVariant = ClaudeTagTan,
    onSurfaceVariant = ClaudeTextGray,
    secondaryContainer = ClaudeStatusYellow,
    onSecondaryContainer = ClaudeMutedOrange,
    outline = ClaudeDivider
)

@Composable
fun MythBustersTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Disable dynamic color to strictly use our Claude palette
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
