package io.github.echoboard

import android.inputmethodservice.InputMethodService
import android.view.View
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewTreeLifecycleOwner

class HelloWorldKeyboardService : InputMethodService() {
    private val lifecycleOwner = ServiceLifecycleOwner()

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.handleOnCreate()
    }

    override fun onDestroy() {
        lifecycleOwner.handleOnDestroy()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            ViewTreeLifecycleOwner.set(this, lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
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

    private class ServiceLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override fun getLifecycle(): Lifecycle = registry

        fun handleOnCreate() {
            registry.currentState = Lifecycle.State.CREATED
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun handleOnDestroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
