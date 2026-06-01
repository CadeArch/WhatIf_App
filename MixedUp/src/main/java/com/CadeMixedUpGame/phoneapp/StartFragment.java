package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import java.util.Objects;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class StartFragment extends Fragment {
    private static final long DEV_TAP_WINDOW_MS = 2000L;

    RoomViewModel roomViewModel;
    UserViewModel userViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    private View createGameButton;
    private int debugNameTapCount = 0;
    private long lastDebugNameTapMs = 0L;

    public StartFragment() {
        super(R.layout.fragment_start);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);
        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                roomViewModel.databaseMessage.setValue("");
                setCreateGameSaving(false);
            }
        });
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
                setCreateGameSaving(false);
            }
        });

        // checking to see if user can unlock any voices based on whether or not they are on the leaderboards
        // assuring leaderboards isnt empty so it wont break, if they are on the leaderboards they can unlock it
        // but it wont notify or try to unlock it again if the player has already unlocked that value
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            UiMessenger.showSnackbar(view, "User is not loaded yet. Go back and try again.");
            AppLog.w(AppLog.AUTH, "Start screen opened without current user");
            return;
        }

        if (currentUser.accountPlay) {
            AppLog.d(AppLog.UI, "Start screen account player; leaderboard size=" + leaderBoardViewModel.getLeaderBoard().size());
            if (currentUser.gamesPlayed > 0) {
                userViewModel.getMadeLeaderBoard(userViewModel.getUser());
                userViewModel.getMadePerfectLeaderBoard(userViewModel.getUser());
            }
            if (leaderBoardViewModel.getLeaderBoard().size() > 0) {
                for (LeaderBoardItem lbi : leaderBoardViewModel.getLeaderBoard()) {
                    if (Objects.equals(lbi.getIfContributorID(), currentUser.getUid()) &&
                            Objects.equals(lbi.getThenContributorID(), currentUser.getUid())) {
                        if (!currentUser.perfectLeaderBoard) {
                            currentUser.perfectLeaderBoard = true;
                            userViewModel.unlockVoice(userViewModel.getUser(), "leaderBoards");
                            UiMessenger.showSnackbar(view, "Unlocked pig latin google voice!");
                            AppLog.i(AppLog.AUTH, "Perfect leaderboard unlock triggered");
                        }
                    } else if (Objects.equals(lbi.getIfContributorID(), currentUser.getUid()) ||
                            Objects.equals(lbi.getThenContributorID(), currentUser.getUid())) {
                        if (!currentUser.madeLeaderBoard) {
                            currentUser.madeLeaderBoard = true;
                            userViewModel.unlockVoice(userViewModel.getUser(), "leaderBoards");
                            UiMessenger.showSnackbar(view, "Unlocked fuddify google voice!");
//                        System.out.println("on Leader Board");
                        }
                    }
                }
            }
        }

        EditText enterName = view.findViewById(R.id.enterName);
        // if user has set name autofil to what user had it set to when on this fragment
        if (!userViewModel.localName.equals("guest-")) {
            enterName.setText(userViewModel.localName.replace("guest-", ""));
        }
        TextView userName = view.findViewById(R.id.displayName);

        userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
