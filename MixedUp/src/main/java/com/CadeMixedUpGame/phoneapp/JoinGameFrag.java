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
import java.util.ArrayList;

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

        roomViewModel.inProgress.observe(this.getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (!joinInProgress || pendingRoom.length() == 0 || aBoolean == null) {
                    return;
                }
                if (!aBoolean) {
                    joinRoom(pendingRoom);
                }
                else {
                    joinInProgress = false;
                    AppLog.w(AppLog.ROOM, "Join blocked: game in progress room=" + pendingRoom);
                    UiMessenger.showSnackbar(view, "Game in progress!");
                }
            }
        });

        // giving joinGame start button functionality
        view.findViewById(R.id.joinGame_start).setOnClickListener(v -> {
            ArrayList<String> allrooms = roomViewModel.roomNames;
            String myRoom = roomToJoin.getText().toString();

            // if the room they want to join exists out there it will add them to the room and push their
            // data to firebase, else it will let the user know it doesn't exist
            // TODO: functionalize a bit more for readability
            if (myRoom.length() == 0) {
                AppLog.w(AppLog.ROOM, "Join blocked: empty room code");
                UiMessenger.showError(roomToJoin, "Game code required");
            }
            else if (allrooms.contains(myRoom)) {
                UiMessenger.clearError(roomToJoin);
                UiMessenger.hideBanner(view);
                pendingRoom = myRoom;
                joinInProgress = true;
                AppLog.i(AppLog.ROOM, "Checking room progress before join room=" + myRoom);
                roomViewModel.checkIfInProgress(myRoom);
            }
            else {
                AppLog.w(AppLog.ROOM, "Join blocked: room does not exist room=" + myRoom);
                UiMessenger.showSnackbar(view, "That game room doesn't exist!");
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

        userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
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
        });
    }
}
