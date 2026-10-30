package com.example

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.ui.MahiMainScreen
import com.example.ui.theme.CyberDarkBg
import com.example.ui.theme.MahiWifeTheme
import com.example.viewmodel.MahiEvent
import com.example.viewmodel.MahiViewModel
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private val viewModel: MahiViewModel by viewModels()
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        tts = TextToSpeech(this, this)

        setContent {
            MahiWifeTheme {
                val voiceLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        if (!spoken.isNullOrEmpty()) {
                            val text = spoken[0]
                            viewModel.onSendMessage(text)
                        }
                    }
                }

                fun launchSpeechRecognizer() {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "মাহি সরাসরি শুনছে... বলুন জানু")
                    }
                    try {
                        voiceLauncher.launch(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "ভয়েস ইনপুট সমর্থিত নয়", Toast.LENGTH_SHORT).show()
                    }
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        when (event) {
                            is MahiEvent.SpeakText -> {
                                speakOut(event.text)
                            }
                            is MahiEvent.OpenUrl -> {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "গান লোড করা যাচ্ছে না", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is MahiEvent.DialPhone -> {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = if (event.number.isNotEmpty()) Uri.parse("tel:${event.number}") else Uri.parse("tel:")
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "ডায়ালার ওপেন করা যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is MahiEvent.SendSms -> {
                                try {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("smsto:${event.number}")
                                        putExtra("sms_body", event.body)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "এসএমএস অ্যাপ পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is MahiEvent.VibratePhone -> {
                                triggerVibration()
                            }
                            is MahiEvent.StartLiveVoiceRecognition -> {
                                launchSpeechRecognizer()
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberDarkBg
                ) {
                    MahiMainScreen(
                        viewModel = viewModel,
                        onVoiceInputRequested = { launchSpeechRecognizer() }
                    )
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("bn", "BD"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("bn", "IN"))
            }
            tts?.setPitch(1.35f)
            tts?.setSpeechRate(0.95f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    viewModel.setSpeakingState(true)
                }

                override fun onDone(utteranceId: String?) {
                    viewModel.setSpeakingState(false)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    viewModel.setSpeakingState(false)
                }
            })
            isTtsReady = true
        }
    }

    private fun speakOut(text: String) {
        if (isTtsReady && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mahi_response_voice")
        }
    }

    private fun triggerVibration() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(400)
            }
        } catch (_: Exception) {
        }
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
