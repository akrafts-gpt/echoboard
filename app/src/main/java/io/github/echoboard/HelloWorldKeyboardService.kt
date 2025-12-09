package io.github.echoboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class HelloWorldKeyboardService : InputMethodService() {
    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.view_hello_world_keyboard, null)
        val helloWorldButton: Button = keyboardView.findViewById(R.id.buttonHelloWorld)
        val initialLeftPadding = keyboardView.paddingLeft
        val initialTopPadding = keyboardView.paddingTop
        val initialRightPadding = keyboardView.paddingRight
        val initialBottomPadding = keyboardView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(keyboardView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeftPadding + systemBars.left,
                initialTopPadding,
                initialRightPadding + systemBars.right,
                initialBottomPadding + systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(keyboardView)

        helloWorldButton.setOnClickListener {
            currentInputConnection?.commitText(getString(R.string.hello_world_text), 1)
        }

        return keyboardView
    }
}
