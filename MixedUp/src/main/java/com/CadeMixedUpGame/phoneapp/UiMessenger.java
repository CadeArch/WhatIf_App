package com.CadeMixedUpGame.phoneapp;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;

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

    public static void observeSnackbar(LifecycleOwner owner, MutableLiveData<String> message, View root) {
        observeSnackbar(owner, message, root, null);
    }

    public static void observeSnackbar(LifecycleOwner owner, MutableLiveData<String> message, View root, Runnable afterShown) {
        if (owner == null || message == null || root == null) {
            return;
        }
        message.observe(owner, value -> {
            if (value == null || value.length() == 0) {
                return;
            }
            showSnackbar(root, value);
            message.setValue("");
            if (afterShown != null) {
                afterShown.run();
            }
        });
    }

    public static void observeBanner(LifecycleOwner owner, MutableLiveData<String> message, View root, MessageType type) {
        if (owner == null || message == null || root == null) {
            return;
        }
        message.observe(owner, value -> {
            if (value == null || value.length() == 0) {
                return;
            }
            showBanner(root, value, type);
            message.setValue("");
        });
    }

    public static void showTopSnackbar(View root, String message) {
        if (root == null || message == null || message.length() == 0) {
            return;
        }
        Snackbar snackbar = Snackbar.make(root, message, Snackbar.LENGTH_SHORT);
        View snackbarView = snackbar.getView();
        ViewGroup.LayoutParams params = snackbarView.getLayoutParams();
        if (params instanceof CoordinatorLayout.LayoutParams) {
            CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) params;
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            layoutParams.topMargin = 8;
            snackbarView.setLayoutParams(layoutParams);
        }
        else if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) params;
            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            layoutParams.topMargin = 8;
            snackbarView.setLayoutParams(layoutParams);
        }
        snackbar.show();
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
                // Darkened from rgb(50,130,86) - that only cleared 4.5:1 by a hair (~4.7:1).
                color = Color.rgb(28, 108, 66);
                break;
            case WARNING:
                // Was rgb(196,121,29) - the same "orange" flagged elsewhere for failing 4.5:1
                // against white text (~3.4:1 actual). Matches orange_deep, ~5.6:1.
                color = Color.rgb(147, 91, 22);
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
