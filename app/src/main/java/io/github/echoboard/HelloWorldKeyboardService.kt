package io.github.echoboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button

class HelloWorldKeyboardService : InputMethodService() {
    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.view_hello_world_keyboard, null)
        val helloWorldButton: Button = keyboardView.findViewById(R.id.buttonHelloWorld)

        helloWorldButton.setOnClickListener {
            currentInputConnection?.commitText(getString(R.string.hello_world_text), 1)
        }

        return keyboardView
    }
}
