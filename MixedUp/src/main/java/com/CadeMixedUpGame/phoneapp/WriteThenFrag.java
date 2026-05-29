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
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class WriteThenFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    String myRandomIf = "";

    public WriteThenFrag() {
        super(R.layout.fragment_write_then);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);
        userViewModel.gamePhase.setValue(GamePhase.WRITING_THEN);

//        for (User user: userViewModel.getUsers()) {
//            System.out.println("ORDER IN WRITE THEN FRAG ------------- " + user.userName);
//        }

        // making sure all if sentances are used but players dont get their own
        // players finding their index in the array.
        int idx = 0;
        for (User user: userViewModel.getUsers()) {
            if(user.ifSentence.equals(userViewModel.getUser().getValue().ifSentence)) {
                AppLog.d(AppLog.GAME_FLOW, "Current If sentence found at index=" + idx + ", player=" + user.userName);
                break;
            }
            idx += 1;
        }

        TextView ifQuestion = view.findViewById(R.id.myIfQuestion);
        EditText thenSentence = view.findViewById(R.id.thenAnswer);

        // players will get the next persons if in the array, if they are the last person in the array
        // they will get the first persons if in the array
        // this works because the arrays are in the same order across devices. and array order differs based upon when the users submit there answer

        int nextIndex = GameLogic.nextPlayerIndex(idx, userViewModel.getUsers().size());
        if (nextIndex >= 0) {
            myRandomIf = userViewModel.getUsers().get(nextIndex).ifSentence;
            userViewModel.localRandIf = myRandomIf;
        }

        ifQuestion.setText(myRandomIf + "?");


        //giving submit button functionality
        view.findViewById(R.id.writeThen_submit).setOnClickListener(v -> {

            if (thenSentence.getText().toString().equals("")) {
                AppLog.w(AppLog.UI, "Then submit blocked: empty response");
                UiMessenger.showError(thenSentence, "Response required");
            }
            else {
                UiMessenger.clearError(thenSentence);
                String thenSent = GameLogic.cleanThenSentence(thenSentence.getText().toString());
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
        });



    }
}
