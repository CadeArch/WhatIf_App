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


public class EndingFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    String myRandomIf;
    String myRandomThen;

    public EndingFrag() {
        super(R.layout.fragment_ending);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        TextView ifQuestion = getActivity().findViewById(R.id.myIfQuestion_ending);

        myRandomIf = userViewModel.localRandIf;
        ifQuestion.setText(myRandomIf);

//        userViewModel.getUsers().sort(null);

        // making sure all then sentances are used but players dont get their own
        // players finding their index in the array.
        int idx = 0;
        for (User user: userViewModel.getUsers()) {
            if(user.thenSentence.equals(userViewModel.getUser().getValue().thenSentence)) {
                System.out.println(user.userName + ": got my own index: " + idx);
                break;
            }
            idx += 1;
        }

        // players will get the next persons if in the array, if they are the last person in the array
        // they will get the first persons if in the array
        // this works because the arrays are in the same order across devices. and array order differs based upon when the users submit there answer
        if (idx + 1 == userViewModel.getUsers().size()) {
            myRandomThen = userViewModel.getUsers().get(0).thenSentence;
        }
        else {
            myRandomThen = userViewModel.getUsers().get(idx + 1).thenSentence;
        }

        TextView thenAnswer = getActivity().findViewById(R.id.myThenAnswer_ending);
        thenAnswer.setText(myRandomThen);

        //giving home button functionality
        view.findViewById(R.id.home_ending).setOnClickListener(v -> {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving again button functionality
        view.findViewById(R.id.again_ending).setOnClickListener(v -> {

            // here I will need to reset players values and database values to how they would be
            // at the start of a match
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WriteIfFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });



    }
}