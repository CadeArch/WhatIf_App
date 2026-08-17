package com.CadeMixedUpGame.phoneapp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.UnlockPolicy;
import com.CadeMixedUpGame.api.VoiceStyle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 *
 * <h3>Why this class tracks readiness itself</h3>
 * {@code new TextToSpeech(...)} returns immediately but the engine is unusable until its init
 * callback fires — it binds to a separate service process, which takes anywhere from a moment to
 * several seconds on a cold start. Calling {@code speak()} before then does nothing at all: it
 * returns {@code ERROR} and produces no sound, no exception and no callback. This class previously
 * treated "the object exists" as "the engine works" and ignored the return code, which is exactly
 * the reported symptom — the mic button doing nothing, then working fine if you waited a bit and
 * tried again. Nothing was lagging; the tap was landing on an engine that had not finished
 * starting, and the tap was thrown away.
 *
 * <p>So a request that arrives early is <b>held and spoken when the engine is ready</b> rather than
 * dropped, and the caller is told which happened so it can say so on screen.
 */
final class VoicePlayback {
    /** Matches the "regular" entry seeded into the picker - no mutation applied. */
    private static final String REGULAR_VOICE_CODE = "0";
    private static final String UTTERANCE_ID = "readIfThen";

    /** Google's en-US speaker ids that are male - see {@code soundsMale}. */
    private static final String[] MALE_VOICE_IDS = {"iob", "iom", "tpc", "tpd", "iod"};

    /**
     * Below this, two taps are one impatient gesture rather than two intentions.
     *
     * <p>Chosen to be longer than it strictly needs to be for de-bouncing, because it also bounds
     * how short a stopped utterance can be: a "stop" can only arrive this long after the "start",
     * so the worst truncation any burst of tapping can produce is nearly a second of speech, which
     * is audibly someone cutting the reading off rather than the click a 20-millisecond fragment
     * makes. At 500ms a burst still produced one sub-second fragment; measured on-device.
     */
    private static final long MIN_TAP_INTERVAL_MS = 900L;

    /** Characters who have to be men: Shaggy and Elmer Fudd. */
    private static final Set<String> MALE_PREFERRED_CODES = new HashSet<String>(Arrays.asList(
            UnlockPolicy.VOICE_SHAGGY_CODE, UnlockPolicy.VOICE_FUDDIFY_CODE));

    /** What became of a speak request, so the screen can explain a silent mic instead of ignoring it. */
    enum SpeakResult {
        /** Handed to the engine. */
        SPOKEN,
        /** It was already speaking, so this tap stopped it instead of restarting it. */
        STOPPED,
        /** Too soon after the previous tap to be a separate intention; deliberately dropped. */
        IGNORED,
        /** The engine is still starting; this will be spoken automatically as soon as it is up. */
        WARMING_UP,
        /** No engine at all - this device cannot speak. */
        UNAVAILABLE
    }

    /** Lets the screen show that a tap took effect even before any audio comes out. */
    interface PlaybackListener {
        void onSpeakingChanged(boolean speaking);
    }

    private TextToSpeech tts;
    private Context appContext;
    private boolean engineReady;
    /** Written from the engine's own callback thread, read on the main thread. */
    private volatile boolean speaking;
    /** Monotonic, so a clock change cannot make the debounce window enormous or negative. */
    private long lastHandledTapAt;
    private PlaybackListener playbackListener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** The one utterance waiting on the engine, if any. Only the latest is worth keeping. */
    private String pendingSpeech;
    private String voiceCode = REGULAR_VOICE_CODE;
    /** Which system voice each unlockable speaks with, once the engine has told us what it has. */
    private final Map<String, android.speech.tts.Voice> voiceByCode =
            new HashMap<String, android.speech.tts.Voice>();

    /** No-op if already started, so a re-created view can call this safely. */
    void start(Context context) {
        if (tts != null) {
            return;
        }
        // The application context, not the fragment's: the engine outlives individual callbacks and
        // holding an Activity in one would leak it across a rotation.
        appContext = context.getApplicationContext();
        createEngine();
    }

