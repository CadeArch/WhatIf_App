package com.CadeMixedUpGame.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void mutateVoiceText_fuddifyLeavesUppercaseRsUnchanged() {
        assertEquals("Run awound", GameLogic.mutateVoiceText("Run around", "1"));
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
    public void mutateVoiceText_backwordsReversesWords() {
        assertEquals("fi, neht", GameLogic.mutateVoiceText("if, then", "3"));
    }

    @Test
    public void mutateVoiceText_unknownCodeReturnsOriginalText() {
        assertEquals("if, then", GameLogic.mutateVoiceText("if, then", "unknown"));
        assertEquals("if, then", GameLogic.mutateVoiceText("if, then", null));
    }
}
