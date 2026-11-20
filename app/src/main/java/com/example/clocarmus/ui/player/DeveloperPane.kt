package com.example.clocarmus.ui.player

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily

private const val bitcoinAddress = "bc1q78uhgjzje48jxsshngsh5ted7dfv39x426ny2z"

@Composable
internal fun DeveloperPane(grid: PlayerGrid, onDonate: () -> Unit) {
    val context = LocalContext.current
    Image(painterResource(R.drawable.about_developer_avatar), null,
        grid.place(350f, 300f, 300f, 271f), contentScale = ContentScale.Fit)
    PlayerLabel("Developed and designed by", grid.place(100f, 600f, 800f, 60f),
        44f * grid.scale, color = Color(0xff999999))
    PlayerLabel("Ismail Al Bermah", grid.place(100f, 662f, 800f, 60f), 44f * grid.scale, bold = true)
    val links = listOf(
        Triple("Twitter/X", R.drawable.about_x, "https://x.com/smishtw"),
        Triple("LinkedIn", R.drawable.about_linkedin, "https://www.linkedin.com/in/ismail-al-bermah-40b00a392"),
        Triple("GitHub", R.drawable.about_github, "https://github.com/smishberma"),
        Triple("Email", R.drawable.about_email, "mailto:smisheduprog@gmail.com")
    )
    links.forEachIndexed { index, (label, icon, uri) ->
        Box(grid.touchTarget(248f + index * 167f, 792f, 90f)
            .clip(RoundedCornerShape(percent = 20))
            .clickable(role = Role.Button, onClickLabel = "Open $label") {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "No app available to open $label", Toast.LENGTH_SHORT).show()
                }
            }, contentAlignment = Alignment.Center) {
            Image(painterResource(icon), label,
                Modifier.size((listOf(44f, 62f, 66f, 54f)[index] * grid.scale).dp), contentScale = ContentScale.Fit)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(percent = 50)
    PlayerLabel("DONATE", grid.place(290f, 895f, 422f, 100f).clip(shape)
        .background(if (pressed) Color.White else Color.Transparent)
        .border((5f * grid.scale).dp, Color.White, shape)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onDonate),
        40f * grid.scale, bold = true, color = if (pressed) Color.Black else Color.White)
}

@Composable
internal fun DonationPane(grid: PlayerGrid, onClose: () -> Unit) {
    val context = LocalContext.current
    Box(grid.place(69f, 340f, 862f, 540f)
        .clip(RoundedCornerShape((56f * grid.scale).dp)).background(Color(0xff333333)))
    PlayerLabel("Donate Bitcoin", grid.place(110f, 380f, 780f, 64f), 40f * grid.scale, bold = true)
    Column(grid.place(132f, 490f, 736f, 170f)) {
        SelectionContainer {
            Text(bitcoinAddress, color = Color.White, fontFamily = InterFontFamily,
                fontSize = (36f * grid.scale).sp, lineHeight = (50f * grid.scale).sp,
                modifier = Modifier.fillMaxWidth().padding((8f * grid.scale).dp))
        }
    }
    CollectionsButton("Copy address", grid, 291f, 688f) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Bitcoin address", bitcoinAddress))
        Toast.makeText(context, "Bitcoin address copied", Toast.LENGTH_SHORT).show()
    }
    CollectionsButton("Close", grid, 291f, 900f, onClose)
}
