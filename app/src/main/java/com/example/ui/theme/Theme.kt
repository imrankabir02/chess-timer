package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val GeometricColorScheme = darkColorScheme(
  primary = GeometricAccentLavender,
  onPrimary = GeometricPurpleDark,
  secondary = GeometricActiveCyan,
  onSecondary = GeometricActiveOnCyan,
  background = GeometricDarkBackground,
  onBackground = Color.White,
  surface = GeometricSurfaceDark,
  onSurface = Color.White,
  surfaceVariant = GeometricInactivePlayer,
  onSurfaceVariant = Color.White.copy(alpha = 0.7f)
)

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to true for chess clock dark immersion
  dynamicColor: Boolean = false, // Use our strict theme colors by default
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    else -> GeometricColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
