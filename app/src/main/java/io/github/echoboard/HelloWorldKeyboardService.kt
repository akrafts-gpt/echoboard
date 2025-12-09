package io.github.echoboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HelloWorldKeyboardService : InputMethodService() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var bufferedText: String = ""
    private var bufferedTextView: TextView? = null
    private var insertButton: ImageButton? = null
    private var composeJob: Job? = null
    private val userPromptLog = StringBuilder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val generativeModel: GenerativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.GEMINI_API_KEY
        )
    }

    private val recognitionIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            updateBufferedText(getString(R.string.listening_status))
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = results?.firstOrNull()
            if (!partialText.isNullOrBlank()) {
                updatePromptFromSpeech(partialText, isFinalResult = false)
            }
        }

        override fun onResults(results: Bundle?) {
            val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                updatePromptFromSpeech(recognizedText, isFinalResult = true)
            } else {
                updateBufferedText(getString(R.string.no_speech_result_message), enableInsert = false)
            }
        }

        override fun onError(error: Int) {
            updateBufferedText(getString(R.string.speech_error_message), enableInsert = false)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        createSpeechRecognizerIfAvailable()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val keyboardView = layoutInflater.inflate(R.layout.view_hello_world_keyboard, null)
        val recordButton: ImageButton = keyboardView.findViewById(R.id.buttonRecord)
        val insertButtonView: ImageButton = keyboardView.findViewById(R.id.buttonInsert)
        val bufferTextView: TextView = keyboardView.findViewById(R.id.textBufferedResult)

        bufferedTextView = bufferTextView
        insertButton = insertButtonView
        updateBufferedText(bufferedText, enableInsert = bufferedText.isNotBlank())

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

        recordButton.setOnClickListener {
            startVoiceRecognition()
        }

        insertButtonView.setOnClickListener {
            commitBufferedText()
        }

        return keyboardView
    }

    private fun startVoiceRecognition() {
        if (!hasRecordAudioPermission()) {
            requestMicrophonePermission()
            updateBufferedText(getString(R.string.mic_permission_required_message), enableInsert = false)
            return
        }

        if (speechRecognizer == null && !createSpeechRecognizerIfAvailable()) {
            updateBufferedText(getString(R.string.speech_unavailable_message), enableInsert = false)
            return
        }

        updateBufferedText(getString(R.string.listening_status), enableInsert = false)
        speechRecognizer?.startListening(recognitionIntent)
    }

    private fun commitBufferedText() {
        if (bufferedText.isBlank()) return
        currentInputConnection?.commitText(bufferedText, 1)
        userPromptLog.clear()
        updateBufferedText("")
    }

    private fun updatePromptFromSpeech(speechText: String, isFinalResult: Boolean) {
        if (speechText.isBlank()) return

        val normalized = speechText.trim()
        val promptInput = if (isFinalResult) {
            if (userPromptLog.isNotEmpty()) {
                userPromptLog.append(' ')
            }
            userPromptLog.append(normalized)
            userPromptLog.toString()
        } else {
            buildString {
                append(userPromptLog)
                if (userPromptLog.isNotEmpty()) append(' ')
                append(normalized)
            }
        }

        requestBoundedMessage(promptInput)
    }

    private fun requestBoundedMessage(userNotes: String) {
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            updateBufferedText(getString(R.string.ai_missing_key_message), enableInsert = false)
            return
        }

        composeJob?.cancel()
        composeJob = serviceScope.launch {
            updateBufferedText(getString(R.string.ai_generating_status), enableInsert = false)

            val prompt = buildString {
                append("Compose a coherent message that is at most 100 characters long. ")
                append("Rewrite or summarize the user's running notes into that target length without adding markup.\n\n")
                append("User notes:\n")
                append(userNotes)
            }

            val generated = withContext(Dispatchers.IO) {
                runCatching { generativeModel.generateContent(prompt).text }.getOrNull()
            }

            val sanitized = generated?.let { sanitizeGeneratedText(it) }

            if (sanitized.isNullOrBlank()) {
                updateBufferedText(getString(R.string.ai_error_message), enableInsert = false)
            } else {
                updateBufferedText(sanitized)
            }
        }
    }

    private fun sanitizeGeneratedText(text: String): String {
        if (text.isBlank()) return ""

        val condensed = text
            .replace("\n", " ")
            .replace("\s+".toRegex(), " ")
            .trim()

        return condensed.take(100)
    }

    private fun createSpeechRecognizerIfAvailable(): Boolean {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return false
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
        return true
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        val intent = Intent(this, PermissionRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun updateBufferedText(text: String, enableInsert: Boolean = true) {
        bufferedText = text
        bufferedTextView?.text = if (text.isBlank()) {
            getString(R.string.buffer_empty_hint)
        } else {
            text
        }
        insertButton?.isEnabled = enableInsert && bufferedText.isNotBlank()
    }
}
