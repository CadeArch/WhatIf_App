package com.CadeMixedUpGame.phoneapp;

import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.ObservableList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.CadeMixedUpGame.api.AppLog;
import com.CadeMixedUpGame.api.GameFlowPolicy;
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

public class ReadSentenceFrag extends Fragment {
    UserViewModel userViewModel;
    RoomViewModel roomViewModel;
    LeaderBoardViewModel leaderBoardViewModel;
    String myRandomIf;
    String myRandomThen = "";
    private final VoicePlayback voicePlayback = new VoicePlayback();
    private SpinnerAdapter voiceAdapter;
    private ObservableList.OnListChangedCallback<ObservableList<Unlockable>> voiceListCallback;
    private ObservableList.OnListChangedCallback<ObservableList<User>> readerPlayersCallback;
    DiffGoogleVoice selectedItemOnSpinner;
    View readButton;
    View doneButton;
    View nextButton;
    View spinnerObject;
    TextView ifQuestionText;
    TextView thenAnswerText;
    Typeface ifQuestionDefaultTypeface;
    Typeface thenAnswerDefaultTypeface;
    int currentUserReadIndex = -1;
    String currentUserPlayerKey = "";
    RoundAssignment currentRoundAssignment;
    boolean sentenceReady = false;
    boolean sentenceRevealed = false;
    boolean currentReaderTurn = false;
    boolean readTurnPassed = false;
    boolean everyoneHasRead = false;
    boolean votingItemPushed = false;
    boolean roundListenersStarted = false;
    boolean assignmentListenerStarted = false;
    boolean readingActionInProgress = false;
    boolean turnPromptShown = false;
    String observedActiveReaderKey = "";
    SoundHelper soundHelper = new SoundHelper();

