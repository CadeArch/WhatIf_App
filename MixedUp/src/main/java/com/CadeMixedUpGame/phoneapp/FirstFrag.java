package com.CadeMixedUpGame.phoneapp;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.CadeMixedUpGame.api.AppLog;
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
        super.onViewCreated(view, savedInstanceState); //what is this?

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        // This did get called, will assure a user isnt signed into a device upon start of app, will need to sign back in
        if (userViewModel.getUser().getValue() != null) {
            AppLog.d(AppLog.AUTH, "Landing screen found existing user");
            if (userViewModel.getUser().getValue().accountPlay) {
                userViewModel.signOut();
                AppLog.i(AppLog.AUTH, "Signed out existing account player on landing screen");
            }
        }

        //giving the free-play game button functionality
        view.findViewById(R.id.freePlay).setOnClickListener(v -> {
            //could build user here for free-play but then would have to update name later
            userViewModel.buildUserFree("reset");

            //moving to the start game fragment
            Utils.navigateToFragment(getActivity(), StartFragment.class);
        });
        //giving the account button functionality
        view.findViewById(R.id.accountPlay).setOnClickListener(v -> {
            //moving to the account game fragment
            Utils.navigateToFragment(getActivity(), AccountFrag.class);
        });
    }
}
