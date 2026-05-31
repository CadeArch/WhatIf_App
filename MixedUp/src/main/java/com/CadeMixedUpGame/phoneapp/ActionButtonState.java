package com.CadeMixedUpGame.phoneapp;

import android.view.View;

public class ActionButtonState {
    private static final float ENABLED_ALPHA = 1.0f;
    private static final float DISABLED_ALPHA = 0.35f;
    private static final float SAVING_ALPHA = 0.45f;

    private ActionButtonState() {
    }

    public static void setEnabled(View button, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setEnabled(enabled);
        button.setAlpha(enabled ? ENABLED_ALPHA : DISABLED_ALPHA);
    }

    public static void setSaving(View button, boolean saving) {
        setSaving(button, saving, true);
    }

    public static void setSaving(View button, boolean saving, boolean enabledWhenDone) {
        if (button == null) {
            return;
        }
        if (saving) {
            button.setEnabled(false);
            button.setAlpha(SAVING_ALPHA);
            return;
        }
        setEnabled(button, enabledWhenDone);
    }
}
