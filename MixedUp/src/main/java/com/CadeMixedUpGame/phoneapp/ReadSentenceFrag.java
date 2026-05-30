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
    View doneButton;
    View nextButton;
    TextView ifQuestionText;
    TextView thenAnswerText;
    int currentUserReadIndex = 0;
    RoundAssignment currentRoundAssignment;
    boolean sentenceReady = false;
    boolean sentenceRevealed = false;
    boolean currentReaderTurn = false;
    boolean readTurnPassed = false;
    boolean everyoneHasRead = false;
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
        setupNextButton(view);
        bindSentenceText();
        setupVoiceSpinner(view);
        setupReadingControls(view);
    }

    private void bindViewModels() {
        userViewModel = new ViewModelProvider(requireActivity()).get(UserViewModel.class);
        roomViewModel = new ViewModelProvider(requireActivity()).get(RoomViewModel.class);
        leaderBoardViewModel = new ViewModelProvider(requireActivity()).get(LeaderBoardViewModel.class);
    }

    private void setupRoomMessages(View view) {
        roomViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showTopSnackbar(view, message);
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
        resetLocalReadingState();
        currentUserReadIndex = findCurrentUserIndex(currentUser);
        roomViewModel.listenToActiveReader(currentUser.gameRoom);
        roomViewModel.listenToReadingComplete(currentUser.gameRoom);
        if (currentUser.host) {
            roomViewModel.setActiveReaderIndex(currentUser.gameRoom, 0);
            roomViewModel.setReadingComplete(currentUser.gameRoom, false);
        }
        roomViewModel.activeReaderIndex.observe(getViewLifecycleOwner(), this::updateActiveReaderControls);
        roomViewModel.readingComplete.observe(getViewLifecycleOwner(), this::handleReadingComplete);
    }

    private void resetLocalReadingState() {
        sentenceReady = false;
        sentenceRevealed = false;
        currentReaderTurn = false;
        readTurnPassed = false;
        everyoneHasRead = false;
        votingItemPushed = false;
        currentRoundAssignment = null;
        myRandomThen = "";
        AppLog.d(AppLog.GAME_FLOW, "Local reading state reset for new read screen");
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
        ifQuestionText = requireActivity().findViewById(R.id.myIfQuestion_ending);
        thenAnswerText = requireActivity().findViewById(R.id.myThenAnswer_ending);
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            AppLog.w(AppLog.GAME_FLOW, "Read sentence assignment skipped: missing current user");
            return;
        }

        ifQuestionText.setText("Loading...");
        thenAnswerText.setText("");
        roomViewModel.listenToAssignment(currentUser.gameRoom, GameLogic.playerKey(currentUser));
        roomViewModel.currentAssignment.observe(getViewLifecycleOwner(), assignment -> {
            if (assignment != null) {
                applyAssignment(assignment);
            }
        });
    }

    private void applyAssignment(RoundAssignment assignment) {
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
        updateSentenceVisibility();
        pushVotingItemIfNeeded();
        AppLog.i(AppLog.GAME_FLOW, "Read assignment applied: ifOwner=" + assignment.ifOwnerKey + ", thenOwner=" + assignment.thenOwnerKey);
    }

    private void setupNextButton(View view) {
        nextButton = view.findViewById(R.id.next_frag);
        nextButton.setEnabled(false);
        setNextButtonText("show");
        nextButton.setOnClickListener(v -> {
            if (!sentenceReady) {
                UiMessenger.showTopSnackbar(view, "Still loading your sentence. Try again in a moment.");
                AppLog.w(AppLog.GAME_FLOW, "Next blocked: read assignment not ready");
                return;
            }
        if (!sentenceRevealed) {
            if (!currentReaderTurn) {
                UiMessenger.showTopSnackbar(view, "It is not your reading turn yet.");
                    AppLog.w(AppLog.GAME_FLOW, "Show sentence blocked: not active reader");
                    return;
                }
                revealSentence();
                return;
            }
            passReadingTurn();
        });
    }

    private void revealSentence() {
        sentenceRevealed = true;
        updateSentenceVisibility();
        AppLog.i(AppLog.GAME_FLOW, "Read sentence revealed for active reader index=" + currentUserReadIndex);
    }

    private void updateSentenceVisibility() {
        if (ifQuestionText == null || thenAnswerText == null || nextButton == null) {
            return;
        }
        if (!sentenceReady) {
            ifQuestionText.setText("Loading...");
            thenAnswerText.setText("");
            nextButton.setEnabled(false);
            nextButton.setAlpha(0.35f);
            setNextButtonText("show");
            return;
        }
        if (!sentenceRevealed) {
            if (currentReaderTurn) {
                ifQuestionText.setText("What if the show button is enabled?");
                thenAnswerText.setText("Then tap show when the group is ready and enjoy a laugh!");
                nextButton.setEnabled(true);
                nextButton.setAlpha(1.0f);
            }
            else {
                ifQuestionText.setText("What if it's not your turn?");
                thenAnswerText.setText("Then listen to the person who is revealing a twisted sentence!");
                nextButton.setEnabled(false);
                nextButton.setAlpha(0.35f);
            }
            setNextButtonText("show");
            return;
        }
        ifQuestionText.setText(GameLogic.formatIfSentence(myRandomIf));
        thenAnswerText.setText(GameLogic.formatThenSentence(myRandomThen));
        boolean canPass = currentReaderTurn && !readTurnPassed;
        nextButton.setEnabled(canPass);
        nextButton.setAlpha(canPass ? 1.0f : 0.35f);
        setNextButtonText("pass");
        updateDoneButtonVisibility();
    }

    private void setNextButtonText(String text) {
        if (nextButton instanceof TextView) {
            ((TextView) nextButton).setText(text);
        }
    }

    private boolean allPlayersHaveAccounts() {
        int numAccountPlayers = 0;
        for (User user: userViewModel.getUsers()) {
            if (user != null && Boolean.TRUE.equals(user.accountPlay)) {
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
                GameLogic.formatIfSentence(myRandomIf),
                GameLogic.formatThenSentence(myRandomThen),
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
        if (!currentUserHasAccount()) {
            view.findViewById(R.id.spinnerObject).setVisibility(View.GONE);
            return;
        }
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

    private void setupReadingControls(View view) {
        readButton = view.findViewById(R.id.readSentence);
        doneButton = view.findViewById(R.id.pass_reading_turn);
        setDoneButtonText("done");
        doneButton.setVisibility(View.GONE);
        if (currentUserHasAccount()) {
            tts = new TextToSpeech(getContext(), status -> {
                tts.setLanguage(Locale.getDefault());
                AppLog.i(AppLog.TTS, "TextToSpeech initialized status=" + status);
            });
            readButton.setOnClickListener(v -> speakCurrentSentence());
        }
        else {
            readButton.setVisibility(View.GONE);
            view.findViewById(R.id.spinnerObject).setVisibility(View.GONE);
            AppLog.i(AppLog.TTS, "TextToSpeech skipped for free-play reader");
        }
        doneButton.setOnClickListener(v -> finishReadingPhase());
        updateActiveReaderControls(roomViewModel.activeReaderIndex.getValue());
    }

    private boolean currentUserHasAccount() {
        User currentUser = userViewModel.getUser().getValue();
        return currentUser != null && Boolean.TRUE.equals(currentUser.accountPlay);
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
        if (readButton == null || doneButton == null) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            return;
        }
        int activeIndex = activeReaderIndex == null ? 0 : activeReaderIndex;
        everyoneHasRead = activeIndex >= userViewModel.getUsers().size() && userViewModel.getUsers().size() > 0;
        boolean isCurrentReader = activeIndex == currentUserReadIndex;
        currentReaderTurn = isCurrentReader;
        boolean canReadAloud = isCurrentReader && sentenceRevealed;
        if (readButton.getVisibility() != View.GONE) {
            readButton.setEnabled(canReadAloud);
            readButton.setAlpha(canReadAloud ? 1.0f : 0.35f);
        }
        updateSentenceVisibility();
        updateDoneButtonVisibility();
        if (isCurrentReader && !sentenceRevealed) {
            UiMessenger.showTopSnackbar(requireView(), "Your turn to read");
        }
        AppLog.d(AppLog.TTS, "Read controls updated: activeIndex=" + activeIndex + ", myIndex=" + currentUserReadIndex);
    }

    private void passReadingTurn() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0 || userViewModel.getUsers().size() == 0) {
            UiMessenger.showTopSnackbar(requireView(), "Cannot pass reading turn yet");
            AppLog.w(AppLog.TTS, "Pass reading turn blocked: missing user, room, or players");
            return;
        }
        if (!currentReaderTurn || !sentenceRevealed || readTurnPassed) {
            UiMessenger.showTopSnackbar(requireView(), "It is not time to pass yet");
            AppLog.w(AppLog.TTS, "Pass reading turn blocked: invalid local state");
            return;
        }
        readTurnPassed = true;
        int nextReaderIndex = currentUserReadIndex + 1;
        roomViewModel.setActiveReaderIndex(currentUser.gameRoom, nextReaderIndex);
        AppLog.i(AppLog.TTS, "Passed reading turn from index=" + currentUserReadIndex + " to index=" + nextReaderIndex);
    }

    private void updateDoneButtonVisibility() {
        if (doneButton == null) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        boolean showDone = currentUser != null && currentUser.host && everyoneHasRead;
        doneButton.setVisibility(showDone ? View.VISIBLE : View.GONE);
        doneButton.setEnabled(showDone);
        doneButton.setAlpha(showDone ? 1.0f : 0.35f);
        if (everyoneHasRead && !showDone && ifQuestionText != null && thenAnswerText != null) {
            ifQuestionText.setText("What if everyone has read?");
            thenAnswerText.setText("Then wait for the host to finish the round.");
            if (nextButton != null) {
                nextButton.setEnabled(false);
                nextButton.setAlpha(0.35f);
            }
        }
        else if (everyoneHasRead && showDone && ifQuestionText != null && thenAnswerText != null) {
            ifQuestionText.setText("What if everyone has read?");
            thenAnswerText.setText("Then tap done to finish the round.");
            if (nextButton != null) {
                nextButton.setEnabled(false);
                nextButton.setAlpha(0.35f);
            }
        }
    }

    private void setDoneButtonText(String text) {
        if (doneButton instanceof TextView) {
            ((TextView) doneButton).setText(text);
        }
    }

    private void finishReadingPhase() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || !currentUser.host || !everyoneHasRead) {
            UiMessenger.showTopSnackbar(requireView(), "Everyone needs a reading turn first");
            AppLog.w(AppLog.GAME_FLOW, "Finish reading blocked: host=" + (currentUser != null && currentUser.host) + ", everyoneHasRead=" + everyoneHasRead);
            return;
        }
        roomViewModel.setReadingComplete(currentUser.gameRoom, true);
        AppLog.i(AppLog.GAME_FLOW, "Host finished reading phase room=" + currentUser.gameRoom);
    }

    private void handleReadingComplete(Boolean complete) {
        if (complete == null || !complete) {
            return;
        }
        if (allPlayersHaveAccounts()) {
            navigateToVoting();
        }
        else {
            navigateToEnd();
        }
    }

    private void speakCurrentSentence() {
        if (!sentenceReady || !sentenceRevealed) {
            UiMessenger.showTopSnackbar(requireView(), "Show your sentence first");
            AppLog.w(AppLog.TTS, "Speak blocked: sentence not visible");
            return;
        }
        String sentence = GameLogic.formatIfSentence(myRandomIf) + ", " + GameLogic.formatThenSentence(myRandomThen);
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
        roomViewModel.removeReadingCompleteListener();
        roomViewModel.removeActiveReaderListener();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        super.onDestroyView();
    }
}
