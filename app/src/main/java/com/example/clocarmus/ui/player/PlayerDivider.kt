package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

/** The divider spans the shell, independently of either pane's centered reference grid. */
@Composable
internal fun PlayerDivider(style: DividerStyle, modifier: Modifier = Modifier) {
    when (style) {
        DividerStyle.HIDDEN -> Unit
        DividerStyle.DEFAULT -> Box(modifier.width(1.dp).fillMaxHeight().background(Color(0xffdddddd)))
        else -> {
            val (asset, aspect) = when (style) {
                DividerStyle.DOTTED -> R.drawable.settings_divider_dotted to (2f / 506.82385f)
                DividerStyle.SQUIGGLY -> R.drawable.settings_divider_squiggly to (11.799561f / 508.23657f)
                else -> R.drawable.settings_divider_beam to (3.9304199f / 507.80643f)
            }
            Image(painterResource(asset), null, modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true),
                contentScale = ContentScale.FillBounds)
        }
    }
}
