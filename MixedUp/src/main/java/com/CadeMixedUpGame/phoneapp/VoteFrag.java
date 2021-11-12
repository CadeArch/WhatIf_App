package com.CadeMixedUpGame.phoneapp;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

public class VoteFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;

    public VoteFrag() {
        super(R.layout.fragment_end);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        //giving home button functionality
        view.findViewById(R.id.vote_submit).setOnClickListener(v -> {
            // TODO push vote to database
            // creating toast and switching text in viewmodel
            Toast.makeText(
                    getActivity(),
                    "vote sent",
                    Toast.LENGTH_SHORT
            ).show();
        });

        //giving next button functionality
        view.findViewById(R.id.vote_next).setOnClickListener(v -> {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, EndFrag.class, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null)
                    .commit();
        });
    }
}
