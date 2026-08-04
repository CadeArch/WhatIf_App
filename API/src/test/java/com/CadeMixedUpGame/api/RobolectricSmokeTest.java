package com.CadeMixedUpGame.api;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RobolectricSmokeTest {
    @Test
    public void applicationContextIsAvailable() {
        Context context = ApplicationProvider.getApplicationContext();
        assertNotNull(context);
    }
}