    void setVoiceCode(String voiceCode) {
        this.voiceCode = voiceCode == null ? REGULAR_VOICE_CODE : voiceCode;
    }

    String getVoiceCode() {
        return voiceCode;
    }

    /** Speaks the sentence in the selected voice, applying that voice's text mutation first. */
    SpeakResult speak(String ifSentence, String thenSentence) {
        // Machine-gun taps are dropped outright. Start/stop toggling already keeps a double tap from
        // flushing an utterance mid-word, but five taps in a second still alternates start-stop-start
        // and each aborted start emits a fraction of a second of audio - a burst of those is heard as
        // a string of clicks, which is precisely what a frustrated player does to a mic that seems
        // dead. Half a second is below the interval of any deliberate "stop that" tap and well above
        // an accidental repeat.
        long now = SystemClock.elapsedRealtime();
        if (now - lastHandledTapAt < MIN_TAP_INTERVAL_MS) {
            AppLog.d(AppLog.TTS, "Ignored a repeat mic tap within " + MIN_TAP_INTERVAL_MS + "ms");
            return SpeakResult.IGNORED;
        }
        lastHandledTapAt = now;
        String sentence = GameLogic.formatIfSentence(ifSentence) + ", " + GameLogic.formatThenSentence(thenSentence);
        String spoken = REGULAR_VOICE_CODE.equals(voiceCode) ? sentence : GameLogic.mutateVoiceText(sentence, voiceCode);
        if (tts == null) {
            AppLog.w(AppLog.TTS, "Speak skipped: no TextToSpeech engine");
            return SpeakResult.UNAVAILABLE;
        }
        if (!engineReady) {
            pendingSpeech = spoken;
            AppLog.i(AppLog.TTS, "Speak queued: engine still starting, voiceCode=" + voiceCode);
            return SpeakResult.WARMING_UP;
        }
        // A tap while it is already talking STOPS it. It used to hand the engine a fresh utterance
        // with QUEUE_FLUSH, which kills whatever is playing and starts synthesising again - so a
        // second tap a moment after the first produced a fragment of audio and then silence, and a
        // few taps in a row produced nothing but a string of clicks. Measured on-device: consecutive
        // taps delivered 4096, then 14976, then 512 frames - the last one is 21 milliseconds of
        // audio. The button was working perfectly and cancelling itself every time.
        if (speaking) {
            tts.stop();
            setSpeaking(false);
            AppLog.i(AppLog.TTS, "Stopped playback on a second tap");
            return SpeakResult.STOPPED;
        }
        AppLog.i(AppLog.TTS, "Speaking read sentence with voiceCode=" + voiceCode);
        // Marked before handing it over, not when the engine reports it started. A neural voice can
        // take a second or more to produce its first sample, and a tap inside that window has to
        // count as "already going" or it flushes the utterance that has not begun playing yet.
        setSpeaking(true);
        if (speakNow(spoken)) {
            return SpeakResult.SPOKEN;
        }
        setSpeaking(false);
        // A ready engine that refuses the utterance has died underneath us - the TTS service is a
        // separate process and the system is free to kill it while the app sits in the background,
        // which leaves this side holding a handle that fails every call from then on. Rebuild it and
        // let the queued utterance go out on init, so one dead engine costs a pause rather than a
        // mic button that never works again for the rest of the round.
        AppLog.w(AppLog.TTS, "Speak rejected by a ready engine; restarting it");
        releaseEngine();
        pendingSpeech = spoken;
        createEngine();
        return SpeakResult.WARMING_UP;
    }

    void setPlaybackListener(PlaybackListener listener) {
        this.playbackListener = listener;
    }

    /** Safe to call more than once; the fragment calls it from onDestroyView. */
    void release() {
        pendingSpeech = null;
        playbackListener = null;
        mainHandler.removeCallbacksAndMessages(null);
        speaking = false;
        releaseEngine();
    }

