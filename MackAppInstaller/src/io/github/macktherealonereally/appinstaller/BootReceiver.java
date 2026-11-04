/*
 * SPDX-FileCopyrightText: 2026 Macktherealonereally
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.macktherealonereally.appinstaller;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.UserManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Installs the bundled Aurora Store (assets/AuroraStore.apk, Aurora's own
 * signature) once per user as a normal user app, so the user can uninstall,
 * downgrade or replace it. The state lives in this app's per-user data: once
 * done, it never installs again, also not after reboots or OTA updates.
 */
public class BootReceiver extends BroadcastReceiver {
    static final String TAG = "MackAppInstaller";
    static final String PACKAGE = "com.aurora.store";
    static final String ASSET = "AuroraStore.apk";
    static final int MAX_ATTEMPTS = 3;

    private static final String PREFS = "state";
    private static final String KEY_DONE = "done";
    private static final String KEY_ATTEMPTS = "attempts";

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void markDone(Context context, String why) {
        Log.i(TAG, PACKAGE + ": " + why + "; done, will not install again for this user");
        prefs(context).edit().putBoolean(KEY_DONE, true).commit();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences prefs = prefs(context);
        if (prefs.getBoolean(KEY_DONE, false)) {
            return;
        }
        String skip = reasonToSkip(context, prefs.getInt(KEY_ATTEMPTS, 0));
        if (skip != null) {
            markDone(context, skip);
            return;
        }
        // Count the attempt before starting it, so a crash cannot loop.
        final int attempt = prefs.getInt(KEY_ATTEMPTS, 0) + 1;
        prefs.edit().putInt(KEY_ATTEMPTS, attempt).commit();

        final PendingResult pending = goAsync();
        new Thread(() -> {
            try {
                install(context);
                Log.i(TAG, PACKAGE + ": install committed (attempt " + attempt + ")");
            } catch (IOException | RuntimeException e) {
                Log.e(TAG, PACKAGE + ": attempt " + attempt + " of " + MAX_ATTEMPTS
                        + " failed, next try on the next boot", e);
            } finally {
                pending.finish();
            }
        }, TAG).start();
    }

    /** Why not to install for this user, or null to install. */
    private static String reasonToSkip(Context context, int attempts) {
        UserManager um = context.getSystemService(UserManager.class);
        if (um.isProfile()) {
            // work profile, clone or private space: the parent user has it
            return "profile user, skipped";
        }
        if (um.hasUserRestriction(UserManager.DISALLOW_INSTALL_APPS)) {
            return "app installs are disallowed for this user, skipped";
        }
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return "already installed for this user";
        } catch (PackageManager.NameNotFoundException e) {
            // not installed for this user
        }
        if (attempts >= MAX_ATTEMPTS) {
            return "gave up after " + attempts + " failed attempts";
        }
        return null;
    }

    private static void install(Context context) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        // Drop a session left over from an attempt that did not finish.
        for (PackageInstaller.SessionInfo old : installer.getMySessions()) {
            abandonQuietly(installer, old.getSessionId());
        }
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(PACKAGE);
        int id = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(id)) {
            try (InputStream in = context.getAssets().open(ASSET);
                    OutputStream out = session.openWrite("base.apk", 0, -1)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                session.fsync(out);
            }
            Intent result = new Intent(context, InstallResultReceiver.class);
            // Mutable: PackageInstaller adds the status extras. Explicit intent.
            PendingIntent sender = PendingIntent.getBroadcast(context, id, result,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            session.commit(sender.getIntentSender());
        } catch (IOException | RuntimeException e) {
            abandonQuietly(installer, id);
            throw e;
        }
    }

    static void abandonQuietly(PackageInstaller installer, int id) {
        try {
            installer.abandonSession(id);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not abandon session " + id, e);
        }
    }
}
