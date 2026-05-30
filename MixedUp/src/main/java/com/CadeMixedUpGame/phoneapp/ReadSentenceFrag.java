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
import com.CadeMixedUpGame.api.models.RoundAssignment;
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
    View readButton;
    View passReadingTurnButton;
    View nextButton;
    int currentUserReadIndex = 0;
    RoundAssignment currentRoundAssignment;
    boolean sentenceReady = false;
    boolean votingItemPushed = false;

    public ReadSentenceFrag() {
        super(R.layout.fragment_read_sentence);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewModels();
        userViewModel.gamePhase.setValue(GamePhase.READING);
        setupRoomMessages(view);
        setupActiveReaderState();
        resetHostPlayAgainIfNeeded();
        hideVoiceControlsForFreePlay();
        setupNextButton(view);
        bindSentenceText();
        setupVoiceSpinner(view);
        setupTextToSpeech(view);
    }

    private void bindViewModels() {
        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(requireActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(requireActivity()).get(LeaderBoardViewModel.class);
    }

    private void setupRoomMessages(View view) {
        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showSnackbar(view, message);
                roomViewModel.databaseMessage.setValue("");
            }
        });
    }

    private void setupActiveReaderState() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0) {
            AppLog.w(AppLog.ROOM, "Active reader setup skipped: missing user or room");
            return;
        }
        currentUserReadIndex = findCurrentUserIndex(currentUser);
        roomViewModel.listenToActiveReader(currentUser.gameRoom);
        if (currentUser.host) {
            roomViewModel.setActiveReaderIndex(currentUser.gameRoom, 0);
        }
        roomViewModel.activeReaderIndex.observe(getViewLifecycleOwner(), this::updateActiveReaderControls);
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
            requireActivity().findViewById(R.id.pass_reading_turn).setVisibility(View.GONE);
        }
    }

    private void bindSentenceText() {
        TextView ifQuestion = requireActivity().findViewById(R.id.myIfQuestion_ending);
        TextView thenAnswer = requireActivity().findViewById(R.id.myThenAnswer_ending);
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            AppLog.w(AppLog.GAME_FLOW, "Read sentence assignment skipped: missing current user");
            return;
        }

        ifQuestion.setText("Loading...");
        thenAnswer.setText("");
        roomViewModel.listenToAssignment(currentUser.gameRoom, GameLogic.playerKey(currentUser));
        roomViewModel.currentAssignment.observe(getViewLifecycleOwner(), assignment -> {
            if (assignment != null) {
                applyAssignment(assignment, ifQuestion, thenAnswer);
            }
        });
    }

    private void applyAssignment(RoundAssignment assignment, TextView ifQuestion, TextView thenAnswer) {
        User ifOwner = findUserByKey(assignment.ifOwnerKey);
        User thenOwner = findUserByKey(assignment.thenOwnerKey);
        if (ifOwner == null || thenOwner == null) {
            AppLog.w(AppLog.GAME_FLOW, "Read assignment loaded before players were available");
            return;
        }

        myRandomIf = ifOwner.ifSentence == null ? "" : ifOwner.ifSentence;
        myRandomThen = thenOwner.thenSentence == null ? "" : thenOwner.thenSentence;
        if (myRandomIf.length() == 0 || myRandomThen.length() == 0) {
            AppLog.w(AppLog.GAME_FLOW, "Read assignment missing sentence text");
            return;
        }

        currentRoundAssignment = assignment;
        sentenceReady = true;
        ifQuestion.setText(myRandomIf + "?");
        thenAnswer.setText(myRandomThen + ".");
        if (nextButton != null) {
            nextButton.setEnabled(true);
        }
        pushVotingItemIfNeeded();
        AppLog.i(AppLog.GAME_FLOW, "Read assignment applied: ifOwner=" + assignment.ifOwnerKey + ", thenOwner=" + assignment.thenOwnerKey);
    }

    private void setupNextButton(View view) {
        nextButton = view.findViewById(R.id.next_frag);
        nextButton.setEnabled(false);
        nextButton.setOnClickListener(v -> {
            if (!sentenceReady) {
                UiMessenger.showSnackbar(view, "Still loading your sentence. Try again in a moment.");
                AppLog.w(AppLog.GAME_FLOW, "Next blocked: read assignment not ready");
                return;
            }
            if (allPlayersHaveAccounts()) {
                navigateToVoting();
            }
            else {
                navigateToEnd();
            }
        });
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

    private void pushVotingItemIfNeeded() {
        if (!allPlayersHaveAccounts() || votingItemPushed || currentRoundAssignment == null) {
            return;
        }

        String uniqueID = roomViewModel.makeRoomID();
        LeaderBoardItem lbi = new LeaderBoardItem(
                myRandomIf + "?",
                myRandomThen + ".",
                currentRoundAssignment.ifContributor,
                currentRoundAssignment.thenContributor,
                currentRoundAssignment.ifContributorID,
                currentRoundAssignment.thenContributorID,
                uniqueID);
        votingItemPushed = true;
        AppLog.i(AppLog.VOTE, "Creating voting item id=" + uniqueID
                + ", ifContributor=" + currentRoundAssignment.ifContributor
                + ", thenContributor=" + currentRoundAssignment.thenContributor);
        leaderBoardViewModel.pushVoteItem(userViewModel.getUser(), lbi);
    }

    private User findUserByKey(String playerKey) {
        for (User user : userViewModel.getUsers()) {
            if (GameLogic.playerKey(user).equals(playerKey)) {
                return user;
            }
        }
        return null;
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
        readButton = view.findViewById(R.id.readSentence);
        passReadingTurnButton = view.findViewById(R.id.pass_reading_turn);
        readButton.setOnClickListener(v -> speakCurrentSentence());
        passReadingTurnButton.setOnClickListener(v -> passReadingTurn());
        updateActiveReaderControls(roomViewModel.activeReaderIndex.getValue());
    }

    private int findCurrentUserIndex(User currentUser) {
        for (int index = 0; index < userViewModel.getUsers().size(); index++) {
            User user = userViewModel.getUsers().get(index);
            if (user.userID == currentUser.userID) {
                return index;
            }
        }
        AppLog.w(AppLog.TTS, "Current user not found in read order; using immediate read-aloud");
        return 0;
    }

    private void updateActiveReaderControls(Integer activeReaderIndex) {
        if (readButton == null || passReadingTurnButton == null) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || !currentUser.accountPlay || readButton.getVisibility() == View.GONE) {
            return;
        }
        int activeIndex = activeReaderIndex == null ? 0 : activeReaderIndex;
        boolean isCurrentReader = activeIndex == currentUserReadIndex;
        readButton.setEnabled(isCurrentReader);
        readButton.setAlpha(isCurrentReader ? 1.0f : 0.35f);
        passReadingTurnButton.setEnabled(isCurrentReader);
        passReadingTurnButton.setAlpha(isCurrentReader ? 1.0f : 0.35f);
        if (isCurrentReader) {
            UiMessenger.showSnackbar(requireView(), "Your turn to read");
        }
        AppLog.d(AppLog.TTS, "Read controls updated: activeIndex=" + activeIndex + ", myIndex=" + currentUserReadIndex);
    }

    private void passReadingTurn() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0 || userViewModel.getUsers().size() == 0) {
            UiMessenger.showSnackbar(requireView(), "Cannot pass reading turn yet");
            AppLog.w(AppLog.TTS, "Pass reading turn blocked: missing user, room, or players");
            return;
        }
        int nextReaderIndex = GameLogic.nextPlayerIndex(currentUserReadIndex, userViewModel.getUsers().size());
        roomViewModel.setActiveReaderIndex(currentUser.gameRoom, nextReaderIndex);
        AppLog.i(AppLog.TTS, "Passed reading turn from index=" + currentUserReadIndex + " to index=" + nextReaderIndex);
    }

    private void speakCurrentSentence() {
        if (!sentenceReady) {
            UiMessenger.showSnackbar(requireView(), "Still loading your sentence");
            AppLog.w(AppLog.TTS, "Speak blocked: sentence not ready");
            return;
        }
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
        roomViewModel.removeAssignmentListener();
        roomViewModel.removeActiveReaderListener();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroyView();
    }
}