    public ReadSentenceFrag() {
        super(R.layout.fragment_read_sentence);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViewModels();
        userViewModel.gamePhase.setValue(GamePhase.READING);
        setupRoomMessages(view);
        setupUserMessages(view);
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
                setReadingActionSaving(false);
            }
        });
    }

    private void setupUserMessages(View view) {
        userViewModel.databaseMessage.observe(getViewLifecycleOwner(), message -> {
            if (message != null && message.length() > 0) {
                UiMessenger.showTopSnackbar(view, message);
                userViewModel.databaseMessage.setValue("");
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
        currentUserPlayerKey = GameLogic.playerKey(currentUser);
        roomViewModel.listenToCurrentRoundId(currentUser.gameRoom);
        roomViewModel.currentRoundId.observe(getViewLifecycleOwner(), roundId -> {
            if (roundId != null && roundId.length() > 0 && !roundListenersStarted) {
                startRoundListeners(currentUser);
            }
        });
        roomViewModel.activeReaderIndex.observe(getViewLifecycleOwner(), activeReaderIndex -> {
            if (Boolean.TRUE.equals(roomViewModel.activeReaderLoaded.getValue())) {
                updateActiveReaderControls();
            }
            else {
                AppLog.d(AppLog.GAME_FLOW, "Read controls waiting for Firebase active reader");
            }
        });
        roomViewModel.activeReaderLoaded.observe(getViewLifecycleOwner(), loaded -> {
            if (Boolean.TRUE.equals(loaded)) {
                updateActiveReaderControls();
            }
        });
        roomViewModel.activeReaderKey.observe(getViewLifecycleOwner(), ignored -> {
            if (Boolean.TRUE.equals(roomViewModel.activeReaderLoaded.getValue())) {
                updateActiveReaderControls();
            }
        });
        roomViewModel.readingComplete.observe(getViewLifecycleOwner(), this::handleReadingComplete);
        // Whether this device may read depends on the *other players* too, not only on the active
        // reader index moving: once the active reader is removed (or has been gone long enough), the
        // host takes their turn. Nothing about the round itself changes at that moment - the index
        // and key stay exactly where they were - so without watching the player list the host's
        // controls never re-evaluate and the round sits on a turn nobody can take.
        readerPlayersCallback = new ObservableList.OnListChangedCallback<ObservableList<User>>() {
            @Override
            public void onChanged(ObservableList<User> sender) {
                updateActiveReaderControls();
            }

            @Override
            public void onItemRangeChanged(ObservableList<User> sender, int start, int count) {
                updateActiveReaderControls();
            }

            @Override
            public void onItemRangeInserted(ObservableList<User> sender, int start, int count) {
                updateActiveReaderControls();
            }

            @Override
            public void onItemRangeMoved(ObservableList<User> sender, int from, int to, int count) {
                updateActiveReaderControls();
            }

            @Override
            public void onItemRangeRemoved(ObservableList<User> sender, int start, int count) {
                updateActiveReaderControls();
            }
        };
        userViewModel.getUsers().addOnListChangedCallback(readerPlayersCallback);
    }

    private void startRoundListeners(User currentUser) {
        roundListenersStarted = true;
        roomViewModel.listenToActiveReader(currentUser.gameRoom);
        roomViewModel.listenToReadingComplete(currentUser.gameRoom);
        AppLog.i(AppLog.GAME_FLOW, "Round listeners started roundId=" + roomViewModel.currentRoundId.getValue());
    }

    private void resetLocalReadingState() {
        sentenceReady = false;
        sentenceRevealed = false;
        currentReaderTurn = false;
        readTurnPassed = false;
        everyoneHasRead = false;
        votingItemPushed = false;
        roundListenersStarted = false;
        assignmentListenerStarted = false;
        currentRoundAssignment = null;
        currentUserReadIndex = -1;
        currentUserPlayerKey = "";
        observedActiveReaderKey = "";
        turnPromptShown = false;
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
        ifQuestionDefaultTypeface = ifQuestionText.getTypeface();
        thenAnswerDefaultTypeface = thenAnswerText.getTypeface();
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            AppLog.w(AppLog.GAME_FLOW, "Read sentence assignment skipped: missing current user");
            return;
        }

        ifQuestionText.setText("Loading...");
        thenAnswerText.setText("");
        roomViewModel.listenToCurrentRoundId(currentUser.gameRoom);
        roomViewModel.currentRoundId.observe(getViewLifecycleOwner(), roundId -> {
            if (roundId != null && roundId.length() > 0 && !assignmentListenerStarted) {
                assignmentListenerStarted = true;
                roomViewModel.listenToAssignment(currentUser.gameRoom, GameLogic.playerKey(currentUser));
            }
        });
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
        updateActiveReaderControls();
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
        // Full control refresh, not just the text: whether the mic applies is derived from
        // sentenceRevealed, and that is computed in updateActiveReaderControls. Refreshing only
        // the text left the mic hidden until some unrelated Firebase update happened to fire.
        updateActiveReaderControls();
        AppLog.i(AppLog.GAME_FLOW, "Read sentence revealed for active reader key=" + currentUserPlayerKey);
    }

    private void updateSentenceVisibility() {
        if (ifQuestionText == null || thenAnswerText == null || nextButton == null) {
            return;
        }
        if (!sentenceReady) {
            applyInstructionTextStyle(false);
            ifQuestionText.setText("Loading...");
            thenAnswerText.setText("");
            ActionButtonState.setEnabled(nextButton, false);
            setNextButtonText("show");
            updateSpinnerVisibility(false);
            return;
        }
        if (!sentenceRevealed || !currentReaderTurn) {
            applyInstructionTextStyle(currentReaderTurn);
            if (currentReaderTurn) {
                ifQuestionText.setText("Your turn to read.");
                thenAnswerText.setText("Tap Show when the group is ready.");
                nextButton.setVisibility(View.VISIBLE);
                ActionButtonState.setEnabled(nextButton, true);
            }
            else {
                ifQuestionText.setText("Waiting for another player to read.");
                thenAnswerText.setText("Listen until it is your turn.");
                // Hidden rather than shown-disabled: there is nothing for a waiting player to do
                // here, and a "show" button on screen while it is someone else's turn invites a
                // tap and then explains it was the wrong moment. The paper text already says what
                // is going on.
                nextButton.setVisibility(View.GONE);
                ActionButtonState.setEnabled(nextButton, false);
            }
            setNextButtonText("show");
            updateSpinnerVisibility(false);
            return;
        }
        applySentenceTextStyle();
        ifQuestionText.setText(GameLogic.formatIfSentence(myRandomIf));
        thenAnswerText.setText(GameLogic.formatThenSentence(myRandomThen));
        nextButton.setVisibility(View.VISIBLE);
        boolean canPass = currentReaderTurn && !readTurnPassed && !readingActionInProgress;
        ActionButtonState.setSaving(nextButton, readingActionInProgress, canPass);
        setNextButtonText("pass");
        updateSpinnerVisibility(true);
        updateDoneButtonVisibility();
    }

    /** Voice picker only makes sense once the active reader can actually see the revealed
     * sentence - showing it earlier (while still deciding whose turn it is) has nothing to apply
     * to yet. No-op for free-play users, whose spinner stays permanently gone. */
    private void updateSpinnerVisibility(boolean visible) {
        if (spinnerObject == null || !currentUserHasAccount()) {
            return;
        }
        spinnerObject.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyInstructionTextStyle(boolean activeReaderPrompt) {
        int color = activeReaderPrompt ? requireContext().getColor(R.color.turn_instruction_text) : requireContext().getColor(R.color.instruction_text);
        ifQuestionText.setTextColor(color);
        thenAnswerText.setTextColor(color);
        ifQuestionText.setTypeface(Typeface.DEFAULT, activeReaderPrompt ? Typeface.BOLD : Typeface.NORMAL);
        thenAnswerText.setTypeface(Typeface.DEFAULT, activeReaderPrompt ? Typeface.BOLD_ITALIC : Typeface.ITALIC);
        ifQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, activeReaderPrompt ? 36 : 32);
        thenAnswerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, activeReaderPrompt ? 28 : 26);
    }

    private void applySentenceTextStyle() {
        ifQuestionText.setTextColor(requireContext().getColor(R.color.black));
        thenAnswerText.setTextColor(requireContext().getColor(R.color.black));
        ifQuestionText.setTypeface(ifQuestionDefaultTypeface, Typeface.BOLD);
        thenAnswerText.setTypeface(thenAnswerDefaultTypeface, Typeface.BOLD);
        ifQuestionText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        thenAnswerText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
    }

    private void setNextButtonText(String text) {
        if (nextButton instanceof TextView) {
            ((TextView) nextButton).setText(text);
        }
    }

    private boolean allPlayersHaveAccounts() {
        boolean allAccountPlayers = GameFlowPolicy.allPlayersHaveAccounts(userViewModel.getUsers());
        AppLog.d(AppLog.GAME_FLOW, "Account player check: allAccountPlayers=" + allAccountPlayers + ", total=" + userViewModel.getUsers().size());
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
        spinnerObject = view.findViewById(R.id.spinnerObject);
        if (!currentUserHasAccount()) {
            spinnerObject.setVisibility(View.GONE);
            return;
        }
        // Hidden until the active reader taps "show" - picking a voice only makes sense once the
        // sentence is actually visible, not while still deciding whether it's your turn.
        spinnerObject.setVisibility(View.GONE);
        ArrayList<DiffGoogleVoice> voicesUnlocked = buildVoiceList();
        loadUnlockedVoicesForAccountPlayers();
        Spinner spinner = (Spinner) spinnerObject;
        Resources res = getResources();
        voiceAdapter = new SpinnerAdapter(getContext(), R.layout.read_method_item, voicesUnlocked, res);
        SpinnerAdapter adapter = voiceAdapter;
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedItemOnSpinner = (DiffGoogleVoice) parent.getItemAtPosition(position);
                voicePlayback.setVoiceCode(selectedItemOnSpinner.getVoiceCode());
                AppLog.d(AppLog.TTS, "Voice selected: " + selectedItemOnSpinner.getVoice() + ", code=" + voicePlayback.getVoiceCode());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                voicePlayback.setVoiceCode("0");
                AppLog.d(AppLog.TTS, "No voice selected; using regular voice");
            }
        });
    }

    private ArrayList<DiffGoogleVoice> buildVoiceList() {
        ArrayList<DiffGoogleVoice> voicesUnlocked = new ArrayList<DiffGoogleVoice>();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));
        // The rows arrive from Firebase *after* the adapter is built, so every insert has to
        // notify the adapter. Adding straight to the backing ArrayList (which is what this used to
        // do) leaves the Spinner showing only "regular" no matter how many voices the account has
        // earned - the unlockables were unreachable in the picker even once unlocked.
        voiceListCallback = new ObservableList.OnListChangedCallback<ObservableList<Unlockable>>() {
            @Override
            public void onChanged(ObservableList<Unlockable> sender) {
                rebuildVoiceOptions(sender, voicesUnlocked);
            }

            @Override
            public void onItemRangeChanged(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
                rebuildVoiceOptions(sender, voicesUnlocked);
            }

            @Override
            public void onItemRangeInserted(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
                rebuildVoiceOptions(sender, voicesUnlocked);
            }

            @Override
            public void onItemRangeMoved(ObservableList<Unlockable> sender, int fromPosition, int toPosition, int itemCount) {
                rebuildVoiceOptions(sender, voicesUnlocked);
            }

            @Override
            public void onItemRangeRemoved(ObservableList<Unlockable> sender, int positionStart, int itemCount) {
                rebuildVoiceOptions(sender, voicesUnlocked);
            }
        };
        userViewModel.userUnlocked.addOnListChangedCallback(voiceListCallback);
        return voicesUnlocked;
    }

    /**
     * Rebuilds the picker from the whole unlocked list rather than appending the one row that just
     * arrived. Appending assumed exactly one insert at {@code positionStart}, so a multi-row insert
     * silently dropped everything after the first, and a re-delivered list added duplicates.
     */
    private void rebuildVoiceOptions(ObservableList<Unlockable> sender, ArrayList<DiffGoogleVoice> voicesUnlocked) {
        voicesUnlocked.clear();
        voicesUnlocked.add(new DiffGoogleVoice("regular", "0"));
        for (Unlockable unlockable : sender) {
            if (unlockable != null && unlockable.isUnlocked()) {
                voicesUnlocked.add(new DiffGoogleVoice(unlockable.getVoiceType(), unlockable.getVoiceCode()));
            }
        }
        AppLog.d(AppLog.TTS, "Voice picker rebuilt with " + voicesUnlocked.size() + " option(s)");
        if (voiceAdapter != null) {
            voiceAdapter.notifyDataSetChanged();
        }
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
        doneButton.setVisibility(View.GONE);
        if (currentUserHasAccount()) {
            voicePlayback.start(requireContext());
            readButton.setOnClickListener(v -> speakCurrentSentence());
        }
        else {
            readButton.setVisibility(View.GONE);
            view.findViewById(R.id.spinnerObject).setVisibility(View.GONE);
            AppLog.i(AppLog.TTS, "TextToSpeech skipped for free-play reader");
        }
        updateSentenceVisibility();
    }

    private boolean currentUserHasAccount() {
        User currentUser = userViewModel.getUser().getValue();
        return currentUser != null && Boolean.TRUE.equals(currentUser.accountPlay);
    }

    /** The player behind the active reader key, or null if they are no longer in the room list. */
    private User findUserByPlayerKey(String playerKey) {
        if (playerKey == null || playerKey.length() == 0) {
            return null;
        }
        for (User user : userViewModel.getUsers()) {
            if (playerKey.equals(GameLogic.playerKey(user))) {
                return user;
            }
        }
        return null;
    }

    private int findCurrentUserIndexInReadOrder() {
        if (roomViewModel.readOrder.getValue() == null) {
            return -1;
        }
        for (int index = 0; index < roomViewModel.readOrder.getValue().size(); index++) {
            if (roomViewModel.readOrder.getValue().get(index).equals(currentUserPlayerKey)) {
                return index;
            }
        }
        AppLog.w(AppLog.TTS, "Current user not found in DB read order; playerKey=" + currentUserPlayerKey);
        return -1;
    }

    private void updateActiveReaderControls() {
        if (readButton == null || doneButton == null) {
            return;
        }
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null) {
            return;
        }
        if (currentUserPlayerKey.length() == 0) {
            currentUserPlayerKey = GameLogic.playerKey(currentUser);
        }
        currentUserReadIndex = findCurrentUserIndexInReadOrder();
        int activeIndex = roomViewModel.activeReaderIndex.getValue() == null ? 0 : roomViewModel.activeReaderIndex.getValue();
        String activeReaderKey = roomViewModel.activeReaderKey.getValue();
        resetRevealStateWhenReaderChanges(activeReaderKey);
        int readOrderSize = roomViewModel.readOrder.getValue() == null ? 0 : roomViewModel.readOrder.getValue().size();
        everyoneHasRead = activeIndex >= readOrderSize && readOrderSize > 0;
        boolean isCurrentReader = currentUserPlayerKey.length() > 0
                && currentUserPlayerKey.equals(activeReaderKey)
                && currentUserReadIndex >= 0;
        // Reading advances by key match alone, so a reader who left (or has been gone long enough to
        // be removable) stops the round for everyone - nobody else can move it on. The host covers
        // that turn instead. This is what makes it safe to leave a departed player's slot in the
        // reading order rather than resequencing it, which would mean fixing up activeReaderIndex
        // mid-round.
        if (!isCurrentReader && GameFlowPolicy.hostMayCoverReadingTurn(
                currentUser, findUserByPlayerKey(activeReaderKey), System.currentTimeMillis())) {
            isCurrentReader = true;
            AppLog.i(AppLog.GAME_FLOW, "Host covering absent reader's turn key=" + activeReaderKey);
        }
        currentReaderTurn = isCurrentReader;
        boolean canReadAloud = isCurrentReader && sentenceRevealed;
        // The mic is only meaningful once you can actually speak your own revealed sentence. It
        // used to merely dim to alpha 0.35 while waiting for someone else to read, which still
        // reads as an available control sitting on the page. Free-play users never get one at all
        // (setupReadingControls hides it), so this only applies to account players.
        if (currentUserHasAccount()) {
            readButton.setVisibility(canReadAloud ? View.VISIBLE : View.GONE);
            readButton.setEnabled(canReadAloud);
        }
        updateSentenceVisibility();
        if (isCurrentReader && !sentenceRevealed && !turnPromptShown) {
            UiMessenger.showTopSnackbar(requireView(), "Your turn to read");
            soundHelper.playTurnAlert();
            turnPromptShown = true;
        }
        AppLog.d(AppLog.TTS, "Read controls updated: activeIndex=" + activeIndex
                + ", activeKey=" + activeReaderKey
                + ", myKey=" + currentUserPlayerKey
                + ", myIndex=" + currentUserReadIndex);
    }

    private void resetRevealStateWhenReaderChanges(String activeReaderKey) {
        if (activeReaderKey == null || activeReaderKey.length() == 0) {
            return;
        }
        if (activeReaderKey.equals(observedActiveReaderKey)) {
            return;
        }
        observedActiveReaderKey = activeReaderKey;
        sentenceRevealed = false;
        readTurnPassed = false;
        readingActionInProgress = false;
        turnPromptShown = false;
        AppLog.d(AppLog.GAME_FLOW, "Read reveal state reset for active reader key=" + activeReaderKey);
    }

    private void passReadingTurn() {
        User currentUser = userViewModel.getUser().getValue();
        if (currentUser == null || currentUser.gameRoom == null || currentUser.gameRoom.length() == 0 || roomViewModel.readOrder.getValue() == null || roomViewModel.readOrder.getValue().size() == 0) {
            UiMessenger.showTopSnackbar(requireView(), "Cannot pass reading turn yet");
            AppLog.w(AppLog.TTS, "Pass reading turn blocked: missing user, room, or DB read order");
            return;
        }
        if (!currentReaderTurn || !sentenceRevealed || readTurnPassed) {
            UiMessenger.showTopSnackbar(requireView(), "It is not time to pass yet");
            AppLog.w(AppLog.TTS, "Pass reading turn blocked: invalid local state");
            return;
        }
        readTurnPassed = true;
        // Advance from the turn actually being played, not from this player's own slot in the read
        // order. Those are the same number for an ordinary turn, and different the moment the host
        // covers for someone who has left: the host sits at index 0 while the active turn is 1, so
        // "my index + 1" set the round straight back to the turn it was trying to finish. That is a
        // perfect no-op, and it deadlocked reading - the host could take the absent player's turn
        // and then never get past it, with everyone else waiting on a turn that kept resetting.
        Integer activeIndexValue = roomViewModel.activeReaderIndex.getValue();
        int activeIndex = activeIndexValue == null ? currentUserReadIndex : activeIndexValue;
        int nextReaderIndex = activeIndex + 1;
        setReadingActionSaving(true);
        if (GameFlowPolicy.finalReaderPassed(nextReaderIndex, roomViewModel.readOrder.getValue().size())) {
            roomViewModel.completeReadingAfterFinalPass(currentUser.gameRoom, nextReaderIndex, () -> setReadingActionSaving(false));
            AppLog.i(AppLog.GAME_FLOW, "Final reader passed; reading phase will complete room=" + currentUser.gameRoom);
        }
        else {
            roomViewModel.setActiveReaderIndex(currentUser.gameRoom, nextReaderIndex, () -> setReadingActionSaving(false));
        }
        AppLog.i(AppLog.TTS, "Passed reading turn from key=" + currentUserPlayerKey + " to index=" + nextReaderIndex);
    }

    private void updateDoneButtonVisibility() {
        if (doneButton == null) {
            return;
        }
        doneButton.setVisibility(View.GONE);
        if (everyoneHasRead && ifQuestionText != null && thenAnswerText != null) {
            applyInstructionTextStyle(false);
            ifQuestionText.setText("What if everyone has read?");
            thenAnswerText.setText("Then the next part of the game starts now.");
            if (nextButton != null) {
                ActionButtonState.setEnabled(nextButton, false);
            }
        }
    }

    private void setReadingActionSaving(boolean saving) {
        readingActionInProgress = saving;
        updateSentenceVisibility();
        updateDoneButtonVisibility();
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
        voicePlayback.speak(myRandomIf, myRandomThen);
    }

    @Override
    public void onDestroyView() {
        roomViewModel.removeAssignmentListener();
        roomViewModel.removeReadingCompleteListener();
        roomViewModel.removeActiveReaderListener();
        roomViewModel.removeCurrentRoundListener();
        // Registered against the ViewModel's list, which outlives this view - without this the
        // callback stack grows on every rotation/re-entry and each copy touches a dead adapter.
        if (readerPlayersCallback != null) {
            userViewModel.getUsers().removeOnListChangedCallback(readerPlayersCallback);
            readerPlayersCallback = null;
        }
        if (voiceListCallback != null) {
            userViewModel.userUnlocked.removeOnListChangedCallback(voiceListCallback);
            voiceListCallback = null;
        }
        voiceAdapter = null;
        voicePlayback.release();
        soundHelper.release();
        super.onDestroyView();
    }
}
