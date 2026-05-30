package com.CadeMixedUpGame.phoneapp;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.common.internal.safeparcel.SafeParcelable;

public class Utils {

    public static void navigateToFragment(FragmentActivity activity, java.lang.Class fragmentClass) {
        if (activity != null) {
            activity.getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragmentClass, null)
                    .setReorderingAllowed(true)
                    .addToBackStack(null) // Or a specific name if needed
                    .commit();
        }
    }

}
