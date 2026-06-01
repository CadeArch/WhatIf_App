package com.CadeMixedUpGame.api.viewmodels;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RoomViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void injectedConstructorDoesNotAttachFirebaseListenersByDefault() {
        FakeGameRepository repository = new FakeGameRepository();

        RoomViewModel viewModel = new RoomViewModel(repository);

        assertFalse(repository.listenToPlayersCalled);
        assertEquals(0, viewModel.getRooms().size());
        assertEquals(0, viewModel.roomNames.size());
    }

    @Test
    public void makeRoomIDReturnsFourUsableCharacters() {
        RoomViewModel viewModel = new RoomViewModel(new FakeGameRepository());

        String roomID = viewModel.makeRoomID();

        assertEquals(4, roomID.length());
        for (int index = 0; index < roomID.length(); index++) {
            assertTrue(viewModel.allChars.contains(String.valueOf(roomID.charAt(index))));
        }
    }

    @Test
    public void makeRoomIDCanReturnMixedCaseLettersAndDigits() {
        RoomViewModel viewModel = new RoomViewModel(new FakeGameRepository());

        assertTrue(viewModel.allChars.contains("a"));
        assertTrue(viewModel.allChars.contains("Z"));
        assertTrue(viewModel.allChars.contains("0"));
        assertTrue(viewModel.allChars.contains("1"));
    }

    @Test
    public void gameInProgressMethodsWriteThroughRepositoryWhenRoomExists() {
        FakeGameRepository repository = new FakeGameRepository();
        RoomViewModel viewModel = new RoomViewModel(repository);

        viewModel.room = new Room("AB12");
        viewModel.gameInProgressTrue();

        assertEquals("AB12", repository.lastRoomId);
        assertTrue(repository.lastInProgress);

        viewModel.gameInProgressFalse("AB12");

        assertEquals("AB12", repository.lastRoomId);
        assertFalse(repository.lastInProgress);
    }

    @Test
    public void clearLocalRoundStateResetsReplaySensitiveValues() {
        RoomViewModel viewModel = new RoomViewModel(new FakeGameRepository());
        viewModel.activeReaderIndex.setValue(3);
        viewModel.readingComplete.setValue(true);
        viewModel.replayState.setValue("yes");
        viewModel.currentRoundId.setValue("round-old");
        viewModel.currentAssignment.setValue(new RoundAssignment(
                "player-1",
                "if-2",
                "then-3",
                "If Contributor",
                "Then Contributor",
                "ifUid",
                "thenUid",
                123L,
                "round-old",
                0));

        viewModel.clearLocalRoundState();

        assertEquals(Integer.valueOf(0), viewModel.activeReaderIndex.getValue());
        assertFalse(viewModel.readingComplete.getValue());
        assertEquals("", viewModel.replayState.getValue());
        assertEquals("", viewModel.currentRoundId.getValue());
        assertFalse(viewModel.currentRoundLoaded.getValue());
        assertNull(viewModel.currentAssignment.getValue());
    }

    @Test
    public void localRoundStateCanBeResetBetweenRepeatedReplayLoops() {
        RoomViewModel viewModel = new RoomViewModel(new FakeGameRepository());

        viewModel.activeReaderIndex.setValue(2);
        viewModel.readingComplete.setValue(true);
        viewModel.replayState.setValue("yes");
        viewModel.currentRoundId.setValue("round-one");
        viewModel.clearLocalRoundState();
        viewModel.activeReaderIndex.setValue(1);
        viewModel.readingComplete.setValue(true);
        viewModel.replayState.setValue("yes");
        viewModel.currentRoundId.setValue("round-two");
        viewModel.clearLocalRoundState();

        assertEquals(Integer.valueOf(0), viewModel.activeReaderIndex.getValue());
        assertFalse(viewModel.readingComplete.getValue());
        assertEquals("", viewModel.replayState.getValue());
        assertEquals("", viewModel.currentRoundId.getValue());
        assertFalse(viewModel.currentRoundLoaded.getValue());
    }

    private static class FakeGameRepository implements GameRepository {
        boolean listenToPlayersCalled;
        String lastRoomId;
        boolean lastInProgress;

        @Override
        public DatabaseReference root() {
            return null;
        }

        @Override
        public DatabaseReference room(String roomId) {
            return null;
        }

        @Override
        public DatabaseReference players(String roomId) {
            return null;
        }

        @Override
        public DatabaseReference player(String roomId, String playerKey) {
            return null;
        }

        @Override
        public Task<Void> setRoomInProgress(String roomId, boolean inProgress) {
            lastRoomId = roomId;
            lastInProgress = inProgress;
            return null;
        }

        @Override
        public void listenToPlayers(String roomId, ChildEventListener listener) {
            listenToPlayersCalled = true;
        }

        @Override
        public void removePlayersListener(String roomId, ChildEventListener listener) {
        }
    }
}
