package com.CadeMixedUpGame.phoneapp;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
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
    String code = "0";
    DiffGoogleVoice selectedItemOnSpinner;

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

        // checking to see if all users are account players
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
                        .replace(R.id.fragment_container, VoteFrag.class, null)
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

        // maybe place this as a class member variable
        // array of unlocked google voices
        ArrayList<DiffGoogleVoice> voicesUnlocked = new ArrayList<DiffGoogleVoice>();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));
        voicesUnlocked.add(new DiffGoogleVoice("fuddified google", "1"));

        // fill in voices unlocked here pull from database which are unlocked
//        String[][] unlockedAll = userViewModel.getUnlocked(userViewModel.getUser());
//        for (String[] unlocked:unlockedAll) {
//            if (unlocked[2].equals("true")) {
//                voicesUnlocked.add(new DiffGoogleVoice(unlocked[0], unlocked[1]));
//            }
//        }

        //finding and filling dropdown menu
        Spinner spinner = view.findViewById(R.id.spinnerObject);
        Resources res = getResources();
        SpinnerAdapter adapter = new SpinnerAdapter(getContext(), R.layout.read_method_item, voicesUnlocked, res);
        spinner.setAdapter(adapter);

        // change code value based upon which item is selected
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                DiffGoogleVoice selectedItem = (DiffGoogleVoice) parent.getItemAtPosition(position);
                System.out.println(selectedItem.getVoice() + selectedItem.getVoiceCode());
                selectedItemOnSpinner = selectedItem;
                //Todo depending on whats selected change code value here
                if (selectedItem.getVoice().equals("fuddified google")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("regular")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                code = "0";
                System.out.println(code);
            }

        });

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
                if (code.equals("0")) {
                    tts.speak(myRandomIf + ", " + myRandomThen, TextToSpeech.QUEUE_FLUSH, null, "readIfThen");
                }
                else {
                    String mutatedString = mutateString(myRandomIf + ", " + myRandomThen);
                    tts.speak(mutatedString, TextToSpeech.QUEUE_FLUSH, null, "readIfThen");
                }
            }
        });
    }


    public String mutateString(String ifThen) {

        //fuddify
        if (code.equals("1")) {
            String mutatedIfThen = ifThen.replace("r", "w");
            return mutatedIfThen;
        }
        //pig latin
        if (code.equals("2")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        //read it backwords
        if (code.equals("3")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        return ifThen;
    }
}