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

        TextView ifQuestion = getActivity().findViewById(R.id.myIfQuestion_ending);
        // TODO need to set my randomif to one from another player
        ifQuestion.setText(myRandomIf);

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

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);


    }
}