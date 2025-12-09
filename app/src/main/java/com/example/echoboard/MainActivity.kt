package com.example.echoboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EchoboardApp() }
    }
}

@Composable
fun EchoboardApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Greeting(message = stringResource(id = R.string.hello_text))
        }
    }
}

@Composable
fun Greeting(message: String) {
    Text(text = message, style = MaterialTheme.typography.headlineSmall)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    EchoboardApp()
}
