package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class AccountFrag extends Fragment {
    RoomViewModel roomViewModel;
    UserViewModel userViewModel;
    private boolean navigatingToStart = false;
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
        Button back = view.findViewById(R.id.back_account);

        userViewModel.signInMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
            }
        });

        userViewModel.getUser().observe(getViewLifecycleOwner(), user -> {
            if (user != null && !navigatingToStart) {
                navigatingToStart = true;
                Utils.navigateToFragment(getActivity(), StartFragment.class);
            }
        });

        // going back to first frag back button functionality
        back.setOnClickListener(v -> {
            Utils.navigateToFragment(getActivity(), FirstFrag.class);
        });

        //giving sign in button functionality
        signin.setOnClickListener(v -> {
            AppLog.d(AppLog.AUTH, "Sign in button clicked");
            if (email.getText().toString().length() == 0 || password.getText().toString().length() == 0) {
                if (email.getText().toString().length() == 0) {
                    UiMessenger.showError(email, "Email required");
                }
                if (password.getText().toString().length() == 0) {
                    UiMessenger.showError(password, "Password required");
                }
            }
            else {
                UiMessenger.clearError(email);
                UiMessenger.clearError(password);
                userViewModel.signIn(
                    email.getText().toString().replace(" ", ""),
                    password.getText().toString()
                );
            }
        });

        //giving sign up button functionality
        signup.setOnClickListener(v -> {
            AppLog.d(AppLog.AUTH, "Sign up button clicked");
            if (email.getText().toString().length() == 0 || password.getText().toString().length() == 0 || userName.getText().toString().length() == 0) {
                if (userName.getText().toString().length() == 0) {
                    UiMessenger.showError(userName, "User name required");
                }
                if (email.getText().toString().length() == 0) {
                    UiMessenger.showError(email, "Email required");
                }
                if (password.getText().toString().length() == 0) {
                    UiMessenger.showError(password, "Password required");
                }
            }
            else {
                UiMessenger.clearError(userName);
                UiMessenger.clearError(email);
                UiMessenger.clearError(password);
                userViewModel.localName = userName.getText().toString();
                userViewModel.signUp(
                    email.getText().toString().replace(" ", ""),
                    password.getText().toString(),
                    userName.getText().toString()
                );
            }
        });
    }
}
