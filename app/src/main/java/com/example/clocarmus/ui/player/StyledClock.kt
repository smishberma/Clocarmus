package com.example.clocarmus.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily
import java.util.Locale

private val lcdClockFont = FontFamily(Font(R.font.clock_lcd))
private val dotMatrixClockFont = FontFamily(Font(R.font.clock_dot_matrix))

/** Alternate faces style the entire clock, sharing the same live time and format preference. */
@Composable
internal fun StyledClock(day: String, time: String, period: String, date: String, style: ClockStyle, grid: PlayerGrid) {
    if (style == ClockStyle.VERTICAL) {
        PlayerLabel(day, grid.place(15f, 196f, 900f, 120f), 100f * grid.scale)
        val density = LocalDensity.current
        val periodWidth = if (period.isEmpty()) 0f else with(density) {
            rememberTextMeasurer().measure(period, clockTextStyle(62f * grid.scale, InterFontFamily, bold = true)).size.width.toDp().value
        } / grid.scale + 24f
        PlayerLabel(time.substringBefore(':'), grid.place(100f - periodWidth / 2f, 350f, 800f, 270f), 270f * grid.scale)
        ClockTimeRow(time.substringAfter(':'), period, grid.place(100f, 575f, 800f, 285f),
            270f * grid.scale, 62f * grid.scale, InterFontFamily, grid.scale, boldPeriod = true)
        PlayerLabel(date, grid.place(15f, 915f, 970f, 110f), 82f * grid.scale)
        return
    }
    val font = if (style == ClockStyle.SEGMENTED) lcdClockFont else dotMatrixClockFont
    ClockFaceLabel(if (style == ClockStyle.SEGMENTED) day.take(3).uppercase(Locale.ENGLISH) else day,
        grid.place(15f, if (style == ClockStyle.SEGMENTED) 325f else 337f, 970f, 140f), 100f * grid.scale, font)
    ClockTimeRow(time, period, grid.place(if (style == ClockStyle.SEGMENTED) 35f else 85f, if (style == ClockStyle.SEGMENTED) 487f else 495f, 850f, 248f),
        (if (style == ClockStyle.SEGMENTED) 270f else 200f) * grid.scale, 80f * grid.scale, font, grid.scale)
    ClockFaceLabel(date, grid.place(15f, if (style == ClockStyle.SEGMENTED) 780f else 748f, 970f, 130f), 100f * grid.scale, font)
}

private fun clockTextStyle(size: Float, font: FontFamily, bold: Boolean = false) = TextStyle(
    color = Color.White, fontFamily = font, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    fontSize = size.sp, letterSpacing = 0.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)
)

@Composable
private fun ClockFaceLabel(text: String, modifier: Modifier, size: Float, font: FontFamily) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val width = rememberTextMeasurer().measure(text, clockTextStyle(size, font)).size.width
        val fit = minOf(1f, with(density) { maxWidth.toPx() } / width.coerceAtLeast(1))
        Text(text, style = clockTextStyle(size * fit, font), maxLines = 1, softWrap = false)
    }
}

/** Fit by changing font size uniformly; AM/PM shares the time's baseline and typeface. */
@Composable
private fun ClockTimeRow(time: String, period: String, modifier: Modifier, size: Float, periodSize: Float,
                         font: FontFamily, scale: Float, boldPeriod: Boolean = false) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val gap = ((if (period.isEmpty()) 0f else 24f) * scale).dp
        val width = measurer.measure(time, clockTextStyle(size, font)).size.width +
            (if (period.isEmpty()) 0 else measurer.measure(period, clockTextStyle(periodSize, font, boldPeriod)).size.width) +
            with(density) { gap.toPx() }
        val fit = minOf(1f, with(density) { maxWidth.toPx() } / width.coerceAtLeast(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, Modifier.alignByBaseline(), style = clockTextStyle(size * fit, font), maxLines = 1, softWrap = false)
            if (period.isNotEmpty()) {
                Spacer(Modifier.width(gap * fit))
                Text(period, Modifier.alignByBaseline(), style = clockTextStyle(periodSize * fit, font, boldPeriod), maxLines = 1, softWrap = false)
            }
        }
    }
}
