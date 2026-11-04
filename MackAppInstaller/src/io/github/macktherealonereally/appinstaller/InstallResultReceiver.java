/*
 * SPDX-FileCopyrightText: 2026 Macktherealonereally
 * SPDX-License-Identifier: Apache-2.0
 */

package io.github.macktherealonereally.appinstaller;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/** Result of the PackageInstaller session that BootReceiver committed. */
public class InstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            BootReceiver.markDone(context, "installed");
            return;
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Only without INSTALL_PACKAGES. Never ask the user from a boot
            // receiver: drop the session, it counts as a failed attempt.
            BootReceiver.abandonQuietly(context.getPackageManager().getPackageInstaller(),
                    intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1));
        }
        Log.w(BootReceiver.TAG, BootReceiver.PACKAGE + ": install failed, status "
                + status + " (" + message + "), next try on the next boot (at most "
                + BootReceiver.MAX_ATTEMPTS + " attempts)");
    }
}
