package com.CadeMixedUpGame.phoneapp;

import android.content.res.Resources;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameLogic;
import com.CadeMixedUpGame.api.models.GamePhase;
import com.CadeMixedUpGame.api.models.LeaderBoardItem;
import com.CadeMixedUpGame.api.models.Unlockable;
import com.CadeMixedUpGame.api.models.User;
import com.CadeMixedUpGame.api.viewmodels.LeaderBoardViewModel;
import com.CadeMixedUpGame.api.viewmodels.RoomViewModel;
import com.CadeMixedUpGame.api.viewmodels.UserViewModel;

import java.util.ArrayList;
import java.util.Locale;

public class ReadSentenceFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    String myRandomIf;
    String myRandomThen = "";
    TextToSpeech tts;
    String code = "0";
    DiffGoogleVoice selectedItemOnSpinner;

    public ReadSentenceFrag() {
        super(R.layout.fragment_read_sentence);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewModels();
        userViewModel.gamePhase.setValue(GamePhase.READING);
        resetHostPlayAgainIfNeeded();
        hideVoiceControlsForFreePlay();
        bindSentenceText();
        setupNextButton(view);
        setupVoiceSpinner(view);
        setupTextToSpeech(view);
    }

    private void bindViewModels() {
        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(requireActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(requireActivity()).get(LeaderBoardViewModel.class);
    }

    private void resetHostPlayAgainIfNeeded() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser != null && currentUser.host) {
            AppLog.i(AppLog.GAME_FLOW, "Resetting host play-again state on read screen");
            userViewModel.hostPlayedAgain(currentUser);
        }
    }

    private void hideVoiceControlsForFreePlay() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser != null && !currentUser.accountPlay) {
            requireActivity().findViewById(R.id.readSentence).setVisibility(View.GONE);
            requireActivity().findViewById(R.id.spinnerObject).setVisibility(View.GONE);
        }
    }

    private void bindSentenceText() {
        myRandomIf = userViewModel.localRandIf;
        myRandomThen = findAssignedThenSentence();
        TextView ifQuestion = requireActivity().findViewById(R.id.myIfQuestion_ending);
        TextView thenAnswer = requireActivity().findViewById(R.id.myThenAnswer_ending);
        ifQuestion.setText(myRandomIf + "?");
        thenAnswer.setText(myRandomThen + ".");
        AppLog.i(AppLog.GAME_FLOW, "Read sentence bound: ifLength=" + myRandomIf.length() + ", thenLength=" + myRandomThen.length());
    }

    private String findAssignedThenSentence() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.thenSentence == null || userViewModel.getUsers().size() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "Cannot assign Then sentence: missing user, Then sentence, or players");
            return "";
        }

        int currentIndex = findCurrentThenIndex(currentUser.thenSentence);
        int nextIndex = GameLogic.nextPlayerIndex(currentIndex, userViewModel.getUsers().size());
        if (nextIndex < 0) {
            AppLog.w(AppLog.GAME_FLOW, "Cannot assign Then sentence: invalid next index");
            return "";
        }
        User assignedUser = userViewModel.getUsers().get(nextIndex);
        AppLog.d(AppLog.GAME_FLOW, "Assigned Then sentence from index=" + nextIndex + ", player=" + assignedUser.userName);
        return assignedUser.thenSentence == null ? "" : assignedUser.thenSentence;
    }

    private int findCurrentThenIndex(String currentThenSentence) {
        int index = 0;
        for (User user: userViewModel.getUsers()) {
            if (currentThenSentence.equals(user.thenSentence)) {
                AppLog.d(AppLog.GAME_FLOW, "Current Then sentence found at index=" + index + ", player=" + user.userName);
                return index;
            }
            index += 1;
        }
        AppLog.w(AppLog.GAME_FLOW, "Current Then sentence was not found; defaulting to index 0");
        return 0;
    }

    private void setupNextButton(View view) {
        if (allPlayersHaveAccounts()) {
            pushVotingItem();
            view.findViewById(R.id.next_frag).setOnClickListener(v -> navigateToVoting());
        }
        else {
            view.findViewById(R.id.next_frag).setOnClickListener(v -> navigateToEnd());
        }
    }

    private boolean allPlayersHaveAccounts() {
        int numAccountPlayers = 0;
        for (User user: userViewModel.getUsers()) {
            if (user.accountPlay) {
                numAccountPlayers += 1;
            }
        }
        boolean allAccountPlayers = numAccountPlayers == userViewModel.getUsers().size();
        AppLog.d(AppLog.GAME_FLOW, "Account player check: accountPlayers=" + numAccountPlayers + ", total=" + userViewModel.getUsers().size());
        return allAccountPlayers;
    }

    private void pushVotingItem() {
        String ifContributor = "";
        String thenContributor = "";
        String ifContributorID = "";
        String thenContributorID = "";
        for (User user: userViewModel.getUsers()) {
            if (myRandomIf.equals(user.ifSentence)) {
                ifContributor = user.userName;
                ifContributorID = user.uid;
            }
            if (myRandomThen.equals(user.thenSentence)) {
                thenContributor = user.userName;
                thenContributorID = user.uid;
            }
        }

        String uniqueID = roomViewModel.makeRoomID();
        LeaderBoardItem lbi = new LeaderBoardItem(myRandomIf + "?", myRandomThen + ".", ifContributor, thenContributor, ifContributorID, thenContributorID, uniqueID);
        AppLog.i(AppLog.VOTE, "Creating voting item id=" + uniqueID + ", ifContributor=" + ifContributor + ", thenContributor=" + thenContributor);
        leaderBoardViewModel.pushVoteItem(userViewModel.getUser(), lbi);
    }

    private void navigateToVoting() {
        AppLog.i(AppLog.GAME_FLOW, "ReadSentenceFrag -> VoteFrag");
        userViewModel.gamePhase.setValue(GamePhase.VOTING);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, VoteFrag.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    private void navigateToEnd() {
        AppLog.i(AppLog.GAME_FLOW, "ReadSentenceFrag -> EndFrag");
        userViewModel.gamePhase.setValue(GamePhase.ENDED);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EndFrag.class, null)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    private void setupVoiceSpinner(View view) {
        ArrayList<DiffGoogleVoice> voicesUnlocked = buildVoiceList();
        loadUnlockedVoicesForAccountPlayers();
        Spinner spinner = view.findViewById(R.id.spinnerObject);
        Resources res = getResources();
        SpinnerAdapter adapter = new SpinnerAdapter(getContext(), R.layout.read_method_item, voicesUnlocked, res);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedItemOnSpinner = (DiffGoogleVoice) parent.getItemAtPosition(position);
                code = selectedItemOnSpinner.getVoiceCode();
                AppLog.d(AppLog.TTS, "Voice selected: " + selectedItemOnSpinner.getVoice() + ", code=" + code);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                code = "0";
                AppLog.d(AppLog.TTS, "No voice selected; using regular voice");
            }
        });
    }

    private ArrayList<DiffGoogleVoice> buildVoiceList() {
        ArrayList<DiffGoogleVoice> voicesUnlocked = new ArrayList<DiffGoogleVoice>();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));
        userViewModel.userUnlocked.addOnListChangedCallback(new ObservableList.OnListChangedCallback<ObservableList<Unlockable>>() {
            @Override
            public void onChanged(ObservableList<Unlockable> sender) {
            }

            @Override
            public void onItemRangeChanged(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
            }

            @Override
            public void onItemRangeInserted(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
                Unlockable unlockable = sender.get(positionStart);
                if (unlockable.isUnlocked()) {
                    voicesUnlocked.add(new DiffGoogleVoice(unlockable.getVoiceType(), unlockable.getVoiceCode()));
                    AppLog.d(AppLog.TTS, "Unlocked voice added: " + unlockable.getVoiceType());
                }
            }

            @Override
            public void onItemRangeMoved(ObservableList<Unlockable> sender, int fromPosition, int toPosition, int itemCount) {
            }

            @Override
            public void onItemRangeRemoved(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
            }
        });
        return voicesUnlocked;
    }

    private void loadUnlockedVoicesForAccountPlayers() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser != null && currentUser.accountPlay) {
            userViewModel.getUnlocked(userViewModel.getUser());
        }
    }

    private void setupTextToSpeech(View view) {
        tts = new TextToSpeech(getContext(), status -> {
            tts.setLanguage(Locale.getDefault());
            AppLog.i(AppLog.TTS, "TextToSpeech initialized status=" + status);
        });
        view.findViewById(R.id.readSentence).setOnClickListener(v -> speakCurrentSentence());
    }

    private void speakCurrentSentence() {
        String sentence = myRandomIf + ", " + myRandomThen;
        String spokenSentence = "0".equals(code) ? sentence : mutateString(sentence);
        AppLog.i(AppLog.TTS, "Speaking read sentence with voiceCode=" + code);
        tts.speak(spokenSentence, TextToSpeech.QUEUE_FLUSH, null, "readIfThen");
    }

    public String mutateString(String ifThen) {
        return GameLogic.mutateVoiceText(ifThen, code);
    }

    @Override
    public void onDestroyView() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroyView();
    }
}
