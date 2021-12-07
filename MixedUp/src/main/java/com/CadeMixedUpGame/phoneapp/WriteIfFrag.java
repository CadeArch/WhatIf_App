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
import android.widget.Toast;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;
import java.util.TreeMap;


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

        EditText ifSentence = getActivity().findViewById(R.id.ifQuestion);

        //giving submit button functionality
        view.findViewById(R.id.writeIf_submit).setOnClickListener(v -> {

            for (User user: userViewModel.getUsers()
                 ) {
                System.out.println("ORDER IN WRITE IF FRAG ------------- " + user.userName);
            }


            if (ifSentence.getText().toString().equals("")) {
                Toast.makeText(
                        getActivity(),
                        "Question needed",
                        Toast.LENGTH_SHORT
                ).show();
            }
            else {
                String ifsent = ifSentence.getText().toString();
                ifsent = ifsent.replaceAll("\\p{Punct}","");
                ifsent = ifsent.substring(0, 1).toUpperCase() + ifsent.substring(1);
                ifsent = ifsent.replaceAll("\\s+$", "");
                ifsent = ifsent.replaceAll("^\\s+", "");
                userViewModel.getUser().getValue().ifSentence = ifsent;
                userViewModel.getUser().getValue().ifFinished = true;

                userViewModel.pushIf(userViewModel.getUser());

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, CollectingQuestionsFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            }
        });

    }
}