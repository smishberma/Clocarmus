package com.example.clocarmus.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.clocarmus.R

/** The divider spans the shell, independently of either pane's centered reference grid. */
@Composable
internal fun PlayerDivider(style: DividerStyle, modifier: Modifier = Modifier, playing: Boolean = false, hasTrack: Boolean = false) {
    // Owned by the shell, not individual screens or the conditional animation renderer.
    val visualizerAnimation = rememberLottieAnimatable()
    val colorLineAnimation = rememberLottieAnimatable()
    val animation = if (style == DividerStyle.COLOR_LINE) colorLineAnimation else visualizerAnimation
    LaunchedEffect(hasTrack) {
        if (!hasTrack) {
            visualizerAnimation.snapTo(progress = 0f)
            colorLineAnimation.snapTo(progress = 0f)
        }
    }
    when (style) {
        DividerStyle.HIDDEN -> Unit
        DividerStyle.DEFAULT -> Box(modifier.width(1.dp).fillMaxHeight().background(Color(0xffdddddd)))
        DividerStyle.EQUALIZER, DividerStyle.COLOR_LINE -> {
            val colorLine = style == DividerStyle.COLOR_LINE
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(
                if (colorLine) R.raw.divider_color_line else R.raw.divider_equalizer))
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, _ ->
                    foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(animation, composition, playing, hasTrack, foreground) {
                if (playing && hasTrack && foreground) {
                    animation.animate(composition, iterations = LottieConstants.IterateForever,
                        initialProgress = animation.progress, speed = 1f,
                        cancellationBehavior = LottieCancellationBehavior.Immediately)
                }
            }
            val white = rememberLottieDynamicProperties(rememberLottieDynamicProperty(
                LottieProperty.COLOR_FILTER, SimpleColorFilter(android.graphics.Color.WHITE), "**"))
            BoxWithConstraints(modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                val scale = minOf(maxWidth.value / 2000f, maxHeight.value / 1236f)
                // Use a full-height canvas and preserve each asset's own aspect ratio.
                val length = (1236f * scale).dp
                val thickness = length * (if (colorLine) 4f / 400f else 40f / 312f)
                // Rotate a proportional horizontal canvas; the shell's panes keep their own sizes.
                Box(Modifier.size(thickness, length), contentAlignment = Alignment.Center) {
                    LottieAnimation(composition, progress = { if (hasTrack) animation.progress else 0f },
                        modifier = Modifier.requiredSize(length, thickness).graphicsLayer { rotationZ = 90f },
                        dynamicProperties = if (colorLine) null else white, contentScale = ContentScale.Fit)
                }
            }
        }
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
