package com.CadeMixedUpGame.phoneapp;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableArrayList;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.View;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class CollectingAnswersFrag extends Fragment {
    UserViewModel userViewModel;
    Boolean allThensFinished = false;
    ObservableArrayList<User> whoSubmittedThen = new ObservableArrayList<>();

    public CollectingAnswersFrag() {
        super(R.layout.fragment_collecting_answers);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        System.out.println("----------------collecting Then frag--------------------------");
        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);

        userViewModel.onCollectingAnswers = true;

        // set up the RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewCollectA);
        recyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));

        // seeing who has submitted
        for (User user:userViewModel.getUsers()) {
            if (user.thenFinished) {
                whoSubmittedThen.add(user);
            }
        }

        // populating view with those who have submitted there if
        MyRecyclerViewAdapter adapter = new MyRecyclerViewAdapter(getActivity(), whoSubmittedThen);

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
                // if they have finished their if sentance add it to the who submitted array and reset adapter to inflate text
                if (userViewModel.getUsers().get(positionStart).thenFinished) {
                    whoSubmittedThen.add(userViewModel.getUsers().get(positionStart));
                    recyclerView.setAdapter(adapter);
                }
//                System.out.println("SAW CHANGE --------------------");
                int count = 0;
                for (User user:userViewModel.getUsers()) {
                    System.out.println("CollectingAnswersfrag: " + user.thenSentence + " " + user.thenFinished);
                    if (user.thenFinished) {
                        count += 1;
                        System.out.println("Count incremented: CAF");
                    }
                }
                System.out.println(count + " ------------------ " + userViewModel.getUsers().size());
                if (count == userViewModel.getUsers().size()) {
                    allThensFinished = true;
                }
                if (allThensFinished && userViewModel.onCollectingAnswers) {
                    System.out.println("going to read sentence frag");
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.fragment_container, ReadSentenceFrag.class, null)
                            .setReorderingAllowed(true)
                            .addToBackStack(null)
                            .commit();
                    userViewModel.onCollectingAnswers = false;
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