//                System.out.println("Frag" + user.userName + user.accountPlay);
                if (user.accountPlay) {
                    view.findViewById(R.id.back).setVisibility(View.GONE);
                    userName.setText(user.userName);
                    enterName.setVisibility(View.GONE);
                    view.findViewById(R.id.signOut).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.profile_button).setVisibility(View.VISIBLE);

                }

                // if non account play take away log out button and show back button
                else {
                    view.findViewById(R.id.signOut).setVisibility(View.GONE);
                    view.findViewById(R.id.back).setVisibility(View.VISIBLE);
                    view.findViewById(R.id.profile_button).setVisibility(View.GONE);
                }
            }
        });

        //giving create game button functionality MAYBE MAKE THIS ONLY AVAILABLE TO ACCOUNT PLAY
        createGameButton = view.findViewById(R.id.create_game);
        createGameButton.setOnClickListener(v -> {
            User user = userViewModel.getUser().getValue();
            if (user == null) {
                UiMessenger.showSnackbar(view, "User is not loaded yet. Go back and try again.");
                AppLog.w(AppLog.AUTH, "Create game blocked: missing current user");
                return;
            }

            // storing the name locally to push up to firebase in the join game fragment for non account play
            if (!user.accountPlay) {
                if (enterName.getText().toString().trim().length() == 0 && shouldAutoFillName(enterName)) {
                    enterName.setText(DevBackdoor.randomGuestName());
                    enterName.setSelection(enterName.getText().length());
                    UiMessenger.clearError(enterName);
                    AppLog.i(AppLog.UI, "Debug auto-filled free-play host name");
                }
                else if (enterName.getText().toString().trim().length() == 0) {
                    UiMessenger.showError(enterName, "Name required");
                    AppLog.w(AppLog.UI, "Create game blocked: missing free-play name");
                    return;
                }
                userViewModel.localName = "guest-" + enterName.getText().toString().trim();
                //building user for first time if in freeplay
                user.userName = userViewModel.localName;
                AppLog.d(AppLog.AUTH, "Free-play host name set");
            }

            createReservedRoom(userName);
        });

        //giving the join game button functionality
        view.findViewById(R.id.joinGame).setOnClickListener(v -> {
            User user = userViewModel.getUser().getValue();
            if (user == null) {
                UiMessenger.showSnackbar(view, "User is not loaded yet. Go back and try again.");
                AppLog.w(AppLog.AUTH, "Join game blocked: missing current user");
                return;
            }
            // storing the name locally to push up to firebase in the join game fragment
            if (!user.accountPlay) {
                if (enterName.getText().toString().trim().length() == 0 && shouldAutoFillName(enterName)) {
                    enterName.setText(DevBackdoor.randomGuestName());
                    enterName.setSelection(enterName.getText().length());
                    UiMessenger.clearError(enterName);
                    AppLog.i(AppLog.UI, "Debug auto-filled free-play guest name");
                }
                else if (enterName.getText().toString().trim().length() == 0) {
                    UiMessenger.showError(enterName, "Name required");
                    AppLog.w(AppLog.UI, "Join game blocked: missing free-play name");
                    return;
                }
                userViewModel.localName = "guest-" + enterName.getText().toString().trim();
                user.userName = userViewModel.localName;
                AppLog.d(AppLog.AUTH, "Free-play guest name set");
            }
            //moving to the join game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, JoinGameFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "StartFragment -> JoinGameFrag");
        });

        //giving the signout button functionality
        view.findViewById(R.id.signOut).setOnClickListener(v -> {
            userViewModel.signOut();
            userViewModel.getUser().setValue(null);
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "StartFragment -> FirstFrag after sign out");
        });

        //giving the back button functionality
        view.findViewById(R.id.back).setOnClickListener(v -> {
            userViewModel.getUser().setValue(null);
            //moving to the first game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "StartFragment -> FirstFrag via back");
        });

        //giving the leaderBoards button functionality
        view.findViewById(R.id.leaderboards_button).setOnClickListener(v -> {
            //moving to the leaderboards fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, LeaderBoardFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "StartFragment -> LeaderBoardFrag");
        });

        //giving the profile button functionality
        view.findViewById(R.id.profile_button).setOnClickListener(v -> {

            //moving to the leaderboards fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, ProfileFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            AppLog.i(AppLog.GAME_FLOW, "StartFragment -> ProfileFrag");
        });

    }

    private boolean shouldAutoFillName(EditText enterName) {
        if (!DevBackdoor.isEnabled(getContext()) || enterName == null || enterName.getText().toString().trim().length() > 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDebugNameTapMs > DEV_TAP_WINDOW_MS) {
            debugNameTapCount = 0;
        }
        lastDebugNameTapMs = now;
        debugNameTapCount += 1;
        return debugNameTapCount >= 3;
    }

    private void createReservedRoom(TextView userName) {
        setCreateGameSaving(true);
        roomViewModel.createUniqueRoom(roomID -> {
            if (!isAdded()) {
                return;
            }
            userViewModel.myRoom = roomID;
            MutableLiveData<User> newUser = userViewModel.getUser();
            if (newUser.getValue().accountPlay && (newUser.getValue().userName == null || newUser.getValue().userName.length() == 0)) {
                newUser.getValue().userName = userName.getText().toString();
            }
            else if (!newUser.getValue().accountPlay) {
                newUser.getValue().userName = userViewModel.localName;
            }
            newUser.getValue().host = true;
            newUser.getValue().gameRoom = userViewModel.myRoom;

            userViewModel.pushPerson(newUser, () -> {
                if (!isAdded()) {
                    return;
                }
                setCreateGameSaving(false);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, CreateGameFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
                AppLog.i(AppLog.GAME_FLOW, "StartFragment -> CreateGameFrag room=" + roomID);
            });
        });
    }

    private void setCreateGameSaving(boolean saving) {
        ActionButtonState.setSaving(createGameButton, saving);
    }
}
