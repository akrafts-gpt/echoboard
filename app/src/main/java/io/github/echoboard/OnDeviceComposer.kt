package io.github.echoboard

import android.app.Person
import android.content.Context
import android.os.Build
import android.view.textclassifier.ConversationAction
import android.view.textclassifier.ConversationActions
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnDeviceComposer(private val context: Context) {
    companion object {
        private const val TAG = "OnDeviceComposer"
        private const val TARGET_LENGTH = 100
    }

    suspend fun compose(userNotes: String): ComposeResult {
        return withContext(Dispatchers.Default) {
            val normalized = userNotes
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            if (normalized.isBlank()) {
                return@withContext ComposeResult.Failure("No speech content to summarize")
            }

            val classifier = obtainTextClassifier()
                ?: return@withContext ComposeResult.Failure("On-device text classifier unavailable")

            val suggestion = tryGenerateReply(classifier, normalized)

            when {
                suggestion != null -> {
                    val bounded = suggestion.take(TARGET_LENGTH)
                    ComposeResult.Success(bounded)
                }

                else -> {
                    ComposeResult.Failure("No on-device suggestion produced")
                }
            }
        }
    }

    private fun obtainTextClassifier(): TextClassifier? {
        val manager = context.getSystemService(TextClassificationManager::class.java)
        val classifier = manager?.textClassifier
        if (classifier == null || classifier === TextClassifier.NO_OP) {
            Log.w(TAG, "System text classifier unavailable; cannot use on-device AI")
            return null
        }
        return classifier
    }

    private fun tryGenerateReply(
        classifier: TextClassifier,
        userNotes: String
    ): String? {
        val messages = buildList {
            add(buildMessage("user", userNotes))
        }

        val request = ConversationActions.Request.Builder(messages)
            .setMaxSuggestions(3)
            .build()

        return try {
            val response = classifier.suggestConversationActions(request)
            Log.d(TAG, "Conversation actions returned ${response.conversationActions.size} suggestions")
            response.conversationActions.firstNotNullOfOrNull { action ->
                if (action.type == ConversationAction.TYPE_TEXT_REPLY) {
                    action.textReply?.toString()
                } else {
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "On-device suggestion failed", t)
            null
        }
    }

    private fun buildMessage(sender: String, text: String): ConversationActions.Message {
        val person = Person.Builder().setName(sender).build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ConversationActions.Message.Builder(person)
                .setText(text)
                .setReferenceTime(System.currentTimeMillis())
                .build()
        } else {
            ConversationActions.Message.Builder(text)
                .setText(text)
                .build()
        }
    }
}
