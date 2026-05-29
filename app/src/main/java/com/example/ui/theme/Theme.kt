package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = CorpIceBlue,
    onPrimary = CorpNavy,
    primaryContainer = CorpNavy,
    onPrimaryContainer = CorpIceBlue,
    secondary = CorpOutline,
    background = CorpTextDark,
    surface = CorpTextDark,
    onSurface = CorpLightBg,
    outline = CorpOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CorpNavy,
    onPrimary = Color.White,
    primaryContainer = CorpIceBlue,
    onPrimaryContainer = CorpNavy,
    secondary = CorpTextGray,
    secondaryContainer = CorpGrayMuted,
    onSecondaryContainer = CorpTextDark,
    background = CorpLightBg,
    surface = CorpLightBg,
    onBackground = CorpTextDark,
    onSurface = CorpTextDark,
    outline = CorpOutline,
    outlineVariant = CorpOutline
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamicColor to strictly respect user's requested corporate palette
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
