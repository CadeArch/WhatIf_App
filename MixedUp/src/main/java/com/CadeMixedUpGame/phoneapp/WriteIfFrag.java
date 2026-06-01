package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.EditText;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class WriteIfFrag extends Fragment {
    private static final long DEV_TAP_WINDOW_MS = 2000L;

    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    private View submitButton;
    private boolean submitInProgress = false;
    private int debugSubmitTapCount = 0;
    private long lastDebugSubmitTapMs = 0L;

    public WriteIfFrag() {
        super(R.layout.fragment_write_if);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.WRITING_IF);
        UiMessenger.observeSnackbar(getViewLifecycleOwner(), userViewModel.databaseMessage, view, () -> setSubmitSaving(false));

        EditText ifSentence = getActivity().findViewById(R.id.ifQuestion);
        submitButton = view.findViewById(R.id.writeIf_submit);
        Utils.clickButtonOnKeyboardSubmit(ifSentence, submitButton, "Keyboard submitted If prompt");

        //giving submit button functionality
        submitButton.setOnClickListener(v -> {
            if (ifSentence.getText().toString().trim().length() == 0 && shouldAutoFillIf(ifSentence)) {
                ifSentence.setText(DevBackdoor.randomIfPrompt());
                ifSentence.setSelection(ifSentence.getText().length());
                AppLog.i(AppLog.UI, "Debug auto-filled If prompt");
            }
            submitIf(ifSentence);
        });

    }

    private boolean shouldAutoFillIf(EditText ifSentence) {
        if (!DevBackdoor.isEnabled(getContext()) || ifSentence == null || ifSentence.getText().toString().trim().length() > 0) {
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

    private void submitIf(EditText ifSentence) {
        if (submitInProgress) {
            return;
        }
        if (ifSentence.getText().toString().trim().equals("")) {
            AppLog.w(AppLog.UI, "If submit blocked: empty question");
            UiMessenger.showError(ifSentence, "Question required");
        }
        else {
            UiMessenger.clearError(ifSentence);
            String ifsent = GameLogic.cleanIfSentence(ifSentence.getText().toString());
            if (ifsent.length() == 0) {
                AppLog.w(AppLog.UI, "If submit blocked: prompt only");
                UiMessenger.showError(ifSentence, "Add your question");
                return;
            }
            userViewModel.getUser().getValue().ifSentence = ifsent;
            userViewModel.getUser().getValue().ifFinished = true;
            setSubmitSaving(true);
            userViewModel.pushIf(userViewModel.getUser(), this::navigateToCollectingQuestions);
        }
    }

    private void setSubmitSaving(boolean saving) {
        submitInProgress = saving;
        ActionButtonState.setSaving(submitButton, saving);
    }

    private void navigateToCollectingQuestions() {
        if (!isAdded()) {
            return;
        }
        userViewModel.gamePhase.setValue(GamePhase.COLLECTING_IFS);
        AppLog.i(AppLog.GAME_FLOW, "WriteIfFrag -> CollectingQuestionsFrag");

        Utils.navigateToFragment(getActivity(), CollectingQuestionsFrag.class);
    }
}
