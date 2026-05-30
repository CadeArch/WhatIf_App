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
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;

    public WriteIfFrag() {
        super(R.layout.fragment_write_if);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.WRITING_IF);
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
            }
        });

        EditText ifSentence = getActivity().findViewById(R.id.ifQuestion);

        //giving submit button functionality
        view.findViewById(R.id.writeIf_submit).setOnClickListener(v -> {
            if (ifSentence.getText().toString().equals("")) {
                AppLog.w(AppLog.UI, "If submit blocked: empty question");
                UiMessenger.showError(ifSentence, "Question required");
            }
            else {
                UiMessenger.clearError(ifSentence);
                String ifsent = GameLogic.cleanIfSentence(ifSentence.getText().toString());
                userViewModel.getUser().getValue().ifSentence = ifsent;
                userViewModel.getUser().getValue().ifFinished = true;
                userViewModel.gamePhase.setValue(GamePhase.COLLECTING_IFS);
                AppLog.i(AppLog.GAME_FLOW, "WriteIfFrag -> CollectingQuestionsFrag");

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, CollectingQuestionsFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            }
        });

    }
}
