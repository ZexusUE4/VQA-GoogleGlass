package com.example.adminstrator.glasstest;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

public class TextToSpeechController implements TextToSpeech.OnInitListener {

    private Context mContext;

    private TextToSpeech tts;

    public TextToSpeechController(Context context) {
        Log.e("TEXT TO S", "controller");
        mContext = context;
        tts = new TextToSpeech(context, this);
    }

    @Override
    public void onInit(int status) {
        Log.e("INIT TTS", "INIT");
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.ENGLISH);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(mContext, "This Language is not supported", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(mContext, "Ready to Speak", Toast.LENGTH_LONG).show();
                speakTheText("Welcome to Visual Question Answering App");
            }

        } else {
            Toast.makeText(mContext, "Can Not Speak", Toast.LENGTH_LONG).show();
        }
    }

    public void stopTTS() {
        Log.e(".....TTS", "SHUTDOWN");
        tts.stop();
        tts.shutdown();
    }

    public void speakTheText(String str) {
        Log.e("SPEAK TEXT!!!!", "SPEAK TEXT");
        tts.speak(str, TextToSpeech.QUEUE_FLUSH, null);
    }
}