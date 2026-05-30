package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.EditText;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class JoinGameFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    boolean loadedUsers = false;
    boolean joinInProgress = false;
    String pendingRoom = "";
    private ObservableList.OnListChangedCallback<ObservableList<User>> usersCallback;

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
        view.findViewById(R.id.joinGame_start).setOnClickListener(v -> {
            String myRoom = roomToJoin.getText().toString().trim();

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
        AppLog.i(AppLog.ROOM, "Joining room=" + myRoom);
        if (!loadedUsers) {
            userViewModel.loadUsers(myRoom);
            loadedUsers = true;
        }
        userViewModel.myRoom = myRoom;
        userViewModel.getUser().getValue().gameRoom = myRoom;
        userViewModel.getUser().getValue().host = false;
        userViewModel.getUser().getValue().hostStarted = false;
        userViewModel.pushPerson(userViewModel.getUser());

        usersCallback = new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                for (User player : userViewModel.getUsers()) {
                    if (userViewModel.localName.equals(player.userName) && !player.hostStarted && !userViewModel.onWaitingForHost) {
                        AppLog.i(AppLog.GAME_FLOW, "JoinGameFrag -> WaitingForHostFrag room=" + myRoom);
                        Utils.navigateToFragment(getActivity(), WaitingForHostFrag.class);
                        userViewModel.onWaitingForHost = true;
                    }
                    if (player.host) {
                        AppLog.d(AppLog.ROOM, "Host found while joining: " + player.userName);
                        userViewModel.host.setValue(player);
                    }
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

            }
        };
        userViewModel.getUsers().addOnListChangedCallback(usersCallback);
    }

    @Override
    public void onDestroyView() {
        if (usersCallback != null) {
            userViewModel.getUsers().removeOnListChangedCallback(usersCallback);
            usersCallback = null;
        }
        super.onDestroyView();
    }
}
