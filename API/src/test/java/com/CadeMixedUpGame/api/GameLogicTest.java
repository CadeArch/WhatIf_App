package com.CadeMixedUpGame.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class GameLogicTest {
    @Test
    public void cleanIfSentence_trimsPunctuationAndCapitalizes() {
        assertEquals("I win", GameLogic.cleanIfSentence("  if i win?! "));
    }

    @Test
    public void cleanIfSentence_handlesNullBlankAndPunctuationOnlyInput() {
        assertEquals("", GameLogic.cleanIfSentence(null));
        assertEquals("", GameLogic.cleanIfSentence("   "));
        assertEquals("", GameLogic.cleanIfSentence("?!"));
    }

    @Test
    public void cleanIfSentence_keepsInternalWhitespaceAndRemovesAllPunctuation() {
        assertEquals("We win", GameLogic.cleanIfSentence(" what if,  we win!!! "));
    }

    @Test
    public void cleanThenSentence_trimsPunctuationWithoutCapitalizing() {
        assertEquals("we play", GameLogic.cleanThenSentence(" then we play. "));
    }

    @Test
    public void cleanThenSentence_handlesNullAndDoesNotChangeCapitalization() {
        assertEquals("", GameLogic.cleanThenSentence(null));
        assertEquals("we play", GameLogic.cleanThenSentence(" Then we play. "));
    }

    @Test
    public void formatIfSentence_addsPromptPrefixAndQuestionMark() {
        assertEquals("What if I win?", GameLogic.formatIfSentence("if i win"));
    }

    @Test
    public void formatThenSentence_addsResponsePrefixAndPeriod() {
        assertEquals("Then we play.", GameLogic.formatThenSentence("then we play"));
        assertEquals("Then everyone would blame the toaster.", GameLogic.formatThenSentence("Then everyone would blame the toaster."));
    }

    @Test
    public void nextPlayerIndex_wrapsAtEnd() {
        assertEquals(0, GameLogic.nextPlayerIndex(2, 3));
        assertEquals(2, GameLogic.nextPlayerIndex(1, 3));
    }

    @Test
    public void nextPlayerIndex_returnsMinusOneWhenThereAreNoPlayers() {
        assertEquals(-1, GameLogic.nextPlayerIndex(0, 0));
        assertEquals(-1, GameLogic.nextPlayerIndex(0, -3));
    }

    @Test
    public void previousPlayerIndex_wrapsAtBeginning() {
        assertEquals(2, GameLogic.previousPlayerIndex(0, 3));
        assertEquals(0, GameLogic.previousPlayerIndex(1, 3));
    }

    @Test
    public void previousPlayerIndex_returnsMinusOneWhenThereAreNoPlayers() {
        assertEquals(-1, GameLogic.previousPlayerIndex(0, 0));
        assertEquals(-1, GameLogic.previousPlayerIndex(0, -3));
    }

    @Test
    public void randomizedAssignment_isDeterministicForSameSeed() {
        List<String> players = Arrays.asList("a-1", "b-2", "c-3", "d-4");

        assertEquals(
                GameLogic.randomizedAssignment(players, 12345L),
                GameLogic.randomizedAssignment(players, 12345L));
    }

    @Test
    public void randomizedAssignment_doesNotAssignPlayersToThemselves() {
        List<String> players = Arrays.asList("a-1", "b-2", "c-3", "d-4", "e-5");
        List<String> assignments = GameLogic.randomizedAssignment(players, 9988L);

        for (int index = 0; index < players.size(); index++) {
            assertFalse(players.get(index).equals(assignments.get(index)));
        }
    }

    @Test
    public void randomizedAssignment_preservesEveryPlayerExactlyOnce() {
        List<String> players = Arrays.asList("a-1", "b-2", "c-3", "d-4", "e-5");
        List<String> assignments = GameLogic.randomizedAssignment(players, 123L);

        ArrayList<String> sortedPlayers = new ArrayList<String>(players);
        ArrayList<String> sortedAssignments = new ArrayList<String>(assignments);
        Collections.sort(sortedPlayers);
        Collections.sort(sortedAssignments);

        assertEquals(sortedPlayers, sortedAssignments);
    }

    @Test
    public void randomizedAssignment_handlesTwoPlayersWithoutSelfAssignment() {
        List<String> players = Arrays.asList("a-1", "b-2");
        List<String> assignments = GameLogic.randomizedAssignment(players, 7L);

        assertEquals("b-2", assignments.get(0));
        assertEquals("a-1", assignments.get(1));
    }

    @Test
    public void randomizedAssignment_allowsSinglePlayerFallback() {
        assertEquals(
                Collections.singletonList("solo-1"),
                GameLogic.randomizedAssignment(Collections.singletonList("solo-1"), 42L));
    }

    @Test
    public void newRoundId_returnsNonEmptyRoundPrefixedValue() {
        String roundId = GameLogic.newRoundId();

        assertTrue(roundId.startsWith("round-"));
        assertTrue(roundId.length() > "round-".length());
    }

    @Test
    public void randomRoomCode_returnsTwoDistinctFourLetterLowercaseWordsJoinedByADash() {
        String code = GameLogic.randomRoomCode(new java.util.Random(42));

        String[] parts = code.split("-");
        assertEquals(2, parts.length);
        assertEquals(4, parts[0].length());
        assertEquals(4, parts[1].length());
        assertFalse(parts[0].equals(parts[1]));
        assertEquals(code, code.toLowerCase());
    }

    @Test
    public void randomRoomCode_isDeterministicForAFixedSeed() {
        assertEquals(GameLogic.randomRoomCode(new java.util.Random(7)),
                GameLogic.randomRoomCode(new java.util.Random(7)));
    }

    @Test
    public void randomRoomCode_nullRandomFallsBackToANewSource() {
        String code = GameLogic.randomRoomCode(null);

        assertEquals(2, code.split("-").length);
    }

    @Test
    public void playerKeyUsesUserNameAndId() {
        com.CadeMixedUpGame.api.models.User user = new com.CadeMixedUpGame.api.models.User("guest-Cade");
        user.userID = 42;

        assertEquals("guest-Cade-42", GameLogic.playerKey(user));
        assertEquals("", GameLogic.playerKey(null));
    }

    @Test
    public void isCurrentRound_acceptsMatchingRoundOnly() {
        assertTrue(GameLogic.isCurrentRound("round-1", "round-1"));
        assertFalse(GameLogic.isCurrentRound("round-1", "round-2"));
        assertFalse(GameLogic.isCurrentRound("", "round-1"));
        assertFalse(GameLogic.isCurrentRound(null, "round-1"));
        assertFalse(GameLogic.isCurrentRound("round-1", null));
    }

    @Test
    public void mostCommonVote_returnsHighestFrequency() {
        assertEquals("b", GameLogic.mostCommonVote(Arrays.asList("a", "b", "b", "c")));
    }

    @Test
    public void mostCommonVote_ignoresNullVotesAndKeepsFirstWinnerOnTie() {
        assertEquals("a", GameLogic.mostCommonVote(Arrays.asList(null, "a", "b", "a", "b")));
    }

    @Test
    public void mostCommonVote_returnsEmptyStringWhenNoVoteCanWin() {
        assertEquals("", GameLogic.mostCommonVote(Collections.<String>emptyList()));
        assertEquals("", GameLogic.mostCommonVote(new ArrayList<String>(Arrays.asList(null, null))));
    }

    @Test
    public void mutateVoiceText_fuddifyReplacesRs() {
        assertEquals("wun awound", GameLogic.mutateVoiceText("run around", "1"));
    }

    @Test
    public void mutateVoiceText_fuddifyAlsoCatchesLsAndCapitals() {
        // Fudd says "wittwe", not "little" - and the If half is always capitalized, so a
        // case-sensitive swap missed the very first word of every sentence.
        assertEquals("Wun awound a wittwe", GameLogic.mutateVoiceText("Run around a little", "1"));
    }


    @Test
    public void mutateVoiceText_pigLatinMovesStartingConsonants() {
        assertEquals("ellohay, appleway", GameLogic.mutateVoiceText("hello, apple", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinHandlesSingleLettersAndConsonantClusters() {
        assertEquals("away ogfray", GameLogic.mutateVoiceText("a frog", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinMovesClustersLongerThanTwoLetters() {
        // Only the first two letters used to move, so "string" came out "ringstay".
        assertEquals("ingstray eorythay", GameLogic.mutateVoiceText("string theory", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinTreatsQuAsOneUnit() {
        // The "u" was being read as the first vowel, giving "ueenqay".
        assertEquals("eenquay", GameLogic.mutateVoiceText("queen", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinKeepsPunctuationOutsideTheWord() {
        // The comma used to be dragged into the middle of the word: "midnight," -> "dnight,miay".
        assertEquals("idnightmay, enthay", GameLogic.mutateVoiceText("midnight, then", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinDoesNotStrandCapitalsMidWord() {
        // "What" used to come out "atWhay".
        assertEquals("Atwhay ifway avitygray ooktay eekendsway offway?",
                GameLogic.mutateVoiceText("What if gravity took weekends off?", "2"));
    }

    @Test
    public void mutateVoiceText_pigLatinTreatsYAsAConsonantOnlyAtTheStart() {
        assertEquals("ellowyay ymay", GameLogic.mutateVoiceText("yellow my", "2"));
    }

    @Test
    public void mutateVoiceText_backwordsReversesEachHalfButKeepsTheHalvesInOrder() {
        // The order of the words reverses; the words themselves do not, and neither does the order
        // of the two halves. Reversing the letters inside each word ("neht") gave text-to-speech
        // nothing but noise to say, and reversing the whole sentence as one run delivered the Then
        // before the What-if - the listener got the punchline with no setup to hang it on.
        assertEquals("sneezes elephant the if What, away runs everybody Then.",
                GameLogic.mutateVoiceText("What if the elephant sneezes?, Then everybody runs away.", "3"));
    }

    @Test
    public void mutateVoiceText_backwordsHandlesASentenceWithNoIfHalf() {
        // No "?" to split on, so there is only one run to reverse - it must not lose the text.
        assertEquals("then if.", GameLogic.mutateVoiceText("if, then", "3"));
    }

    @Test
    public void mutateVoiceText_unknownCodeReturnsOriginalText() {
        assertEquals("if, then", GameLogic.mutateVoiceText("if, then", "unknown"));
        assertEquals("if, then", GameLogic.mutateVoiceText("if, then", null));
    }

    @Test
    public void mutateVoiceText_jokesterLaughsThroughTheSentence() {
        assertEquals("if, heh heh, then ha ha ha!", GameLogic.mutateVoiceText("if, then", "4"));
    }

    @Test
    public void mutateVoiceText_forgetfulTrailsOffOnlyOnLongerWords() {
        // "if" and "the" stay intact; "elephant" is long enough to lose the thread on - and the
        // whole word always follows the stumble, so the sentence itself is never lost.
        String spoken = GameLogic.mutateVoiceText("if the elephant", "5", new Random(11));
        assertTrue("short words must be left alone: " + spoken, spoken.startsWith("if the ele"));
        assertTrue("the full word must still arrive: " + spoken, spoken.endsWith("elephant"));
        assertTrue("something has to be said in the gap: " + spoken,
                spoken.length() > "if the eleelephant".length());
    }

    @Test
    public void mutateVoiceText_forgetfulVariesHowItLosesTheWord() {
        // Which words stumble is fixed by length; only the excuse in the gap is drawn. One fixed
        // excuse became a catchphrase by the third long word of the first sentence.
        Set<String> interjections = new HashSet<>();
        Random random = new Random(5);
        for (int draw = 0; draw < 500; draw++) {
            String spoken = GameLogic.mutateVoiceText("if the elephant", "5", random);
            interjections.add(spoken.substring("if the ele".length(),
                    spoken.length() - "elephant".length()));
        }
        assertTrue("expected at least 8 different interjections, saw " + interjections.size(),
                interjections.size() >= 8);
    }

    @Test
    public void mutateVoiceText_forgetfulNeverStumblesTheSameWayTwiceRunning() {
        // Back-to-back repeats inside one sentence read as a stuck record, not as someone groping
        // for a word - and sentences routinely have several long words in a row.
        Random random = new Random(3);
        for (int draw = 0; draw < 200; draw++) {
            String spoken = GameLogic.mutateVoiceText("elephant elephant", "5", random);
            String[] stumbles = spoken.split("elephant");
            assertEquals("expected one stumble per long word: " + spoken, 2, stumbles.length);
            assertNotEquals("the same excuse twice running: " + spoken,
                    stumbles[0].trim(), stumbles[1].trim());
        }
    }

    @Test
    public void mutateVoiceText_forgetfulIsReproducibleFromASeededRandom() {
        assertEquals(GameLogic.mutateVoiceText("if the elephant sneezes", "5", new Random(88)),
                GameLogic.mutateVoiceText("if the elephant sneezes", "5", new Random(88)));
    }

    @Test
    public void mutateVoiceText_shaggyBookendsWithZoinksAndLikesTheClauseBreak() {
        // Too short to earn any of the scattered extras, so this is the fixed part of the delivery.
        assertEquals("Zoinks! Like, if, like, then Zoinks!",
                GameLogic.mutateVoiceText("if, then", "6", new Random(1)));
    }

    @Test
    public void mutateVoiceText_shaggyScattersAFewExtraLikesWithoutOverdoingIt() {
        // A verbal tic every other word stops being a character and becomes unlistenable, so the
        // extras are budgeted per word and capped - this guards the ceiling, not the exact spots.
        String sample = "What if the elephant sneezes at midnight, Then everybody runs away from "
                + "the building screaming loudly";
        Random random = new Random(4);
        Set<String> renderings = new HashSet<>();
        for (int draw = 0; draw < 200; draw++) {
            String spoken = GameLogic.mutateVoiceText(sample, "6", random);
            assertTrue("must open with Zoinks: " + spoken, spoken.startsWith("Zoinks! Like, "));
            assertTrue("must close with Zoinks: " + spoken, spoken.endsWith(" Zoinks!"));
            assertFalse("two likes in a row read as a stutter, not a tic: " + spoken,
                    spoken.contains("like, like,"));
            // The clause break always gets one; 17 words earns two more and no further. The
            // capitalised "Like," in the opener is not counted, which is why this is exactly three.
            assertEquals("wrong number of likes for a 17-word sentence: " + spoken,
                    3, countOccurrences(spoken, "like,"));
            renderings.add(spoken);
        }
        assertTrue("the extras should land somewhere different across readings, saw only "
                + renderings.size() + " distinct rendering(s)", renderings.size() > 1);
    }

    @Test
    public void mutateVoiceText_shaggyIsReproducibleFromASeededRandom() {
        String sample = "What if the elephant sneezes at midnight, Then everybody runs away";
        assertEquals(GameLogic.mutateVoiceText(sample, "6", new Random(77)),
                GameLogic.mutateVoiceText(sample, "6", new Random(77)));
    }

    private int countOccurrences(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    @Test
    public void mutateVoiceText_disobedientComplainsEitherSideOfAnIntactSentence() {
        String spoken = GameLogic.mutateVoiceText("if, then", "7", new Random(42));
        int sentenceAt = spoken.indexOf("if, then");
        assertTrue("the sentence itself must survive intact: " + spoken, sentenceAt >= 0);
        assertTrue("it should complain before reading: " + spoken, sentenceAt > 0);
        assertTrue("...and complain again after: " + spoken,
                spoken.length() > sentenceAt + "if, then".length() + 1);
    }

    @Test
    public void mutateVoiceText_disobedientVariesItsComplaintsAcrossReadings() {
        // One fixed opener and one fixed closer is a joke that lands once; this voice gets picked
        // repeatedly within a single round, so by the third sentence the players were reciting
        // along with it. Both lists are asserted separately because they are drawn independently -
        // a regression that froze one of them would still leave plenty of distinct whole outputs.
        String sample = "if the elephant sneezes, then everybody runs away";
        Set<String> openers = new HashSet<>();
        Set<String> closers = new HashSet<>();
        Random random = new Random(7);
        for (int draw = 0; draw < 500; draw++) {
            String spoken = GameLogic.mutateVoiceText(sample, "7", random);
            int sentenceAt = spoken.indexOf(sample);
            assertTrue("the sentence must survive every draw: " + spoken, sentenceAt > 0);
            openers.add(spoken.substring(0, sentenceAt));
            closers.add(spoken.substring(sentenceAt + sample.length()));
        }
        assertTrue("expected at least 7 different openers, saw " + openers.size(), openers.size() >= 7);
        assertTrue("expected at least 7 different closers, saw " + closers.size(), closers.size() >= 7);
    }

    @Test
    public void mutateVoiceText_disobedientIsReproducibleFromASeededRandom() {
        // The randomness is injectable so this voice stays assertable at all - see randomRoomCode
        // for the same pattern.
        assertEquals(GameLogic.mutateVoiceText("if, then", "7", new Random(99)),
                GameLogic.mutateVoiceText("if, then", "7", new Random(99)));
    }

    @Test
    public void everyUnlockableVoiceActuallyChangesTheText() {
        // The structural guard, and the reason this test exists: codes 4-7 were in the catalog with
        // no branch in mutateVoiceText, so they fell through to `return ifThen`. While those voices
        // were permanently locked nobody could notice; the moment they became earnable they were
        // four rewards that sound exactly like no reward. Any new voice added to the catalog
        // without a mutation fails here by name instead of shipping silent.
        String sample = "if the elephant sneezes, then everybody runs away";
        List<String> silent = new ArrayList<>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            if (sample.equals(GameLogic.mutateVoiceText(sample, voice.getVoiceCode()))) {
                silent.add(voice.getVoiceType() + " (code " + voice.getVoiceCode() + ")");
            }
        }
        assertTrue("these unlockable voices leave the sentence unchanged, so earning them does "
                + "nothing the player can hear: " + silent, silent.isEmpty());
    }

    @Test
    public void everyUnlockableVoiceHasAUniqueCode() {
        // Two voices sharing a code means the second is unreachable in mutateVoiceText.
        List<String> codes = new ArrayList<>();
        for (UnlockPolicy.Voice voice : UnlockPolicy.catalog()) {
            assertFalse("duplicate voice code " + voice.getVoiceCode() + " on " + voice.getVoiceType(),
                    codes.contains(voice.getVoiceCode()));
            codes.add(voice.getVoiceCode());
        }
    }
}
