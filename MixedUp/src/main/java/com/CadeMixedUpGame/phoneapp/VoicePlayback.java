package com.CadeMixedUpGame.phoneapp;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;

import java.util.Locale;

/**
 * Owns the Text-To-Speech engine for the reading screen: creation, the selected voice, speaking a
 * sentence, and shutdown.
 *
 * <p>Split out of {@code ReadSentenceFrag}, which had grown to mix view binding, Firebase
 * listeners, turn mechanics, navigation *and* TTS in one class. The engine is the easiest of those
 * to separate cleanly because nothing else on the screen touches it — the fragment only ever needs
 * "start it", "this voice is selected", "say the sentence" and "let it go".
 *
 * <p>Lives in the app module rather than {@code API/} because it needs a {@code Context} and the
 * Android TTS engine; the text mutation it applies is already a pure function
 * ({@link GameLogic#mutateVoiceText}) and stays there.
 */
final class VoicePlayback {
    /** Matches the "regular" entry seeded into the picker - no mutation applied. */
    private static final String REGULAR_VOICE_CODE = "0";
    private static final String UTTERANCE_ID = "readIfThen";

    private TextToSpeech tts;
    private String voiceCode = REGULAR_VOICE_CODE;

    /** No-op if already started, so a re-created view can call this safely. */
    void start(Context context) {
        if (tts != null) {
            return;
        }
        tts = new TextToSpeech(context, status -> {
            if (tts != null) {
                tts.setLanguage(Locale.getDefault());
            }
            AppLog.i(AppLog.TTS, "TextToSpeech initialized status=" + status);
        });
    }

    void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode == null ? REGULAR_VOICE_CODE : voiceCode;
    }

    String getVoiceCode() {
        return voiceCode;
    }

    /** True when there is an engine to speak with - the caller decides what to tell the user. */
    boolean isReady() {
        return tts != null;
    }

    /** Speaks the sentence in the selected voice, applying that voice's text mutation first. */
    void speak(String ifSentence, String thenSentence) {
        if (tts == null) {
            AppLog.w(AppLog.TTS, "Speak skipped: TextToSpeech not initialized");
            return;
        }
        String sentence = GameLogic.formatIfSentence(ifSentence) + ", " + GameLogic.formatThenSentence(thenSentence);
        String spoken = REGULAR_VOICE_CODE.equals(voiceCode) ? sentence : GameLogic.mutateVoiceText(sentence, voiceCode);
        AppLog.i(AppLog.TTS, "Speaking read sentence with voiceCode=" + voiceCode);
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
    }

    /** Safe to call more than once; the fragment calls it from onDestroyView. */
    void release() {
        if (tts == null) {
            return;
        }
        tts.stop();
        tts.shutdown();
        tts = null;
    }
}
