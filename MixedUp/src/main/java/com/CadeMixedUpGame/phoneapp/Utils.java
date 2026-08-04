package com.CadeMixedUpGame.phoneapp;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.CadeMixedUpGame.api.AppLog;

public class Utils {

    private static final float GRID_MIN_ITEM_WIDTH_DP = 320f;
    private static final int GRID_MAX_SPAN_COUNT = 4;

    /**
     * Column count for player-list/answer grids, sized to available width instead of a
     * fixed constant, so the same grid uses fewer columns in portrait's narrower viewport
     * and more on wide landscape/tablet screens.
     */
    public static int computeSpanCount(Context context) {
        if (context == null) {
            return 2;
        }
        float density = context.getResources().getDisplayMetrics().density;
        float widthDp = context.getResources().getDisplayMetrics().widthPixels / density;
        int span = (int) (widthDp / GRID_MIN_ITEM_WIDTH_DP);
        return Math.max(1, Math.min(GRID_MAX_SPAN_COUNT, span));
    }

    public static void navigateToFragment(FragmentActivity activity, Class<? extends Fragment> fragmentClass) {
        if (activity != null) {
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragmentClass, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null) // Or a specific name if needed
                    .commit();
        }
    }

    public static void navigateHomeReplacingCurrent(FragmentActivity activity) {
        if (activity == null) {
            return;
        }
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, StartFragment.class, null)
                .setReorderingAllowed(true)
                .commitNowAllowingStateLoss();
    }

    public static void navigateLandingReplacingCurrent(FragmentActivity activity) {
        if (activity == null) {
            return;
        }
        activity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, FirstFrag.class, null)
                .setReorderingAllowed(true)
                .commitNowAllowingStateLoss();
    }

    public static String currentFragmentName(FragmentActivity activity) {
        if (activity == null) {
            return "unknown";
        }
        Fragment fragment = activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        return fragment == null ? "none" : fragment.getClass().getSimpleName();
    }

    public static void clickButtonOnKeyboardSubmit(TextView input, View button, String logMessage) {
        if (input == null || button == null) {
            return;
        }
        input.setSingleLine(true);
        input.setOnEditorActionListener((textView, actionId, event) -> {
            boolean actionSubmit = actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_SEND;
            boolean enterSubmit = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (!actionSubmit && !enterSubmit) {
                return false;
            }
            if (button.isEnabled()) {
                AppLog.d(AppLog.UI, logMessage);
                button.performClick();
            }
            return true;
        });
    }

}
