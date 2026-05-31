package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.EditText;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class JoinGameFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    boolean loadedUsers = false;
    boolean joinInProgress = false;
    String pendingRoom = "";

    public JoinGameFrag() {
        super(R.layout.fragment_join_game);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        //giving back button functionality
        view.findViewById(R.id.joinGame_back).setOnClickListener(v -> {
            Utils.navigateToFragment(getActivity(), StartFragment.class);
        });

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        EditText roomToJoin = view.findViewById(R.id.enterGameCode);
        View joinButton = view.findViewById(R.id.joinGame_start);
        Utils.clickButtonOnKeyboardSubmit(roomToJoin, joinButton, "Keyboard submitted room join");

        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showBanner(view, message, UiMessenger.MessageType.ERROR);
                roomViewModel.databaseMessage.setValue("");
            }
        });

        roomViewModel.roomJoinState.observe(this.getViewLifecycleOwner(), new Observer<RoomViewModel.RoomJoinState>() {
            @Override
            public void onChanged(RoomViewModel.RoomJoinState state) {
                if (!joinInProgress || pendingRoom.length() == 0 || state == null || state == RoomViewModel.RoomJoinState.IDLE) {
                    return;
                }
                if (state == RoomViewModel.RoomJoinState.AVAILABLE) {
                    joinRoom(pendingRoom);
                }
                else if (state == RoomViewModel.RoomJoinState.IN_PROGRESS) {
                    joinInProgress = false;
                    AppLog.w(AppLog.ROOM, "Join blocked: game in progress room=" + pendingRoom);
                    UiMessenger.showSnackbar(view, "Game in progress!");
                }
                else if (state == RoomViewModel.RoomJoinState.DOES_NOT_EXIST) {
                    joinInProgress = false;
                    AppLog.w(AppLog.ROOM, "Join blocked: room does not exist room=" + pendingRoom);
                    UiMessenger.showSnackbar(view, "That game room doesn't exist!");
                }
                else if (state == RoomViewModel.RoomJoinState.ERROR) {
                    joinInProgress = false;
                    UiMessenger.showBanner(view, "Could not check room. Check your connection and try again.", UiMessenger.MessageType.ERROR);
                }
            }
        });

        // giving joinGame start button functionality
        joinButton.setOnClickListener(v -> {
            String myRoom = GameFlowPolicy.normalizeRoomCodeInput(roomToJoin.getText().toString());

            // if the room they want to join exists out there it will add them to the room and push their
            // data to firebase, else it will let the user know it doesn't exist
            if (myRoom.length() == 0) {
                AppLog.w(AppLog.ROOM, "Join blocked: empty room code");
                UiMessenger.showError(roomToJoin, "Game code required");
            }
            else {
                UiMessenger.clearError(roomToJoin);
                UiMessenger.hideBanner(view);
                pendingRoom = myRoom;
                joinInProgress = true;
                roomViewModel.checkRoomCanJoin(myRoom);
            }
        });
    }

    private void joinRoom(String myRoom) {
        joinInProgress = false;
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            AppLog.w(AppLog.AUTH, "Join room blocked: missing current user");
            UiMessenger.showBanner(requireView(), "User is not loaded yet. Go back and try again.", UiMessenger.MessageType.ERROR);
            return;
        }
        AppLog.i(AppLog.ROOM, "Joining room=" + myRoom);
        if (!loadedUsers) {
            userViewModel.loadUsers(myRoom);
            loadedUsers = true;
        }
        userViewModel.myRoom = myRoom;
        currentUser.gameRoom = myRoom;
        currentUser.host = false;
        userViewModel.pushPerson(userViewModel.getUser(), () -> {
            if (!isAdded()) {
                return;
            }
            AppLog.i(AppLog.GAME_FLOW, "JoinGameFrag -> WaitingForHostFrag room=" + myRoom);
            Utils.navigateToFragment(getActivity(), WaitingForHostFrag.class);
            userViewModel.onWaitingForHost = true;
        });
    }
}
