package com.CadeMixedUpGame.phoneapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;

import android.os.Bundle;

import com.CadeMixedUpGame.api.viewmodels.UserViewModel;


public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // TODO check to see if this disables dark mode in application
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // which fragment to display first
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, FirstFrag.class, null)
                    .setReorderingAllowed(true)
                    .commit();
        }

    }

    @Override
    public void onBackPressed() {
        // by not calling the below i am disabling the phones back button for all fragments
//        super.onBackPressed();

    }
}