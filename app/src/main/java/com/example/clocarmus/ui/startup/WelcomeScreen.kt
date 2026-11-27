package com.example.clocarmus.ui.startup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.clocarmus.R
import com.example.clocarmus.ui.theme.InterFontFamily

internal enum class WelcomePage { Welcome, Warning, Permission, Folders, Loading }

/** The supplied 2000 × 1236 landscape canvas, fitted uniformly on other displays. */
@Composable
internal fun WelcomeScreen(page: WelcomePage, folders: List<OnboardingFolder>, onNext: () -> Unit,
                           onPermission: () -> Unit, onAdd: () -> Unit, onRemove: (String) -> Unit,
                           onExclusions: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val s = minOf(maxWidth.value / 2000f, maxHeight.value / 1236f)
        fun place(x: Float, y: Float, w: Float, h: Float) = Modifier.offset((x*s).dp, (y*s).dp).size((w*s).dp, (h*s).dp)
        @Composable fun label(text: String, x: Float, y: Float, w: Float, h: Float, size: Float,
                              bold: Boolean = false, color: Color = Color.White) {
            Box(place(x,y,w,h), contentAlignment = Alignment.CenterStart) {
                Text(text, color = color, softWrap = false, style = TextStyle(fontFamily = InterFontFamily,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontSize = with(LocalDensity.current) { (size*s).dp.toSp() },
                    lineHeight = with(LocalDensity.current) { (size*1.32f*s).dp.toSp() },
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
            }
        }
        @Composable fun line(x: Float, y: Float) { Box(place(x,y,939f,4f).background(Color(0xffe6c4d8))) }
        @Composable fun button(name: String, normal: Int, pressed: Int, x: Float, y: Float, w: Float, click: () -> Unit) {
            val interaction = remember { MutableInteractionSource() }
            val down by interaction.collectIsPressedAsState()
            Image(painterResource(if (down) pressed else normal), name,
                place(x,y,w,100f).clip(RoundedCornerShape(percent = 50))
                    .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = click),
                contentScale = ContentScale.FillBounds)
        }
        @Composable fun next(x: Float, y: Float) = button("Next", R.drawable.welcome_next_button,
            R.drawable.welcome_next_button_pressed, x,y,240f,onNext)
        Box(Modifier.size((2000*s).dp, (1236*s).dp)) {
            when (page) {
                WelcomePage.Welcome -> {
                    label("Welcome to",200f,226f,1000f,112f,102f)
                    label("Clocarmus",200f,324f,1000f,112f,102f,true,Color(0xffff7f22))
                    line(191f,535f)
                    label("Your minimal offline music\nplayer for your car.",200f,637f,1100f,190f,72f)
                    Image(painterResource(R.drawable.welcome_logo), "Clocarmus logo", place(1435f,335f,395f,395f))
                    next(1512f,913f)
                }
                WelcomePage.Warning -> {
                    label("DRIVE SAFELY",240f,324f,1500f,112f,102f,true)
                    line(231f,486f)
                    label("Pay attention to the road, obey local laws,\nand don't drive distracted.",240f,540f,1580f,190f,72f)
                    button("I understand",R.drawable.welcome_i_understand_button,R.drawable.welcome_i_understand_button_pressed,
                        723f,825f,554f,onNext)
                }
                WelcomePage.Permission -> {
                    // Allow the full line height, including the descender in “Storage”.
                    label("Storage permission",198f,235f,1500f,140f,102f,true)
                    line(189f,397f)
                    label("Clocarmus requires permission\nto storage for music files.",198f,451f,1600f,190f,72f)
                    button("Grant permission",R.drawable.welcome_grant_permission_button,R.drawable.welcome_grant_permission_button_pressed,
                        1018f,757f,698f,onPermission)
                }
                WelcomePage.Folders -> {
                    label("Select folder to scan",200f,226f,1500f,112f,102f)
                    label("Select folders that contain\nyour music files.",200f,371f,1500f,190f,72f)
                    line(191f,617f)
                    Column(place(210f,652f,930f,500f).verticalScroll(rememberScrollState())) {
                        if (folders.isEmpty()) Text("No folders added", color = Color.White, fontFamily = InterFontFamily,
                            fontSize = with(LocalDensity.current) { (40*s).dp.toSp() })
                        folders.forEach { folder ->
                            // Text line metrics can exceed the reference row height on some devices.
                            // Let both lines determine the row height inside the scrolling viewport.
                            Row(Modifier.fillMaxWidth().heightIn(min = (116*s).dp)
                                .padding(vertical = (8*s).dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(R.drawable.folder_folder_icon), null, Modifier.size((86*s).dp))
                                Column(Modifier.weight(1f).padding(start = (30*s).dp)) {
                                    Text(folder.name, color = Color.White, fontFamily = InterFontFamily, fontWeight = FontWeight.Bold,
                                        fontSize = with(LocalDensity.current) { (40*s).dp.toSp() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(folder.location, color = Color.White, fontFamily = InterFontFamily,
                                        fontSize = with(LocalDensity.current) { (38*s).dp.toSp() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Box(Modifier.size((80*s).dp).clickable(role = Role.Button) { onRemove(folder.uri) }, contentAlignment = Alignment.Center) {
                                    Image(painterResource(R.drawable.settings_remove_library_button), "Remove ${folder.name}", Modifier.size((54*s).dp))
                                }
                            }
                        }
                    }
                    Box(place(1195f,617f,484f,95f).clip(RoundedCornerShape(percent = 50)).background(Color(0xff333333))
                        .clickable(role = Role.Button, onClick = onExclusions), contentAlignment = Alignment.Center) {
                        Text("Exclusions", color = Color.White, fontFamily = InterFontFamily,
                            fontSize = with(LocalDensity.current) { (38*s).dp.toSp() })
                    }
                    button("Add folder",R.drawable.welcome_add_folder_button,R.drawable.welcome_add_folder_button_pressed,
                        1158f,801f,558f,onAdd)
                    next(1317f,990f)
                }
                WelcomePage.Loading -> {
                    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.welcome_loading))
                    LottieAnimation(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        modifier = Modifier.align(Alignment.Center).size((160f*s).dp)
                            .semantics { contentDescription = "Loading" }
                    )
                }
            }
        }
    }
}
