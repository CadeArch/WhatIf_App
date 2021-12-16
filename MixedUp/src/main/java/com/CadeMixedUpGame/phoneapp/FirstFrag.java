package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;



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

        // todo check to see if this fixes issue
        // This did get called, will assure a user isnt signed into a device upon start of app, will need to sign back in
        if (userViewModel.getUser().getValue() != null) {
            System.out.println("USER NOT NULL");
            if (userViewModel.getUser().getValue().accountPlay) {
                userViewModel.signOut();
                System.out.println("FIRST FRAG: user already signed in: SIGNING OUT");
            }
        }

        //giving the freeplay game button functionality
        view.findViewById(R.id.freePlay).setOnClickListener(v -> {

            //could build user here for freeplay but then would have to update name later
            userViewModel.buildUserFree("reset");

            //moving to the start game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
        //giving the account button functionality
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
