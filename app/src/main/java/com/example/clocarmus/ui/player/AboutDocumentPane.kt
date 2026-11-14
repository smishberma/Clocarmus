package com.example.clocarmus.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.clocarmus.ui.theme.InterFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bundled documents stay readable without a browser or network permission. */
@Composable
internal fun AboutDocumentPane(grid: PlayerGrid, title: String, asset: String, scroll: LazyListState) {
    val context = LocalContext.current
    var attempt by remember(asset) { mutableStateOf(0) }
    val document by produceState<Result<List<String>>?>(null, asset, attempt) {
        value = null
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(asset).bufferedReader().use { it.readText() }
                    .trim().split(Regex("\\n\\s*\\n"))
            }
        }
    }
    val s = grid.scale
    Column(grid.place(41f, 264f, 864f, 732f)) {
        PlayerLabel(title, Modifier.fillMaxWidth().height((70f * s).dp)
            .padding(start = (27f * s).dp), 36f * s, Alignment.CenterStart,
            bold = true, color = Color.Cyan)
        val paragraphs = document?.getOrNull()
        if (paragraphs != null) {
            LazyColumn(state = scroll, contentPadding = PaddingValues((27f * s).dp),
                verticalArrangement = Arrangement.spacedBy((28f * s).dp)) {
                items(paragraphs) { paragraph ->
                    SelectionContainer {
                        Text(paragraph, color = Color.White, fontFamily = InterFontFamily,
                            fontSize = (30f * s).sp, lineHeight = (43f * s).sp)
                    }
                }
            }
        } else {
            Text(if (document == null) "Loading…" else "Could not load this document.",
                color = Color.White, fontFamily = InterFontFamily, fontSize = (30f * s).sp)
            if (document != null) TextButton(onClick = { attempt++ }) { Text("Retry") }
        }
    }
}
