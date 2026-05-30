package com.CadeMixedUpGame.api.viewmodels;

import com.CadeMixedUpGame.api.models.GamePhase;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class UserViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Test
    public void injectedConstructorInitializesLocalStateWithoutFirebaseAuthListener() {
        UserViewModel viewModel = new UserViewModel(null, null);

        assertEquals(GamePhase.LOBBY, viewModel.gamePhase.getValue());
        assertEquals(0, viewModel.getUsers().size());
        assertNull(viewModel.getUser().getValue());
        assertEquals("", viewModel.localRandIf);
        assertEquals("", viewModel.localName);
    }
}
