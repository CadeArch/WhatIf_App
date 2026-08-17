package com.CadeMixedUpGame.api;

/**
 * How each unlockable voice should <em>sound</em>, as opposed to what it says.
 *
 * <p>{@link GameLogic#mutateVoiceText} changes the words; this changes the delivery. Until now
 * every voice came out of the engine at exactly the same pitch and speed, so seven rewards all
 * sounded like the same narrator reading seven different scripts - the character was entirely in
 * the text, and a listener who missed the wordplay heard nothing different at all. Pitch and rate
 * are the two knobs Android's TextToSpeech exposes on any device, with no assets to ship and no
 * network call.
 *
 * <p>Pure and Android-free on purpose (see CLAUDE.md §5): it is a lookup table with a rule, so it
 * belongs where it can be unit tested, not inside the class that owns the engine. {@code
 * VoicePlayback} applies these; nothing here knows that TextToSpeech exists.
 *
 * <p>Values are multipliers where 1.0 is the engine's normal pitch/speed, and are kept inside
 * {@link #MIN_MULTIPLIER}..{@link #MAX_MULTIPLIER} - Android accepts wider values but they stop
 * being intelligible well before the extremes, and an unintelligible reward is not a reward.
 */
public final class VoiceStyle {
    public static final float MIN_MULTIPLIER = 0.5f;
    public static final float MAX_MULTIPLIER = 2.0f;

    /** The unchanged narrator, for the "regular" option that is not an unlockable. */
    public static final String REGULAR_VOICE_CODE = "0";

    private static final VoiceStyle NORMAL = new VoiceStyle(1.0f, 1.0f);

    private final float pitch;
    private final float speechRate;

    private VoiceStyle(float pitch, float speechRate) {
        this.pitch = pitch;
        this.speechRate = speechRate;
    }

    public float getPitch() {
        return pitch;
    }

    public float getSpeechRate() {
        return speechRate;
    }

    /**
     * The delivery for a voice code, or the plain narrator for "regular" and anything unrecognized.
     *
     * <p>Falling back rather than throwing for an unknown code matches {@code mutateVoiceText}: a
     * stale row in the database for a voice that no longer exists should read plainly, not crash
     * the reading screen.
     */
    public static VoiceStyle forCode(String voiceCode) {
        if (UnlockPolicy.VOICE_FUDDIFY_CODE.equals(voiceCode)) {
            // Reedy and unhurried - the joke is in the consonants, so it has to stay clear.
            return new VoiceStyle(1.30f, 0.90f);
        }
        if (UnlockPolicy.VOICE_PIG_LATIN_CODE.equals(voiceCode)) {
            // Brisk patter; the words are nonsense either way, so speed adds to it.
            return new VoiceStyle(1.10f, 1.20f);
        }
        if (UnlockPolicy.VOICE_BACKWORDS_CODE.equals(voiceCode)) {
            // Slow and low. This one is a puzzle - the listener is reassembling a sentence in their
            // head, and at normal speed they simply lose it.
            return new VoiceStyle(0.85f, 0.85f);
        }
        if (UnlockPolicy.VOICE_JOKESTER_CODE.equals(voiceCode)) {
            // High and fast, like someone who cannot wait to get to their own punchline.
            return new VoiceStyle(1.35f, 1.15f);
        }
        if (UnlockPolicy.VOICE_FORGETFUL_CODE.equals(voiceCode)) {
            // Slow, but not as slow as it first shipped. At 0.70 a sentence with four long words in
            // it ran fifteen seconds of audio (measured: 362,760 frames), which stops being a joke
            // and becomes a wait - and the hesitation is already written into the words, so the rate
            // does not have to carry it as well.
            return new VoiceStyle(0.95f, 0.80f);
        }
        if (UnlockPolicy.VOICE_SHAGGY_CODE.equals(voiceCode)) {
            // The drawl: lowest pitch, unhurried.
            return new VoiceStyle(0.70f, 0.85f);
        }
        if (UnlockPolicy.VOICE_DISOBEDIENT_CODE.equals(voiceCode)) {
            // Low and clipped - grudging, not sleepy. Faster than normal because it wants this over
            // with.
            return new VoiceStyle(0.80f, 1.15f);
        }
        return NORMAL;
    }

    /** True if this is the untouched narrator - i.e. nothing needs saying about the delivery. */
    public boolean isNormal() {
        return pitch == NORMAL.pitch && speechRate == NORMAL.speechRate;
    }

    @Override
    public String toString() {
        return "pitch=" + pitch + ", rate=" + speechRate;
    }
}
