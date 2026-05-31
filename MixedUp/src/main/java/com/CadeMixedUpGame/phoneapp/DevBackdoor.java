package com.CadeMixedUpGame.phoneapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import java.util.Random;

public class DevBackdoor {
    private static final Random RANDOM = new Random();

    private static final String[] IF_PROMPTS = {
            "pizza could talk",
            "homework was illegal",
            "clouds were made of mashed potatoes",
            "everyone had to walk backwards",
            "your shoes told the truth",
            "the moon got stage fright",
            "breakfast cereal became mayor",
            "gravity took a day off"
    };

    private static final String[] THEN_RESPONSES = {
            "everyone would blame the toaster",
            "the school bus would wear sunglasses",
            "my grandma would start a detective agency",
            "we would all pretend this was normal",
            "the dog would demand a lawyer",
            "someone would definitely bring nachos",
            "the principal would call a dance battle",
            "I would quietly move to the moon"
    };

    private static final String[] GUEST_NAMES = {
            "Noodle",
            "Pickle",
            "Waffles",
            "Banjo",
            "Muffin",
            "Biscuit",
            "Sprinkles",
            "Zippy"
    };

    private DevBackdoor() {
    }

    public static boolean isEnabled(Context context) {
        return context != null && (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    public static String randomIfPrompt() {
        return IF_PROMPTS[RANDOM.nextInt(IF_PROMPTS.length)];
    }

    public static String randomThenResponse() {
        return THEN_RESPONSES[RANDOM.nextInt(THEN_RESPONSES.length)];
    }

    public static String randomGuestName() {
        return GUEST_NAMES[RANDOM.nextInt(GUEST_NAMES.length)] + RANDOM.nextInt(100);
    }
}
