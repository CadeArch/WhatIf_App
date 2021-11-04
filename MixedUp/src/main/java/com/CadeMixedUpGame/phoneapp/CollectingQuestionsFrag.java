package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    ObservableArrayList<User> whoSubmitted = new ObservableArrayList<>();

    public CollectingQuestionsFrag() {
        super(R.layout.fragment_collecting_questions);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        System.out.println("----------------collecting frag--------------------------");
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewCollectQ);
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));

        // seeing who has submitted
        for (User user:userViewModel.getUsers()) {
            if (user.ifFinished) {
                whoSubmitted.add(user);
            }
        }

        // populating view with those who have submitted there if
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), whoSubmitted);

//        for (User usr:userViewModel.getUsers()) {
//            if (usr.ifFinished) {
//                System.out.println(usr.userName + usr.ifFinished);
//                recyclerView.setAdapter(adapter);
//            }
//        }

        // when the users array changes reset the adapter to include all people
        userViewModel.getUsers().addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int positionStart, int itemCount) {
            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int positionStart, int itemCount) {
                if (userViewModel.getUsers().get(positionStart).ifFinished) {
                    whoSubmitted.add(userViewModel.getUsers().get(positionStart));
                    recyclerView.setAdapter(adapter);
                    System.out.println("1 TRUE");
                }
                System.out.println("SAW CHANGE --------------------");
                int count = 0;
                for (User user:userViewModel.getUsers()) {
                    if (user.ifFinished) {
                        count += 1;
                    }
                }
                System.out.println(count + " ------------------ " + userViewModel.getUsers().size());
                if (count == userViewModel.getUsers().size()) {
                    allIfsFinished = true;
                }
                if (allIfsFinished) {
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, WriteThenFrag.class, null)
                            .setReorderingAllowed(true)
                            .addToBackStack(null)
                            .commit();
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int positionStart, int itemCount) {

            }
        });


    }
}

