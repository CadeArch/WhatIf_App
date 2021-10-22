package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.firebase.auth.FirebaseUser;


public class AccountFrag extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;

    public AccountFrag() {
        super(R.layout.fragment_account);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        EditText email = view.findViewById(R.id.email);
        EditText password = view.findViewById(R.id.password);
        EditText userName = view.findViewById(R.id.userName);
        Button signin = view.findViewById(R.id.signIn);
        Button signup = view.findViewById(R.id.signUp);

        //giving sign in button functionality
        signin.setOnClickListener(v -> {
            userViewModel.signIn(
                    email.getText().toString(),
                    password.getText().toString()

            );

            //moving to the start game fragment
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, StartFragment.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });

        //giving sign up button functionality
        signup.setOnClickListener(v -> {
            userViewModel.localName = userName.getText().toString();
            userViewModel.signUp(
                    email.getText().toString(),
                    password.getText().toString(),
                    userName.getText().toString()
            );

            // timing issue with account being created and moving activities. mutable live data?
            // give a toast that they have signed up
            Toast.makeText(
                    getActivity(),
                    "Account Created",
                    Toast.LENGTH_LONG
            ).show();

            userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
                //moving to the start game fragment
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, StartFragment.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();

            });

        });

    }

}