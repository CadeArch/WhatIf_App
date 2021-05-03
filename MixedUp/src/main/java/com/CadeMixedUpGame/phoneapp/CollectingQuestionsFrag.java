package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;


public class CollectingQuestionsFrag extends Fragment {
    UserViewModel userViewModel;
    Boolean allIfsFinished = false;

    public CollectingQuestionsFrag() {
        super(R.layout.fragment_collecting_questions);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        if (allIfsFinished) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, WriteThenFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
            }
    }
}

