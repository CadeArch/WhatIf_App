package com.CadeMixedUpGame.api;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Points the app at the local Firebase Emulator Suite (see firebase.json at the repo root)
 * instead of the live production project. Opt-in only — see MixedUp/build.gradle's
 * USE_FIREBASE_EMULATOR flag.
 */
public final class FirebaseEmulatorConfig {
    // Real device/emulator: 10.0.2.2 is the special host-loopback alias. Robolectric JVM tests
    // run directly on the host machine (no virtual device network layer), so "localhost" is
    // correct there instead - see configureIfEnabled(FirebaseApp, String, boolean).
    private static final String EMULATOR_HOST = "10.0.2.2";
    private static final int DATABASE_EMULATOR_PORT = 9000;
    private static final int AUTH_EMULATOR_PORT = 9099;

    private FirebaseEmulatorConfig() {
    }

    /**
     * Creates the default FirebaseApp, pointing RTDB at the local emulator when {@code enabled}.
     * Must be the first Firebase call in the process — debug builds remove FirebaseInitProvider
     * (see MixedUp/src/debug/AndroidManifest.xml) so that this runs instead of the SDK's auto-init.
     *
     * <p>The emulator URL has to be baked into FirebaseOptions <em>before</em> the app is created,
     * because neither of the two obvious alternatives works:
     * <ul>
     *   <li>{@code FirebaseDatabase.useEmulator()} appears to succeed and then silently doesn't
     *       stick. Proven on-device: immediately after the call {@code getInstance()} returned an
     *       instance pointing at {@code http://10.0.2.2:9000}, but 0.7s later (same process, same
     *       FirebaseApp, same FirebaseOptions) MainActivity's RoomViewModel received a
     *       <em>different</em> instance pointing back at production. Every read/write went to the
     *       live project while the "Using local Firebase Emulator Suite" log line claimed
     *       otherwise — this silently wrote real Tier B test rooms into production for days.</li>
     *   <li>Deleting the auto-created app to re-initialize it with new options crashes the process
     *       with {@code IllegalStateException: FirebaseApp was deleted}, because Firebase
     *       Installations is already using it on a background thread by then.</li>
     * </ul>
     *
     * <p>Auth still uses {@code useEmulator()}: FirebaseOptions has no auth-endpoint field, and
     * FirebaseAuth does not exhibit the instance-swapping problem above.
     */
    public static void initializeDefaultApp(Context context, boolean enabled) {
        Context appContext = context.getApplicationContext();
        if (!FirebaseApp.getApps(appContext).isEmpty()) {
            // Release builds (FirebaseInitProvider still present) already have the default app.
            configureAuthIfEnabled(enabled);
            return;
        }

        FirebaseOptions options = FirebaseOptions.fromResource(appContext);
        if (options == null) {
            AppLog.w(AppLog.FIREBASE, "No Firebase options resource; skipping Firebase initialization");
            return;
        }
        if (enabled) {
            options = new FirebaseOptions.Builder(options)
                    .setDatabaseUrl(emulatorDatabaseUrl(options))
                    .build();
        }
        FirebaseApp.initializeApp(appContext, options);
        configureAuthIfEnabled(enabled);
        if (enabled) {
            AppLog.i(AppLog.FIREBASE, "Using local Firebase Emulator Suite (database="
                    + options.getDatabaseUrl() + ", auth=" + EMULATOR_HOST + ":" + AUTH_EMULATOR_PORT + ")");
        }
    }

    private static void configureAuthIfEnabled(boolean enabled) {
        if (!enabled) {
            return;
        }
        try {
            FirebaseAuth.getInstance(FirebaseApp.getInstance()).useEmulator(EMULATOR_HOST, AUTH_EMULATOR_PORT);
        }
        catch (IllegalStateException e) {
            AppLog.w(AppLog.FIREBASE, "Default FirebaseApp not ready; skipping auth emulator config for this session");
        }
    }

    /** The emulator serves each project under a namespace matching its production RTDB instance,
     * so the namespace has to be carried across from the real databaseUrl (or derived from the
     * project id, for a google-services.json that omits the URL as this project's once did). */
    private static String emulatorDatabaseUrl(FirebaseOptions options) {
        return "http://" + EMULATOR_HOST + ":" + DATABASE_EMULATOR_PORT + "/?ns=" + namespaceOf(options);
    }

    private static String namespaceOf(FirebaseOptions options) {
        String databaseUrl = options.getDatabaseUrl();
        if (databaseUrl != null) {
            int schemeEnd = databaseUrl.indexOf("://");
            String withoutScheme = schemeEnd < 0 ? databaseUrl : databaseUrl.substring(schemeEnd + 3);
            int hostEnd = withoutScheme.indexOf('/');
            String host = hostEnd < 0 ? withoutScheme : withoutScheme.substring(0, hostEnd);
            int dot = host.indexOf('.');
            if (dot > 0) {
                return host.substring(0, dot);
            }
        }
        return options.getProjectId() + "-default-rtdb";
    }

    /** For tests driving a specific (possibly non-default, e.g. named per simulated player)
     * FirebaseApp instance directly, where "host" is also caller-controlled (Robolectric JVM
     * tests use "localhost"; a real device/emulator uses the 10.0.2.2 loopback alias).
     *
     * <p>These callers build their own FirebaseOptions with an explicit demo-project databaseUrl
     * and keep using the FirebaseDatabase instance they configure here, so the instance-swapping
     * problem described on {@link #initializeDefaultApp(Context, boolean)} — which only shows up
     * via repeated no-arg {@code getInstance()} calls on the default app — doesn't affect them. */
    public static void configureIfEnabled(FirebaseApp app, String host, boolean enabled) {
        if (!enabled) {
            return;
        }
        FirebaseDatabase.getInstance(app).useEmulator(host, DATABASE_EMULATOR_PORT);
        FirebaseAuth.getInstance(app).useEmulator(host, AUTH_EMULATOR_PORT);
        AppLog.i(AppLog.FIREBASE, "Using local Firebase Emulator Suite at " + host
                + " (database:" + DATABASE_EMULATOR_PORT + ", auth:" + AUTH_EMULATOR_PORT
                + ", app:" + app.getName() + ")");
    }
}
