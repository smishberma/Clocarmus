package com.example.clocarmus.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily

/** One 2000 × 1236 reference canvas, uniformly fitted with black letterboxing. */
@Composable
internal fun SplashScreen(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / 2000f, maxHeight.value / 1236f)
        Box(Modifier.size((2000 * scale).dp, (1236 * scale).dp)) {
            Image(painterResource(R.drawable.splash_background), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Image(ImageBitmap.imageResource(R.drawable.splash_logo), "Clocarmus logo",
                Modifier.offset((804.5f * scale).dp, (362.5f * scale).dp).size((391f * scale).dp), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
            Box(Modifier.offset(x = (-2.5f * scale).dp, y = (772f * scale).dp).fillMaxWidth().height((112f * scale).dp), contentAlignment = Alignment.Center) {
                Text("Clocarmus", maxLines = 1, softWrap = false, style = TextStyle(
                    fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,
                    fontSize = with(LocalDensity.current) { (106f * scale).dp.toSp() },
                    color = Color(0xFFFFCCA8), letterSpacing = with(LocalDensity.current) { (-3.25f * scale).dp.toSp() },
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                ))
            }
        }
    }
}
