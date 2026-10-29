package com.example.clocarmus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.clocarmus.ui.theme.ClocarmusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App()
        }
    }
}

@Composable
fun App() {
    ClocarmusTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Add the new app's content here.
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    App()
}
