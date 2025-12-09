package io.github.echoboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var text by remember { mutableStateOf("") }
                    val context = LocalContext.current

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.sample_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(text = stringResource(id = R.string.sample_description))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                                )
                            }
                        ) {
                            Text(text = stringResource(id = R.string.enable_keyboard_button_label))
                        }
                        Text(text = stringResource(id = R.string.enable_keyboard_steps))
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(text = stringResource(id = R.string.sample_field_label)) }
                        )
                    }
                }
            }
        }
    }
}
