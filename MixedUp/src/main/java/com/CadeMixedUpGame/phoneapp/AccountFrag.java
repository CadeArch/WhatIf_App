package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
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
            if (email.getText().toString().length() == 0 || password.getText().toString().length() == 0) {
                // creating toast and switching text in viewmodel
                Toast.makeText(
                        getActivity(),
                        "Fill Email and Password Fields",
                        Toast.LENGTH_SHORT
                ).show();
            }
            else {
                userViewModel.signIn(
                        email.getText().toString(),
                        password.getText().toString()

                );
                // creating toast and switching text in viewmodel
                Toast theToast = Toast.makeText(
                        getActivity(),
                        "",
                        Toast.LENGTH_SHORT
                );
                userViewModel.signInToast.setValue(theToast);
                userViewModel.signInToast.observe(getViewLifecycleOwner(), new Observer<Toast>() {
                    @Override
                    public void onChanged(Toast toast) {
                        userViewModel.signInToast.getValue().show();
                    }
                });

                //moving to the start game fragment
                userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
                    if (userViewModel.getUser().getValue() != null) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, StartFragment.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                    }
                });
            }
        });

        //giving sign up button functionality
        signup.setOnClickListener(v -> {
            if (email.getText().toString().length() == 0 || password.getText().toString().length() == 0 || userName.getText().toString().length() == 0) {
                // creating toast and switching text in viewmodel
                Toast.makeText(
                        getActivity(),
                        "Please Fill Fields",
                        Toast.LENGTH_SHORT
                ).show();
            }
            else {
                userViewModel.localName = userName.getText().toString();
                userViewModel.signUp(
                        email.getText().toString(),
                        password.getText().toString(),
                        userName.getText().toString()
                );

                // creating toast and switching text in viewmodel
                Toast theToast = Toast.makeText(
                        getActivity(),
                        "",
                        Toast.LENGTH_SHORT
                );
                userViewModel.signInToast.setValue(theToast);
                userViewModel.signInToast.observe(getViewLifecycleOwner(), new Observer<Toast>() {
                    @Override
                    public void onChanged(Toast toast) {
                        userViewModel.signInToast.getValue().show();
                    }
                });
//            System.out.println("user ----------------" + userViewModel.getUser().getValue());

                userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
                    //moving to the start game fragment
                    if (userViewModel.getUser().getValue() != null) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, StartFragment.class, null)
                                .setReorderingAllowed(true)
                                .addToBackStack(null)
                                .commit();
                    }
                });

            }
        });

    }

}