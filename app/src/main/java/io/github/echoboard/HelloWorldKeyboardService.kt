package io.github.echoboard

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars

class HelloWorldKeyboardService : InputMethodService() {
    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                currentInputConnection?.commitText(
                                    getString(R.string.hello_world_text),
                                    1
                                )
                            }
                        ) {
                            Text(text = stringResource(id = R.string.hello_world_button_label))
                        }
                    }
                }
            }
        }
    }
}
