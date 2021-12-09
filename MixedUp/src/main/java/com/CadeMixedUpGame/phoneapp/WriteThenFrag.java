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
import java.util.Collections;
import java.util.Comparator;


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

//        for (User user: userViewModel.getUsers()) {
//            System.out.println("ORDER IN WRITE THEN FRAG ------------- " + user.userName);
//        }

        // making sure all if sentances are used but players dont get their own
        // players finding their index in the array.
        int idx = 0;
        for (User user: userViewModel.getUsers()) {
            if(user.ifSentence.equals(userViewModel.getUser().getValue().ifSentence)) {
                System.out.println(user.userName + ": got my own index: " + idx);
                break;
            }
            idx += 1;
        }

        TextView ifQuestion = view.findViewById(R.id.myIfQuestion);
        EditText thenSentence = view.findViewById(R.id.thenAnswer);

        // players will get the next persons if in the array, if they are the last person in the array
        // they will get the first persons if in the array
        // this works because the arrays are in the same order across devices. and array order differs based upon when the users submit there answer

        if (idx + 1 == userViewModel.getUsers().size()) {
            myRandomIf = userViewModel.getUsers().get(0).ifSentence;
            userViewModel.localRandIf = myRandomIf;
            System.out.println("WRITE THEN FRAG: hit if");
        }
        else {
            myRandomIf = userViewModel.getUsers().get(idx + 1).ifSentence;
            userViewModel.localRandIf = myRandomIf;
            System.out.println("hit else");
        }

        ifQuestion.setText(myRandomIf + "?");


        //giving submit button functionality
        view.findViewById(R.id.writeThen_submit).setOnClickListener(v -> {

            if (thenSentence.getText().toString().equals("")) {
                Toast.makeText(
                        getActivity(),
                        "Response needed",
                        Toast.LENGTH_SHORT
                ).show();
            }
            else {
                String thenSent = thenSentence.getText().toString();
                thenSent = thenSent.replaceAll("\\p{Punct}","");
                thenSent = thenSent.replaceAll("\\s+$", "");
                thenSent = thenSent.replaceAll("^\\s+", "");
                userViewModel.getUser().getValue().thenSentence = thenSent;
                userViewModel.getUser().getValue().thenFinished = true;

                userViewModel.pushThen(userViewModel.getUser());

                System.out.println("ButtonPressed to move to collecting answers frag");

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, CollectingAnswersFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            }
        });



    }
}