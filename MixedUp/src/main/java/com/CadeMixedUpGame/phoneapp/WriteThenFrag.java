package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;


public class WriteThenFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    String myRandomIf;

    public WriteThenFrag() {
        super(R.layout.fragment_write_then);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        for (User user: userViewModel.getUsers()
        ) {
            System.out.println("ORDER IN WRITE THEN FRAG ------------- " + user.userName);
        }

        // this works for two people but not for more...
        for (User user: userViewModel.getUsers()) {
            if(user.ifSentence.equals(userViewModel.getUser().getValue().ifSentence)) {
                System.out.println("passed: dont give user there own");
            }
            else {
                myRandomIf = user.ifSentence;
                break;
            }
        }

        TextView ifQuestion = view.findViewById(R.id.myIfQuestion);
        EditText thenSentence = view.findViewById(R.id.thenAnswer);
        // TODO need to set my randomif to one from another player
        ifQuestion.setText(myRandomIf);

        //giving submit button functionality
        view.findViewById(R.id.writeThen_submit).setOnClickListener(v -> {

            // need to push the then sentance to firebase and change thenComplete to true
//            System.out.println("ButtonPressed to move to collecting answers frag");
            userViewModel.getUser().getValue().thenSentence = thenSentence.getText().toString();
            userViewModel.getUser().getValue().thenFinished = true;

            userViewModel.pushThen(userViewModel.getUser());

            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, CollectingAnswersFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });



    }
}