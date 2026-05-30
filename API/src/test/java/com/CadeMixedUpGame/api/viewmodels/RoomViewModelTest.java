package com.CadeMixedUpGame.api.viewmodels;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.repositories.GameRepository;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RoomViewModelTest {
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
