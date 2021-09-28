package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.View;
import android.widget.EditText;

import com.CadeMixedUpGame.api.models.Room;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;

public class FirstFrag extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;

    public FirstFrag() {
        super(R.layout.fragment_first);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        //giving the freeplay game button functionality
        view.findViewById(R.id.freePlay).setOnClickListener(v -> {

            //moving to the start game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
        //giving the join game button functionality
        view.findViewById(R.id.accountPlay).setOnClickListener(v -> {
            // create temporary user here? similar with what would happen in the create user or login phase?

            //moving to the account game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, AccountFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

    }


}
