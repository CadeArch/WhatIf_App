package com.CadeMixedUpGame.phoneapp;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.Unlockable;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;


public class ReadSentenceFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
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
        leaderBoardViewModel = new ViewModelProvider(getActivity()).get(LeaderBoardViewModel.class);

//        System.out.println(userViewModel.getUser().getValue());
        if (userViewModel.getUser().getValue().host) {
            System.out.println("ReadSentenceFrag: set host play again value to ''");
            userViewModel.hostPlayedAgain(userViewModel.getUser().getValue());
        }

        TextView ifQuestion = getActivity().findViewById(R.id.myIfQuestion_ending);

        // if user isnt account play they cannot read text aloud
        if (!userViewModel.getUser().getValue().accountPlay) {
            getActivity().findViewById(R.id.readSentence).setVisibility(View.GONE);
            getActivity().findViewById(R.id.spinnerObject).setVisibility(View.GONE);
        }

        myRandomIf = userViewModel.localRandIf;
        ifQuestion.setText(myRandomIf);

        // making sure all then sentances are used.
        // players finding their index in the array.
        int idx = 0;
        for (User user: userViewModel.getUsers()) {
            System.out.println(userViewModel.getUser().getValue().thenSentence + " " + user.thenSentence);
            if(user.thenSentence.equals(userViewModel.getUser().getValue().thenSentence)) {
                System.out.println(user.userName + ": got my own index: " + idx);
                break;
            }
            idx += 1;
        }

        // players will get the next persons if in the array, if they are the last person in the array
        // they will get the first persons if in the array. this works because the arrays are in the same order across devices.
        // and array order differs based upon when the users submit there answer
        System.out.println(idx + " " + userViewModel.getUsers().size());
        if (userViewModel.getUsers().size() > 0) {
            if (idx + 1 == userViewModel.getUsers().size()) {
                myRandomThen = userViewModel.getUsers().get(0).thenSentence;
            } else {
                myRandomThen = userViewModel.getUsers().get(idx + 1).thenSentence;
            }
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

            String ifContributor = "";
            String thenContributor = "";
            String ifContributorID = "";
            String thenContributorID = "";
            // finding the contributors to the if and then
            for (User user: userViewModel.getUsers()) {
                if (user.ifSentence.equals(myRandomIf)) {
                    ifContributor = user.userName;
                    ifContributorID = user.uid;
                }
                if (user.thenSentence.equals(myRandomThen)) {
                    thenContributor = user.userName;
                    thenContributorID = user.uid;
                }
            }

            String uniqueID = roomViewModel.makeRoomID();
            // could add if and then contributor user id to the item to be able to know who made the sentences later on
            LeaderBoardItem lbi = new LeaderBoardItem(myRandomIf, myRandomThen, ifContributor, thenContributor, ifContributorID, thenContributorID, uniqueID);
            leaderBoardViewModel.pushVoteItem(userViewModel.getUser(), lbi);

            view.findViewById(R.id.next_frag).setOnClickListener(v -> {

                getActivity().getSupportFragmentManager().beginTransaction()
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

        // array of unlocked google voices
        ArrayList<DiffGoogleVoice> voicesUnlocked = new ArrayList<DiffGoogleVoice>();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));

        // fill in voices unlocked here pull from database which are unlocked
        userViewModel.userUnlocked.addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<Unlockable>>() {
            @Override
            public void onChanged(ObservableList<Unlockable> sender) {

            }

            @Override
            public void onItemRangeChanged(ObservableList<Unlockable> sender, int positionStart, int itemCount) {

            }

            @Override
            public void onItemRangeInserted(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
                // grabbing users unlocked voices and adding to voicesUnlocked array which will fill the spinner
                if (sender.get(positionStart).isUnlocked()) {
                    voicesUnlocked.add(new DiffGoogleVoice(sender.get(positionStart).getVoiceType(), sender.get(positionStart).getVoiceCode()));
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<Unlockable> sender, int fromPosition, int toPosition, int itemCount) {

            }

            @Override
            public void onItemRangeRemoved(ObservableList<Unlockable> sender, int positionStart, int itemCount) {

            }
        });
        // so it doesnt break for non account players
        if (userViewModel.getUser().getValue().accountPlay) {
            userViewModel.getUnlocked(userViewModel.getUser());
        }


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
                if (selectedItem.getVoice().equals("fuddify")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("pig latin")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("jokester")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("disobedient")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("forgetful")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("shaggy")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("regular")) {
                    code = selectedItem.getVoiceCode();
                    System.out.println(code);
                }
                if (selectedItem.getVoice().equals("backwords")) {
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
                System.out.println("mic button hit");
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


    // Todo: write codes 4 - 7 and figure out ways to unlock them
    public String mutateString(String ifThen) {

        //fuddify
        if (code.equals("1")) {
            String mutatedIfThen = ifThen.replace("r", "w");
            return mutatedIfThen;
        }
        //pig latin
        if (code.equals("2")) {
            String[] toMutate = ifThen.split(",");

            ArrayList<String> totalLatinfied = new ArrayList<>();
            for (String part: toMutate) {
                String[] listWords = part.split(" ");
                ArrayList<String> partLatinfied = new ArrayList<>();
                for (String word: listWords) {
                    if (word.length() == 0) {
                        continue;
                    }
                    else if (word.substring(0, 1).equalsIgnoreCase("a") || word.substring(0, 1).equalsIgnoreCase("e") || word.substring(0, 1).equalsIgnoreCase("i") || word.substring(0, 1).equalsIgnoreCase("o") || word.substring(0, 1).equalsIgnoreCase("u")) {
                        partLatinfied.add(word + "way");
                    }
                    else if (word.length() == 1) {
                        partLatinfied.add(word + "ay");
                    }
                    else if (word.length() >= 2 && word.substring(1, 2).equalsIgnoreCase("a") ||
                            word.substring(1, 2).equalsIgnoreCase("e") ||
                            word.substring(1, 2).equalsIgnoreCase("i") ||
                            word.substring(1, 2).equalsIgnoreCase("o") ||
                            word.substring(1, 2).equalsIgnoreCase("u")) {
                        char firstLetter = word.charAt(0);
                        String firstLetterRemoved = word.substring(1);
                        String pigLatinFied = firstLetterRemoved + firstLetter + "ay";
                        partLatinfied.add(pigLatinFied);
                    }
                    else if (word.length() >= 2 && !word.substring(1, 2).equalsIgnoreCase("a") ||
                            !word.substring(1, 2).equalsIgnoreCase("e") ||
                            !word.substring(1, 2).equalsIgnoreCase("i") ||
                            !word.substring(1, 2).equalsIgnoreCase("o") ||
                            !word.substring(1, 2).equalsIgnoreCase("u")) {
                        String firstTwoLets = word.substring(0, 2);
                        String firstTwoLettersRemoved = word.substring(2);
                        String pigLatinFied = firstTwoLettersRemoved + firstTwoLets + "ay";
                        partLatinfied.add(pigLatinFied);
                    }
                    else {
                        partLatinfied.add(word);
                    }
                }
                totalLatinfied.addAll(partLatinfied);
                totalLatinfied.add(", ");
            }
            String toReturn = "";
            totalLatinfied.remove(totalLatinfied.size() - 1);
            for (String element:totalLatinfied) {
                toReturn += element + " ";
            }
            return toReturn;
        }
        //read it backwords
        if (code.equals("3")) {
            String toReturn = "";
            ArrayList<String> totalBackword = new ArrayList<>();
            String[] toMutate = ifThen.split(",");
            for (String part: toMutate) {
                String[] listWords = part.split(" ");
                ArrayList<String> partBackword = new ArrayList<>();
                for (String word: listWords) {
                    if (word.contains(",")) {
                        word = word.replace(",", " ");
                        String reversed = new StringBuilder(word).reverse().toString();
                        partBackword.add(reversed);
                        continue;
                    }
                    String reversed = new StringBuilder(word).reverse().toString();
                    partBackword.add(reversed);
                }
                totalBackword.addAll(partBackword);
                totalBackword.add(", ");
            }
            totalBackword.remove(totalBackword.size() - 1);
            for (String element:totalBackword) {
                toReturn += element + " ";
            }
            System.out.println(toReturn);
            return toReturn;
        }
        // jokester
        if (code.equals("4")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        // forgetful
        if (code.equals("5")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        // shaggy
        if (code.equals("6")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        // disobedient
        if (code.equals("7")) {
            String mutatedIfThen = ifThen;
            return mutatedIfThen;
        }
        return ifThen;
    }
}