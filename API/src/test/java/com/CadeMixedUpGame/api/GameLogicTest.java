package com.CadeMixedUpGame.api;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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
        assertEquals("then we play.", GameLogic.formatThenSentence("then we play"));
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
    public void randomizedAssignment_allowsSinglePlayerFallback() {
        assertEquals(
                Collections.singletonList("solo-1"),
                GameLogic.randomizedAssignment(Collections.singletonList("solo-1"), 42L));
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
