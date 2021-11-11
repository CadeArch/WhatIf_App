package com.CadeMixedUpGame.phoneapp;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;
import java.util.Locale;


public class ReadSentenceFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    String myRandomIf;
    String myRandomThen;
    TextToSpeech tts;

    public ReadSentenceFrag() {
        super(R.layout.fragment_read_sentence);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        userViewModel = new ViewModelProvider(getActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(getActivity()).get(RoomViewModel.class);

        TextView ifQuestion = getActivity().findViewById(R.id.myIfQuestion_ending);

        myRandomIf = userViewModel.localRandIf;
        ifQuestion.setText(myRandomIf);

        // making sure all then sentances are used but players dont get their own
        // players finding their index in the array.
        int idx = 0;
        for (User user: userViewModel.getUsers()) {
            if(user.thenSentence.equals(userViewModel.getUser().getValue().thenSentence)) {
                System.out.println(user.userName + ": got my own index: " + idx);
                break;
            }
            idx += 1;
        }

        // players will get the next persons if in the array, if they are the last person in the array
        // they will get the first persons if in the array. this works because the arrays are in the same order across devices.
        // and array order differs based upon when the users submit there answer
        if (idx + 1 == userViewModel.getUsers().size()) {
            myRandomThen = userViewModel.getUsers().get(0).thenSentence;
        }
        else {
            myRandomThen = userViewModel.getUsers().get(idx + 1).thenSentence;
        }

        TextView thenAnswer = getActivity().findViewById(R.id.myThenAnswer_ending);
        thenAnswer.setText(myRandomThen);

        // checking to see if all uers are account players
        int numAccountPlayers = 0;
        for (User user: userViewModel.getUsers()) {
            if (user.accountPlay) {
                numAccountPlayers += 1;
            }
        }

        //giving next button functionality
        if (numAccountPlayers == userViewModel.getUsers().size()) {
            view.findViewById(R.id.next_frag).setOnClickListener(v -> {
                getActivity().getSupportFragmentManager().beginTransaction()
                        // TODO CHANGE TO VOTE FRAG
                        .replace(R.id.fragment_container, EndFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            });
        }
        else {
            view.findViewById(R.id.next_frag).setOnClickListener(v -> {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, EndFrag.class, null)
                        .setReorderingAllowed(true)
                        .addToBackStack(null)
                        .commit();
            });
        }

        // Todo make spinner and mic button invisible if non account play

        //creating my text to speech object
        tts = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                tts.setLanguage(Locale.getDefault());
            }
        });

        // giving mic button functionality to speak sentence
        view.findViewById(R.id.readSentence).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tts.speak(myRandomIf + ", " + myRandomThen, TextToSpeech.QUEUE_FLUSH, null, "readIfThen");
            }
        });

        // maybe place this as a class member variable
        // array of unlocked google voices
        ArrayList<DiffGoogleVoice> voicesUnlocked = new ArrayList<DiffGoogleVoice>();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));
        // fill in voices unlocked here pull from database which are unlocked


        Spinner spinner = view.findViewById(R.id.spinnerObject);
        Resources res = getResources();
        SpinnerAdapter adapter = new SpinnerAdapter(getContext(), R.layout.read_method_item, voicesUnlocked, res);
        spinner.setAdapter(adapter);



    }
}