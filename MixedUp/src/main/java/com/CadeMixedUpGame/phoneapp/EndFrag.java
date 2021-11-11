package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class EndFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;

    public EndFrag() {
        super(R.layout.fragment_end);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

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
