package com.CadeMixedUpGame.phoneapp;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

public class UiMessenger {
    public enum MessageType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }

    public static void showSnackbar(View root, String message) {
        if (root == null || message == null || message.length() == 0) {
            return;
        }
        Snackbar.make(root, message, Snackbar.LENGTH_SHORT).show();
    }

    public static void showError(EditText field, String message) {
        if (field == null) {
            return;
        }
        field.setError(message);
        field.requestFocus();
    }

    public static void clearError(EditText field) {
        if (field != null) {
            field.setError(null);
        }
    }

    public static void showBanner(View root, String message, MessageType type) {
        if (root == null || message == null || message.length() == 0) {
            return;
        }
        View banner = root.findViewById(R.id.message_banner);
        TextView text = root.findViewById(R.id.message_banner_text);
        if (banner == null || text == null) {
            showSnackbar(root, message);
            return;
        }

        text.setText(message);
        banner.setBackground(makeBackground(type));
        banner.setVisibility(View.VISIBLE);

        View dismiss = root.findViewById(R.id.message_banner_dismiss);
        if (dismiss != null) {
            dismiss.setOnClickListener(v -> hideBanner(root));
        }
    }

    public static void hideBanner(View root) {
        if (root == null) {
            return;
        }
        View banner = root.findViewById(R.id.message_banner);
        if (banner != null) {
            banner.setVisibility(View.GONE);
        }
    }

    private static GradientDrawable makeBackground(MessageType type) {
        int color;
        switch (type) {
            case SUCCESS:
                color = Color.rgb(50, 130, 86);
                break;
            case WARNING:
                color = Color.rgb(196, 121, 29);
                break;
            case ERROR:
                color = Color.rgb(166, 57, 57);
                break;
            case INFO:
            default:
                color = Color.rgb(68, 96, 135);
                break;
        }
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12f);
        return drawable;
    }
}
