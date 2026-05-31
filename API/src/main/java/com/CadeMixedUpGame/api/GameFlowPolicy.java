package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.User;

import java.util.List;

public final class GameFlowPolicy {
    private GameFlowPolicy() {
    }

    public static int countFinishedIfs(List<User> players) {
        int count = 0;
        if (players == null) {
            return count;
        }
        for (User player : players) {
            if (player != null && Boolean.TRUE.equals(player.ifFinished)) {
                count += 1;
            }
        }
        return count;
    }

    public static int countFinishedThens(List<User> players) {
        int count = 0;
        if (players == null) {
            return count;
        }
        for (User player : players) {
            if (player != null && Boolean.TRUE.equals(player.thenFinished)) {
                count += 1;
            }
        }
        return count;
    }

    public static boolean allPlayersFinishedIfs(List<User> players) {
        return players != null && players.size() > 0 && countFinishedIfs(players) == players.size();
    }

    public static boolean allPlayersFinishedThens(List<User> players) {
        return players != null && players.size() > 0 && countFinishedThens(players) == players.size();
    }

    public static boolean allPlayersHaveAccounts(List<User> players) {
        if (players == null || players.size() == 0) {
            return false;
        }
        for (User player : players) {
            if (player == null || !Boolean.TRUE.equals(player.accountPlay)) {
                return false;
            }
        }
        return true;
    }

    public static boolean finalReaderPassed(int nextReaderIndex, int readOrderSize) {
        return readOrderSize > 0 && nextReaderIndex >= readOrderSize;
    }

    public static String normalizeRoomCodeInput(String roomCode) {
        return roomCode == null ? "" : roomCode.trim();
    }
}
