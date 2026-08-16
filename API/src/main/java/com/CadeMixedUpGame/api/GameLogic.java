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
        if ("1".equals(code)) {
            return ifThen.replace("r", "w");
        }
        if ("2".equals(code)) {
            return pigLatin(ifThen);
        }
        if ("3".equals(code)) {
            return reverseWords(ifThen);
        }
        if ("4".equals(code)) {
            return jokester(ifThen);
        }
        if ("5".equals(code)) {
            return forgetful(ifThen);
        }
        if ("6".equals(code)) {
            return shaggy(ifThen);
        }
        if ("7".equals(code)) {
            return disobedient(ifThen);
        }
        return ifThen;
    }

    /** Punctuates the sentence with laughter, so the reader can't get through it straight. */
    private static String jokester(String ifThen) {
        return ifThen.replace(", ", ", heh heh, ") + " ha ha ha!";
    }

    /**
     * Trails off on the longer words, as if the reader keeps losing the thread. Deterministic on
     * word length rather than random so the same sentence always reads the same way - a voice that
     * changed every time it was read would be untestable and would feel broken to the player.
     */
    private static String forgetful(String ifThen) {
        String[] words = ifThen.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (word.length() >= 6) {
                result.append(word, 0, 3).append("... uh... what was it... ").append(word);
            } else {
                result.append(word);
            }
        }
        return result.toString();
    }

    /** Stoner-detective delivery: "like" wedged in, and a "Zoinks!" on the front. */
    private static String shaggy(String ifThen) {
        return "Zoinks! Like, " + ifThen.replace(", ", ", like, ");
    }

    /** Reads the sentence, then immediately refuses to have read it. */
    private static String disobedient(String ifThen) {
        return "No. I'm not reading that. " + ifThen + ". There, happy?";
    }

    private static String pigLatin(String ifThen) {
        String[] parts = ifThen.split(",");
        StringBuilder result = new StringBuilder();
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            String[] words = parts[partIndex].trim().split(" ");
            for (String word : words) {
                if (word.length() == 0) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(pigLatinWord(word));
            }
            if (partIndex < parts.length - 1) {
                result.append(", ");
            }
        }
        return result.toString().replace(",  ", ", ");
    }

    private static String pigLatinWord(String word) {
        if (startsWithVowel(word)) {
            return word + "way";
        }
        if (word.length() == 1) {
            return word + "ay";
        }
        if (startsWithVowel(word.substring(1))) {
            return word.substring(1) + word.charAt(0) + "ay";
        }
        return word.substring(2) + word.substring(0, 2) + "ay";
    }

    private static boolean startsWithVowel(String word) {
        if (word == null || word.length() == 0) {
            return false;
        }
        String first = word.substring(0, 1);
        return first.equalsIgnoreCase("a")
                || first.equalsIgnoreCase("e")
                || first.equalsIgnoreCase("i")
                || first.equalsIgnoreCase("o")
                || first.equalsIgnoreCase("u");
    }

    private static String reverseWords(String ifThen) {
        String[] parts = ifThen.split(",");
        StringBuilder result = new StringBuilder();
        for (int partIndex = 0; partIndex < parts.length; partIndex++) {
            String[] words = parts[partIndex].trim().split(" ");
            for (String word : words) {
                if (word.length() == 0) {
                    continue;
                }
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(new StringBuilder(word).reverse());
            }
            if (partIndex < parts.length - 1) {
                result.append(", ");
            }
        }
        return result.toString().replace(",  ", ", ");
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
