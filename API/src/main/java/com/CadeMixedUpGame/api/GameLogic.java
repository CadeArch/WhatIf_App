package com.CadeMixedUpGame.api;

import java.util.List;

public class GameLogic {
    public static String cleanIfSentence(String sentence) {
        String cleaned = cleanSentence(sentence);
        if (cleaned.length() == 0) {
            return cleaned;
        }
        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
    }

    public static String cleanThenSentence(String sentence) {
        return cleanSentence(sentence);
    }

    public static int nextPlayerIndex(int currentIndex, int playerCount) {
        if (playerCount <= 0) {
            return -1;
        }
        return (currentIndex + 1) % playerCount;
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
        return ifThen;
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
}
