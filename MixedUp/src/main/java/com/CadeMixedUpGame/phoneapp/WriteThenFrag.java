package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.RoundAssignment;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class WriteThenFrag extends Fragment {
    private static final long DEV_TAP_WINDOW_MS = 2000L;

    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    String myRandomIf = "";
    View submitButton;
    private int debugSubmitTapCount = 0;
    private long lastDebugSubmitTapMs = 0L;

    public WriteThenFrag() {
        super(R.layout.fragment_write_then);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.WRITING_THEN);
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
            }
        });

        TextView ifQuestion = view.findViewById(R.id.myIfQuestion);
        EditText thenSentence = view.findViewById(R.id.thenAnswer);
        submitButton = view.findViewById(R.id.writeThen_submit);
        submitButton.setEnabled(false);
        bindAssignment(ifQuestion);


        //giving submit button functionality
        submitButton.setOnClickListener(v -> {
            if (thenSentence.getText().toString().trim().length() == 0 && shouldAutoFillThen(thenSentence)) {
                thenSentence.setText(DevBackdoor.randomThenResponse());
                thenSentence.setSelection(thenSentence.getText().length());
                AppLog.i(AppLog.UI, "Debug auto-filled Then response");
            }
            submitThen(view, thenSentence);
        });



    }

    private boolean shouldAutoFillThen(EditText thenSentence) {
        if (!DevBackdoor.isEnabled(getContext()) || thenSentence == null || thenSentence.getText().toString().trim().length() > 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastDebugSubmitTapMs > DEV_TAP_WINDOW_MS) {
            debugSubmitTapCount = 0;
        }
        lastDebugSubmitTapMs = now;
        debugSubmitTapCount += 1;
        return debugSubmitTapCount >= 3;
    }

    private void submitThen(View view, EditText thenSentence) {
        if (myRandomIf == null || myRandomIf.length() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "Then submit blocked: assignment not loaded");
            UiMessenger.showSnackbar(view, "Still loading your prompt. Try again in a moment.");
        }
        else if (thenSentence.getText().toString().trim().equals("")) {
            AppLog.w(AppLog.UI, "Then submit blocked: empty response");
            UiMessenger.showError(thenSentence, "Response required");
        }
        else {
            UiMessenger.clearError(thenSentence);
            String thenSent = GameLogic.cleanThenSentence(thenSentence.getText().toString());
            if (thenSent.length() == 0) {
                AppLog.w(AppLog.UI, "Then submit blocked: response prefix only");
                UiMessenger.showError(thenSentence, "Add your response");
                return;
            }
            userViewModel.getUser().getValue().thenSentence = thenSent;
            userViewModel.getUser().getValue().thenFinished = true;
            userViewModel.gamePhase.setValue(GamePhase.COLLECTING_THENS);
            AppLog.i(AppLog.GAME_FLOW, "WriteThenFrag -> CollectingAnswersFrag");

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, CollectingAnswersFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        }
    }

    private void bindAssignment(TextView ifQuestion) {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            AppLog.w(AppLog.GAME_FLOW, "WriteThen assignment skipped: missing current user");
            return;
        }

        roomViewModel.listenToAssignment(currentUser.gameRoom, GameLogic.playerKey(currentUser));
        roomViewModel.currentAssignment.observe(getViewLifecycleOwner(), assignment -> {
            if (assignment != null) {
                applyAssignment(assignment, ifQuestion);
            }
        });
    }

    private void applyAssignment(RoundAssignment assignment, TextView ifQuestion) {
        User ifOwner = findUserByKey(assignment.ifOwnerKey);
        if (ifOwner == null || ifOwner.ifSentence == null || ifOwner.ifSentence.length() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "WriteThen assignment loaded without If sentence ownerKey=" + assignment.ifOwnerKey);
            return;
        }

        myRandomIf = ifOwner.ifSentence;
        userViewModel.localRandIf = myRandomIf;
        ifQuestion.setText(GameLogic.formatIfSentence(myRandomIf));
        submitButton.setEnabled(true);
        AppLog.i(AppLog.GAME_FLOW, "WriteThen assignment applied ifOwner=" + assignment.ifOwnerKey);
    }

    private User findUserByKey(String playerKey) {
        for (User user : userViewModel.getUsers()) {
            if (GameLogic.playerKey(user).equals(playerKey)) {
                return user;
            }
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        roomViewModel.removeAssignmentListener();
        super.onDestroyView();
    }
}
