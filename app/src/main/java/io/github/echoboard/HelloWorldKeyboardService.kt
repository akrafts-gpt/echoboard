package io.github.echoboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class HelloWorldKeyboardService : InputMethodService() {
    private var speechRecognizer: SpeechRecognizer? = null
    private var bufferedText: String = ""
    private var bufferedTextView: TextView? = null
    private var insertButton: ImageButton? = null
    private var composeJob: Job? = null
    private val userPromptLog = StringBuilder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "HelloWorldKeyboard"
    }

    private val recognitionIntent: Intent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Speech ready for input")
            updateBufferedText(getString(R.string.listening_status))
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onPartialResults(partialResults: Bundle?) {
            Log.d(TAG, "Partial speech results: $partialResults")
            val results = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partialText = results?.firstOrNull()
            if (!partialText.isNullOrBlank()) {
                updatePromptFromSpeech(partialText, isFinalResult = false)
            }
        }

        override fun onResults(results: Bundle?) {
            Log.d(TAG, "Final speech results: $results")
            val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!recognizedText.isNullOrBlank()) {
                updatePromptFromSpeech(recognizedText, isFinalResult = true)
            } else {
                updateBufferedText(getString(R.string.no_speech_result_message), enableInsert = false)
            }
        }

        override fun onError(error: Int) {
            val description = describeSpeechError(error)
            Log.e(TAG, "Speech error ($error): $description")
            updateBufferedText("${getString(R.string.speech_error_message)} ($description)", enableInsert = false)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createSpeechRecognizerIfAvailable()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "Service destroyed")
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

        Log.d(TAG, "Input view created; buffer='${bufferedText}'")

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
            Log.w(TAG, "Microphone permission missing")
            requestMicrophonePermission()
            updateBufferedText(getString(R.string.mic_permission_required_message), enableInsert = false)
            return
        }

        if (speechRecognizer == null && !createSpeechRecognizerIfAvailable()) {
            Log.e(TAG, "Speech recognizer unavailable")
            updateBufferedText(getString(R.string.speech_unavailable_message), enableInsert = false)
            return
        }

        Log.d(TAG, "Starting voice recognition")
        updateBufferedText(getString(R.string.listening_status), enableInsert = false)
        speechRecognizer?.startListening(recognitionIntent)
    }

    private fun commitBufferedText() {
        if (bufferedText.isBlank()) return
        Log.d(TAG, "Committing buffered text: $bufferedText")
        currentInputConnection?.commitText(bufferedText, 1)
        userPromptLog.clear()
        updateBufferedText("")
    }

    private fun updatePromptFromSpeech(speechText: String, isFinalResult: Boolean) {
        if (speechText.isBlank()) return

        Log.d(TAG, "Updating prompt from speech; isFinal=$isFinalResult text='$speechText'")

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

        Log.d(TAG, "Prompt input now: '$promptInput'")
        requestBoundedMessage(promptInput)
    }

    private fun requestBoundedMessage(userNotes: String) {
        composeJob?.cancel()
        composeJob = serviceScope.launch {
            Log.d(TAG, "Requesting bounded message for notes length=${userNotes.length}")
            updateBufferedText(getString(R.string.ai_generating_status), enableInsert = false)

            val prompt = buildString {
                append("Compose a coherent message that is at most 100 characters long. ")
                append("Rewrite or summarize the user's running notes into that target length without adding markup.\n\n")
                append("User notes:\n")
                append(userNotes)
            }

            val result = when {
                shouldUseOnDeviceModel() -> {
                    Log.d(TAG, "Using on-device composer (no API key or preference)")
                    composeOnDevice(userNotes)
                }

                else -> {
                    val remoteResult = requestGeminiText(prompt)
                    if (remoteResult is GeminiComposeResult.Failure && shouldFallbackToOnDevice(remoteResult)) {
                        Log.w(TAG, "Remote compose failed (${remoteResult.reason}); falling back to on-device composer")
                        composeOnDevice(userNotes)
                    } else {
                        remoteResult
                    }
                }
            }

            when (result) {
                is GeminiComposeResult.Success -> {
                    val sanitized = sanitizeGeneratedText(result.text)
                    if (sanitized.isBlank()) {
                        Log.e(TAG, "Sanitized AI text is blank; raw='${result.text}'")
                        updateBufferedText(
                            getString(R.string.ai_error_message),
                            enableInsert = false
                        )
                    } else {
                        Log.d(TAG, "AI composed text: '$sanitized'")
                        updateBufferedText(sanitized)
                    }
                }

                is GeminiComposeResult.Failure -> {
                    Log.e(TAG, "Gemini compose failed: ${result.reason}")
                    val message = "${getString(R.string.ai_error_message)} (${result.reason})"
                    updateBufferedText(message, enableInsert = false)
                }
            }
        }
    }

    private fun shouldUseOnDeviceModel(): Boolean {
        return BuildConfig.GEMINI_API_KEY.isBlank()
    }

    private fun shouldFallbackToOnDevice(result: GeminiComposeResult.Failure): Boolean {
        val reason = result.reason.lowercase()
        return "quota" in reason || "billing" in reason || "api key" in reason
    }

    private fun composeOnDevice(userNotes: String): GeminiComposeResult {
        val condensed = userNotes
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (condensed.isBlank()) {
            return GeminiComposeResult.Failure("No speech content to summarize")
        }

        val targetLength = 100
        val bounded = if (condensed.length <= targetLength) {
            val suffix = " (draft)"
            val paddingNeeded = (targetLength - condensed.length - suffix.length).coerceAtLeast(0)
            buildString {
                append(condensed)
                if (paddingNeeded > 0) {
                    append(' ')
                    append("·".repeat(paddingNeeded.coerceAtMost(10)))
                }
                append(suffix)
            }.take(targetLength)
        } else {
            condensed.take(targetLength)
        }

        Log.d(TAG, "On-device composed text: '$bounded'")
        return GeminiComposeResult.Success(bounded)
    }

    private suspend fun requestGeminiText(prompt: String): GeminiComposeResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                Log.d(TAG, "Sending prompt to Gemini (${prompt.length} chars)")
                val endpoint =
                    "https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash-live:generateContent?key=${BuildConfig.GEMINI_API_KEY}"

                val payload = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("role", "user")
                                    put(
                                        "parts",
                                        JSONArray().apply {
                                            put(
                                                JSONObject().apply {
                                                    put("text", prompt)
                                                }
                                            )
                                        }
                                    )
                                }
                            )
                        }
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("maxOutputTokens", 120)
                        }
                    )
                }

                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 15000
                }

                connection.outputStream.use { outputStream ->
                    OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(payload.toString())
                        writer.flush()
                    }
                }

                val responseText = if (connection.responseCode in 200..299) {
                    connection.inputStream
                } else {
                    Log.w(TAG, "Gemini request failed with code ${connection.responseCode}")
                    connection.errorStream ?: return@withContext GeminiComposeResult.Failure(
                        "HTTP ${connection.responseCode} with empty body"
                    )
                }.bufferedReader(Charsets.UTF_8).use(BufferedReader::readText)

                if (connection.responseCode !in 200..299) {
                    val errorDetails = parseGeminiError(responseText)
                    return@withContext GeminiComposeResult.Failure(
                        "HTTP ${connection.responseCode}: $errorDetails"
                    )
                }

                val json = JSONObject(responseText)
                val candidates = json.optJSONArray("candidates") ?: return@withContext GeminiComposeResult.Failure(
                    "No candidates in response"
                )
                val firstCandidate = candidates.optJSONObject(0) ?: return@withContext GeminiComposeResult.Failure(
                    "Missing first candidate"
                )
                val content = firstCandidate.optJSONObject("content") ?: return@withContext GeminiComposeResult.Failure(
                    "Missing content"
                )
                val parts = content.optJSONArray("parts") ?: return@withContext GeminiComposeResult.Failure(
                    "Missing parts"
                )
                val firstPart = parts.optJSONObject(0) ?: return@withContext GeminiComposeResult.Failure(
                    "Missing text part"
                )
                val text = firstPart.optString("text", null)

                if (text.isNullOrBlank()) {
                    return@withContext GeminiComposeResult.Failure("Empty text returned")
                }

                Log.d(TAG, "Gemini response parsed")
                GeminiComposeResult.Success(text)
            }.getOrElse { throwable ->
                Log.e(TAG, "Gemini request threw an exception", throwable)
                GeminiComposeResult.Failure(throwable.message ?: "Unexpected error")
            }
        }
    }

    private fun sanitizeGeneratedText(text: String): String {
        if (text.isBlank()) return ""

        val condensed = text
            .replace("\n", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return condensed.take(100)
    }

    private fun parseGeminiError(response: String): String {
        return runCatching {
            val json = JSONObject(response)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message")
            val status = errorObj?.optString("status")
            listOfNotNull(status, message).joinToString(": ").ifBlank { response.take(200) }
        }.getOrElse {
            Log.w(TAG, "Failed to parse Gemini error body", it)
            response.take(200)
        }
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
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Record audio permission granted=$granted")
        return granted
    }

    private fun requestMicrophonePermission() {
        val intent = Intent(this, PermissionRequestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        Log.d(TAG, "Requesting microphone permission via activity")
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
        Log.d(TAG, "Buffered text updated; enabled=$enableInsert text='$bufferedText'")
    }

    private fun describeSpeechError(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
            else -> "Unknown error"
        }
    }
}

private sealed class GeminiComposeResult {
    data class Success(val text: String) : GeminiComposeResult()
    data class Failure(val reason: String) : GeminiComposeResult()
}
