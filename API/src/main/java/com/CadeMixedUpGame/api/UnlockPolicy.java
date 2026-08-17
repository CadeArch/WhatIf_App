package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.Unlockable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The single source of truth for which voices exist and what earns them.
 *
 * <p>Previously the catalog was spelled out twice — seven {@code new Unlockable(...)} lines in
 * {@code UserViewModel.fillUnlockables} and a separate chain of {@code if}s in
 * {@code unlockVoice} that hard-coded each voice name, its condition and its database path
 * together, keyed off a stringly-typed {@code "numGames"}/{@code "leaderBoards"} argument. The two
 * lists had drifted: only three of the seven voices had any rule at all, so {@code jokester},
 * {@code forgetful}, {@code shaggy} and {@code disobedient} were written to every account as
 * {@code unlocked=false} with nothing in the app able to ever flip them — rewards a player could
 * see but never earn.
 *
 * <p>Everything here is pure and Firebase-free so the rules are unit-testable without an emulator
 * (see GameFlowPolicy/GameLogic for the same pattern). The ViewModel keeps the database writes;
 * this only answers "what should be unlocked for these stats".
 */
public final class UnlockPolicy {
    // Progression ladder. Tuned so the first voice lands immediately after a first game rather
    // than leaving a new account staring at seven locked rows, then spaces out from there. These
    // are the only knobs worth retuning if the pacing feels wrong - nothing else needs to change.
    public static final int JOKESTER_GAMES = 1;
    public static final int BACKWORDS_GAMES = 5;
    public static final int FORGETFUL_GAMES = 10;
    public static final int SHAGGY_GAMES = 20;
    public static final int DISOBEDIENT_GAMES = 40;

    public static final String VOICE_FUDDIFY = "fuddify";
    public static final String VOICE_PIG_LATIN = "pig latin";
    public static final String VOICE_BACKWORDS = "backwords";
    public static final String VOICE_JOKESTER = "jokester";
    public static final String VOICE_FORGETFUL = "forgetful";
    public static final String VOICE_SHAGGY = "shaggy";
    public static final String VOICE_DISOBEDIENT = "disobedient";

    /** The voice codes are what SpinnerAdapter/DiffGoogleVoice match on, so they must keep their
     * original values ("regular" is 0 and is not an unlockable). Named here because two other
     * places switch on them - {@code GameLogic.mutateVoiceText} for the words and
     * {@link VoiceStyle} for the delivery - and a bare literal in three files is a typo waiting to
     * silently drop a voice back to plain narration. */
    public static final String VOICE_FUDDIFY_CODE = "1";
    public static final String VOICE_PIG_LATIN_CODE = "2";
    public static final String VOICE_BACKWORDS_CODE = "3";
    public static final String VOICE_JOKESTER_CODE = "4";
    public static final String VOICE_FORGETFUL_CODE = "5";
    public static final String VOICE_SHAGGY_CODE = "6";
    public static final String VOICE_DISOBEDIENT_CODE = "7";

    private static final List<Voice> CATALOG = Collections.unmodifiableList(buildCatalog());

    private UnlockPolicy() {
    }

    /** A voice and the condition that earns it. */
    public static final class Voice {
        private final String voiceType;
        private final String voiceCode;
        private final String requirement;

        Voice(String voiceType, String voiceCode, String requirement) {
            this.voiceType = voiceType;
            this.voiceCode = voiceCode;
            this.requirement = requirement;
        }

        public String getVoiceType() {
            return voiceType;
        }

        public String getVoiceCode() {
            return voiceCode;
        }

        /** Player-facing description of what earns this voice, for the profile screen. */
        public String getRequirement() {
            return requirement;
        }

        /** A fresh, locked row for a brand new account. */
        public Unlockable toLockedUnlockable() {
            return new Unlockable(voiceType, voiceCode, false);
        }
    }

