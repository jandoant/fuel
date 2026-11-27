package dev.jandoant.fuel.ui.addfood

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Thin wrapper around Android's SpeechRecognizer for German dictation (de-DE). Uses on-device recognition when the
 * phone has it, and falls back to the system recogniser if the on-device German model is missing.
 */
class VoiceRecognizer(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var triedOnDevice = false

    fun start() {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context) && !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this phone")
            return
        }
        val onDevice = !triedOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        recognizer = (if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context)).apply {
            setRecognitionListener(listener(onDevice))
            startListening(intent())
        }
    }

    fun stop() {
        recognizer?.run { stopListening(); destroy() }
        recognizer = null
    }

    /** Stops listening early; the recogniser still delivers what it heard. */
    fun finish() = recognizer?.stopListening()

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }

    private fun listener(onDevice: Boolean) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onListening(true)
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onListening(false)
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartial)
        }

        override fun onResults(results: Bundle?) {
            onListening(false)
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (text.isNullOrBlank()) onError("Nothing understood — try again") else onResult(text)
            stop()
        }

        override fun onError(error: Int) {
            onListening(false)
            Log.i("FuelVoice", "Recognition error $error (onDevice=$onDevice)")
            val languageMissing = error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE
            if (onDevice && (languageMissing || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED || error == SpeechRecognizer.ERROR_CLIENT)) {
                triedOnDevice = true
                start() // retry with the system recogniser
                return
            }
            stop()
            onError(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing understood — try again"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission missing"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No network for speech recognition"
                    else -> "Speech recognition failed ($error)"
                },
            )
        }
    }

    companion object {
        const val LANGUAGE = "de-DE"
    }
}