    /**
     * Publishes the speaking flag on the main thread.
     *
     * <p>The engine reports progress from its own binder thread, so the flag is volatile and the
     * callback out to the screen is posted rather than delivered inline - a listener that touches
     * views must not run off the main thread.
     */
    private void setSpeaking(boolean value) {
        speaking = value;
        mainHandler.post(() -> {
            if (playbackListener != null) {
                playbackListener.onSpeakingChanged(value);
            }
        });
    }

    private UtteranceProgressListener progressListener() {
        return new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                AppLog.d(AppLog.TTS, "Utterance started");
            }

            @Override
            public void onDone(String utteranceId) {
                AppLog.d(AppLog.TTS, "Utterance finished");
                setSpeaking(false);
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                AppLog.d(AppLog.TTS, "Utterance stopped, interrupted=" + interrupted);
                setSpeaking(false);
            }

            @Override
            @Deprecated
            public void onError(String utteranceId) {
                AppLog.w(AppLog.TTS, "Utterance failed");
                setSpeaking(false);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                AppLog.w(AppLog.TTS, "Utterance failed with code " + errorCode);
                setSpeaking(false);
            }
        };
    }

    private void createEngine() {
        engineReady = false;
        tts = new TextToSpeech(appContext, this::onEngineInit);
    }

    private void onEngineInit(int status) {
        engineReady = status == TextToSpeech.SUCCESS && tts != null;
        if (engineReady) {
            int languageResult = tts.setLanguage(Locale.getDefault());
            if (languageResult == TextToSpeech.LANG_MISSING_DATA
                    || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // The device's own locale has no voice data installed. US English is the fallback
                // rather than giving up, since the sentences are English either way.
                AppLog.w(AppLog.TTS, "No voice data for " + Locale.getDefault() + "; falling back to US English");
                tts.setLanguage(Locale.US);
            }
            tts.setOnUtteranceProgressListener(progressListener());
            assignSystemVoices();
        }
        AppLog.i(AppLog.TTS, "TextToSpeech initialized status=" + status + ", ready=" + engineReady);
        String queued = pendingSpeech;
        pendingSpeech = null;
        if (queued != null && engineReady) {
            AppLog.i(AppLog.TTS, "Speaking the utterance queued while the engine was starting");
            speakNow(queued);
        }
    }

    /**
     * Assigns each unlockable a different system voice, where the device has more than one.
     *
     * <p>Pitch and rate alone still leave every voice sounding like the same speaker putting on an
     * accent. Most engines ship several distinct English voices; handing a different one to each
     * unlockable makes them sound like different people, for free, with no assets.
     *
     * <p>Sorted by name and assigned by catalog position so a given voice is stable on a given
     * device rather than shuffling between readings. Network-only and not-yet-installed voices are
     * skipped — the first would fail silently offline, and the second is a name for a voice that
     * isn't there. If the engine offers nothing usable, this does nothing and pitch/rate carry the
     * difference on their own.
     */
    private void assignSystemVoices() {
        voiceByCode.clear();
        Set<android.speech.tts.Voice> available;
        try {
            available = tts.getVoices();
        }
        catch (Exception engineRefused) {
            // getVoices() is documented to return null but some engines throw outright.
            AppLog.w(AppLog.TTS, "Could not list system voices: " + engineRefused);
            return;
        }
        if (available == null || available.isEmpty()) {
            return;
        }
        List<android.speech.tts.Voice> usable = new ArrayList<>();
        for (android.speech.tts.Voice voice : available) {
            if (isUsable(voice)) {
                usable.add(voice);
            }
        }
        if (usable.isEmpty()) {
            return;
        }
        Collections.sort(usable, (first, second) -> first.getName().compareTo(second.getName()));

        // Shaggy and Elmer Fudd are both men, and no amount of pitch shifting makes a female voice
        // read as either of them - pitch changes the note, not the timbre. Android's Voice exposes
        // no gender at all, so this goes off the speaker id Google embeds in the voice name
        // (en-us-x-<id>-local). Best-effort by design: an unrecognized engine or a device with no
        // male voice installed falls through to the ordinary round-robin, which is no worse than
        // before, and the pitch/rate styling still tells the voices apart.
        List<android.speech.tts.Voice> male = new ArrayList<>();
        List<android.speech.tts.Voice> rest = new ArrayList<>();
        for (android.speech.tts.Voice voice : usable) {
            if (soundsMale(voice)) {
                male.add(voice);
            }
            else {
                rest.add(voice);
            }
        }
        List<UnlockPolicy.Voice> catalog = UnlockPolicy.catalog();
        int maleTaken = 0;
        int restTaken = 0;
        StringBuilder mapping = new StringBuilder();
        for (UnlockPolicy.Voice unlockable : catalog) {
            android.speech.tts.Voice chosen;
            if (MALE_PREFERRED_CODES.contains(unlockable.getVoiceCode()) && !male.isEmpty()) {
                chosen = male.get(maleTaken % male.size());
                maleTaken++;
            }
            else if (!rest.isEmpty()) {
                chosen = rest.get(restTaken % rest.size());
                restTaken++;
            }
            else {
                chosen = usable.get((maleTaken + restTaken) % usable.size());
                restTaken++;
            }
            voiceByCode.put(unlockable.getVoiceCode(), chosen);
            mapping.append(mapping.length() == 0 ? "" : ", ")
                    .append(unlockable.getVoiceType()).append("->").append(chosen.getName());
        }
        AppLog.i(AppLog.TTS, "System voices available: " + usable.size() + " (" + male.size()
                + " male-sounding). Voice map: " + mapping);
    }

    /**
     * Guesses gender from Google's voice id, since {@code Voice} carries no gender field.
     *
     * <p>Google's en-US voices are named {@code en-us-x-<id>-local}/{@code -network}, where the
     * three-letter id identifies the speaker. These ids are the male ones; anything unrecognized is
     * treated as not-male so a wrong guess never sends a female voice to Shaggy - it just falls back
     * to the normal assignment.
     */
    private boolean soundsMale(android.speech.tts.Voice voice) {
        String name = voice.getName().toLowerCase(Locale.US);
        for (String maleId : MALE_VOICE_IDS) {
            if (name.contains("-" + maleId + "-")) {
                return true;
            }
        }
        return name.contains("#male") || name.contains("_male");
    }

    private boolean isUsable(android.speech.tts.Voice voice) {
        if (voice == null || voice.getName() == null || voice.getLocale() == null) {
            return false;
        }
        if (voice.isNetworkConnectionRequired()) {
            return false;
        }
        if (voice.getFeatures() != null
                && voice.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
            return false;
        }
        try {
            return "eng".equalsIgnoreCase(voice.getLocale().getISO3Language());
        }
        catch (RuntimeException noThreeLetterCode) {
            return false;
        }
    }

    private boolean speakNow(String spoken) {
        // Applied per utterance rather than when the voice is picked: these are engine-wide settings
        // and they persist, so setting them at selection time would leave whatever was chosen last
        // still in force if anything else ever speaks.
        VoiceStyle style = VoiceStyle.forCode(voiceCode);
        tts.setPitch(style.getPitch());
        tts.setSpeechRate(style.getSpeechRate());
        android.speech.tts.Voice assigned = voiceByCode.get(voiceCode);
        if (assigned != null) {
            tts.setVoice(assigned);
        }
        int result = tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID);
        if (result != TextToSpeech.SUCCESS) {
            AppLog.w(AppLog.TTS, "TextToSpeech.speak returned " + result);
            return false;
        }
        return true;
    }

    private void releaseEngine() {
        engineReady = false;
        if (tts == null) {
            return;
        }
        tts.stop();
        tts.shutdown();
        tts = null;
    }
}
