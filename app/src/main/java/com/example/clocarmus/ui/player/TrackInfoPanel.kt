package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily

@Composable
internal fun TrackInfoPanel(grid: PlayerGrid, track: TrackMenuEntry?, onClose: () -> Unit, filename: String? = null) {
    val content = trackInfoContent(track, filename)
    val scroll = rememberScrollState()
    MenuAssetButton("Back", R.drawable.menu_back_button, R.drawable.menu_back_button_pressed,
        grid, 55f, 20f, 172f, 172f, onClose)
    CollectionsButton("Close", grid, 293f, onClose)
    Box(grid.place(69f, 304f, 862f, 754f).clip(RoundedCornerShape((80f * grid.scale).dp))
        .background(Color(0xff333333)))
    Row(grid.place(69f, 326f, 862f, 50f), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.options_info_icon), null, Modifier.size((30f * grid.scale).dp))
        Spacer(Modifier.width((22f * grid.scale).dp))
        PlayerLabel("TRACK INFO", Modifier.fillMaxHeight(), 28f * grid.scale, bold = true)
    }
    Column(grid.place(132f, 389f, 700f, 624f).verticalScroll(scroll)) {
        content.notice?.let {
            TagText(it, 34f * grid.scale, Color(0xffcccccc))
            Spacer(Modifier.height((40f * grid.scale).dp))
        }
        content.fields.forEach { (label, value) ->
            TagText(label, 34f * grid.scale, Color(0xffcccccc))
            TagText(value ?: "Empty", 48f * grid.scale, if (value == null) Color(0xffcccccc) else Color.White)
            Spacer(Modifier.height((33f * grid.scale).dp))
        }
    }
    if (scroll.maxValue > 0) {
        val viewport = with(LocalDensity.current) { (624f * grid.scale).dp.toPx() }
        val thumb = (viewport / (viewport + scroll.maxValue) * 620f).coerceAtLeast(40f)
        val offset = scroll.value.toFloat() / scroll.maxValue * (620f - thumb)
        Box(grid.place(859f, 393f, 25f, 620f).background(Color(0xff707070)))
        Box(grid.place(859f, 393f + offset, 27f, thumb)
            .clip(RoundedCornerShape((3f * grid.scale).dp)).background(Color.White))
    }
}

@Composable
private fun TagText(value: String, size: Float, color: Color) {
    Text(value, style = TextStyle(fontFamily = InterFontFamily, fontSize = size.sp,
        lineHeight = (size * 1.12f).sp, letterSpacing = 0.sp, color = color,
        platformStyle = PlatformTextStyle(includeFontPadding = false)))
}