    private static List<Voice> buildCatalog() {
        List<Voice> voices = new ArrayList<Voice>();
        voices.add(new Voice(VOICE_FUDDIFY, VOICE_FUDDIFY_CODE, "Make the leaderboard"));
        voices.add(new Voice(VOICE_PIG_LATIN, VOICE_PIG_LATIN_CODE, "Get a perfect leaderboard sentence"));
        voices.add(new Voice(VOICE_BACKWORDS, VOICE_BACKWORDS_CODE, "Play " + BACKWORDS_GAMES + " games"));
        voices.add(new Voice(VOICE_JOKESTER, VOICE_JOKESTER_CODE, "Play your first game"));
        voices.add(new Voice(VOICE_FORGETFUL, VOICE_FORGETFUL_CODE, "Play " + FORGETFUL_GAMES + " games"));
        voices.add(new Voice(VOICE_SHAGGY, VOICE_SHAGGY_CODE, "Play " + SHAGGY_GAMES + " games"));
        voices.add(new Voice(VOICE_DISOBEDIENT, VOICE_DISOBEDIENT_CODE, "Play " + DISOBEDIENT_GAMES + " games"));
        return voices;
    }

    /** Every voice that can be earned, in catalog order. */
    public static List<Voice> catalog() {
        return CATALOG;
    }

    /**
     * Whether the given voice is earned by these stats.
     *
     * <p>Deliberately takes the raw stats rather than a {@code User} so it stays usable from tests
     * and from anywhere that only has the numbers. An unknown voice type returns false rather than
     * throwing — a stale row in the database for a voice that no longer exists shouldn't crash the
     * profile screen.
     */
    public static boolean isEarned(String voiceType, int gamesPlayed, boolean madeLeaderBoard, boolean perfectLeaderBoard) {
        if (voiceType == null) {
            return false;
        }
        if (VOICE_FUDDIFY.equals(voiceType)) {
            return madeLeaderBoard;
        }
        if (VOICE_PIG_LATIN.equals(voiceType)) {
            return perfectLeaderBoard;
        }
        if (VOICE_JOKESTER.equals(voiceType)) {
            return gamesPlayed >= JOKESTER_GAMES;
        }
        if (VOICE_BACKWORDS.equals(voiceType)) {
            return gamesPlayed >= BACKWORDS_GAMES;
        }
        if (VOICE_FORGETFUL.equals(voiceType)) {
            return gamesPlayed >= FORGETFUL_GAMES;
        }
        if (VOICE_SHAGGY.equals(voiceType)) {
            return gamesPlayed >= SHAGGY_GAMES;
        }
        if (VOICE_DISOBEDIENT.equals(voiceType)) {
            return gamesPlayed >= DISOBEDIENT_GAMES;
        }
        return false;
    }

    /**
     * The games-played voice earned by reaching <em>exactly</em> this game count, or null if this
     * game wasn't a milestone — i.e. "what should we congratulate the player for right now".
     *
     * <p>Exact rather than {@code >=} on purpose: this drives a one-off "Unlocked X!" message as a
     * game starts, so it must fire on the game that crosses the line and stay quiet on every game
     * after it. Leaderboard voices aren't here because their screen announces them itself.
     */
    public static String voiceEarnedAtExactly(int gamesPlayed) {
        if (gamesPlayed == JOKESTER_GAMES) {
            return VOICE_JOKESTER;
        }
        if (gamesPlayed == BACKWORDS_GAMES) {
            return VOICE_BACKWORDS;
        }
        if (gamesPlayed == FORGETFUL_GAMES) {
            return VOICE_FORGETFUL;
        }
        if (gamesPlayed == SHAGGY_GAMES) {
            return VOICE_SHAGGY;
        }
        if (gamesPlayed == DISOBEDIENT_GAMES) {
            return VOICE_DISOBEDIENT;
        }
        return null;
    }

    /**
     * Every voice these stats have earned. The caller writes the result; this decides it.
     *
     * <p>Returning the whole earned set rather than "what changed" is deliberate: unlocking is
     * idempotent, and re-asserting an already-unlocked voice is harmless, so an account that
     * somehow missed an unlock (offline at the moment it was earned, or earned before the rule
     * existed at all) is repaired the next time this runs instead of staying permanently short.
     */
    public static List<Voice> earnedVoices(int gamesPlayed, boolean madeLeaderBoard, boolean perfectLeaderBoard) {
        List<Voice> earned = new ArrayList<Voice>();
        for (Voice voice : CATALOG) {
            if (isEarned(voice.getVoiceType(), gamesPlayed, madeLeaderBoard, perfectLeaderBoard)) {
                earned.add(voice);
            }
        }
        return earned;
    }
}
