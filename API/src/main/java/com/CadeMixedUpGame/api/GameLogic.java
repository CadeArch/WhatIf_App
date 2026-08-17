package com.CadeMixedUpGame.api;

import com.CadeMixedUpGame.api.models.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class GameLogic {
    public static String cleanIfSentence(String sentence) {
        String cleaned = removeLeadingPhrase(cleanSentence(sentence), "what if");
        cleaned = removeLeadingPhrase(cleaned, "if");
        if (cleaned.length() == 0) {
            return cleaned;
        }
        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
    }

    public static String cleanThenSentence(String sentence) {
        return removeLeadingPhrase(cleanSentence(sentence), "then");
    }

    public static String formatIfSentence(String sentence) {
        String cleaned = cleanIfSentence(sentence);
        if (cleaned.length() == 0) {
            return "";
        }
        return "What if " + cleaned + "?";
    }

    public static String formatThenSentence(String sentence) {
        String cleaned = cleanThenSentence(sentence);
        if (cleaned.length() == 0) {
            return "";
        }
        return "Then " + cleaned + ".";
    }

    public static int nextPlayerIndex(int currentIndex, int playerCount) {
        if (playerCount <= 0) {
            return -1;
        }
        return (currentIndex + 1) % playerCount;
    }

    public static int previousPlayerIndex(int currentIndex, int playerCount) {
        if (playerCount <= 0) {
            return -1;
        }
        return (currentIndex - 1 + playerCount) % playerCount;
    }

    public static String playerKey(User user) {
        if (user == null) {
            return "";
        }
        return user.userName + "-" + user.userID;
    }

    public static List<String> randomizedAssignment(List<String> playerKeys, long seed) {
        ArrayList<String> assignedKeys = new ArrayList<String>();
        if (playerKeys == null || playerKeys.size() == 0) {
            return assignedKeys;
        }
        assignedKeys.addAll(playerKeys);
        if (assignedKeys.size() == 1) {
            return assignedKeys;
        }

        Collections.shuffle(assignedKeys, new Random(seed));
        repairSelfAssignments(playerKeys, assignedKeys);
        return assignedKeys;
    }

    public static String newRoundId() {
        return "round-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Simple, common, unambiguous 4-letter words - easy to read aloud and type on a phone
    // keyboard, unlike the old random-letters-and-digits room code it replaced.
    private static final String[] ROOM_CODE_WORDS = {
            "able", "acid", "acre", "aged", "arch", "area", "army", "atom", "away", "axis",
            "baby", "back", "bake", "ball", "band", "bank", "bark", "barn", "base", "bean",
            "bear", "beat", "beef", "beer", "bell", "belt", "bend", "best", "bike", "bird",
            "blue", "boat", "body", "bold", "bolt", "bond", "bone", "book", "boot", "born",
            "boss", "both", "bowl", "boys", "brew", "brow", "buck", "bulb", "bulk", "bull",
            "burn", "bush", "cafe", "cake", "calm", "camp", "card", "care", "carp", "cart",
            "case", "cash", "cast", "cave", "cell", "chef", "chip", "city", "clay", "clip",
            "club", "coal", "coat", "code", "coin", "cold", "colt", "cook", "cool", "cord",
            "core", "cork", "corn", "cost", "crab", "crew", "crop", "cube", "cure", "curl",
            "cute", "dark", "dash", "dawn", "dead", "deal", "dear", "deck", "deer", "desk",
            "dial", "dice", "dish", "dive", "dock", "does", "doll", "done", "door", "dose",
            "down", "draw", "drop", "drum", "duck", "dust", "duty", "each", "earn", "east",
            "easy", "echo", "edge", "epic", "even", "exit", "face", "fact", "fair", "fall",
            "farm", "fast", "fate", "fear", "feed", "feet", "fern", "file", "fill", "film",
            "find", "fine", "fire", "fish", "fist", "flag", "flat", "flow", "foam", "fold",
            "folk", "food", "fool", "foot", "fork", "form", "fort", "four", "free", "frog",
            "fuel", "full", "fund", "fury", "gain", "gala", "game", "gate", "gaze", "gear",
            "gift", "girl", "give", "glad", "glow", "goal", "goat", "gold", "golf", "good",
            "grey", "grid", "grow", "gulf", "gust", "hair", "half", "hall", "hand", "hard",
            "hare", "harp", "hawk", "haze", "head", "heal", "heap", "heat", "help", "herb",
            "here", "hero", "hide", "high", "hike", "hill", "hint", "hive", "hold", "hole",
            "home", "hood", "hook", "hope", "horn", "host", "hour", "huge", "hush", "icon",
            "idea", "inch", "iris", "iron", "isle", "jade", "jazz", "jeep", "join", "joke",
            "jolt", "jump", "june", "jury", "just", "keen", "keep", "kept", "kick", "kind",
            "king", "kite", "kiwi", "knee", "knit", "knot", "lace", "lake", "lamb", "lamp",
            "land", "lane", "leaf", "lean", "leap", "left", "lens", "lift", "like", "lily",
            "lime", "line", "link", "lion", "list", "live", "load", "loaf", "loan", "lock",
            "loft", "long", "look", "loop", "lord", "lose", "loud", "love", "luck", "lump",
            "lung", "lynx", "mail", "main", "make", "male", "mall", "many", "mark", "mask",
            "mast", "meal", "meat", "meet", "melt", "menu", "mesh", "mild", "mile", "milk",
            "mill", "mind", "mine", "mint", "miss", "mist", "mode", "mold", "mole", "monk",
            "moon", "moss", "moth", "move", "much", "mule", "must", "myth", "name", "navy",
            "near", "neat", "neck", "need", "nest", "news", "next", "nice", "node", "noon",
            "nose", "note", "oath", "oats", "obey", "oboe", "oval", "oven", "over", "pace",
            "pack", "page", "paid", "pail", "pain", "pair", "palm", "park", "part", "pass",
            "past", "path", "peak", "pear", "peel", "peer", "pest", "pick", "pier", "pike",
            "pile", "pine", "pint", "pipe", "plan", "play", "plot", "plow", "plum", "plus",
            "poem", "poet", "pole", "polo", "pond", "pony", "pool", "poor", "pork", "port",
            "pose", "post", "pour", "pray", "prep", "prop", "pull", "pulp", "pump", "pure",
            "push", "quiz", "race", "rack", "rail", "rain", "rank", "rare", "rash", "rate",
            "read", "real", "reef", "rely", "rent", "rest", "rice", "rich", "ride", "ring",
            "rise", "risk", "road", "roar", "rock", "role", "roll", "roof", "room", "root",
            "rope", "rose", "rows", "rude", "ruby", "rule", "rush", "rust", "sack", "safe",
            "sage", "sail", "salt", "same", "sand", "sane", "save", "seal", "seat", "seed",
            "seek", "seem", "self", "sell", "send", "ship", "shoe", "shop", "shot", "show",
            "shut", "sift", "sign", "silk", "sing", "sink", "site", "size", "skin", "skip",
            "slap", "sled", "slot", "slow", "snap", "snow", "soap", "sock", "soda", "sofa",
            "soft", "soil", "sold", "sole", "some", "song", "soon", "sort", "soul", "soup",
            "spin", "spot", "star", "stay", "stem", "step", "stir", "stop", "such", "suit",
            "sunk", "sure", "swan", "swim", "tail", "take", "tale", "talk", "tall", "tame",
            "tank", "tape", "task", "team", "tell", "tent", "term", "test", "text", "than",
            "that", "thin", "this", "tide", "tidy", "tile", "time", "tiny", "tire", "toad",
            "tone", "tool", "tore", "torn", "tour", "town", "trap", "tray", "tree", "trim",
            "trip", "true", "tube", "tune", "turf", "turn", "twig", "twin", "type", "ugly",
            "unit", "upon", "urge", "used", "user", "vast", "veil", "vein", "verb", "very",
            "vest", "view", "vine", "visa", "void", "vote", "wade", "wage", "wait", "wake",
            "walk", "wall", "want", "warm", "warn", "wash", "wasp", "wave", "weak", "wear",
            "week", "well", "west", "what", "when", "whip", "wide", "wife", "wild", "will",
            "wind", "wine", "wing", "wire", "wise", "wish", "wolf", "wood", "wool", "word",
            "wore", "work", "yard", "yarn", "yawn", "year", "zest", "zinc", "zone", "zoom",
    };

    /**
     * What the disobedient voice says before and after it caves and reads the sentence.
     *
     * <p>Kept as two independent lists rather than eight matched pairs on purpose: pairing them
     * randomly means a player who has heard every line still keeps hearing new combinations, and
     * adding one line to either list multiplies the variety instead of adding one more script.
     * Each opener has to work in front of any sentence and each closer behind any sentence, so
     * none of them refer to what the sentence actually said.
     */
    private static final String[] DISOBEDIENT_OPENERS = {
            "No. I am not reading that.",
            "Ugh. Fine. But let the record show this was not my idea.",
            "Absolutely not. ... Okay, once, and only because you are all staring at me.",
            "Whoever wrote this owes me an apology. Here goes.",
            "I have read many things in my life. This is going to be the worst one of them.",
            "Do I have to? ... Apparently I have to.",
            "I am reading this under protest.",
            "I want a lawyer present for this one.",
    };

    /**
     * What the forgetful voice says while it gropes for a word it has already started saying.
     *
     * <p>Each one has to work in the gap between a half-said word and the whole word — "ele... no
     * wait... elephant" — so they are all things you say <em>about</em> a word you cannot retrieve,
     * never about what the sentence means.
     */
    private static final String[] FORGETFUL_INTERJECTIONS = {
            "... uh... what was it... ",
            "... hang on... ",
            "... no wait... ",
            "... what's the word... ",
            "... give me a second... ",
            "... it's on the tip of my tongue... ",
            "... oh come on... ",
            "... you know the one... ",
            "... I had it a second ago... ",
            "... don't tell me... ",
    };

    /** How thinly the extra "like"s are spread, and the ceiling on them — see {@code shaggy}. */
    private static final int SHAGGY_WORDS_PER_LIKE = 6;
    private static final int SHAGGY_MAX_EXTRA_LIKES = 3;
    private static final int SHAGGY_PLACEMENT_ATTEMPTS = 40;

    private static final String[] DISOBEDIENT_CLOSERS = {
            "There. Happy?",
            "I hope you are all very proud of yourselves.",
            "I need a moment.",
            "Never ask me to do that again.",
            "That is the worst thing I have ever said out loud.",
            "I am going to go lie down.",
            "You did this. All of you.",
            "Do not make me read the next one.",
    };

    public static String randomRoomCode(Random random) {
        Random source = random == null ? new Random() : random;
        String first = ROOM_CODE_WORDS[source.nextInt(ROOM_CODE_WORDS.length)];
        String second;
        do {
            second = ROOM_CODE_WORDS[source.nextInt(ROOM_CODE_WORDS.length)];
        } while (second.equals(first));
        return first + "-" + second;
    }

    public static boolean isCurrentRound(String currentRoundId, String eventRoundId) {
        return currentRoundId != null
                && currentRoundId.length() > 0
                && currentRoundId.equals(eventRoundId);
    }

    private static void repairSelfAssignments(List<String> playerKeys, ArrayList<String> assignedKeys) {
        for (int index = 0; index < assignedKeys.size(); index++) {
            if (assignedKeys.get(index).equals(playerKeys.get(index))) {
                int swapIndex = (index + 1) % assignedKeys.size();
                Collections.swap(assignedKeys, index, swapIndex);
            }
        }
    }

    public static String mostCommonVote(List<String> votes) {
        String bestVote = "";
        int bestCount = 0;
        for (String vote : votes) {
            if (vote == null) {
                continue;
            }
            int count = 0;
            for (String candidate : votes) {
                if (vote.equals(candidate)) {
                    count += 1;
                }
            }
            if (count > bestCount) {
                bestVote = vote;
                bestCount = count;
            }
        }
        return bestVote;
    }

    /**
     * Applies a voice's text mutation. Every code in {@link UnlockPolicy#catalog()} except the
     * "regular" voice must change the text somehow — a voice that returns its input unchanged is
     * an unlockable the player earns and then can't tell apart from no voice at all, which is
     * exactly what codes 4-7 silently did until they became earnable.
     * {@code GameLogicTest.everyUnlockableVoiceActuallyChangesTheText} guards that.
     */
    public static String mutateVoiceText(String ifThen, String code) {
        return mutateVoiceText(ifThen, code, null);
    }

    /**
     * As above, with the randomness injectable.
     *
     * <p>{@code forgetful}, {@code shaggy} and {@code disobedient} use it — each picks fresh wording
     * every time it speaks, so a test asserting on their output needs a seeded {@link Random} to
     * have anything stable to assert. Same shape as {@link #randomRoomCode(Random)}: {@code null}
     * means "use a real one".
     */
    public static String mutateVoiceText(String ifThen, String code, Random random) {
        if ("1".equals(code)) {
            return fuddify(ifThen);
        }
        if ("2".equals(code)) {
            return pigLatin(ifThen);
        }
        if ("3".equals(code)) {
            return backwords(ifThen);
        }
        if ("4".equals(code)) {
            return jokester(ifThen);
        }
        if ("5".equals(code)) {
            return forgetful(ifThen, random);
        }
        if ("6".equals(code)) {
            return shaggy(ifThen, random);
        }
        if ("7".equals(code)) {
            return disobedient(ifThen, random);
        }
        return ifThen;
    }

    /**
     * The Elmer Fudd consonant swap: both {@code r} and {@code l} become {@code w}, in either case.
     *
     * <p>This used to be a bare {@code replace("r", "w")}, which was wrong twice over. It never
     * touched {@code l}, so "little" stayed "little" where Fudd says "wittwe" — half the joke was
     * missing. And being case-sensitive on a single lowercase letter, it skipped every capital:
     * the If half is always capitalized by {@link #cleanIfSentence}, so a sentence starting with
     * "Run" or "Rabbit" came out completely unfuddified at exactly the most audible moment. Case is
     * preserved rather than ignored so the text still reads correctly if it is ever shown rather
     * than spoken.
     */
    private static String fuddify(String ifThen) {
        StringBuilder result = new StringBuilder(ifThen.length());
        for (int index = 0; index < ifThen.length(); index++) {
            char character = ifThen.charAt(index);
            if (character == 'r' || character == 'l') {
                result.append('w');
            }
            else if (character == 'R' || character == 'L') {
                result.append('W');
            }
            else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /** Punctuates the sentence with laughter, so the reader can't get through it straight. */
    private static String jokester(String ifThen) {
        return ifThen.replace(", ", ", heh heh, ") + " ha ha ha!";
    }

    /**
     * Trails off on the longer words, as if the reader keeps losing the thread — with a different
     * excuse each time.
     *
     * <p><em>Which</em> words stumble is still fixed by word length, so the shape of a given
     * sentence is stable; only the excuse in the gap is drawn. That split matters: the sentence
     * itself is never lost (the whole word always follows the stumble), so a listener who asks
     * "what?" hears the same content again, just with different flailing around it. A single fixed
     * excuse — which is what this had — turns into a catchphrase by the third long word of the
     * first sentence, and there are usually several per sentence.
     *
     * <p>Never draws the same excuse twice in a row, because back-to-back repeats inside one
     * sentence read as a stuck record rather than someone genuinely groping for a word.
     */
    private static String forgetful(String ifThen, Random random) {
        Random source = random == null ? new Random() : random;
        String[] words = ifThen.split(" ");
        StringBuilder result = new StringBuilder();
        String previousInterjection = "";
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (word.length() >= 6) {
                String interjection;
                do {
                    interjection = FORGETFUL_INTERJECTIONS[source.nextInt(FORGETFUL_INTERJECTIONS.length)];
                } while (interjection.equals(previousInterjection));
                previousInterjection = interjection;
                result.append(word, 0, 3).append(interjection).append(word);
            } else {
                result.append(word);
            }
        }
        return result.toString();
    }

    /**
     * Stoner-detective delivery: "Zoinks!" at both ends, "like" wedged into the clause breaks, and a
     * few more scattered through wherever they happen to land this time.
     *
     * <p>The clause-break "like"s are fixed, so the delivery has a reliable rhythm; the extras are
     * placed at random so two readings of the same sentence do not come out identically. Budgeted
     * at one per {@link #SHAGGY_WORDS_PER_LIKE} words and capped at
     * {@link #SHAGGY_MAX_EXTRA_LIKES} — the joke is a verbal tic, and a tic every other word stops
     * being a character and becomes unlistenable, especially with a long sentence to get through.
     * Never placed next to an existing "like" or next to another extra, for the same reason.
     */
    private static String shaggy(String ifThen, Random random) {
        Random source = random == null ? new Random() : random;
        String[] words = ifThen.replace(", ", ", like, ").split(" ");
        boolean[] likeBefore = new boolean[words.length];
        int wanted = words.length < 2 ? 0 : Math.min(SHAGGY_MAX_EXTRA_LIKES, words.length / SHAGGY_WORDS_PER_LIKE);
        int placed = 0;
        // Bounded rather than looping until satisfied: on a short sentence the "not next to another
        // like" rule can make the last slot unreachable, and this would spin forever looking for it.
        for (int attempt = 0; attempt < SHAGGY_PLACEMENT_ATTEMPTS && placed < wanted; attempt++) {
            int index = 1 + source.nextInt(words.length - 1);
            if (likeBefore[index] || likeBefore[index - 1]
                    || (index + 1 < words.length && likeBefore[index + 1])) {
                continue;
            }
            if (words[index].startsWith("like") || words[index - 1].endsWith("like,")) {
                continue;
            }
            likeBefore[index] = true;
            placed++;
        }
        StringBuilder result = new StringBuilder("Zoinks! Like, ");
        for (int index = 0; index < words.length; index++) {
            if (likeBefore[index]) {
                result.append("like, ");
            }
            result.append(words[index]);
            if (index < words.length - 1) {
                result.append(" ");
            }
        }
        return result.append(" Zoinks!").toString();
    }

    /**
     * Complains, reads it anyway, then complains again — with a different pair of complaints every
     * time.
     *
     * <p>It used to be one fixed opener and one fixed closer, which is a joke that lands once. The
     * voice is meant to be picked repeatedly across a round, and by the third sentence the player
     * is reciting along with it. Drawing an opener and a closer independently gives
     * {@code OPENERS.length * CLOSERS.length} combinations from two short lists, so the pairings
     * stay fresh far longer than either list alone.
     *
     * <p>Deliberately the <em>only</em> random voice. {@code forgetful} is keyed off word length
     * precisely so it reads the same way twice; that matters there because it mangles the words
     * themselves and a listener needs to be able to ask "what?" and hear the same thing again.
     * Disobedient never touches the sentence — it only wraps it — so re-reading still delivers the
     * same content.
     */
    private static String disobedient(String ifThen, Random random) {
        Random source = random == null ? new Random() : random;
        String opener = DISOBEDIENT_OPENERS[source.nextInt(DISOBEDIENT_OPENERS.length)];
        String closer = DISOBEDIENT_CLOSERS[source.nextInt(DISOBEDIENT_CLOSERS.length)];
        return opener + " " + ifThen + " " + closer;
    }

    private static String pigLatin(String ifThen) {
        String[] words = ifThen.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(pigLatinWord(word));
        }
        return result.toString();
    }

    /**
     * Moves a word's whole opening consonant cluster to the end and adds "ay".
     *
     * <p>The previous version got four things wrong, all audible:
     * <ul>
     *   <li><b>Clusters longer than two letters.</b> It moved at most two, so "string" came out
     *       "ringstay" instead of "ingstray" and "three" came out "reethay" instead of "eethray".</li>
     *   <li><b>Punctuation.</b> It treated the raw whitespace-delimited token as the word, so
     *       "midnight," became "dnight,miay" - the comma ends up <em>inside</em> the word, which is
     *       where the mangled-sounding output came from. Leading and trailing punctuation is now
     *       split off and put back around the result.</li>
     *   <li><b>Capitals.</b> "What" became "atWhay" with a capital mid-word. The word is converted
     *       in lower case and re-capitalized at the front if it started that way.</li>
     *   <li><b>"qu".</b> The "u" was read as the first vowel, so "queen" became "ueenqay" rather
     *       than "eenquay" - "qu" moves as a unit.</li>
     * </ul>
     *
     * <p>"y" counts as a consonant only at the start of a word ("yellow" -> "ellowyay") and as a
     * vowel anywhere after that, which is what stops "my" being treated as consonants all the way
     * through.
     */
    private static String pigLatinWord(String word) {
        int coreStart = 0;
        while (coreStart < word.length() && !Character.isLetter(word.charAt(coreStart))) {
            coreStart++;
        }
        int coreEnd = word.length();
        while (coreEnd > coreStart && !Character.isLetter(word.charAt(coreEnd - 1))) {
            coreEnd--;
        }
        if (coreStart >= coreEnd) {
            return word;
        }
        String leading = word.substring(0, coreStart);
        String core = word.substring(coreStart, coreEnd);
        String trailing = word.substring(coreEnd);

        String lower = core.toLowerCase();
        int cluster = 0;
        while (cluster < lower.length() && isConsonantAt(lower, cluster)) {
            cluster++;
        }
        if (cluster > 0 && cluster < lower.length()
                && lower.charAt(cluster - 1) == 'q' && lower.charAt(cluster) == 'u') {
            cluster++;
        }

        String pigged;
        if (cluster == 0) {
            pigged = lower + "way";
        }
        else if (cluster >= lower.length()) {
            // No vowel anywhere ("hmm", "tsk") - there is nothing to move it in front of.
            pigged = lower + "ay";
        }
        else {
            pigged = lower.substring(cluster) + lower.substring(0, cluster) + "ay";
        }
        if (Character.isUpperCase(core.charAt(0)) && pigged.length() > 0) {
            pigged = Character.toUpperCase(pigged.charAt(0)) + pigged.substring(1);
        }
        return leading + pigged + trailing;
    }

    private static boolean isConsonantAt(String lowerWord, int index) {
        char character = lowerWord.charAt(index);
        if (!Character.isLetter(character)) {
            return false;
        }
        if ("aeiou".indexOf(character) >= 0) {
            return false;
        }
        return character != 'y' || index == 0;
    }

    /**
     * Says the sentence back to front — every word intact and pronounced normally, just delivered
     * last word first.
     *
     * <p>This used to reverse the letters <em>inside</em> each word, which is a fine joke to read
     * and a terrible one to hear: text-to-speech pronounces "etov" as noise, so the listener gets
     * gibberish with no sentence underneath it to reconstruct. Reversing the word order instead
     * keeps every word recognizable, which is what makes it a puzzle rather than static.
     *
     * <p>Sentence punctuation is dropped rather than carried along with its word. Reversing puts
     * the closing full stop in front of the first word spoken, where it reads as a pause before
     * the sentence has started, and a trailing question mark lifts the pitch on entirely the wrong
     * syllable. Apostrophes are kept, so contractions still say themselves.
     */
    private static String backwords(String ifThen) {
        // The If half and the Then half are reversed separately and stay in that order. Reversing
        // the whole thing as one run put the Then before the What-if, which stops being a sentence
        // read backwards and starts being two answers in the wrong order - the listener loses the
        // setup before they have anything to attach it to.
        int boundary = ifThen.indexOf('?');
        if (boundary < 0) {
            return terminate(reverseWordOrder(ifThen));
        }
        String ifHalf = reverseWordOrder(ifThen.substring(0, boundary + 1));
        String thenHalf = reverseWordOrder(ifThen.substring(boundary + 1));
        if (ifHalf.length() == 0) {
            return terminate(thenHalf);
        }
        if (thenHalf.length() == 0) {
            return terminate(ifHalf);
        }
        return terminate(ifHalf + ", " + thenHalf);
    }

    private static String reverseWordOrder(String half) {
        String[] words = half.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int index = words.length - 1; index >= 0; index--) {
            String word = stripSentencePunctuation(words[index]);
            if (word.length() == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(word);
        }
        return result.toString();
    }

    private static String terminate(String spoken) {
        return spoken.length() == 0 ? spoken : spoken + ".";
    }

    private static String stripSentencePunctuation(String word) {
        StringBuilder kept = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            if (",.?!;:".indexOf(character) < 0) {
                kept.append(character);
            }
        }
        return kept.toString();
    }

    private static String cleanSentence(String sentence) {
        if (sentence == null) {
            return "";
        }
        return sentence.replaceAll("\\p{Punct}", "")
                .replaceAll("\\s+$", "")
                .replaceAll("^\\s+", "");
    }

    private static String removeLeadingPhrase(String sentence, String phrase) {
        if (sentence == null || sentence.length() == 0) {
            return "";
        }
        String lowerSentence = sentence.toLowerCase();
        String lowerPhrase = phrase.toLowerCase();
        if (lowerSentence.equals(lowerPhrase)) {
            return "";
        }
        if (lowerSentence.startsWith(lowerPhrase + " ")) {
            return sentence.substring(phrase.length()).trim();
        }
        return sentence;
    }
}
